package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/**
 * Builds Online Music recommendations as a deliberate mix:
 *  - at least [SIMILAR_SHARE] (65%) "similar" tracks, i.e. what the listener is playing / usually plays, and
 *  - the remaining ~35% "discovery" tracks from unrelated genres/moods, so the listener keeps meeting
 *    music outside their usual category.
 *
 * Used by the auto-queue that follows a played online track and by the endless "Recommended for you" feed.
 * All network access goes through [OnlineMusicService.search]; [mix] itself is pure and unit-testable.
 */
object OnlineRecommendationMixer {

    const val SIMILAR_SHARE = 0.65

    /** Broad, deliberately different genres / moods used for the discovery share. */
    private val DISCOVERY_TOPICS = listOf(
        "lofi chill", "classical piano", "jazz", "bollywood", "punjabi", "electronic dance",
        "rock classics", "acoustic", "sufi", "ghazal", "k-pop", "reggae", "country",
        "old hindi", "instrumental", "r&b soul", "devotional bhajan", "latin pop", "indie",
        "heavy metal", "ambient", "afrobeats", "retro 80s", "carnatic", "hip hop", "blues",
        "folk", "orchestral cinematic", "japanese city pop", "arabic"
    )

    /** Suffixes so repeated batches for the same base query return different results. */
    private val QUERY_MODIFIERS = listOf(
        "songs", "best songs", "hits", "new songs", "top tracks", "unplugged", "live", "audio"
    )

    // Topics that belong to one family: if the listener is already in that family (Hindi / Indian music),
    // none of these count as "a different category" for the discovery share.
    private val INDIAN_TOPICS = setOf(
        "bollywood", "punjabi", "sufi", "ghazal", "old hindi", "devotional bhajan", "carnatic"
    )
    private val INDIAN_HINTS = listOf(
        "hindi", "bollywood", "punjabi", "sufi", "ghazal", "bhajan", "carnatic", "tamil", "telugu",
        "t-series", "zee music", "sony music india", "tips official"
    )

    private val GENERIC_SIMILAR_BASES = listOf("trending", "popular", "top hits", "new releases")

    /**
     * Combines two pools into one list of [total] items where ~[similarShare] come from [similar] and the
     * rest from [discovery]. Discovery items are spread evenly through the list (not bunched at the end).
     * If one pool is too small the other one fills the gap so the list stays as full as possible.
     */
    fun <T> mix(
        similar: List<T>,
        discovery: List<T>,
        total: Int,
        similarShare: Double = SIMILAR_SHARE
    ): List<T> {
        if (total <= 0) return emptyList()
        val wantSimilar = kotlin.math.ceil(total * similarShare - 1e-9).toInt().coerceIn(0, total)
        val wantDiscovery = total - wantSimilar

        var takeSimilar = minOf(wantSimilar, similar.size)
        var takeDiscovery = minOf(wantDiscovery, discovery.size)

        var missing = total - takeSimilar - takeDiscovery
        if (missing > 0) {
            val extra = minOf(missing, similar.size - takeSimilar)
            takeSimilar += extra
            missing -= extra
        }
        if (missing > 0) {
            takeDiscovery += minOf(missing, discovery.size - takeDiscovery)
        }

        val s = similar.take(takeSimilar)
        val d = discovery.take(takeDiscovery)
        val n = s.size + d.size
        val out = ArrayList<T>(n)
        var si = 0
        var di = 0
        for (pos in 0 until n) {
            val useDiscovery = when {
                di >= d.size -> false
                si >= s.size -> true
                // Bresenham-style spread: a discovery item is "due" once its proportional share is reached.
                else -> ((pos + 1) * d.size) / n > di
            }
            if (useDiscovery) out.add(d[di++]) else out.add(s[si++])
        }
        return out
    }

    /** Interleaves several lists (one item from each in turn) so no single query/artist dominates. */
    fun <T> roundRobin(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        var index = 0
        while (true) {
            var added = false
            for (list in lists) {
                if (index < list.size) {
                    out.add(list[index])
                    added = true
                }
            }
            if (!added) break
            index++
        }
        return out
    }

    /**
     * Discovery topics that do NOT resemble what the listener is currently on: a topic is skipped when
     * one of its significant words appears in [avoidText] (current title/artist, favourite artists).
     * Heuristic only - there is no genre metadata available from the search results.
     */
    fun eligibleDiscoveryTopics(avoidText: List<String>): List<String> {
        val haystack = avoidText.joinToString(" ").lowercase(Locale.ROOT)
        val inIndianFamily = INDIAN_HINTS.any { haystack.contains(it) }
        val eligible = DISCOVERY_TOPICS.filter { topic ->
            (!inIndianFamily || topic !in INDIAN_TOPICS) &&
                topic.split(' ', '&').filter { it.length >= 4 }.none { haystack.contains(it.lowercase(Locale.ROOT)) }
        }
        return if (eligible.size >= 3) eligible else DISCOVERY_TOPICS
    }

    /**
     * Picks a base+modifier query that was not used yet in [used] (and records it). When every
     * combination has been used, [used] is cleared so the feed can keep going forever.
     */
    fun freshQuery(bases: List<String>, used: MutableSet<String>, random: Random = Random.Default): String? {
        if (bases.isEmpty()) return null
        repeat(2) {
            for (base in bases.shuffled(random)) {
                for (modifier in QUERY_MODIFIERS.shuffled(random)) {
                    val q = "$base $modifier"
                    if (used.add(q)) return q
                }
            }
            used.clear()
        }
        return null
    }

    private suspend fun searchMany(queries: List<String>): List<List<OnlineTrackResult>> = coroutineScope {
        queries.map { q -> async(Dispatchers.IO) { OnlineMusicService.search(q) } }.awaitAll()
    }

    private fun List<List<OnlineTrackResult>>.cleaned(exclude: Set<String>): List<OnlineTrackResult> {
        val seen = HashSet<String>(exclude)
        return map { list ->
            list.filter {
                it.title.isNotBlank() &&
                    OnlineMusicService.isAudioOrMusicContent(it.title, it.artist, it.durationSec)
            }
        }.let { roundRobin(it) }.filter { seen.add(it.videoUrl) }
    }

    /** Tracks from genres unlike [avoidText]; [exclude] holds video URLs that must not be returned. */
    suspend fun fetchDiscoveryTracks(
        avoidText: List<String>,
        exclude: Set<String>,
        usedQueries: MutableSet<String>,
        queryCount: Int = 3
    ): List<OnlineTrackResult> = withContext(Dispatchers.IO) {
        val bases = eligibleDiscoveryTopics(avoidText)
        val queries = List(queryCount) { freshQuery(bases, usedQueries) }.filterNotNull()
        searchMany(queries).cleaned(exclude)
    }

    /**
     * One batch of the endless "Recommended for you" feed: [SIMILAR_SHARE] from the listener's favourite
     * artists (or generic trending queries when there is no history yet), the rest from unrelated genres.
     * Call repeatedly with the same [usedQueries] to keep receiving fresh tracks.
     */
    suspend fun fetchFeedBatch(
        topArtists: List<String>,
        excludeUrls: Set<String>,
        usedQueries: MutableSet<String>,
        batchSize: Int = 24
    ): List<OnlineTrackResult> = withContext(Dispatchers.IO) {
        val similarBases = if (topArtists.isNotEmpty()) topArtists else GENERIC_SIMILAR_BASES
        val similarQueries = List(2) { freshQuery(similarBases, usedQueries) }.filterNotNull()
        val discoveryBases = eligibleDiscoveryTopics(topArtists)
        val discoveryQueries = List(3) { freshQuery(discoveryBases, usedQueries) }.filterNotNull()

        // Both groups of searches run at the same time.
        val (similarRaw, discoveryRaw) = coroutineScope {
            val similarJob = async { searchMany(similarQueries) }
            val discoveryJob = async { searchMany(discoveryQueries) }
            similarJob.await() to discoveryJob.await()
        }
        val similar = similarRaw.cleaned(excludeUrls)
        val discovery = discoveryRaw.cleaned(excludeUrls + similar.map { it.videoUrl })
        mix(similar, discovery, batchSize)
    }
}
