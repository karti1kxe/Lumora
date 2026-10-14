package com.example.util

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small persistent store behind the swipe actions of Online Video / Online Music:
 *  - metadata of videos/tracks the user acted on (so playlists & "Watch later" can rebuild cards after a restart)
 *  - "Watch later" list (pinned at the very top of the online folder)
 *  - "Not interested" list (hidden from the feeds)
 * [KIND_VIDEO] and [KIND_AUDIO] keep completely separate lists.
 */
object OnlineMediaLibrary {
    const val KIND_VIDEO = "video"
    const val KIND_AUDIO = "audio"

    private const val PREFS = "lumora_online_media_library"
    private const val MAX_NOT_INTERESTED = 3000

    @Volatile private var appCtx: Context? = null

    /** Lets context-less callers (PlaylistManager.getVideosForPlaylist) read the stored metadata. */
    fun attach(context: Context) {
        if (appCtx == null) appCtx = context.applicationContext
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readObj(c: Context, key: String): JSONObject =
        try { JSONObject(prefs(c).getString(key, "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }

    private fun readArr(c: Context, key: String): JSONArray =
        try { JSONArray(prefs(c).getString(key, "[]") ?: "[]") } catch (_: Throwable) { JSONArray() }

    private fun OnlineTrackResult.toJson(): JSONObject = JSONObject().apply {
        put("u", videoUrl); put("t", title); put("a", artist); put("d", durationSec)
        put("th", thumbnailUrl ?: ""); put("ud", uploadDateEpochMs ?: 0L); put("v", viewCount)
    }

    private fun JSONObject.toResult(): OnlineTrackResult = OnlineTrackResult(
        videoUrl = optString("u"),
        title = optString("t"),
        artist = optString("a"),
        durationSec = optLong("d"),
        thumbnailUrl = optString("th").ifBlank { null },
        uploadDateEpochMs = optLong("ud").takeIf { it > 0L },
        viewCount = optLong("v")
    )

    // ---------------- metadata ----------------

    @Synchronized
    fun rememberMeta(c: Context, kind: String, r: OnlineTrackResult) {
        attach(c)
        val key = "meta_$kind"
        val obj = readObj(c, key)
        obj.put(r.videoUrl, r.toJson())
        prefs(c).edit().putString(key, obj.toString()).apply()
    }

    /** Same as [rememberMeta] for many items with a single read + write (used by the YouTube playlist import). */
    @Synchronized
    fun rememberMetaBatch(c: Context, kind: String, list: List<OnlineTrackResult>) {
        attach(c)
        val key = "meta_$kind"
        val obj = readObj(c, key)
        for (r in list) obj.put(r.videoUrl, r.toJson())
        prefs(c).edit().putString(key, obj.toString()).apply()
    }

    @Synchronized
    fun getMeta(kind: String, url: String, context: Context? = null): OnlineTrackResult? {
        val c = context ?: appCtx ?: return null
        val o = readObj(c, "meta_$kind").optJSONObject(url) ?: return null
        return try { o.toResult() } catch (_: Throwable) { null }
    }

    // ---------------- watch later ----------------

    @Synchronized
    fun watchLater(c: Context, kind: String): List<OnlineTrackResult> {
        val meta = readObj(c, "meta_$kind")
        val arr = readArr(c, "wl_$kind")
        val out = ArrayList<OnlineTrackResult>()
        for (i in 0 until arr.length()) {
            val url = arr.optString(i)
            val o = meta.optJSONObject(url) ?: continue
            try { out.add(o.toResult()) } catch (_: Throwable) {}
        }
        return out
    }

    @Synchronized
    fun isWatchLater(c: Context, kind: String, url: String): Boolean {
        val arr = readArr(c, "wl_$kind")
        for (i in 0 until arr.length()) if (arr.optString(i) == url) return true
        return false
    }

    /** Adds the item (newest first) or removes it when it is already saved. Returns true when it is saved now. */
    @Synchronized
    fun toggleWatchLater(c: Context, kind: String, r: OnlineTrackResult): Boolean {
        rememberMeta(c, kind, r)
        val arr = readArr(c, "wl_$kind")
        val list = ArrayList<String>()
        var found = false
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            if (u == r.videoUrl) found = true else list.add(u)
        }
        if (!found) list.add(0, r.videoUrl)
        prefs(c).edit().putString("wl_$kind", JSONArray(list).toString()).apply()
        return !found
    }

    @Synchronized
    fun removeWatchLater(c: Context, kind: String, url: String) {
        val arr = readArr(c, "wl_$kind")
        val list = (0 until arr.length()).map { arr.optString(it) }.filter { it != url }
        prefs(c).edit().putString("wl_$kind", JSONArray(list).toString()).apply()
    }

    // ---------------- not interested ----------------

    @Synchronized
    fun markNotInterested(c: Context, kind: String, url: String) {
        val arr = readArr(c, "ni_$kind")
        val list = ArrayList<String>()
        list.add(url)
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            if (u != url) list.add(u)
        }
        prefs(c).edit().putString("ni_$kind", JSONArray(list.take(MAX_NOT_INTERESTED)).toString()).apply()
        removeWatchLater(c, kind, url)
    }

    @Synchronized
    fun notInterestedSet(c: Context, kind: String): Set<String> {
        val arr = readArr(c, "ni_$kind")
        return (0 until arr.length()).map { arr.optString(it) }.toSet()
    }
}

/**
 * "Play next in queue" / "Play last in queue" for Online Video. The video player can't be open at the same time as
 * the browse list, so the queue waits here and is spliced into the playlist of the next online video that is played
 * (or played directly with the "Play queue" button of the Online folder).
 */
object OnlineVideoQueue {
    val items = mutableStateListOf<OnlineTrackResult>()

    fun addNext(r: OnlineTrackResult) {
        items.removeAll { it.videoUrl == r.videoUrl }
        items.add(0, r)
    }

    fun addLast(r: OnlineTrackResult) {
        items.removeAll { it.videoUrl == r.videoUrl }
        items.add(r)
    }

    fun remove(url: String) { items.removeAll { it.videoUrl == url } }

    fun clear() { items.clear() }
}
