package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Data model for persisted video playback records.
 */
data class VideoPlaybackRecord(
    val videoId: Long = 0L,
    val path: String = "",
    val uriString: String = "",
    val title: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedPosition: String
        get() = formatTimestamp(positionMs)

    val formattedDuration: String
        get() = formatTimestamp(durationMs)

    val isFinished: Boolean
        get() = durationMs > 0L && (positionMs >= durationMs - 4000L || progressFraction >= 0.97f)

    companion object {
        fun formatTimestamp(timeMs: Long): String {
            if (timeMs <= 0L) return "00:00"
            val totalSec = timeMs / 1000
            val hours = totalSec / 3600
            val mins = (totalSec % 3600) / 60
            val secs = totalSec % 60
            return if (hours > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hours, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
            }
        }
    }
}

/**
 * Global persistent playback history and resume manager.
 * Stores and manages the last timestamps for every video file so playback can resume
 * with precision whenever the same file is reopened.
 */
object PlaybackHistoryManager {
    private const val PREFS_NAME = "video_playback_history_v2"
    private const val KEY_POS_PREFIX = "pos_"
    private const val KEY_DUR_PREFIX = "dur_"
    private const val KEY_TIME_PREFIX = "time_"
    private const val KEY_TITLE_PREFIX = "title_"
    private const val KEY_PATH_PREFIX = "path_"
    private const val KEY_URI_PREFIX = "uri_"
    private const val KEY_ID_PREFIX = "id_"
    private const val KEY_LAST_WATCHED_KEY = "last_watched_primary_key"

    // Fast in-memory cache for instant UI rendering without disk latency
    private val memoryPosCache = ConcurrentHashMap<String, Long>()
    private val memoryDurCache = ConcurrentHashMap<String, Long>()
    private val memoryRecordCache = ConcurrentHashMap<String, VideoPlaybackRecord>()
    private val lastDiskSaveTimes = ConcurrentHashMap<String, Long>()
    @Volatile
    private var inMemoryLastWatchedRecord: VideoPlaybackRecord? = null
    @Volatile
    private var cachedPrefs: SharedPreferences? = null
    @Volatile
    private var isMemoryCacheInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                cachedPrefs = it
            }
        }
    }

    private fun ensureMemoryCacheLoaded(context: Context) {
        if (isMemoryCacheInitialized) return
        synchronized(this) {
            if (isMemoryCacheInitialized) return
            try {
                val prefs = getPrefs(context)
                val allEntries = prefs.all
                for ((k, v) in allEntries) {
                    if (k.startsWith(KEY_POS_PREFIX) && v is Long) {
                        memoryPosCache[k.removePrefix(KEY_POS_PREFIX)] = v
                    } else if (k.startsWith(KEY_DUR_PREFIX) && v is Long) {
                        memoryDurCache[k.removePrefix(KEY_DUR_PREFIX)] = v
                    }
                }
                val primaryKey = prefs.getString(KEY_LAST_WATCHED_KEY, null)
                if (primaryKey != null) {
                    val pos = prefs.getLong(KEY_POS_PREFIX + primaryKey, 0L)
                    val dur = prefs.getLong(KEY_DUR_PREFIX + primaryKey, 0L)
                    val time = prefs.getLong(KEY_TIME_PREFIX + primaryKey, 0L)
                    val title = prefs.getString(KEY_TITLE_PREFIX + primaryKey, "") ?: ""
                    val path = prefs.getString(KEY_PATH_PREFIX + primaryKey, "") ?: ""
                    val uriString = prefs.getString(KEY_URI_PREFIX + primaryKey, "") ?: ""
                    val videoId = prefs.getLong(KEY_ID_PREFIX + primaryKey, 0L)
                    if (pos > 0L || dur > 0L || title.isNotBlank() || path.isNotBlank() || uriString.isNotBlank()) {
                        inMemoryLastWatchedRecord = VideoPlaybackRecord(
                            videoId = videoId,
                            path = path,
                            uriString = uriString,
                            title = title,
                            positionMs = pos,
                            durationMs = dur,
                            lastWatchedTimestamp = time
                        )
                    }
                }
                isMemoryCacheInitialized = true
            } catch (_: Throwable) {
                isMemoryCacheInitialized = true
            }
        }
    }

    private fun generateCandidateKeys(videoId: Long, path: String, uriString: String = "", title: String = ""): List<String> {
        val keys = ArrayList<String>(4)
        if (path.isNotBlank()) {
            val normalized = path.trim().replace('\\', '/')
            keys.add("path_${normalized.hashCode()}")
        }
        if (uriString.isNotBlank()) {
            keys.add("uri_${uriString.trim().hashCode()}")
        }
        if (videoId > 0L) {
            keys.add("id_$videoId")
        }
        // Only use title if all other strong identifiers are absent
        if (keys.isEmpty() && title.isNotBlank()) {
            keys.add("title_${title.trim().hashCode()}")
        }
        return if (keys.isEmpty()) listOf("unknown") else keys
    }

    /**
     * Persists playback timestamp and metadata for a video file.
     * Pure metadata only: No video bytes, no file caching.
     * Throttles disk writes during periodic playback ticks, writes immediately on lifecycle/exit events.
     */
    fun savePosition(
        context: Context,
        videoId: Long,
        path: String,
        positionMs: Long,
        durationMs: Long,
        uriString: String = "",
        title: String = "",
        forceDiskWrite: Boolean = false
    ) {
        val candidateKeys = generateCandidateKeys(videoId, path, uriString, title)
        val primaryKey = candidateKeys.firstOrNull() ?: return
        if (primaryKey == "unknown") return

        // Validate and clamp position safely
        val validDur = durationMs.coerceAtLeast(0L)
        val safePos = when {
            positionMs < 0L -> 0L
            validDur > 0L && positionMs > validDur -> validDur
            else -> positionMs
        }

        // If completed or near the very end (> 96% or remaining < 3.5 seconds), reset to 0 so next open starts fresh
        val isFinished = validDur > 0L && (safePos >= validDur - 3500L || (safePos.toFloat() / validDur.toFloat()) >= 0.96f)
        val finalPos = if (isFinished) {
            0L
        } else {
            safePos.coerceAtLeast(0L)
        }

        // Protection against spurious 0L ticks overwriting valid resume position (> 1000ms) during initialization or rapid exit
        if (finalPos <= 1000L && !isFinished) {
            val existingPos = getSavedPosition(context, videoId, path, uriString, title)
            if (existingPos > 1000L) {
                // Protect existing resume position from being wiped out by transient startup 0 ticks
                return
            }
        }

        val now = System.currentTimeMillis()
        val record = VideoPlaybackRecord(
            videoId = videoId,
            path = path,
            uriString = uriString,
            title = if (title.isNotBlank()) title else path.substringAfterLast('/'),
            positionMs = finalPos,
            durationMs = validDur,
            lastWatchedTimestamp = now
        )

        inMemoryLastWatchedRecord = record

        // Instant in-memory cache update (UI sees changes in 0ms)
        candidateKeys.forEach { key ->
            memoryPosCache[key] = finalPos
            if (validDur > 0L) memoryDurCache[key] = validDur
            memoryRecordCache[key] = record
        }

        // Throttled / Debounced disk persistence: only write to disk if forced (e.g. pause/seek/back/exit) or > 2.5s since last write
        val lastSave = lastDiskSaveTimes[primaryKey] ?: 0L
        if (forceDiskWrite || (now - lastSave >= 2500L)) {
            lastDiskSaveTimes[primaryKey] = now
            try {
                val prefs = getPrefs(context)
                val editor = prefs.edit()
                candidateKeys.forEach { key ->
                    editor.putLong(KEY_POS_PREFIX + key, finalPos)
                    if (validDur > 0L) {
                        editor.putLong(KEY_DUR_PREFIX + key, validDur)
                    }
                    editor.putLong(KEY_TIME_PREFIX + key, now)
                    if (title.isNotBlank()) editor.putString(KEY_TITLE_PREFIX + key, title)
                    if (path.isNotBlank()) editor.putString(KEY_PATH_PREFIX + key, path)
                    if (uriString.isNotBlank()) editor.putString(KEY_URI_PREFIX + key, uriString)
                    if (videoId > 0L) editor.putLong(KEY_ID_PREFIX + key, videoId)
                }
                editor.putString(KEY_LAST_WATCHED_KEY, primaryKey)
                if (forceDiskWrite) {
                    editor.commit()
                } else {
                    editor.apply()
                }
            } catch (_: Throwable) {
                // Ignore background disk write failures gracefully
            }
        }
    }

    /**
     * Retrieves the saved timestamp in milliseconds for resuming a video file.
     * Pure O(1) in-memory execution to ensure 60/120fps list scrolling without UI thread blocks.
     */
    fun getSavedPosition(
        context: Context,
        videoId: Long,
        path: String,
        uriString: String = "",
        title: String = ""
    ): Long {
        ensureMemoryCacheLoaded(context)
        val candidateKeys = generateCandidateKeys(videoId, path, uriString, title)

        // 1. Instant check in memory cache
        for (key in candidateKeys) {
            val cached = memoryPosCache[key]
            if (cached != null && cached > 0L) {
                return cached
            }
        }

        // 2. Check SharedPreferences if not yet loaded
        try {
            val prefs = getPrefs(context)
            for (key in candidateKeys) {
                val posKey = KEY_POS_PREFIX + key
                if (prefs.contains(posKey)) {
                    val pos = prefs.getLong(posKey, 0L).coerceAtLeast(0L)
                    if (pos > 0L) {
                        memoryPosCache[key] = pos
                        return pos
                    }
                }
            }
        } catch (_: Throwable) {}

        return 0L
    }

    /**
     * Gets playback completion fraction (0.0 to 1.0) for UI progress bars.
     */
    fun getProgressFraction(
        context: Context,
        videoId: Long,
        path: String,
        durationMs: Long,
        uriString: String = "",
        title: String = ""
    ): Float {
        if (durationMs <= 0L) return 0f
        val pos = getSavedPosition(context, videoId, path, uriString, title)
        return (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Retrieves the most recently watched video record.
     */
    fun getLastWatchedRecordInFolder(context: Context, folderPath: String): VideoPlaybackRecord? {
        if (folderPath.isBlank()) return null
        return getAllPlaybackRecords(context).firstOrNull { record ->
            if (record.path.isBlank()) return@firstOrNull false
            try {
                val file = java.io.File(record.path).canonicalFile
                val folder = java.io.File(folderPath).canonicalFile
                file.toPath().startsWith(folder.toPath()) && file.path != folder.path
            } catch (_: Throwable) {
                val file = record.path.replace('\\', '/').trimEnd('/')
                val folder = folderPath.replace('\\', '/').trimEnd('/')
                file.startsWith("$folder/")
            }
        }
    }

    fun getLastWatchedRecord(context: Context): VideoPlaybackRecord? {
        inMemoryLastWatchedRecord?.let { return it }
        ensureMemoryCacheLoaded(context)
        inMemoryLastWatchedRecord?.let { return it }
        return try {
            val prefs = getPrefs(context)
            val primaryKey = prefs.getString(KEY_LAST_WATCHED_KEY, null) ?: return null
            val pos = prefs.getLong(KEY_POS_PREFIX + primaryKey, 0L)
            val dur = prefs.getLong(KEY_DUR_PREFIX + primaryKey, 0L)
            val time = prefs.getLong(KEY_TIME_PREFIX + primaryKey, 0L)
            val title = prefs.getString(KEY_TITLE_PREFIX + primaryKey, "") ?: ""
            val path = prefs.getString(KEY_PATH_PREFIX + primaryKey, "") ?: ""
            val uriString = prefs.getString(KEY_URI_PREFIX + primaryKey, "") ?: ""
            val videoId = prefs.getLong(KEY_ID_PREFIX + primaryKey, 0L)

            if (pos > 0L || dur > 0L || title.isNotBlank() || path.isNotBlank() || uriString.isNotBlank()) {
                VideoPlaybackRecord(
                    videoId = videoId,
                    path = path,
                    uriString = uriString,
                    title = title,
                    positionMs = pos,
                    durationMs = dur,
                    lastWatchedTimestamp = time
                ).also { inMemoryLastWatchedRecord = it }
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Clears saved position for a specific video (e.g. user manually requests restart).
     */
    fun clearPosition(
        context: Context,
        videoId: Long,
        path: String,
        uriString: String = "",
        title: String = ""
    ) {
        val candidateKeys = generateCandidateKeys(videoId, path, uriString, title)
        candidateKeys.forEach { key ->
            memoryPosCache.remove(key)
            memoryDurCache.remove(key)
            memoryRecordCache.remove(key)
        }
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                candidateKeys.forEach { key ->
                    remove(KEY_POS_PREFIX + key)
                    remove(KEY_DUR_PREFIX + key)
                    remove(KEY_TIME_PREFIX + key)
                    remove(KEY_TITLE_PREFIX + key)
                    remove(KEY_PATH_PREFIX + key)
                    remove(KEY_URI_PREFIX + key)
                    remove(KEY_ID_PREFIX + key)
                }
                apply()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Returns all playback history records sorted by lastWatchedTimestamp descending.
     */
    fun getAllPlaybackRecords(context: Context): List<VideoPlaybackRecord> {
        ensureMemoryCacheLoaded(context)
        val recordsMap = mutableMapOf<String, VideoPlaybackRecord>()
        try {
            val prefs = getPrefs(context)
            val allEntries = prefs.all
            for ((k, _) in allEntries) {
                if (k.startsWith(KEY_TIME_PREFIX)) {
                    val key = k.removePrefix(KEY_TIME_PREFIX)
                    val time = prefs.getLong(KEY_TIME_PREFIX + key, 0L)
                    val pos = prefs.getLong(KEY_POS_PREFIX + key, 0L)
                    val dur = prefs.getLong(KEY_DUR_PREFIX + key, 0L)
                    val title = prefs.getString(KEY_TITLE_PREFIX + key, "") ?: ""
                    val path = prefs.getString(KEY_PATH_PREFIX + key, "") ?: ""
                    val uriString = prefs.getString(KEY_URI_PREFIX + key, "") ?: ""
                    val videoId = prefs.getLong(KEY_ID_PREFIX + key, 0L)

                    if (path.isNotBlank() || title.isNotBlank() || uriString.isNotBlank()) {
                        val dedupeKey = if (path.isNotBlank()) path else if (uriString.isNotBlank()) uriString else title
                        val existing = recordsMap[dedupeKey]
                        if (existing == null || time > existing.lastWatchedTimestamp) {
                            recordsMap[dedupeKey] = VideoPlaybackRecord(
                                videoId = videoId,
                                path = path,
                                uriString = uriString,
                                title = title.ifBlank { path.substringAfterLast('/') },
                                positionMs = pos,
                                durationMs = dur,
                                lastWatchedTimestamp = time
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Merge in-memory records
        for ((_, record) in memoryRecordCache) {
            val dedupeKey = if (record.path.isNotBlank()) record.path else if (record.uriString.isNotBlank()) record.uriString else record.title
            val existing = recordsMap[dedupeKey]
            if (existing == null || record.lastWatchedTimestamp > existing.lastWatchedTimestamp) {
                recordsMap[dedupeKey] = record
            }
        }

        return recordsMap.values.sortedByDescending { it.lastWatchedTimestamp }
    }

    /**
     * Clears all playback history.
     */
    fun clearAllHistory(context: Context) {
        memoryPosCache.clear()
        memoryDurCache.clear()
        memoryRecordCache.clear()
        inMemoryLastWatchedRecord = null
        try {
            val prefs = getPrefs(context)
            prefs.edit().clear().apply()
        } catch (_: Throwable) {}
    }
}
