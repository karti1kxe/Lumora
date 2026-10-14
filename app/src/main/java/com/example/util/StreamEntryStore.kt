package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Metadata of one imported IPTV / M3U stream entry (the playlist item itself only stores the stream URL). */
data class StreamEntryMeta(
    val url: String,
    val name: String,
    val logo: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val extraHeaders: Map<String, String> = emptyMap()
)

/**
 * Persistent store (one JSON file in app-private storage) that remembers name / logo / group and the
 * special HTTP options of every imported M3U stream, keyed by stream URL. It lets playlists rebuild the
 * normal video cards after an app restart and lets the player send the right User-Agent / headers.
 */
object StreamEntryStore {
    private const val FILE_NAME = "stream_entries_v1.json"

    @Volatile private var appCtx: Context? = null
    @Volatile private var loaded = false
    private val map = ConcurrentHashMap<String, StreamEntryMeta>()

    /** Lets context-less callers (PlaylistManager.getVideosForPlaylist, the player) read the store. */
    fun attach(context: Context) {
        if (appCtx == null) appCtx = context.applicationContext
        ensureLoaded()
    }

    private fun file(): File? = appCtx?.let { File(it.filesDir, FILE_NAME) }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        val f = file() ?: return
        try {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("u")
                    if (url.isBlank()) continue
                    val headers = LinkedHashMap<String, String>()
                    o.optJSONObject("h")?.let { h ->
                        val keys = h.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            headers[k] = h.optString(k)
                        }
                    }
                    map[url] = StreamEntryMeta(
                        url = url,
                        name = o.optString("n"),
                        logo = o.optString("l").ifBlank { null },
                        group = o.optString("g").ifBlank { null },
                        tvgId = o.optString("i").ifBlank { null },
                        userAgent = o.optString("ua").ifBlank { null },
                        referrer = o.optString("r").ifBlank { null },
                        extraHeaders = headers
                    )
                }
            }
        } catch (_: Throwable) {
            // a corrupt file must never crash the app: start with an empty store
        }
        loaded = true
    }

    fun get(url: String): StreamEntryMeta? {
        ensureLoaded()
        return map[url]
    }

    @Synchronized
    fun putAll(entries: List<StreamEntryMeta>) {
        ensureLoaded()
        for (e in entries) map[e.url] = e
        persist()
    }

    /** Drops metadata of streams that no playlist references any more. */
    @Synchronized
    fun prune(keepUrls: Set<String>) {
        ensureLoaded()
        var changed = false
        for (k in map.keys.toList()) {
            if (k !in keepUrls) { map.remove(k); changed = true }
        }
        if (changed) persist()
    }

    private fun persist() {
        val f = file() ?: return
        try {
            val arr = JSONArray()
            for (e in map.values) {
                val o = JSONObject()
                o.put("u", e.url)
                o.put("n", e.name)
                o.put("l", e.logo ?: "")
                o.put("g", e.group ?: "")
                o.put("i", e.tvgId ?: "")
                o.put("ua", e.userAgent ?: "")
                o.put("r", e.referrer ?: "")
                if (e.extraHeaders.isNotEmpty()) {
                    val h = JSONObject()
                    for ((k, v) in e.extraHeaders) h.put(k, v)
                    o.put("h", h)
                }
                arr.put(o)
            }
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(arr.toString())
                tmp.delete()
            }
        } catch (_: Throwable) {
            // best effort: playback still works from the playlist itself, only logos/groups would be lost
        }
    }
}

/**
 * Per-stream HTTP options handed to mpv right before a stream is opened. The player calls [forUrl];
 * null means "no special options" (mpv then keeps the app's normal User-Agent).
 */
object StreamRequestOptions {
    data class Options(val userAgent: String?, val referrer: String?, val headerFields: String?)

    fun forUrl(url: String): Options? {
        val m = StreamEntryStore.get(url) ?: return null
        val ua = m.userAgent?.takeIf { it.isNotBlank() }
        val ref = m.referrer?.takeIf { it.isNotBlank() }
        val fields = m.extraHeaders.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(",") { "${it.key}: ${it.value.replace(",", "%2C")}" }
            .takeIf { it.isNotBlank() }
        if (ua == null && ref == null && fields == null) return null
        return Options(ua, ref, fields)
    }
}
