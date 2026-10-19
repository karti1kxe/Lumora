package com.example.util

import android.content.Context
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One entry of the Network tab's "Recent Streams" list. */
data class RecentStreamEntry(
    /** What gets opened again: the pasted link. */
    val url: String,
    val title: String,
    val subtitle: String?,
    val playedAt: Long,
    /** Thumbnail of the link: an image URL, or the path of a frame saved from the video (no thumbnail attached). */
    val thumbnail: String? = null
)

/**
 * Remembers every network link the user played (newest first, max [MAX_ENTRIES]) so a stream that was
 * closed by mistake can be opened again with one tap. Stored as one small JSON string in SharedPreferences.
 */
object RecentStreamStore {
    private const val PREFS = "lumora_recent_streams"
    private const val KEY = "entries_v1"
    private const val MAX_ENTRIES = 60

    private val _changes = MutableStateFlow(0)
    /** Increments on every change, so the Network tab can refresh its list even while it stays composed. */
    val changes: StateFlow<Int> = _changes

    @Synchronized
    fun getAll(context: Context): List<RecentStreamEntry> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("u")
                if (url.isBlank()) null
                else RecentStreamEntry(
                    url = url,
                    title = o.optString("t").ifBlank { url },
                    subtitle = o.optString("s").ifBlank { null },
                    playedAt = o.optLong("p", 0L),
                    thumbnail = o.optString("th").ifBlank { null }
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Adds (or moves to the top) one played stream. */
    @Synchronized
    fun record(context: Context, url: String, title: String?, subtitle: String? = null, thumbnail: String? = null) {
        if (url.isBlank()) return
        val old = getAll(context).firstOrNull { it.url == url }
        val list = getAll(context).filter { it.url != url }.toMutableList()
        list.add(
            0,
            RecentStreamEntry(
                url,
                title?.takeIf { it.isNotBlank() } ?: url,
                subtitle?.takeIf { it.isNotBlank() },
                System.currentTimeMillis(),
                thumbnail?.takeIf { it.isNotBlank() } ?: old?.thumbnail
            )
        )
        save(context, list.take(MAX_ENTRIES))
    }

    /** Sets the thumbnail of an entry that is already in the list (used when a frame is saved a bit later). */
    @Synchronized
    fun setThumbnail(context: Context, url: String, thumbnail: String) {
        val list = getAll(context)
        if (list.none { it.url == url }) return
        save(context, list.map { if (it.url == url) it.copy(thumbnail = thumbnail) else it })
    }

    @Synchronized
    fun remove(context: Context, url: String) {
        save(context, getAll(context).filter { it.url != url })
    }

    @Synchronized
    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, list: List<RecentStreamEntry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("u", e.url)
                    .put("t", e.title)
                    .put("s", e.subtitle ?: "")
                    .put("p", e.playedAt)
                    .put("th", e.thumbnail ?: "")
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        _changes.value = _changes.value + 1
    }
}
