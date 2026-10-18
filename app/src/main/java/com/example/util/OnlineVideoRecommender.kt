package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * YouTube-like personalised feed for Online Video.
 *
 * Signals (all stored locally): channels the user watched (count), channels the user searched, and which videos were
 * shown but never opened ("ignored"). A brand-new user (no history) gets a broad, mixed "fresh account" feed.
 * Rules: ~65% from the user's taste / ~35% discovery, no channel repeated back-to-back, a channel is capped per batch,
 * and videos that were shown several times without a tap are not shown again.
 */
object OnlineVideoRecommender {
    private const val PREFS = "lumora_online_video_history"
    private const val KEY_WATCHED = "watched_channels"      // JSON object: channel -> count
    private const val KEY_SEARCHED = "searched_channels"    // JSON array, most recent first
    private const val KEY_SHOWN = "shown_counts"            // JSON object: url -> times shown
    private const val KEY_OPENED = "opened_urls"            // JSON array
    private const val KEY_COMPLETED = "completed_urls"      // JSON array: videos watched to the end
    private const val MAX_COMPLETED = 3000
    private const val SUBSCRIBED_QUOTA = 6                  // max videos from subscribed channels per feed batch
    private const val SUBSCRIBED_CHANNELS_PER_BATCH = 12
    private const val MAX_SHOWN = 800
    private const val IGNORE_AFTER = 3

    private val DISCOVERY_TOPICS = listOf(
        "trending today", "music video", "podcast episode", "tech review", "documentary", "cooking recipe",
        "travel vlog", "movie trailer", "science explained", "sports highlights", "comedy sketch", "news today",
        "gaming gameplay", "DIY project", "fitness workout", "history explained", "short film", "street food",
        "car review", "stand up comedy", "nature 4k", "cricket highlights", "bollywood", "anime"
    )

    // ---------- storage ----------
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readObj(c: Context, key: String): JSONObject =
        try { JSONObject(prefs(c).getString(key, "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }

    private fun readArr(c: Context, key: String): JSONArray =
        try { JSONArray(prefs(c).getString(key, "[]") ?: "[]") } catch (_: Throwable) { JSONArray() }

    @Synchronized
    fun recordOpened(c: Context, videoUrl: String, channel: String, title: String = "") {
        // what the video is about (Minecraft, tech review, ...) steers the next recommendations
        if (title.isNotBlank()) OnlineInterestProfile.record(c, OnlineInterestProfile.KIND_VIDEO, title, 3.0)
        val opened = readArr(c, KEY_OPENED)
        val list = (0 until opened.length()).map { opened.optString(it) }.toMutableList()
        if (!list.contains(videoUrl)) list.add(videoUrl)
        val trimmed = if (list.size > 1500) list.takeLast(1500) else list
        val watched = readObj(c, KEY_WATCHED)
        if (channel.isNotBlank()) watched.put(channel, watched.optInt(channel, 0) + 1)
        prefs(c).edit()
            .putString(KEY_OPENED, JSONArray(trimmed).toString())
            .putString(KEY_WATCHED, watched.toString())
            .apply()
    }

    // In-memory copy of the completed list so playback ticks can call recordCompleted() cheaply.
    @Volatile private var completedCache: MutableSet<String>? = null

    private fun completedSet(c: Context): MutableSet<String> {
        completedCache?.let { return it }
        return synchronized(this) {
            completedCache ?: run {
                val arr = readArr(c, KEY_COMPLETED)
                val set = LinkedHashSet<String>()
                for (i in 0 until arr.length()) set.add(arr.optString(i))
                completedCache = set
                set
            }
        }
    }

    /** Marks an online video as watched to the end; it is not recommended again afterwards. */
    fun recordCompleted(c: Context, videoUrl: String) {
        if (videoUrl.isBlank()) return
        synchronized(this) {
            val set = completedSet(c)
            if (!set.add(videoUrl)) return
            while (set.size > MAX_COMPLETED) set.remove(set.first())
            prefs(c).edit().putString(KEY_COMPLETED, JSONArray(set.toList()).toString()).apply()
        }
    }

    fun isCompleted(c: Context, videoUrl: String): Boolean = videoUrl in completedSet(c)

    @Synchronized
    fun recordChannelSearch(c: Context, channelName: String) {
        if (channelName.isBlank()) return
        val arr = readArr(c, KEY_SEARCHED)
        val list = (0 until arr.length()).map { arr.optString(it) }.filter { !it.equals(channelName, true) }.toMutableList()
        list.add(0, channelName)
        prefs(c).edit().putString(KEY_SEARCHED, JSONArray(list.take(20)).toString()).apply()
    }

    /** A real video search by the user: what they look for counts as interest too. */
    fun recordSearch(c: Context, query: String) {
        if (query.isBlank()) return
        OnlineInterestProfile.record(c, OnlineInterestProfile.KIND_VIDEO, query, 2.0)
    }

    /** Call with the urls that were actually put on screen in a feed batch. */
    @Synchronized
    fun recordShown(c: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val shown = readObj(c, KEY_SHOWN)
        urls.forEach { shown.put(it, shown.optInt(it, 0) + 1) }
        if (shown.length() > MAX_SHOWN) {
            val keys = shown.keys().asSequence().toList()
            keys.take(shown.length() - MAX_SHOWN).forEach { shown.remove(it) }
        }
        prefs(c).edit().putString(KEY_SHOWN, shown.toString()).apply()
    }

    private fun topChannels(c: Context, limit: Int): List<String> {
        val watched = readObj(c, KEY_WATCHED)
        val byCount = watched.keys().asSequence().map { it to watched.optInt(it) }
            .sortedByDescending { it.second }.map { it.first }.toList()
        val searched = readArr(c, KEY_SEARCHED).let { a -> (0 until a.length()).map { a.optString(it) } }
        return (byCount.take(limit) + searched.take(3)).distinct().take(limit + 2)
    }

    // ---------- feed ----------
    /**
     * One batch of feed videos. [excludeUrls] = already on screen, [usedQueries] is shared between calls so
     * repeated batches (infinite scroll) keep returning different videos.
     */
    suspend fun fetchFeedBatch(
        context: Context,
        excludeUrls: Set<String>,
        usedQueries: MutableSet<String>,
        total: Int = 20
    ): List<OnlineTrackResult> = coroutineScope {
        val favs = topChannels(context, 4)
        val shown = readObj(context, KEY_SHOWN)
        val opened = readArr(context, KEY_OPENED).let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }

        val focus = OnlineInterestProfile.focus(context, OnlineInterestProfile.KIND_VIDEO, 2)
        if (focus.isNotEmpty()) {
            return@coroutineScope fetchInterestBatch(context, focus, excludeUrls, usedQueries, total, shown, opened)
        }

        val similarQueries = if (favs.isEmpty()) emptyList() else
            List(minOf(3, favs.size + 1)) { OnlineRecommendationMixer.freshQuery(favs, usedQueries) }.filterNotNull()
        val discoveryQueries = List(if (favs.isEmpty()) 5 else 3) {
            OnlineRecommendationMixer.freshQuery(DISCOVERY_TOPICS, usedQueries)
        }.filterNotNull()

        suspend fun run(queries: List<String>): List<List<OnlineTrackResult>> =
            queries.map { q -> async(Dispatchers.IO) { OnlineMusicService.search(q) } }.awaitAll()

        val similarRaw = run(similarQueries)
        val discoveryRaw = run(discoveryQueries)

        fun ok(r: OnlineTrackResult): Boolean =
            r.videoUrl.isNotBlank() && r.title.isNotBlank() &&
                r.videoUrl !in excludeUrls &&
                r.durationSec >= 60L && r.durationSec <= 4 * 3600L &&
                !isCompleted(context, r.videoUrl) &&
                !(shown.optInt(r.videoUrl, 0) >= IGNORE_AFTER && r.videoUrl !in opened)

        // per query: drop bad ones, cap each channel to 2 so one channel cannot flood a query's results
        fun clean(lists: List<List<OnlineTrackResult>>): List<List<OnlineTrackResult>> = lists.map { l ->
            val perChannel = HashMap<String, Int>()
            l.filter { ok(it) }.filter {
                val key = it.artist.lowercase(Locale.ROOT)
                val n = perChannel.getOrDefault(key, 0)
                perChannel[key] = n + 1
                n < 2
            }
        }

        val subscribed = fetchSubscribedUploads(context, excludeUrls)
        val subscribedUrls = subscribed.map { it.videoUrl }.toSet()

        val similar = OnlineRecommendationMixer.roundRobin(clean(similarRaw)).distinctBy { it.videoUrl }
            .filter { it.videoUrl !in subscribedUrls }
        val discovery = OnlineRecommendationMixer.roundRobin(clean(discoveryRaw)).distinctBy { it.videoUrl }
            .filter { it.videoUrl !in subscribedUrls }
        val rest = (total - subscribed.size).coerceAtLeast(0)
        val mixed = if (favs.isEmpty()) discovery.take(rest)
        else OnlineRecommendationMixer.mix(similar = similar, discovery = discovery, total = rest)
        // New uploads of subscribed channels go first, the personalised / discovery mix follows.
        spreadChannels((subscribed + mixed).distinctBy { it.videoUrl })
    }

    /**
     * Feed batch built around what the user watched / searched last. Per block of 10: 3 more videos of the same topic
     * (watched Minecraft -> 3 Minecraft videos), 2 of something they may like next (other games), 3 from different
     * categories (tech, popular music, a trending topic) and 2 from popular channels. New uploads of subscribed
     * channels still go first. All searches run at the same time.
     */
    private suspend fun fetchInterestBatch(
        context: Context,
        focus: List<OnlineInterestProfile.Topic>,
        excludeUrls: Set<String>,
        usedQueries: MutableSet<String>,
        total: Int,
        shown: JSONObject,
        opened: Set<String>
    ): List<OnlineTrackResult> = coroutineScope {
        val blocks = ((total + 9) / 10).coerceAtLeast(1)
        val plain = listOf("", "new", "latest", "best")
        val focusNames = focus.map { it.name }
        val sameBases = focusNames.map { OnlineInterestProfile.videoQueryFor(it) }
        val relatedBases = focusNames.flatMap { OnlineInterestProfile.relatedTopics(it) }
            .filter { r -> focusNames.none { it.equals(r, true) } }.distinct()
            .map { OnlineInterestProfile.videoQueryFor(it) }
        val avoid = focus.map { it.category }.toSet()

        val sameQueries = List(2 * blocks) { OnlineRecommendationMixer.freshQuery(sameBases, usedQueries, modifiers = plain) }.filterNotNull()
        val relatedQueries = List(2 * blocks) { OnlineRecommendationMixer.freshQuery(relatedBases, usedQueries, modifiers = plain) }.filterNotNull()
        val diverseQueries = OnlineInterestProfile.diverseQueries(OnlineInterestProfile.KIND_VIDEO, avoid, 3 * blocks) { opts ->
            OnlineRecommendationMixer.freshQuery(opts, usedQueries, modifiers = listOf("")) ?: opts.random()
        }
        val channelPicked = List(2 * blocks) {
            OnlineRecommendationMixer.freshQuery(OnlineInterestProfile.POPULAR_VIDEO_CHANNELS, usedQueries, modifiers = listOf("latest video", "new video", "best video"))
        }.filterNotNull()

        suspend fun run(queries: List<String>): List<List<OnlineTrackResult>> = coroutineScope {
            queries.map { q -> async(Dispatchers.IO) { OnlineMusicService.search(q) } }.awaitAll()
        }
        val sameJob = async { run(sameQueries) }
        val relatedJob = async { run(relatedQueries) }
        val diverseJob = async { run(diverseQueries) }
        val channelJob = async { run(channelPicked) }
        val subscribedJob = async { fetchSubscribedUploads(context, excludeUrls) }

        fun ok(r: OnlineTrackResult): Boolean =
            r.videoUrl.isNotBlank() && r.title.isNotBlank() &&
                r.videoUrl !in excludeUrls &&
                r.durationSec >= 60L && r.durationSec <= 4 * 3600L &&
                !isCompleted(context, r.videoUrl) &&
                !(shown.optInt(r.videoUrl, 0) >= IGNORE_AFTER && r.videoUrl !in opened)

        fun clean(lists: List<List<OnlineTrackResult>>): List<OnlineTrackResult> {
            val perChannel = HashMap<String, Int>()
            val flat = lists.map { l -> l.filter { ok(it) } }
            // one result of each query in turn, a channel never fills more than 2 places of a pool
            return OnlineRecommendationMixer.roundRobin(flat).filter {
                val key = it.artist.lowercase(Locale.ROOT)
                val n = perChannel.getOrDefault(key, 0)
                perChannel[key] = n + 1
                n < 2
            }.distinctBy { it.videoUrl }
        }

        val subscribed = subscribedJob.await()
        val taken = HashSet<String>(subscribed.map { it.videoUrl })
        val same = clean(sameJob.await()).filter { taken.add(it.videoUrl) }
        val related = clean(relatedJob.await()).filter { taken.add(it.videoUrl) }
        val diverse = clean(diverseJob.await()).filter { taken.add(it.videoUrl) }
        val channelRaw = channelJob.await()
        val channels = clean(channelRaw.mapIndexed { i, list ->
            val name = channelPicked[i].substringBefore(" latest").substringBefore(" new").substringBefore(" best")
                .lowercase(Locale.ROOT)
            list.filter { it.artist.lowercase(Locale.ROOT).contains(name) }.ifEmpty { list.take(2) }
        }).filter { taken.add(it.videoUrl) }

        val rest = (total - subscribed.size).coerceAtLeast(0)
        val mixed = OnlineRecommendationMixer.blockMix(same, related, diverse, channels, rest)
        // block order (3 same / 2 related / 3 different / 2 popular) is kept: only neighbours of one channel are pulled apart
        val ordered = (subscribed + mixed).distinctBy { it.videoUrl }
        spreadChannelsKeepingBlocks(ordered)
    }

    /** Like [spreadChannels] but only swaps an item with its direct follower, so the block order stays recognisable. */
    private fun spreadChannelsKeepingBlocks(items: List<OnlineTrackResult>): List<OnlineTrackResult> {
        val out = items.toMutableList()
        for (i in 1 until out.size) {
            if (out[i].artist.equals(out[i - 1].artist, true) && out[i].artist.isNotBlank()) {
                val j = (i + 1 until out.size).firstOrNull { !out[it].artist.equals(out[i - 1].artist, true) } ?: continue
                val tmp = out[i]; out[i] = out[j]; out[j] = tmp
            }
        }
        return out
    }

    /**
     * Newest uploads of the channels the user subscribed to. Videos already finished are skipped, as are videos
     * that are already on screen ([excludeUrls]). Returns at most [SUBSCRIBED_QUOTA] videos, newest first.
     */
    private suspend fun fetchSubscribedUploads(context: Context, excludeUrls: Set<String>): List<OnlineTrackResult> =
        coroutineScope {
            val channels = SubscribedChannelsManager.getAll(context).take(SUBSCRIBED_CHANNELS_PER_BATCH)
            if (channels.isEmpty()) return@coroutineScope emptyList()
            val perChannel = channels.map { ch ->
                async(Dispatchers.IO) {
                    try {
                        OnlineMusicService.fetchChannelLatestVideos(ch.channelUrl, 4)
                            .map { if (it.artist.isBlank()) it.copy(artist = ch.name) else it }
                            .filter {
                                it.videoUrl.isNotBlank() && it.title.isNotBlank() &&
                                    it.videoUrl !in excludeUrls &&
                                    it.durationSec >= 60L && it.durationSec <= 4 * 3600L &&
                                    !isCompleted(context, it.videoUrl)
                            }
                            .take(2)
                    } catch (t: kotlinx.coroutines.CancellationException) {
                        throw t
                    } catch (_: Throwable) {
                        emptyList<OnlineTrackResult>()
                    }
                }
            }.awaitAll()
            perChannel.flatten()
                .distinctBy { it.videoUrl }
                .sortedByDescending { it.uploadDateEpochMs ?: 0L }
                .take(SUBSCRIBED_QUOTA)
        }

    /** Re-orders so the same channel is never shown twice in a row (and rarely close together). */
    fun spreadChannels(items: List<OnlineTrackResult>): List<OnlineTrackResult> {
        val pending = items.toMutableList()
        val out = ArrayList<OnlineTrackResult>(items.size)
        while (pending.isNotEmpty()) {
            val recent = out.takeLast(3).map { it.artist.lowercase(Locale.ROOT) }
            val idx = pending.indexOfFirst { it.artist.lowercase(Locale.ROOT) !in recent }
                .takeIf { it >= 0 } ?: pending.indexOfFirst { it.artist.lowercase(Locale.ROOT) != recent.lastOrNull() }
                .takeIf { it >= 0 } ?: 0
            out.add(pending.removeAt(idx))
        }
        return out
    }
}
