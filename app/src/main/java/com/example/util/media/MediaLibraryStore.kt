package com.example.util.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A movie / episode the user saved with "Watch later" on a media plugin. [link] is the plugin's own link for getStream. */
data class MediaSavedVideo(
    val pluginId: String,
    val title: String,
    val pageTitle: String,
    val poster: String,
    val link: String,
    val type: String,
    val savedAt: Long = System.currentTimeMillis()
)

/** "Watch later" list of the media plugins (shown as the first category of each plugin). */
object MediaLibraryStore {
    private const val PREFS = "media_library_prefs"
    private const val KEY_WATCH_LATER = "watch_later"

    /** Bumped whenever the list changes (collect it to refresh the UI). */
    val changes = MutableStateFlow(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): List<MediaSavedVideo> {
        val raw = prefs(context).getString(KEY_WATCH_LATER, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<MediaSavedVideo>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val link = o.optString("l", "")
                val plugin = o.optString("p", "")
                if (link.isBlank() || plugin.isBlank()) continue
                out.add(
                    MediaSavedVideo(
                        pluginId = plugin,
                        title = o.optString("t", ""),
                        pageTitle = o.optString("pt", ""),
                        poster = o.optString("im", ""),
                        link = link,
                        type = o.optString("ty", "movie"),
                        savedAt = o.optLong("at", 0L)
                    )
                )
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeAll(context: Context, list: List<MediaSavedVideo>) {
        val arr = JSONArray()
        for (v in list) {
            arr.put(
                JSONObject()
                    .put("p", v.pluginId).put("t", v.title).put("pt", v.pageTitle).put("im", v.poster)
                    .put("l", v.link).put("ty", v.type).put("at", v.savedAt)
            )
        }
        prefs(context).edit().putString(KEY_WATCH_LATER, arr.toString()).apply()
        changes.value++
    }

    /** Newest first. */
    fun watchLater(context: Context, pluginId: String): List<MediaSavedVideo> =
        readAll(context).filter { it.pluginId == pluginId }.sortedByDescending { it.savedAt }

    fun isSaved(context: Context, pluginId: String, link: String): Boolean =
        readAll(context).any { it.pluginId == pluginId && it.link == link }

    /** Returns true when the video is saved now, false when it was removed. */
    @Synchronized
    fun toggleWatchLater(context: Context, video: MediaSavedVideo): Boolean {
        val all = readAll(context)
        val exists = all.any { it.pluginId == video.pluginId && it.link == video.link }
        if (exists) {
            writeAll(context, all.filterNot { it.pluginId == video.pluginId && it.link == video.link })
            return false
        }
        writeAll(context, all + video)
        return true
    }

    @Synchronized
    fun remove(context: Context, pluginId: String, link: String) {
        writeAll(context, readAll(context).filterNot { it.pluginId == pluginId && it.link == link })
    }
}
