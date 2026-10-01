package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

object FolderBlockListManager {

    private const val PREFS_NAME = "lumora_folder_blocklist"
    private const val KEY_BLOCKED_VIDEOS = "blocked_video_folder_ids"
    private const val KEY_BLOCKED_AUDIOS = "blocked_audio_folder_paths"

    private val _blockedVideoFolderIds = mutableStateOf<Set<String>>(emptySet())
    val blockedVideoFolderIds: State<Set<String>> = _blockedVideoFolderIds

    private val _blockedAudioFolderPaths = mutableStateOf<Set<String>>(emptySet())
    val blockedAudioFolderPaths: State<Set<String>> = _blockedAudioFolderPaths

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)
        val videos = prefs.getStringSet(KEY_BLOCKED_VIDEOS, emptySet()) ?: emptySet()
        val audios = prefs.getStringSet(KEY_BLOCKED_AUDIOS, emptySet()) ?: emptySet()
        _blockedVideoFolderIds.value = videos.toSet()
        _blockedAudioFolderPaths.value = audios.toSet()
        isInitialized = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isVideoFolderBlocked(context: Context, folderId: String, folderPath: String = ""): Boolean {
        init(context)
        val set = _blockedVideoFolderIds.value
        return set.contains(folderId) || (folderPath.isNotBlank() && set.contains(folderPath))
    }

    fun isAudioFolderBlocked(context: Context, folderPath: String): Boolean {
        init(context)
        val set = _blockedAudioFolderPaths.value
        return set.contains(folderPath) || set.contains(VideoLibraryCache.normalizeCanonicalPath(folderPath))
    }

    fun isAnyFolderBlocked(context: Context, identifier: String): Boolean {
        init(context)
        return _blockedVideoFolderIds.value.contains(identifier) ||
                _blockedAudioFolderPaths.value.contains(identifier) ||
                _blockedVideoFolderIds.value.contains(VideoLibraryCache.normalizeCanonicalPath(identifier)) ||
                _blockedAudioFolderPaths.value.contains(VideoLibraryCache.normalizeCanonicalPath(identifier))
    }

    fun blockVideoFolder(context: Context, folderId: String) {
        init(context)
        val current = _blockedVideoFolderIds.value.toMutableSet()
        current.add(folderId)
        _blockedVideoFolderIds.value = current
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_VIDEOS, current).apply()
    }

    fun unblockVideoFolder(context: Context, folderId: String) {
        init(context)
        val current = _blockedVideoFolderIds.value.toMutableSet()
        current.remove(folderId)
        _blockedVideoFolderIds.value = current
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_VIDEOS, current).apply()
    }

    fun blockAudioFolder(context: Context, folderPath: String) {
        init(context)
        val current = _blockedAudioFolderPaths.value.toMutableSet()
        current.add(folderPath)
        _blockedAudioFolderPaths.value = current
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_AUDIOS, current).apply()
    }

    fun unblockAudioFolder(context: Context, folderPath: String) {
        init(context)
        val current = _blockedAudioFolderPaths.value.toMutableSet()
        current.remove(folderPath)
        _blockedAudioFolderPaths.value = current
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_AUDIOS, current).apply()
    }

    fun unblockFolder(context: Context, identifier: String) {
        init(context)
        unblockVideoFolder(context, identifier)
        unblockAudioFolder(context, identifier)
    }

    fun updateBlockList(
        context: Context,
        videoFolderIds: Set<String>,
        audioFolderPaths: Set<String>
    ) {
        init(context)
        _blockedVideoFolderIds.value = videoFolderIds.toSet()
        _blockedAudioFolderPaths.value = audioFolderPaths.toSet()
        getPrefs(context).edit()
            .putStringSet(KEY_BLOCKED_VIDEOS, videoFolderIds)
            .putStringSet(KEY_BLOCKED_AUDIOS, audioFolderPaths)
            .apply()
    }

    fun getTotalBlockedCount(context: Context): Int {
        init(context)
        return _blockedVideoFolderIds.value.size + _blockedAudioFolderPaths.value.size
    }
}
