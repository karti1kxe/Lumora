package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/**
 * A link without an attached thumbnail still gets a picture in Recent Streams: a frame from a random point of the
 * video is saved next to the app's files and remembered in [RecentStreamStore].
 */
object RecentThumbnails {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = HashSet<String>()

    private fun dir(context: Context): File = File(context.applicationContext.filesDir, "recent_thumbs").apply { mkdirs() }

    fun captureRandomFrame(context: Context, key: String, streamUrl: String, durationSec: Long) {
        val app = context.applicationContext
        synchronized(running) { if (!running.add(key)) return }
        scope.launch {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(streamUrl, HashMap<String, String>())
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?: (durationSec * 1000L)
                    // somewhere between 10% and 80% of the video: not the black intro, not the end credits
                    val atMs = if (durMs > 0L) (durMs * (0.10 + Random.nextDouble() * 0.70)).toLong() else 5_000L
                    val frame = (
                        if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
                        } else null
                        ) ?: retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val file = File(dir(app), "t_" + (key.hashCode().toLong() and 0xFFFFFFFFL) + ".jpg")
                        file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        RecentStreamStore.setThumbnail(app, key, file.absolutePath)
                    }
                } finally {
                    try { retriever.release() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(running) { running.remove(key) }
            }
        }
    }
}
