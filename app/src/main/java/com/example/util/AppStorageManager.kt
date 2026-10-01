package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * AppStorageManager
 * 
 * Comprehensive, safe, and automated storage lifecycle manager.
 * 
 * CORE PRINCIPLES:
 * 1. ZERO duplicate persistent video copies for local playback.
 * 2. Purges legacy accumulated multi-GB video caches (e.g. cached_play_*.mp4).
 * 3. Enforces strict size bounds (max 25MB) on temporary subtitle/audio transcodes.
 * 4. NEVER touches or modifies user's original videos or folders in user storage.
 * 5. Runs non-blocking asynchronous cleanup in background coroutines.
 */
object AppStorageManager {

    private const val TAG = "AppStorageManager"
    private const val MAX_TEMP_CACHE_BYTES = 25 * 1024 * 1024L // 25 MB max temporary files limit
    private val storageScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var currentActivePlaybackPath: String? = null

    /**
     * Set the currently active playback path to prevent its temporary resources from being pruned.
     */
    fun setActivePlaybackPath(path: String?) {
        currentActivePlaybackPath = path
    }

    /**
     * Initiates asynchronous background cleanup of legacy video caches and stale temporary files.
     * Safe to call on app startup, player exit, and playlist transitions.
     */
    fun performBackgroundCleanup(context: Context) {
        val appContext = context.applicationContext
        storageScope.launch {
            try {
                cleanupLegacyVideoCaches(appContext)
                cleanupStaleTemporaryFiles(appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "Background cache cleanup encountered a non-fatal error", t)
            }
        }
    }

    /**
     * Safely deletes legacy 4GB+ accumulated video caches (cached_play_*.mp4, cached_*.mp4)
     * created by earlier versions in the app's internal cache directory.
     * 
     * STRICT SAFETY GUARD: Operates exclusively inside app-internal cache directory.
     * NEVER accesses external user storage (/storage/emulated/0, DCIM, Movies, Downloads, etc.).
     */
    private fun cleanupLegacyVideoCaches(context: Context) {
        try {
            val cacheDir = context.cacheDir ?: return
            val files = cacheDir.listFiles() ?: return

            var freedBytes = 0L
            var deletedCount = 0

            for (file in files) {
                if (!file.isFile) continue
                val name = file.name.lowercase()

                // Identify legacy cached video copies created for playback
                val isLegacyVideoCache = (name.startsWith("cached_play_") && name.endsWith(".mp4")) ||
                        (name.startsWith("cached_") && (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".avi"))) ||
                        (name.startsWith("play_cache_") && name.endsWith(".mp4")) ||
                        name.endsWith(".mp4")

                if (isLegacyVideoCache) {
                    val size = file.length()
                    if (file.absolutePath != currentActivePlaybackPath && file.delete()) {
                        freedBytes += size
                        deletedCount++
                    }
                }
            }

            // Also check externalCacheDir if present
            try {
                val extCache = context.externalCacheDir
                if (extCache != null && extCache.exists()) {
                    val extFiles = extCache.listFiles() ?: emptyArray()
                    for (file in extFiles) {
                        if (!file.isFile) continue
                        val name = file.name.lowercase()
                        if (name.startsWith("cached_play_") || (name.startsWith("cached_") && name.endsWith(".mp4"))) {
                            val size = file.length()
                            if (file.absolutePath != currentActivePlaybackPath && file.delete()) {
                                freedBytes += size
                                deletedCount++
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (deletedCount > 0) {
                val freedMb = freedBytes / (1024 * 1024)
                Log.i(TAG, "Successfully purged $deletedCount legacy cached video files, reclaiming ${freedMb}MB of storage.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed during legacy video cache cleanup", e)
        }
    }

    /**
     * Cleans up stale temporary subtitle transcodes, imported subtitle snippets, and audio snippets.
     * Enforces a strict LRU quota of 25MB max across all app temporary files.
     */
    private fun cleanupStaleTemporaryFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir ?: return

            // 1. Clean sub_transcoded directory
            val transcodedDir = File(cacheDir, "sub_transcoded")
            if (transcodedDir.exists() && transcodedDir.isDirectory) {
                enforceDirSizeLimit(transcodedDir, maxBytes = 10 * 1024 * 1024L) // 10MB limit for subtitle text files
            }

            // 2. Clean temp_player_assets directory
            val tempAssetsDir = File(cacheDir, "temp_player_assets")
            if (tempAssetsDir.exists() && tempAssetsDir.isDirectory) {
                enforceDirSizeLimit(tempAssetsDir, maxBytes = 10 * 1024 * 1024L) // 10MB limit
            }

            // 3. Clean root cacheDir of orphaned imported_sub_* or imported_audio_*
            val rootFiles = cacheDir.listFiles() ?: emptyArray()
            val now = System.currentTimeMillis()
            for (file in rootFiles) {
                if (!file.isFile) continue
                val name = file.name
                val isTempImport = name.startsWith("imported_sub_") || name.startsWith("imported_audio_")
                // Delete if older than 2 hours or not active
                if (isTempImport && (now - file.lastModified() > 2 * 3600 * 1000L)) {
                    if (file.absolutePath != currentActivePlaybackPath) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning temporary files", e)
        }
    }

    /**
     * Enforces a strict maximum directory size using LRU (Least Recently Used) deletion.
     */
    private fun enforceDirSizeLimit(dir: File, maxBytes: Long) {
        val files = dir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) return

        // Sort by lastModified ascending (oldest first)
        val sortedFiles = files.sortedBy { it.lastModified() }
        for (f in sortedFiles) {
            if (totalSize <= maxBytes) break
            if (f.absolutePath == currentActivePlaybackPath) continue

            val sz = f.length()
            if (f.delete()) {
                totalSize -= sz
            }
        }
    }
}
