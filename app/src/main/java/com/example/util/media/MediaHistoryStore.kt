package com.example.util.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A title (movie / series) the user last played from a media plugin. [link] is the plugin's own link for getMeta. */
data class MediaWatchedTitle(
    val pluginId: String,
    val title: String,
    val link: String,
    val image: String,
    val watchedAt: Long = System.currentTimeMillis()
)

/** "Continue watching" list of the media plugins: the titles played last, newest first, one entry per title. */
object MediaHistoryStore {
    private const val PREFS = "media_history_prefs"
    private const val KEY_HISTORY = "watched_titles"
    private const val MAX_ENTRIES = 60

    /** Bumped whenever the list changes (collect it to refresh the UI). */
    val changes = MutableStateFlow(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): List<MediaWatchedTitle> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<MediaWatchedTitle>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val link = o.optString("l", "")
                val plugin = o.optString("p", "")
                if (link.isBlank() || plugin.isBlank()) continue
                out.add(
                    MediaWatchedTitle(
                        pluginId = plugin,
                        title = o.optString("t", ""),
                        link = link,
                        image = o.optString("im", ""),
                        watchedAt = o.optLong("at", 0L)
                    )
                )
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeAll(context: Context, list: List<MediaWatchedTitle>) {
        val arr = JSONArray()
        for (v in list) {
            arr.put(
                JSONObject().put("p", v.pluginId).put("t", v.title).put("l", v.link).put("im", v.image).put("at", v.watchedAt)
            )
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
        changes.value++
    }

    /** Newest first. */
    fun recent(context: Context, pluginId: String): List<MediaWatchedTitle> =
        readAll(context).filter { it.pluginId == pluginId }.sortedByDescending { it.watchedAt }

    /** Puts [title] on top of the list (an older entry of the same title is replaced). */
    @Synchronized
    fun record(context: Context, title: MediaWatchedTitle) {
        if (title.link.isBlank()) return
        val rest = readAll(context).filterNot { it.pluginId == title.pluginId && it.link == title.link }
        writeAll(context, (listOf(title.copy(watchedAt = System.currentTimeMillis())) + rest).take(MAX_ENTRIES))
    }

    @Synchronized
    fun remove(context: Context, pluginId: String, link: String) {
        writeAll(context, readAll(context).filterNot { it.pluginId == pluginId && it.link == link })
    }
}
