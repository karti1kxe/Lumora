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
    fun recordOpened(c: Context, videoUrl: String, channel: String) {
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

    @Synchronized
    fun recordChannelSearch(c: Context, channelName: String) {
        if (channelName.isBlank()) return
        val arr = readArr(c, KEY_SEARCHED)
        val list = (0 until arr.length()).map { arr.optString(it) }.filter { !it.equals(channelName, true) }.toMutableList()
        list.add(0, channelName)
        prefs(c).edit().putString(KEY_SEARCHED, JSONArray(list.take(20)).toString()).apply()
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

        val similar = OnlineRecommendationMixer.roundRobin(clean(similarRaw)).distinctBy { it.videoUrl }
        val discovery = OnlineRecommendationMixer.roundRobin(clean(discoveryRaw)).distinctBy { it.videoUrl }
        val mixed = if (favs.isEmpty()) discovery.take(total)
        else OnlineRecommendationMixer.mix(similar = similar, discovery = discovery, total = total)
        spreadChannels(mixed.distinctBy { it.videoUrl })
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
