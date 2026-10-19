package com.example.util.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.util.NetworkBypass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The picture of an episode card is a frame of the episode's own video, and getting it doubles as a real playability
 * test: every server the website offers is tried in turn (best first) until one really delivers video that decodes.
 * That server is remembered for the episode, so tapping the episode later opens it at once instead of buffering on a
 * server that only connects. The frame is kept on disk (and the choice in preferences), so a second visit is instant.
 */
object EpisodeFrames {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val PREFS = "episode_servers"
    private const val MAX_SERVERS_TRIED = 4
    private const val PER_SERVER_MS = 12_000L
    /** The next server is started in parallel when the running one has not delivered a frame after this long. */
    private const val HEDGE_MS = 3_500L
    private const val PARTIAL_BYTES = 5 * 1024 * 1024
    private const val RETRY_FAILED_AFTER_MS = 6L * 60L * 60L * 1000L

    private val gate = Semaphore(5)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val failedAt = ConcurrentHashMap<String, Long>()

    private fun key(plugin: MediaPluginInfo, link: String) = plugin.value + "|" + link

    private fun file(context: Context, key: String): File {
        val dir = File(context.applicationContext.filesDir, "episode_frames").apply { mkdirs() }
        val md = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir, md.joinToString("") { "%02x".format(it) } + ".jpg")
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The saved frame's path, or null when there is none yet. */
    fun cached(context: Context, plugin: MediaPluginInfo, link: String): String? =
        file(context, key(plugin, link)).takeIf { it.exists() && it.length() > 0L }?.absolutePath

    /** The server (and quality) that really delivered this episode's video last time, if it was checked. */
    class Preferred(val server: String, val quality: String?, val type: String)

    fun preferred(context: Context, plugin: MediaPluginInfo, link: String): Preferred? {
        val raw = prefs(context).getString(key(plugin, link), null) ?: return null
        return try {
            val o = JSONObject(raw)
            Preferred(o.getString("s"), o.optString("q").ifBlank { null }, o.optString("t"))
        } catch (_: Throwable) { null }
    }

    private fun remember(context: Context, plugin: MediaPluginInfo, link: String, s: MediaStream) {
        val o = JSONObject().put("s", s.server).put("q", s.quality ?: "").put("t", s.type)
        prefs(context).edit().putString(key(plugin, link), o.toString()).apply()
    }

    /**
     * Makes (or finds) the frame of one episode and finds the server that works for it. Null when no server delivered video.
     * Callers that ask for the same episode at the same time share one run.
     */
    suspend fun obtain(context: Context, plugin: MediaPluginInfo, link: String, type: String, title: String, episodeIndex: Int): String? {
        cached(context, plugin, link)?.let { return it }
        val k = key(plugin, link)
        failedAt[k]?.let { if (System.currentTimeMillis() - it < RETRY_FAILED_AFTER_MS) return null }
        val mine = CompletableDeferred<String?>()
        val running = inflight.putIfAbsent(k, mine)
        if (running != null) return running.await()
        val app = context.applicationContext
        return try {
            val result = gate.withPermit { cached(app, plugin, link) ?: work(app, plugin, link, type, k) }
            if (result == null) failedAt[k] = System.currentTimeMillis()
            mine.complete(result)
            result
        } catch (e: CancellationException) {
            mine.complete(null)
            throw e
        } catch (_: Throwable) {
            failedAt[k] = System.currentTimeMillis()
            mine.complete(null)
            null
        } finally {
            inflight.remove(k)
        }
    }

    private suspend fun work(context: Context, plugin: MediaPluginInfo, link: String, type: String, k: String): String? {
        val streams = MediaProviderRepository.loadStreams(context, plugin.value, link, type, false)
            .distinctBy { it.link }
            .take(MAX_SERVERS_TRIED)
        val preferred = preferred(context, plugin, link)
        // the server that worked last time is tried first
        val ordered = if (preferred == null) streams else streams.sortedBy { if (it.server == preferred.server && it.quality == preferred.quality) 0 else 1 }
        val out = file(context, k)
        if (ordered.isEmpty()) return null
        // Servers are tried "hedged": the best one starts at once, the next one joins as soon as the running one fails or
        // is slow (HEDGE_MS), so a dead first server no longer costs its whole timeout before the next is even tried.
        class Run(val stream: MediaStream, val tmp: File) {
            @Volatile var done = false
            @Volatile var ok = false
            var job: kotlinx.coroutines.Job? = null
        }
        val runs = ArrayList<Run>()
        var next = 0
        var lastStart = 0L
        fun startNext() {
            val s = ordered[next]
            val tmp = File(out.parentFile, out.name + "." + next + ".tmp")
            next++
            lastStart = System.currentTimeMillis()
            val r = Run(s, tmp)
            r.job = scope.launch {
                r.ok = try { grab(context, s, tmp) } catch (_: Throwable) { false }
                r.done = true
            }
            runs.add(r)
        }
        val begin = System.currentTimeMillis()
        try {
            startNext()
            while (true) {
                val winner = runs.firstOrNull { it.done && it.ok }
                if (winner != null) {
                    if (out.exists()) out.delete()
                    if (!winner.tmp.renameTo(out)) { winner.tmp.copyTo(out, overwrite = true) }
                    remember(context, plugin, link, winner.stream)
                    return out.absolutePath
                }
                val now = System.currentTimeMillis()
                val allDone = runs.all { it.done }
                if (next < ordered.size && (allDone || now - lastStart >= HEDGE_MS)) startNext()
                else if (allDone) return null
                if (now - begin > PER_SERVER_MS * 2) return null
                kotlinx.coroutines.delay(40L)
            }
        } finally {
            for (r in runs) { r.job?.cancel(); if (r.tmp.exists()) r.tmp.delete() }
        }
    }

    // ---------------------------------------------------------------- getting one frame out of one stream

    private fun open(url: String, headers: Map<String, String>, proxy: Proxy?, rangeTo: Int?): HttpURLConnection =
        (URL(url).openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            instanceFollowRedirects = true
            for ((k, v) in headers) {
                if (k.isNotBlank() && v.isNotBlank() && !k.equals("Host", true) && !k.equals("Accept-Encoding", true)) {
                    try { setRequestProperty(k, v) } catch (_: Throwable) {}
                }
            }
            if (headers.keys.none { it.equals("User-Agent", true) }) setRequestProperty("User-Agent", UA)
            if (rangeTo != null) setRequestProperty("Range", "bytes=0-$rangeTo")
        }

    private fun fetchText(url: String, headers: Map<String, String>, proxy: Proxy?): String? {
        val c = open(url, headers, proxy, null)
        return try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.use { ins ->
                val buf = ByteArray(256 * 1024)
                var n = 0
                while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } finally { c.disconnect() }
    }

    /** Appends up to [maxBytes] of [url] to [out]; returns the number of bytes written (0 = nothing / error status). */
    private fun appendBytes(url: String, headers: Map<String, String>, proxy: Proxy?, maxBytes: Int, ranged: Boolean, out: java.io.OutputStream): Int {
        val c = open(url, headers, proxy, if (ranged) maxBytes - 1 else null)
        return try {
            if (c.responseCode !in 200..299) return 0
            val type = (c.contentType ?: "").lowercase()
            if (type.startsWith("text/html") || type.contains("json")) return 0
            var total = 0
            c.inputStream.use { ins ->
                val buf = ByteArray(32 * 1024)
                while (total < maxBytes) {
                    val r = ins.read(buf, 0, minOf(buf.size, maxBytes - total))
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    total += r
                }
            }
            total
        } finally { c.disconnect() }
    }

    private fun resolveUrl(base: String, ref: String): String =
        try { URI(base.substringBefore('#')).resolve(ref.trim()).toString() } catch (_: Throwable) { ref }

    private fun grab(context: Context, s: MediaStream, out: File): Boolean {
        val bypass = try { NetworkBypass.okHttpProxy(context) } catch (_: Throwable) { null }
        val routes: List<Proxy?> = if (bypass != null) listOf(null, bypass) else listOf(null)
        for (proxy in routes) {
            val bmp = try { frameOf(context, s, proxy) } catch (_: Throwable) { null }
            if (bmp != null) {
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                return true
            }
        }
        return false
    }

    private fun frameOf(context: Context, s: MediaStream, proxy: Proxy?): Bitmap? {
        val tmp = File(context.cacheDir, "frame_" + System.nanoTime() + (if (HlsVariants.looksLikeHls(s.link, s.type)) ".ts" else ".bin"))
        try {
            return if (HlsVariants.looksLikeHls(s.link, s.type)) {
                // the first few segments are downloaded and a frame is taken from them (the player APIs are unreliable with remote playlists)
                if (hlsHead(s, proxy, tmp)) frameFromFile(tmp, 1_000L) else null
            } else {
                // a direct file: first the whole file through the player APIs (a frame from inside the video) ...
                val whole = if (proxy == null) frameFromUrl(s) else null
                // ... then the first megabytes only
                whole ?: if (fileHead(s, proxy, tmp)) frameFromFile(tmp, 2_000L) else null
            }
        } finally {
            tmp.delete()
        }
    }

    private fun hlsHead(s: MediaStream, proxy: Proxy?, out: File): Boolean {
        var url = s.link
        var text = fetchText(url, s.headers, proxy) ?: return false
        if (!text.contains("#EXTM3U")) return false
        if (text.contains("#EXT-X-STREAM-INF")) {
            // the lightest rendition is enough for a picture and downloads fastest
            val variants = HlsVariants.parseMaster(text, url)
            val v = variants.filter { it.height in 1..540 }.maxByOrNull { it.height } ?: variants.lastOrNull() ?: return false
            url = v.uri
            text = fetchText(url, s.headers, proxy) ?: return false
        }
        // encrypted segments cannot be decoded from a partial download
        if (Regex("#EXT-X-KEY:[^\\n]*METHOD=(?!NONE)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return false
        val lines = text.lines().map { it.trim() }
        val segs = lines.filter { it.isNotEmpty() && !it.startsWith("#") }.take(3).map { resolveUrl(url, it) }
        if (segs.isEmpty()) return false
        val mapUri = Regex("#EXT-X-MAP:[^\\n]*URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        var written = 0
        out.outputStream().use { os ->
            if (mapUri != null) written += appendBytes(resolveUrl(url, mapUri), s.headers, proxy, 2 * 1024 * 1024, false, os)
            for (seg in segs) {
                if (written >= PARTIAL_BYTES) break
                val n = appendBytes(seg, s.headers, proxy, PARTIAL_BYTES - written, false, os)
                if (n <= 0 && written == 0) return false
                written += n
            }
        }
        return written > 0
    }

    private fun fileHead(s: MediaStream, proxy: Proxy?, out: File): Boolean {
        val n = out.outputStream().use { os -> appendBytes(s.link, s.headers, proxy, PARTIAL_BYTES, true, os) }
        return n > 0
    }

    private fun frameFromUrl(s: MediaStream): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val headers = HashMap<String, String>()
            for ((k, v) in s.headers) if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
            if (headers.keys.none { it.equals("User-Agent", true) }) headers["User-Agent"] = UA
            retriever.setDataSource(s.link, headers)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            for (attempt in 0 until 2) {
                // somewhere inside the video (15 % - 65 %): not the intro logo, not the credits
                val atMs = if (durMs > 0L) (durMs * (0.15 + Random.nextDouble() * 0.50)).toLong() else 30_000L
                val f = grabAt(retriever, atMs)
                if (f != null && !isMostlyBlack(f)) return f
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun frameFromFile(f: File, atMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(f.absolutePath)
            var last: Bitmap? = null
            for (t in longArrayOf(atMs, atMs + 2_000L, 500L)) {
                val b = grabAt(retriever, t)
                if (b != null) { if (!isMostlyBlack(b)) return b; last = b }
            }
            last
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun grabAt(r: MediaMetadataRetriever, atMs: Long): Bitmap? =
        (if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270) else null)
            ?: r.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

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
