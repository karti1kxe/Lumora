package com.example.util.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The picture of an episode card: a frame taken from the video itself (not the title's banner). It is made when the card
 * is shown - the episode's stream is resolved and one frame is read from somewhere inside the video - and kept on disk,
 * so every later visit shows it at once.
 */
object EpisodeFrames {
    private val gate = Semaphore(2)
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun key(plugin: MediaPluginInfo, link: String) = plugin.value + "|" + link

    private fun file(context: Context, key: String): File {
        val dir = File(context.applicationContext.filesDir, "episode_frames").apply { mkdirs() }
        val md = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir, md.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    /** The saved frame's path, or null when there is none yet. */
    fun cached(context: Context, plugin: MediaPluginInfo, link: String): String? =
        file(context, key(plugin, link)).takeIf { it.exists() && it.length() > 0L }?.absolutePath

    /** Makes (or finds) the frame of one episode. Null when the video could not be opened. */
    suspend fun obtain(context: Context, plugin: MediaPluginInfo, link: String, type: String, title: String, episodeIndex: Int): String? {
        cached(context, plugin, link)?.let { return it }
        val k = key(plugin, link)
        if (k in failed) return null
        return try {
            gate.withPermit {
                cached(context, plugin, link)?.let { return@withPermit it }
                val stream = MediaPlayback.resolve(context, plugin, link, type, false, title, episodeIndex, verify = false).getOrNull()
                if (stream == null || stream.link.startsWith("torrent://", true)) { failed.add(k); return@withPermit null }
                val out = file(context, k)
                val job = scope.async { grab(stream, out) }
                val ok = withTimeoutOrNull(25_000L) { job.await() } ?: false
                if (ok) out.absolutePath else { failed.add(k); null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed.add(k)
            null
        }
    }

    private fun grab(stream: MediaStream, out: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            val headers = HashMap<String, String>()
            for ((k, v) in stream.headers) if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
            if (headers.keys.none { it.equals("User-Agent", true) }) {
                headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            }
            retriever.setDataSource(stream.link, headers)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var frame: Bitmap? = null
            // a point inside the video (15 % - 65 %): not the intro logo, not the credits; a black frame is retried once
            for (attempt in 0 until 2) {
                val atMs = if (durMs > 0L) (durMs * (0.15 + Random.nextDouble() * 0.50)).toLong() else 40_000L
                val f = (
                    if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270) else null
                    ) ?: retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (f != null && !isMostlyBlack(f)) { frame = f; break }
                if (frame == null) frame = f
            }
            val bmp = frame ?: return false
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } catch (_: Throwable) {
            false
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun isMostlyBlack(b: Bitmap): Boolean {
        return try {
            val s = Bitmap.createScaledBitmap(b, 16, 9, true)
            var sum = 0L
            for (y in 0 until 9) for (x in 0 until 16) {
                val p = s.getPixel(x, y)
                sum += ((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)
            }
            sum / (16 * 9 * 10) < 14
        } catch (_: Throwable) { false }
    }
}
