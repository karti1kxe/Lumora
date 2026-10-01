package com.example.util

import android.content.Context
import com.example.ui.screens.AudioTrackItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight persistent resume history for Lumora's audio player.
 * Stores metadata only; no media bytes are copied or cached.
 */
object AudioPlaybackHistoryManager {
    private const val PREFS = "audio_playback_history_v1"
    private val lastWriteTimes = ConcurrentHashMap<String, Long>()
    private const val KEY_PREFIX = "track_"
    private const val KEY_LAST = "last_track"

    private fun keyFor(track: AudioTrackItem): String {
        val strong = when {
            track.path.isNotBlank() -> "path:${track.path.trim().replace('\\', '/')}"
            track.uri != android.net.Uri.EMPTY -> "uri:${track.uri}"
            track.id > 0L -> "id:${track.id}"
            else -> "title:${track.title}"
        }
        return KEY_PREFIX + strong.hashCode().toString()
    }

    fun savePosition(context: Context, track: AudioTrackItem?, positionMs: Long, durationMs: Long, force: Boolean = false) {
        if (track == null) return
        val safeDuration = durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration.coerceAtLeast(1L))
        val key = keyFor(track)
        val now = System.currentTimeMillis()
        val previousWrite = lastWriteTimes[key] ?: 0L
        if (!force && now - previousWrite < 1200L) return
        lastWriteTimes[key] = now
        val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("${key}_position", safePosition)
            .putLong("${key}_duration", safeDuration)
            .putString("${key}_path", track.path)
            .putString("${key}_uri", track.uri.toString())
            .putLong("${key}_id", track.id)
            .putString("${key}_title", track.title)
            .putString("${key}_artist", track.artist)
            .putString("${key}_album", track.album)
            .putLong("${key}_time", now)
            .putString(KEY_LAST, key)

        if (!track.thumbnailUrl.isNullOrBlank()) {
            editor.putString("${key}_thumb", track.thumbnailUrl)
        }
        val vUrl = when {
            track.path.startsWith("online://") -> track.path.removePrefix("online://")
            track.path.startsWith("http://") || track.path.startsWith("https://") -> track.path
            else -> null
        }
        if (!vUrl.isNullOrBlank()) {
            editor.putString("${key}_video_url", vUrl)
        }

        editor.apply()
    }

    fun getPosition(context: Context, track: AudioTrackItem): Long {
        val key = keyFor(track)
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("${key}_position", 0L)
            .coerceAtLeast(0L)
    }

    fun getLastTrack(context: Context, folderPath: String? = null): AudioPlaybackResume? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        val candidates = mutableListOf<AudioPlaybackResume>()
        for ((key, value) in all) {
            if (!key.startsWith(KEY_PREFIX) || !key.endsWith("_time") || value !is Long) continue
            val base = key.removeSuffix("_time")
            val path = prefs.getString("${base}_path", "") ?: ""
            val uri = prefs.getString("${base}_uri", "") ?: ""
            val title = prefs.getString("${base}_title", "") ?: ""
            val artist = prefs.getString("${base}_artist", "") ?: ""
            val album = prefs.getString("${base}_album", "") ?: ""
            val id = prefs.getLong("${base}_id", 0L)
            val position = prefs.getLong("${base}_position", 0L)
            val duration = prefs.getLong("${base}_duration", 0L)
            val thumb = prefs.getString("${base}_thumb", null)
            val vUrl = prefs.getString("${base}_video_url", null)
            if (path.isBlank() && uri.isBlank() && title.isBlank()) continue
            if (folderPath != null && !isWithinFolder(path, folderPath)) continue
            candidates += AudioPlaybackResume(
                id = id,
                path = path,
                uriString = uri,
                title = title,
                artist = artist,
                album = album,
                positionMs = position,
                durationMs = duration,
                lastWatchedTimestamp = value,
                thumbnailUrl = thumb,
                videoUrl = vUrl
            )
        }
        return candidates.maxByOrNull { it.lastWatchedTimestamp }
    }

    fun getAllHistory(context: Context): List<AudioPlaybackResume> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        val candidates = mutableListOf<AudioPlaybackResume>()
        for ((key, value) in all) {
            if (!key.startsWith(KEY_PREFIX) || !key.endsWith("_time") || value !is Long) continue
            val base = key.removeSuffix("_time")
            val path = prefs.getString("${base}_path", "") ?: ""
            val uri = prefs.getString("${base}_uri", "") ?: ""
            val title = prefs.getString("${base}_title", "") ?: ""
            val artist = prefs.getString("${base}_artist", "") ?: ""
            val album = prefs.getString("${base}_album", "") ?: ""
            val id = prefs.getLong("${base}_id", 0L)
            val position = prefs.getLong("${base}_position", 0L)
            val duration = prefs.getLong("${base}_duration", 0L)
            val thumb = prefs.getString("${base}_thumb", null)
            val vUrl = prefs.getString("${base}_video_url", null)
            if (path.isBlank() && uri.isBlank() && title.isBlank()) continue
            candidates += AudioPlaybackResume(
                id = id,
                path = path,
                uriString = uri,
                title = title,
                artist = artist,
                album = album,
                positionMs = position,
                durationMs = duration,
                lastWatchedTimestamp = value,
                thumbnailUrl = thumb,
                videoUrl = vUrl
            )
        }
        val deduplicated = mutableMapOf<String, AudioPlaybackResume>()
        for (item in candidates.sortedByDescending { it.lastWatchedTimestamp }) {
            val dedupeKey = if (item.path.isNotBlank()) item.path else item.title
            if (!deduplicated.containsKey(dedupeKey)) {
                deduplicated[dedupeKey] = item
            }
        }
        return deduplicated.values.sortedByDescending { it.lastWatchedTimestamp }
    }

    fun deleteRecord(context: Context, resume: AudioPlaybackResume) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        val editor = prefs.edit()
        for ((key, _) in all) {
            if (key.startsWith(KEY_PREFIX) && key.endsWith("_time")) {
                val base = key.removeSuffix("_time")
                val path = prefs.getString("${base}_path", "") ?: ""
                val id = prefs.getLong("${base}_id", 0L)
                val title = prefs.getString("${base}_title", "") ?: ""
                val match = (resume.path.isNotBlank() && path == resume.path) ||
                        (resume.id > 0L && id == resume.id) ||
                        (resume.title.isNotBlank() && title == resume.title)
                if (match) {
                    editor.remove("${base}_position")
                    editor.remove("${base}_duration")
                    editor.remove("${base}_path")
                    editor.remove("${base}_uri")
                    editor.remove("${base}_id")
                    editor.remove("${base}_title")
                    editor.remove("${base}_artist")
                    editor.remove("${base}_album")
                    editor.remove("${base}_time")
                    editor.remove("${base}_thumb")
                    editor.remove("${base}_video_url")
                }
            }
        }
        editor.apply()
    }

    fun clearAllHistory(context: Context) {
        lastWriteTimes.clear()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun getTopArtists(context: Context, limit: Int = 5): List<String> {
        val history = getAllHistory(context)
        val counts = mutableMapOf<String, Int>()
        for (item in history) {
            val a = item.artist.trim()
            if (a.isNotBlank() && !a.equals("<unknown>", ignoreCase = true) && !a.equals("YouTube Music", ignoreCase = true)) {
                counts[a] = (counts[a] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun isWithinFolder(filePath: String, folderPath: String): Boolean {
        if (filePath.isBlank() || folderPath.isBlank()) return false
        return try {
            val file = File(filePath).canonicalFile
            val folder = File(folderPath).canonicalFile
            file.path == folder.path || file.toPath().startsWith(folder.toPath())
        } catch (_: Throwable) {
            val f = filePath.replace('\\', '/').trimEnd('/')
            val d = folderPath.replace('\\', '/').trimEnd('/')
            f == d || f.startsWith("$d/")
        }
    }
}

data class AudioPlaybackResume(
    val id: Long,
    val path: String,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null
) {
    val isOnline: Boolean
        get() = path.startsWith("online://") || path.startsWith("http://") || path.startsWith("https://") || !videoUrl.isNullOrBlank()

    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isFinished: Boolean
        get() = durationMs > 0L && (positionMs >= durationMs - 4000L || progressFraction >= 0.95f)

    val formattedPosition: String
        get() = VideoPlaybackRecord.formatTimestamp(positionMs)

    val formattedDuration: String
        get() = VideoPlaybackRecord.formatTimestamp(durationMs)
}
