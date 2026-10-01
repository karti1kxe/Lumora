package com.example.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.player.AudioMetadataExtractor
import com.example.ui.components.hasAllFilesAccess
import com.example.ui.screens.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AudioLibraryCache {
    private const val TAG = "AudioLibraryCache"
    private const val CACHE_FILE_NAME = "audio_library_cache.json"
    private const val SCHEMA_VERSION = 1

    private val mutex = Mutex()
    private var cachedTracks: List<AudioTrackItem>? = null
    var hasScannedOnce: Boolean = false
        private set

    fun getCachedTracks(): List<AudioTrackItem>? = cachedTracks

    fun invalidate() {
        cachedTracks = null
        hasScannedOnce = false
    }

    /**
     * Loads cached audio tracks from local persistent storage (0ms startup display).
     */
    fun loadCachedTracks(context: Context): List<AudioTrackItem>? {
        cachedTracks?.let { return it }

        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return null
        }

        try {
            val jsonStr = cacheFile.readText()
            if (jsonStr.isBlank()) return null

            val tracks = mutableListOf<AudioTrackItem>()
            val rootObj = JSONObject(jsonStr)
            val ver = rootObj.optInt("version", 0)
            if (ver < SCHEMA_VERSION) {
                cacheFile.delete()
                return null
            }
            val rootArray = rootObj.optJSONArray("tracks") ?: return null
            for (i in 0 until rootArray.length()) {
                val trackObj = rootArray.optJSONObject(i) ?: continue
                val track = parseTrackFromJson(trackObj)
                if (track != null) {
                    tracks.add(track)
                }
            }

            cachedTracks = tracks
            hasScannedOnce = true
            Log.d(TAG, "Loaded ${tracks.size} audio tracks from disk cache")
            return tracks
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading cached audio tracks from disk", e)
            return null
        }
    }

    /**
     * Saves audio tracks to local persistent storage.
     */
    fun saveCachedTracks(context: Context, tracks: List<AudioTrackItem>) {
        cachedTracks = tracks
        hasScannedOnce = true

        try {
            val rootObj = JSONObject()
            rootObj.put("version", SCHEMA_VERSION)
            val rootArray = JSONArray()
            for (track in tracks) {
                rootArray.put(trackToJson(track))
            }
            rootObj.put("tracks", rootArray)

            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            tempFile.writeText(rootObj.toString())
            if (!tempFile.renameTo(cacheFile)) {
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "Saved ${tracks.size} audio tracks to disk cache")
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving audio tracks to disk cache", e)
        }
    }

    fun updateCachedTracks(newTracks: List<AudioTrackItem>, context: Context? = null) {
        val current = cachedTracks ?: emptyList()
        val map = LinkedHashMap<String, AudioTrackItem>()
        for (t in current) {
            map[t.path] = t
        }
        for (t in newTracks) {
            map[t.path] = t
        }
        val merged = map.values.toList()
        cachedTracks = merged
        hasScannedOnce = true
        if (context != null) {
            saveCachedTracks(context, merged)
            val onlineItems = newTracks.filter { it.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) }
            if (onlineItems.isNotEmpty()) {
                OnlineTrackMetadataCache.saveTracks(context, onlineItems)
            }
        }
    }

    suspend fun getOrScanAudio(context: Context, force: Boolean = false): List<AudioTrackItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!force && hasScannedOnce && cachedTracks != null) {
                return@withContext cachedTracks ?: emptyList()
            }

            // Load initial from disk if in-memory is empty
            if (cachedTracks == null) {
                loadCachedTracks(context)
            }

            val list = mutableListOf<AudioTrackItem>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATE_MODIFIED
                )

                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )

                val nowSec = System.currentTimeMillis() / 1000L

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val rawTitle = c.getString(titleCol) ?: "Unknown Track"
                        val artist = c.getString(artistCol) ?: "Unknown Artist"
                        val album = c.getString(albumCol) ?: "Unknown Album"
                        val duration = c.getLong(durCol)
                        val rawPath = c.getString(dataCol) ?: ""
                        val path = if (rawPath.isNotBlank()) VideoLibraryCache.normalizeCanonicalPath(rawPath) else ""
                        val size = c.getLong(sizeCol)
                        val dateModified = c.getLong(dateCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                        val ext = if (path.contains('.')) path.substringAfterLast('.').uppercase() else "MP3"
                        val title = if (rawTitle.isBlank() || rawTitle.startsWith("AUD-")) {
                            if (path.isNotBlank()) path.substringAfterLast('/').substringBeforeLast('.') else rawTitle
                        } else rawTitle

                        var hasLyrics = false
                        var lyricsText = ""
                        try {
                            if (path.isNotBlank()) {
                                val audioFile = File(path)
                                val lrcFile = File(path.substringBeforeLast('.') + ".lrc")
                                if (lrcFile.exists() && lrcFile.length() > 0) {
                                    hasLyrics = true
                                    lyricsText = lrcFile.readText()
                                } else if (audioFile.exists()) {
                                    val extLower = audioFile.extension.lowercase()
                                    val embedded = when (extLower) {
                                        "mp3" -> AudioMetadataExtractor.extractId3Lyrics(audioFile)
                                        "m4a", "mp4", "aac" -> AudioMetadataExtractor.extractMp4Lyrics(audioFile)
                                        "flac", "ogg", "opus" -> AudioMetadataExtractor.extractVorbisLyrics(audioFile)
                                        else -> null
                                    }
                                    if (!embedded.isNullOrBlank()) {
                                        hasLyrics = true
                                        lyricsText = embedded
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        val audioType = if (ext.equals("WAV", ignoreCase = true) || ext.equals("AMR", ignoreCase = true)) "Mono" else "Stereo"
                        val isNew = (nowSec - dateModified) < (7 * 24 * 3600)

                        list.add(
                            AudioTrackItem(
                                id = id,
                                uri = uri,
                                title = title,
                                artist = if (artist.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else artist,
                                album = if (album.contains("<unknown>", ignoreCase = true)) "Unknown Album" else album,
                                durationMs = duration,
                                path = path,
                                sizeBytes = size,
                                format = ext,
                                audioType = audioType,
                                hasLyrics = hasLyrics,
                                lyricsText = lyricsText,
                                dateModified = dateModified,
                                isNew = isNew
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "MediaStore audio query exception", e)
            }

            if (hasAllFilesAccess(context)) {
                val knownPaths = list.map { it.path }.filter { it.isNotBlank() }.toMutableSet()
                // NOTE: "mp4" and "webm" are video container extensions and must NOT be treated
                // as audio here. Including them previously caused MP4 video files to incorrectly
                // show up in the Audio section during the direct filesystem scan (they are almost
                // never audio-only files — that's what ".m4a" is for). Genuine audio-only MPEG-4
                // container files are still picked up separately via the MediaStore.Audio query
                // above, which classifies them correctly using the media database, not extension.
                val audioExtensions = setOf(
                    "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma",
                    "amr", "awb", "mid", "midi", "aiff", "aif", "ape", "alac", "ac3",
                    "eac3", "mka", "3gp", "3gpp"
                )

                val skippedDirNames = setOf(
                    ".thumbnails", ".trashed", "cache", ".cache", ".temp", ".tmp"
                )

                // Iterative (stack-based) walk instead of recursion, with a canonical-path
                // "visited" set: avoids StackOverflowError on very deep folder trees and avoids
                // infinite loops through symlinked/bind-mounted directories some OEMs expose.
                fun walk(startRoot: File) {
                    val visited = HashSet<String>()
                    val stack = ArrayDeque<File>()
                    stack.addLast(startRoot)

                    while (stack.isNotEmpty()) {
                        val dir = stack.removeLast()
                        val canonicalDirPath = try { dir.canonicalPath } catch (_: Throwable) { dir.absolutePath }
                        if (!visited.add(canonicalDirPath)) continue

                        val children = try { dir.listFiles() } catch (_: Throwable) { null } ?: continue
                        for (file in children) {
                            try {
                                if (file.isDirectory) {
                                    val dirName = file.name
                                    if (dirName.isEmpty() || dirName.startsWith(".")) continue
                                    if (dir.name.equals("Android", ignoreCase = true) &&
                                        (dirName.equals("data", ignoreCase = true) || dirName.equals("obb", ignoreCase = true))
                                    ) continue
                                    if (dirName.lowercase() in skippedDirNames) continue
                                    stack.addLast(file)
                                    continue
                                }
                                if (!file.isFile || file.name.startsWith(".")) continue
                                if (file.extension.lowercase() !in audioExtensions) continue
                                val canonical = VideoLibraryCache.normalizeCanonicalPath(file.absolutePath)
                                if (!knownPaths.add(canonical)) continue

                                val ext = file.extension.uppercase().ifBlank { "MP3" }
                                val title = file.name.substringBeforeLast('.', file.name).ifBlank { file.name }
                                val dateModified = file.lastModified()
                                val lrcFile = File(file.parentFile ?: file, file.nameWithoutExtension + ".lrc")
                                var hasLyrics = try { lrcFile.isFile && lrcFile.length() > 0L } catch (_: Throwable) { false }
                                var lyricsText = if (hasLyrics) {
                                    try { lrcFile.readText() } catch (_: Throwable) { "" }
                                } else ""

                                if (!hasLyrics && file.isFile) {
                                    val extLower = file.extension.lowercase()
                                    val embedded = when (extLower) {
                                        "mp3" -> AudioMetadataExtractor.extractId3Lyrics(file)
                                        "m4a", "mp4", "aac" -> AudioMetadataExtractor.extractMp4Lyrics(file)
                                        "flac", "ogg", "opus" -> AudioMetadataExtractor.extractVorbisLyrics(file)
                                        else -> null
                                    }
                                    if (!embedded.isNullOrBlank()) {
                                        hasLyrics = true
                                        lyricsText = embedded
                                    }
                                }

                                list.add(
                                    AudioTrackItem(
                                        id = canonical.hashCode().toLong(),
                                        uri = Uri.fromFile(file),
                                        title = title,
                                        artist = "Unknown Artist",
                                        album = file.parentFile?.name ?: "Unknown Album",
                                        durationMs = 0L,
                                        path = canonical,
                                        sizeBytes = file.length(),
                                        format = ext,
                                        audioType = if (ext.equals("WAV", ignoreCase = true) || ext.equals("AMR", ignoreCase = true)) "Mono" else "Stereo",
                                        hasLyrics = hasLyrics,
                                        lyricsText = lyricsText,
                                        dateModified = dateModified / 1000L,
                                        isNew = ((System.currentTimeMillis() / 1000L) - (dateModified / 1000L)) < (7 * 24 * 3600)
                                    )
                                )
                            } catch (_: Throwable) {
                                // One bad entry must never abort the scan of its siblings or of
                                // the other storage volumes.
                                continue
                            }
                        }
                    }
                }

                val roots = try { accessibleStorageRoots(context) } catch (e: Throwable) {
                    Log.w(TAG, "accessibleStorageRoots() failed", e)
                    emptyList()
                }
                for (root in roots) {
                    try {
                        walk(root)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Full-storage audio scan exception for root ${root.path}", e)
                    }
                }
            }

            // Deduplicate tracks by canonical path or id
            val deduplicated = deduplicateTracksList(list)

            // If scan failed or returned empty while we already had valid items, preserve valid state
            val finalTracks = if (deduplicated.isEmpty() && !cachedTracks.isNullOrEmpty()) {
                Log.w(TAG, "Audio scan returned 0 tracks; retaining last known valid cached state")
                cachedTracks!!
            } else {
                deduplicated
            }

            val onlineTracks = OnlineTrackMetadataCache.getAllTracks(context)
            val mergedWithOnline = if (onlineTracks.isNotEmpty()) {
                val existingPaths = finalTracks.map { it.path }.toSet()
                finalTracks + onlineTracks.filter { !existingPaths.contains(it.path) }
            } else {
                finalTracks
            }

            saveCachedTracks(context, mergedWithOnline)
            mergedWithOnline
        }
    }

    private fun deduplicateTracksList(tracks: List<AudioTrackItem>): List<AudioTrackItem> {
        val map = LinkedHashMap<String, AudioTrackItem>()
        for (t in tracks) {
            val key = if (t.path.isNotBlank()) t.path.lowercase() else "${t.title.lowercase()}:${t.sizeBytes}"
            val existing = map[key]
            if (existing == null) {
                map[key] = t
            } else {
                // Keep the one with richer metadata
                val betterUri = if (t.uri.toString().startsWith("content://")) t.uri else existing.uri
                val betterDur = if (t.durationMs > 0) t.durationMs else existing.durationMs
                val betterLyrics = if (t.hasLyrics) t.hasLyrics else existing.hasLyrics
                val betterLyricsText = if (t.lyricsText.isNotBlank()) t.lyricsText else existing.lyricsText
                map[key] = existing.copy(
                    uri = betterUri,
                    durationMs = betterDur,
                    hasLyrics = betterLyrics,
                    lyricsText = betterLyricsText
                )
            }
        }
        return map.values.toList()
    }

    private fun trackToJson(t: AudioTrackItem): JSONObject {
        return JSONObject().apply {
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
            if (t.thumbnailUrl != null) {
                put("thumbnailUrl", t.thumbnailUrl)
            }
        }
    }

    private fun parseTrackFromJson(obj: JSONObject): AudioTrackItem? {
        return try {
            val id = obj.getLong("id")
            val uriStr = obj.getString("uri")
            val title = obj.getString("title")
            val artist = obj.optString("artist", "Unknown Artist")
            val album = obj.optString("album", "Unknown Album")
            val durationMs = obj.optLong("durationMs", 0L)
            val path = obj.optString("path", "")
            val sizeBytes = obj.optLong("sizeBytes", 0L)
            val format = obj.optString("format", "MP3")
            val audioType = obj.optString("audioType", "Stereo")
            val hasLyrics = obj.optBoolean("hasLyrics", false)
            val lyricsText = obj.optString("lyricsText", "")
            val dateModified = obj.optLong("dateModified", 0L)
            val playbackProgress = obj.optDouble("playbackProgress", 0.0).toFloat()
            val isNew = obj.optBoolean("isNew", false)
            val thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }

            AudioTrackItem(
                id = id,
                uri = Uri.parse(uriStr),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                path = path,
                sizeBytes = sizeBytes,
                format = format,
                audioType = audioType,
                hasLyrics = hasLyrics,
                lyricsText = lyricsText,
                dateModified = dateModified,
                playbackProgress = playbackProgress,
                isNew = isNew,
                thumbnailUrl = thumbnailUrl
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing audio track JSON", e)
            null
        }
    }
}
