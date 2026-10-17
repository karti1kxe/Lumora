package com.example.util.media

import android.content.Context
import android.os.SystemClock
import com.example.util.NetworkBypass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Before a video opens, the servers a website offered are checked for a few seconds (does the playlist / file answer,
 * does its first segment / first byte come), and the first one that really answers - best quality first - is played.
 * The rest stay available as the player's other servers.
 */
object StreamProbe {
    private const val TOTAL_MS = 6_000L
    private const val CALL_MS = 4_000
    private const val MAX_CANDIDATES = 6
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [ranked] best first. Returns the same streams, the first one that works moved to the front. */
    suspend fun orderByWorking(context: Context, ranked: List<MediaStream>): List<MediaStream> {
        val cand = ranked.take(MAX_CANDIDATES)
        if (cand.size < 2) return ranked
        val state = AtomicIntegerArray(cand.size) // 0 = not known yet, 1 = works, 2 = does not
        val app = context.applicationContext
        for (i in cand.indices) {
            scope.launch {
                state.set(i, if (try { works(app, cand[i]) } catch (_: Throwable) { false }) 1 else 2)
            }
        }
        val deadline = SystemClock.elapsedRealtime() + TOTAL_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val firstOk = (0 until cand.size).firstOrNull { state.get(it) == 1 }
            val limit = firstOk ?: cand.size
            val undecidedBefore = (0 until limit).any { state.get(it) == 0 }
            if (firstOk != null && !undecidedBefore) break
            if ((0 until cand.size).none { state.get(it) == 0 }) break
            delay(100L)
        }
        val ok = (0 until cand.size).firstOrNull { state.get(it) == 1 } ?: return ranked
        if (ok == 0) return ranked
        // streams that failed the check go to the back; their order is otherwise kept
        val good = ArrayList<MediaStream>()
        val bad = ArrayList<MediaStream>()
        good.add(cand[ok])
        for (i in cand.indices) if (i != ok) (if (state.get(i) == 2) bad else good).add(cand[i])
        return good + bad + ranked.drop(cand.size)
    }

    private fun works(context: Context, s: MediaStream): Boolean {
        val bypass = try { NetworkBypass.okHttpProxy(context) } catch (_: Throwable) { null }
        return check(s, null) || (bypass != null && check(s, bypass))
    }

    private fun open(url: String, headers: Map<String, String>, proxy: Proxy?, range: Boolean): HttpURLConnection =
        (URL(url).openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).apply {
            connectTimeout = CALL_MS
            readTimeout = CALL_MS
            instanceFollowRedirects = true
            for ((k, v) in headers) {
                if (k.isNotBlank() && v.isNotBlank() && !k.equals("Host", true) && !k.equals("Accept-Encoding", true)) {
                    try { setRequestProperty(k, v) } catch (_: Throwable) {}
                }
            }
            if (headers.keys.none { it.equals("User-Agent", true) }) setRequestProperty("User-Agent", UA)
            if (range) setRequestProperty("Range", "bytes=0-0")
        }

    private fun text(url: String, headers: Map<String, String>, proxy: Proxy?): String? {
        val c = open(url, headers, proxy, false)
        return try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.use { ins ->
                val buf = ByteArray(64 * 1024)
                var n = 0
                while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } finally { c.disconnect() }
    }

    /** One byte of [url] arrives and it is not a web page / error text. */
    private fun firstByte(url: String, headers: Map<String, String>, proxy: Proxy?): Boolean {
        val c = open(url, headers, proxy, true)
        return try {
            if (c.responseCode !in 200..299) return false
            val type = (c.contentType ?: "").lowercase()
            if (type.startsWith("text/html") || type.contains("json") || type.startsWith("text/plain") && !url.contains(".txt")) return false
            c.inputStream.use { it.read() >= 0 }
        } finally { c.disconnect() }
    }

    private fun resolveUrl(base: String, ref: String): String =
        try { URI(base.substringBefore('#')).resolve(ref.trim()).toString() } catch (_: Throwable) { ref }

    private fun check(s: MediaStream, proxy: Proxy?): Boolean {
        val link = s.link
        if (link.startsWith("torrent://", true) || link.startsWith("magnet:", true)) return true
        val h = s.headers
        return try {
            when {
                HlsVariants.looksLikeHls(link, s.type) -> {
                    var url = link
                    var body = text(url, h, proxy) ?: return false
                    if (!body.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("#EXTM3U")) return false
                    // master playlist -> its first rendition, then that rendition's first segment
                    if (body.contains("#EXT-X-STREAM-INF")) {
                        val variant = body.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return true
                        url = resolveUrl(url, variant)
                        body = text(url, h, proxy) ?: return false
                    }
                    val seg = body.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return true
                    firstByte(resolveUrl(url, seg), h, proxy)
                }
                s.type.equals("mpd", true) || s.type.equals("dash", true) || link.substringBefore('?').lowercase().endsWith(".mpd") ->
                    (text(link, h, proxy) ?: return false).contains("<MPD")
                else -> firstByte(link, h, proxy)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
