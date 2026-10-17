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
    private const val TOTAL_MS = 12_000L
    private const val CALL_MS = 5_000
    private const val MAX_CANDIDATES = 8
    /** Headers the player (mpv) builds itself and never sends from a provider: the check must not send them either. */
    private val PLAYER_OWN_HEADERS = setOf(
        "host", "connection", "content-length", "content-type", "accept-encoding", "range", "if-range",
        "transfer-encoding", "te", "upgrade-insecure-requests"
    )
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The checked servers: [ordered] has the first one that really delivers video in front; [anyWorks] is false when none did. */
    class Outcome(val ordered: List<MediaStream>, val anyWorks: Boolean)

    /** [ranked] best first. Returns the same streams, the first one that works moved to the front. */
    suspend fun orderByWorking(context: Context, ranked: List<MediaStream>): List<MediaStream> = verify(context, ranked).ordered

    /**
     * Checks the servers the way the player will use them (same headers, same requests: playlist, first segment, key,
     * first bytes of the file) and says whether any of them really delivers video. A single server is checked too.
     */
    suspend fun verify(context: Context, ranked: List<MediaStream>): Outcome {
        val cand = ranked.take(MAX_CANDIDATES)
        if (cand.isEmpty()) return Outcome(ranked, false)
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
        val ok = (0 until cand.size).firstOrNull { state.get(it) == 1 } ?: return Outcome(ranked, false)
        if (ok == 0) return Outcome(ranked, true)
        // streams that failed the check go to the back; their order is otherwise kept
        val good = ArrayList<MediaStream>()
        val bad = ArrayList<MediaStream>()
        good.add(cand[ok])
        for (i in cand.indices) if (i != ok) (if (state.get(i) == 2) bad else good).add(cand[i])
        return Outcome(good + bad + ranked.drop(cand.size), true)
    }

    private fun works(context: Context, s: MediaStream): Boolean {
        val bypass = try { NetworkBypass.okHttpProxy(context) } catch (_: Throwable) { null }
        return check(s, null) || (bypass != null && check(s, bypass))
    }

    private fun open(url: String, headers: Map<String, String>, proxy: Proxy?, range: String?): HttpURLConnection =
        (URL(url).openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).apply {
            connectTimeout = CALL_MS
            readTimeout = CALL_MS
            instanceFollowRedirects = true
            for ((k, v) in headers) {
                if (k.isNotBlank() && v.isNotBlank() && k.lowercase() !in PLAYER_OWN_HEADERS) {
                    try { setRequestProperty(k, v) } catch (_: Throwable) {}
                }
            }
            if (headers.keys.none { it.equals("User-Agent", true) }) setRequestProperty("User-Agent", UA)
            if (range != null) setRequestProperty("Range", range)
        }

    private fun text(url: String, headers: Map<String, String>, proxy: Proxy?): String? {
        val c = open(url, headers, proxy, null)
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

    /** The first bytes of [url] arrive and they are media, not a web page / JSON / error text. */
    private fun firstByte(url: String, headers: Map<String, String>, proxy: Proxy?, range: String?): Boolean {
        val c = open(url, headers, proxy, range)
        return try {
            if (c.responseCode !in 200..299) return false
            val type = (c.contentType ?: "").lowercase()
            if (type.startsWith("text/html") || type.contains("json") || type.startsWith("text/plain") && !url.contains(".txt")) return false
            c.inputStream.use { ins ->
                val buf = ByteArray(32)
                var n = 0
                while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
                n > 0 && !looksLikeWebPage(buf, n)
            }
        } finally { c.disconnect() }
    }

    private fun looksLikeWebPage(b: ByteArray, n: Int): Boolean {
        var i = 0
        if (n >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) i = 3
        while (i < n && (b[i] == ' '.code.toByte() || b[i] == '\n'.code.toByte() || b[i] == '\r'.code.toByte() || b[i] == '\t'.code.toByte())) i++
        if (i >= n) return false
        return b[i] == '<'.code.toByte() || b[i] == '{'.code.toByte()
    }

    /** An HLS key file: it must really answer with its key bytes, or the segments can never be decrypted (endless buffering). */
    private fun keyReachable(url: String, headers: Map<String, String>, proxy: Proxy?): Boolean {
        val c = open(url, headers, proxy, null)
        return try {
            if (c.responseCode !in 200..299) return false
            c.inputStream.use { ins ->
                val buf = ByteArray(32)
                var n = 0
                while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
                n >= 16 && !looksLikeWebPage(buf, n)
            }
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
                    val keyUri = Regex("#EXT-X-KEY:[^\\n]*METHOD=(?!NONE)[^\\n]*URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
                    if (keyUri != null && !keyUri.startsWith("data:", true) && !keyReachable(resolveUrl(url, keyUri), h, proxy)) return false
                    val seg = body.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return true
                    // like the player: a segment is asked for as a whole, without a Range
                    firstByte(resolveUrl(url, seg), h, proxy, null)
                }
                s.type.equals("mpd", true) || s.type.equals("dash", true) || link.substringBefore('?').lowercase().endsWith(".mpd") ->
                    (text(link, h, proxy) ?: return false).contains("<MPD")
                // like the player: a plain file is asked for from its start
                else -> firstByte(link, h, proxy, "bytes=0-")
            }
        } catch (_: Throwable) {
            false
        }
    }
}
