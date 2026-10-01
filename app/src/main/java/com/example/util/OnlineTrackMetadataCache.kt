package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ui.screens.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent storage for online track metadata (thumbnails, title, artist, duration, etc.).
 * Guarantees that playlist items created from online searches, channels, and shared playlists
 * persist across application restarts with full visual fidelity.
 */
object OnlineTrackMetadataCache {
    private const val TAG = "OnlineTrackMetaCache"
    private const val CACHE_FILE_NAME = "online_track_metadata_cache.json"

    private val memoryCache = ConcurrentHashMap<String, AudioTrackItem>()
    @Volatile
    private var isLoaded = false

    fun ensureLoaded(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0L) {
                    val jsonStr = file.readText()
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val path = obj.optString("path")
                        if (path.isNotBlank()) {
                            val track = AudioTrackItem(
                                id = obj.optLong("id", -(kotlin.math.abs(path.hashCode().toLong()))),
                                uri = runCatching { Uri.parse(obj.optString("uri", "")) }.getOrDefault(Uri.EMPTY),
                                title = obj.optString("title", "Online Track"),
                                artist = obj.optString("artist", "Online Music"),
                                album = obj.optString("album", "Online Playlist"),
                                durationMs = obj.optLong("durationMs", 0L),
                                path = path,
                                sizeBytes = obj.optLong("sizeBytes", 0L),
                                format = obj.optString("format", "STREAM"),
                                audioType = obj.optString("audioType", "Stereo"),
                                hasLyrics = obj.optBoolean("hasLyrics", false),
                                lyricsText = obj.optString("lyricsText", ""),
                                dateModified = obj.optLong("dateModified", System.currentTimeMillis()),
                                playbackProgress = obj.optDouble("playbackProgress", 0.0).toFloat(),
                                isNew = obj.optBoolean("isNew", false),
                                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                            )
                            memoryCache[path] = track
                            val cleanPath = path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                            memoryCache[cleanPath] = track
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error loading online track metadata cache", e)
            } finally {
                isLoaded = true
            }
        }
    }

    fun getTrack(context: Context, path: String): AudioTrackItem? {
        ensureLoaded(context)
        val clean = path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
        return memoryCache[path] ?: memoryCache[clean]
    }

    fun getAllTracks(context: Context): List<AudioTrackItem> {
        ensureLoaded(context)
        return memoryCache.values.distinctBy { it.path }
    }

    fun saveTrack(context: Context, track: AudioTrackItem) {
        saveTracks(context, listOf(track))
    }

    fun saveTracks(context: Context, tracks: List<AudioTrackItem>) {
        if (tracks.isEmpty()) return
        ensureLoaded(context)
        var changed = false
        for (t in tracks) {
            if (t.path.isNotBlank()) {
                val clean = t.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                memoryCache[t.path] = t
                memoryCache[clean] = t
                changed = true
            }
        }
        if (changed) {
            persistToDisk(context)
        }
    }

    private fun persistToDisk(context: Context) {
        try {
            val array = JSONArray()
            val distinctTracks = memoryCache.values.distinctBy { it.path }
            for (t in distinctTracks) {
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("uri", t.uri.toString())
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("durationMs", t.durationMs)
                    put("path", t.path)
                    put("sizeBytes", t.sizeBytes)
                    put("format", t.format)
                    put("audioType", t.audioType)
                    put("hasLyrics", t.hasLyrics)
                    put("lyricsText", t.lyricsText)
                    put("dateModified", t.dateModified)
                    put("playbackProgress", t.playbackProgress.toDouble())
                    put("isNew", t.isNew)
                    if (!t.thumbnailUrl.isNullOrBlank()) {
                        put("thumbnailUrl", t.thumbnailUrl)
                    }
                }
                array.put(obj)
            }
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            tempFile.writeText(array.toString())
            if (!tempFile.renameTo(cacheFile)) {
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "Persisted ${distinctTracks.size} online tracks to disk")
        } catch (e: Throwable) {
            Log.e(TAG, "Error persisting online track metadata cache", e)
        }
    }
}
