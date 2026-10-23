package com.example.player

import android.content.Context
import java.io.File

/**
 * Legacy online-track disk cache disabled by design. User-triggered media downloads and automatic full-track
 * caching have been removed; online audio is played as a stream. This compatibility shim remains because the
 * audio player still calls these methods while managing playback.
 */
object OnlineAudioCache {
    private const val DIR_NAME = "online_audio_cache"

    @Volatile private var legacyCacheCleared = false

    private fun clearLegacyCacheOnce(context: Context) {
        if (legacyCacheCleared) return
        synchronized(this) {
            if (legacyCacheCleared) return
            runCatching {
                File(context.applicationContext.cacheDir, DIR_NAME).deleteRecursively()
            }
            legacyCacheCleared = true
        }
    }

    /** There are no offline media cache entries after this cleanup. */
    fun cachedFile(context: Context, videoUrl: String): File? {
        clearLegacyCacheOnce(context)
        return null
    }

    /** Removes any legacy cache directory; no newly downloaded media is stored. */
    fun delete(context: Context, videoUrl: String) {
        clearLegacyCacheOnce(context)
    }

}
