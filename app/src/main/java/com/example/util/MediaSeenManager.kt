package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.screens.AudioTrackItem
import com.example.ui.screens.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified, persistent Seen/Unplayed media manager for both Video and Audio across Lumora.
 * 
 * Rules:
 * - A file is NEW / UNPLAYED if the user has never opened/played it in the player,
 *   its watched progress is 0%, it has not been marked seen, and (if applicable)
 *   its date added falls within the configured days threshold.
 * - Opening a file immediately marks it seen, instantly removing the "NEW" badge.
 * - Seen state persists across app restarts and player navigation.
 */
object MediaSeenManager {
    private const val PREFS_NAME = "lumora_media_seen_v1"
    private const val KEY_PREFIX = "seen_"

    private val seenKeyCache = ConcurrentHashMap.newKeySet<String>()
    private val _seenVersion = MutableStateFlow(0L)
    val seenVersion: StateFlow<Long> = _seenVersion.asStateFlow()

    @Volatile
    private var isInitialized = false
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                cachedPrefs = it
            }
        }
    }

    private fun normalizePathKey(path: String): String? {
        if (path.isBlank()) return null
        return "path:${path.trim().replace('\\', '/')}"
    }

    private fun normalizeIdKey(id: Long): String? {
        if (id <= 0L) return null
        return "id:$id"
    }

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val prefs = getPrefs(context)
                val all = prefs.all
                for ((k, v) in all) {
                    if (k.startsWith(KEY_PREFIX) && v == true) {
                        seenKeyCache.add(k.removePrefix(KEY_PREFIX))
                    }
                }
                isInitialized = true
            } catch (_: Throwable) {
                isInitialized = true
            }
        }
    }

    /**
     * Mark a media file (Video or Audio) as seen immediately.
     */
    fun markSeen(context: Context, path: String, id: Long = 0L) {
        ensureInitialized(context)
        val pKey = normalizePathKey(path)
        val idKey = normalizeIdKey(id)
        if (pKey == null && idKey == null) return

        var changed = false
        val editor = getPrefs(context).edit()
        if (pKey != null && seenKeyCache.add(pKey)) {
            editor.putBoolean(KEY_PREFIX + pKey, true)
            changed = true
        }
        if (idKey != null && seenKeyCache.add(idKey)) {
            editor.putBoolean(KEY_PREFIX + idKey, true)
            changed = true
        }

        if (changed) {
            try {
                editor.apply()
            } catch (_: Throwable) {}
        }
        // Always increment seenVersion to trigger instant UI recomposition/decrement across screens
        _seenVersion.value += 1L
    }

    /**
     * Checks whether a video or audio file has been seen/played by the user.
     */
    fun isSeen(context: Context, path: String, id: Long = 0L): Boolean {
        ensureInitialized(context)
        val pKey = normalizePathKey(path)
        val idKey = normalizeIdKey(id)

        if (pKey != null && seenKeyCache.contains(pKey)) return true
        if (idKey != null && seenKeyCache.contains(idKey)) return true

        // Fallback check against saved playback positions
        val videoProgress = PlaybackHistoryManager.getProgressFraction(context, id, path, 0L)
        if (videoProgress > 0f) {
            if (pKey != null) seenKeyCache.add(pKey)
            if (idKey != null) seenKeyCache.add(idKey)
            return true
        }

        return false
    }

    /**
     * Calculates whether a VideoItem should display the "NEW" badge.
     */
    fun isVideoNew(
        context: Context,
        video: VideoItem,
        daysThreshold: Int = 7,
        showNewVideoLabel: Boolean = true
    ): Boolean {
        if (!showNewVideoLabel) return false
        if (isSeen(context, video.path, video.id)) return false

        val progress = PlaybackHistoryManager.getProgressFraction(context, video.id, video.path, video.durationMs)
        if (progress > 0f) {
            markSeen(context, video.path, video.id)
            return false
        }

        if (daysThreshold > 0 && video.dateModified > 0L) {
            val dateSec = if (video.dateModified > 10_000_000_000L) video.dateModified / 1000L else video.dateModified
            val nowSec = System.currentTimeMillis() / 1000L
            val thresholdSec = daysThreshold.toLong() * 24L * 3600L
            return (nowSec - dateSec) <= thresholdSec
        }

        return true
    }

    /**
     * Calculates whether an AudioTrackItem should display the "NEW" badge.
     */
    fun isAudioTrackNew(
        context: Context,
        track: AudioTrackItem,
        daysThreshold: Int = 7,
        showNewBadge: Boolean = true
    ): Boolean {
        if (!showNewBadge) return false
        if (isSeen(context, track.path, track.id)) return false

        val savedPos = AudioPlaybackHistoryManager.getPosition(context, track)
        if (savedPos > 0L) {
            markSeen(context, track.path, track.id)
            return false
        }

        if (daysThreshold > 0 && track.dateModified > 0L) {
            val dateSec = if (track.dateModified > 10_000_000_000L) track.dateModified / 1000L else track.dateModified
            val nowSec = System.currentTimeMillis() / 1000L
            val thresholdSec = daysThreshold.toLong() * 24L * 3600L
            return (nowSec - dateSec) <= thresholdSec
        }

        return true
    }
}
