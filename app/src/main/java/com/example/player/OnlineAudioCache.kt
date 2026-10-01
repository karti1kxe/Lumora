package com.example.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * On-disk cache for online audio tracks. The whole track is downloaded in resumable 2 MB Range
 * chunks (so a dropped connection or a throttled CDN never truncates playback), then played from
 * the local file. Only COMPLETE files are ever exposed through [cachedFile]; partial data lives in
 * `<key>.part` and is resumed on the next attempt.
 */
object OnlineAudioCache {
    private const val TAG = "OnlineAudioCache"
    private const val DIR_NAME = "online_audio_cache"
    private const val CHUNK_BYTES = 2L * 1024L * 1024L
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    private const val MAX_CONSECUTIVE_FAILURES = 6
    /** Anything smaller than this is an error page / stub, never a real audio track. */
    private const val MIN_AUDIO_BYTES = 16L * 1024L
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Not an IOException on purpose: retrying an HTML/JSON response can never succeed. */
    private class NotAudioException(message: String) : Exception(message)

    private fun keyFor(videoUrl: String): String = AdvancedAssStyleEngine.sha1Hex(videoUrl).take(24)

    private fun dir(context: Context): File =
        File(context.applicationContext.cacheDir, DIR_NAME).apply { mkdirs() }

    /** The fully downloaded file for [videoUrl], or null when it is not (completely) cached. */
    fun cachedFile(context: Context, videoUrl: String): File? {
        val f = File(dir(context), keyFor(videoUrl) + ".audio")
        if (!(f.isFile && f.length() >= MIN_AUDIO_BYTES)) return null
        // Refresh the "last used" time so trimming behaves as a real LRU: tracks that are played
        // again and again stay cached (and load instantly), the oldest unused ones go first.
        try {
            val now = System.currentTimeMillis()
            if (now - f.lastModified() > 60_000L) f.setLastModified(now)
        } catch (_: Throwable) {}
        return f
    }

    fun delete(context: Context, videoUrl: String) {
        val key = keyFor(videoUrl)
        try { File(dir(context), "$key.audio").delete() } catch (_: Throwable) {}
        try { File(dir(context), "$key.part").delete() } catch (_: Throwable) {}
        try { File(dir(context), "$key.meta").delete() } catch (_: Throwable) {}
    }

    /**
     * Downloads [streamUrl] completely (resuming a previous partial download) and returns the
     * cached file, or null on failure. Concurrent calls for the same track are serialised, so a
     * second caller simply receives the finished file.
     */
    suspend fun download(context: Context, videoUrl: String, streamUrl: String): File? =
        withContext(Dispatchers.IO) {
            cachedFile(context, videoUrl)?.let { return@withContext it }
            val key = keyFor(videoUrl)
            val lock = locks.getOrPut(key) { Mutex() }
            lock.withLock {
                cachedFile(context, videoUrl)?.let { return@withLock it }
                val directory = dir(context)
                val part = File(directory, "$key.part")
                val target = File(directory, "$key.audio")
                try {
                    if (!downloadInto(part, File(directory, "$key.meta"), streamUrl)) return@withLock null
                    if (part.length() < MIN_AUDIO_BYTES) {
                        try { part.delete() } catch (_: Throwable) {}
                        return@withLock null
                    }
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) return@withLock null
                    target.setLastModified(System.currentTimeMillis())
                    try { File(directory, "$key.meta").delete() } catch (_: Throwable) {}
                    trim(directory)
                    target
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Download failed: ${t.message}")
                    null
                }
            }
        }

    private suspend fun downloadInto(part: File, meta: File, streamUrl: String): Boolean {
        var offset = if (part.isFile) part.length() else 0L
        var total = -1L
        // Total size of the file the .part belongs to. A different total means the resolver handed
        // us another format of the same video, so the partial data must not be reused.
        val storedTotal = try { meta.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() } catch (_: Throwable) { null }
        var failures = 0
        while (true) {
            coroutineContext.ensureActive()
            if (total in 1..offset) return true
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
                .build()
            var finishedWhole = false
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 416) {
                        // Nothing left after our offset: the file is already complete.
                        if (offset > 0L) {
                            total = offset
                            return@use
                        }
                        throw IOException("HTTP 416")
                    }
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    // A web page / JSON error must never be stored as if it were audio.
                    val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                    if (contentType.startsWith("text/") || contentType.contains("json") || contentType.contains("html")) {
                        throw NotAudioException("Not an audio response ($contentType)")
                    }
                    val body = resp.body ?: throw IOException("Empty body")
                    if (resp.code == 200) {
                        // Server ignored Range: it sends the whole file, so start over.
                        offset = 0L
                        FileOutputStream(part, false).use { out -> offset += copy(body.byteStream(), out) }
                        total = offset
                        finishedWhole = true
                    } else {
                        val range = resp.header("Content-Range")
                        val totalFromHeader = range?.substringAfterLast('/')?.trim()?.toLongOrNull()
                        if (totalFromHeader != null && totalFromHeader > 0L) {
                            if (offset > 0L && storedTotal != null && storedTotal != totalFromHeader) {
                                try { part.delete() } catch (_: Throwable) {}
                                offset = 0L
                                try { meta.writeText(totalFromHeader.toString()) } catch (_: Throwable) {}
                                return@use
                            }
                            total = totalFromHeader
                            try { meta.writeText(totalFromHeader.toString()) } catch (_: Throwable) {}
                        }
                        var written = 0L
                        FileOutputStream(part, true).use { out -> written = copy(body.byteStream(), out) }
                        offset += written
                        if (written == 0L) throw IOException("No data received")
                        if (total < 0L && written < CHUNK_BYTES) {
                            total = offset // short final chunk and no total announced
                            finishedWhole = true
                        }
                    }
                }
                failures = 0
                if (finishedWhole) return true
                if (total in 1..offset) return true
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: IOException) {
                failures++
                Log.w(TAG, "Chunk failed at $offset (attempt $failures): ${e.message}")
                if (failures >= MAX_CONSECUTIVE_FAILURES) return false
                // Keep whatever is on disk in sync with our offset before retrying.
                offset = if (part.isFile) part.length() else 0L
                delay(700L * failures)
            }
        }
    }

    private suspend fun copy(input: java.io.InputStream, out: FileOutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
            coroutineContext.ensureActive()
        }
        return total
    }

    private fun trim(directory: File) {
        try {
            val now = System.currentTimeMillis()
            val files = directory.listFiles() ?: return
            files.filter { it.name.endsWith(".part") && now - it.lastModified() > 24L * 3600_000L }
                .forEach { it.delete() }
            var total = files.filter { it.name.endsWith(".audio") }.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return
            // Oldest first; never touch anything used in the last 10 minutes (current / next tracks).
            for (f in files.filter { it.name.endsWith(".audio") }.sortedBy { it.lastModified() }) {
                if (total <= MAX_CACHE_BYTES) break
                if (now - f.lastModified() < 10L * 60_000L) continue
                total -= f.length()
                f.delete()
            }
        } catch (_: Throwable) {}
    }
}
