package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.screens.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CustomPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val videoPaths: List<String> = emptyList(),
    val mediaType: String = "VIDEO",
    val isOnline: Boolean = false
) {
    val isAudio: Boolean
        get() = mediaType.equals("AUDIO", ignoreCase = true) ||
                (videoPaths.isNotEmpty() && videoPaths.all { path ->
                    val ext = path.substringAfterLast('.', "").lowercase()
                    ext in listOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")
                })

    val isOnlineFolder: Boolean
        get() = isOnline || videoPaths.any { path ->
            path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
            path.startsWith("http://") ||
            path.startsWith("https://")
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }

    val itemCountText: String
        get() = if (isAudio) {
            val suffix = if (isOnlineFolder) " (Online)" else ""
            "${videoPaths.size} audio track${if (videoPaths.size != 1) "s" else ""}$suffix"
        } else {
            "${videoPaths.size} video${if (videoPaths.size != 1) "s" else ""}"
        }

    val videoCountText: String
        get() = itemCountText
}

object PlaylistManager {
    private const val PREFS_NAME = "custom_playlists_prefs"
    private const val KEY_PLAYLISTS = "saved_playlists_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getPlaylists(context: Context): List<CustomPlaylist> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val list = mutableListOf<CustomPlaylist>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Untitled")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val mediaType = obj.optString("mediaType", "VIDEO")
                val isOnline = obj.optBoolean("isOnline", false)
                val pathsArray = obj.optJSONArray("videoPaths")
                val paths = mutableListOf<String>()
                if (pathsArray != null) {
                    for (j in 0 until pathsArray.length()) {
                        val p = pathsArray.optString(j)
                        if (p.isNotBlank()) paths.add(p)
                    }
                }
                list.add(CustomPlaylist(id, name, createdAt, paths, mediaType, isOnline))
            }
        } catch (_: Throwable) {}
        return list
    }

    @Synchronized
    fun savePlaylists(context: Context, playlists: List<CustomPlaylist>) {
        val prefs = getPrefs(context)
        val array = JSONArray()
        for (pl in playlists) {
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            obj.put("createdAt", pl.createdAt)
            obj.put("mediaType", pl.mediaType)
            obj.put("isOnline", pl.isOnline || pl.isOnlineFolder)
            val pathsArray = JSONArray()
            for (path in pl.videoPaths) {
                pathsArray.put(path)
            }
            obj.put("videoPaths", pathsArray)
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    @Synchronized
    fun createPlaylist(context: Context, name: String, mediaType: String = "VIDEO", isOnline: Boolean = false): CustomPlaylist {
        val trimmed = name.trim().ifBlank { "Playlist ${System.currentTimeMillis() % 1000}" }
        val current = getPlaylists(context).toMutableList()
        val newPl = CustomPlaylist(name = trimmed, mediaType = mediaType, isOnline = isOnline)
        current.add(0, newPl)
        savePlaylists(context, current)
        return newPl
    }

    @Synchronized
    fun addVideosToPlaylist(context: Context, playlistId: String, newVideoPaths: List<String>): Int {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return 0

        val pl = current[index]
        val updatedPaths = pl.videoPaths.toMutableList()
        var addedCount = 0
        for (path in newVideoPaths) {
            if (path.isNotBlank() && !updatedPaths.contains(path)) {
                updatedPaths.add(path)
                addedCount++
            }
        }
        val hasOnlineTracks = updatedPaths.any {
            it.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) ||
            it.startsWith("http://") ||
            it.startsWith("https://")
        }
        current[index] = pl.copy(
            videoPaths = updatedPaths,
            isOnline = pl.isOnline || hasOnlineTracks
        )
        savePlaylists(context, current)
        return addedCount
    }

    @Synchronized
    fun removeVideoFromPlaylist(context: Context, playlistId: String, videoPath: String) {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = current[index]
            val updated = pl.videoPaths.filter { it != videoPath }
            current[index] = pl.copy(videoPaths = updated)
            savePlaylists(context, current)
        }
    }

    @Synchronized
    fun deletePlaylist(context: Context, playlistId: String) {
        val current = getPlaylists(context).toMutableList()
        current.removeAll { it.id == playlistId }
        savePlaylists(context, current)
    }

    @Synchronized
    fun renamePlaylist(context: Context, playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(name = trimmed)
            savePlaylists(context, current)
        }
    }

    @Synchronized
    fun duplicatePlaylist(context: Context, playlistId: String, customName: String? = null): CustomPlaylist? {
        val current = getPlaylists(context).toMutableList()
        val original = current.find { it.id == playlistId } ?: return null
        val newName = customName?.trim()?.ifBlank { null } ?: "${original.name} (Copy)"
        val newPl = CustomPlaylist(
            name = newName,
            videoPaths = original.videoPaths.toList(),
            mediaType = original.mediaType,
            isOnline = original.isOnlineFolder
        )
        current.add(0, newPl)
        savePlaylists(context, current)
        return newPl
    }

    @Synchronized
    fun updatePlaylistVideosOrder(context: Context, playlistId: String, newVideoPaths: List<String>) {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(videoPaths = newVideoPaths)
            savePlaylists(context, current)
        }
    }

    @Synchronized
    fun clearPlaylistVideos(context: Context, playlistId: String) {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(videoPaths = emptyList())
            savePlaylists(context, current)
        }
    }

    fun getVideosForPlaylist(
        playlist: CustomPlaylist,
        allVideos: List<VideoItem>
    ): List<VideoItem> {
        val videoMap = allVideos.associateBy { it.path }
        val result = mutableListOf<VideoItem>()
        for (path in playlist.videoPaths) {
            val matched = videoMap[path]
            if (matched != null) {
                result.add(matched)
            } else {
                val f = File(path)
                if (f.exists()) {
                    val cachedMeta = VideoLibraryCache.getCachedVideoMeta(f.absolutePath, f.length(), f.lastModified())
                    result.add(
                        VideoItem(
                            id = f.name.hashCode().toLong(),
                            uri = android.net.Uri.fromFile(f),
                            displayName = f.name,
                            path = f.absolutePath,
                            sizeBytes = f.length(),
                            durationMs = cachedMeta?.durationMs ?: 0L,
                            dateModified = f.lastModified() / 1000L,
                            isNew = false,
                            resolution = cachedMeta?.resolution ?: "",
                            framerate = cachedMeta?.framerate ?: 0.0,
                            hasSubtitles = (cachedMeta?.subtitleFormats?.isNotEmpty() == true) || (cachedMeta?.embeddedSubtitleFormats?.isNotEmpty() == true),
                            subtitleFormats = cachedMeta?.subtitleFormats ?: emptyList(),
                            embeddedSubtitleFormats = cachedMeta?.embeddedSubtitleFormats ?: emptyList(),
                            externalSubtitleFormats = cachedMeta?.externalSubtitleFormats ?: emptyList(),
                            embeddedFontCount = cachedMeta?.embeddedFontCount ?: 0
                        )
                    )
                }
            }
        }
        return result
    }

    @Synchronized
    fun addToWatchLater(context: Context, videoPaths: List<String>): Int {
        val current = getPlaylists(context)
        val watchLater = current.find { it.name.equals("Watch Later", ignoreCase = true) && !it.isAudio }
            ?: createPlaylist(context, "Watch Later", "VIDEO")
        return addVideosToPlaylist(context, watchLater.id, videoPaths)
    }
}
