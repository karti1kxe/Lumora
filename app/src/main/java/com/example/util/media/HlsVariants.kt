package com.example.util.media

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL

/** One video rendition (EXT-X-STREAM-INF) of an HLS master playlist. */
data class HlsVariant(
    val bandwidth: Long,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    /** Video codec only (e.g. avc1.640028), blank when the playlist does not say. */
    val videoCodec: String,
    val uri: String,
    /** GROUP-ID of the EXT-X-MEDIA audio / subtitle renditions this variant plays with (blank = none / muxed in). */
    val audioGroup: String = "",
    val subtitleGroup: String = ""
) {
    /** "1080p", or the bitrate when the playlist has no resolution. */
    val label: String get() = if (height > 0) "${height}p" else bitrateText()

    /** "HD" / "SD" (and 2K / 4K) tag shown next to the quality, like the reference player. */
    val badge: String get() = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "2K"
        height >= 720 -> "HD"
        else -> "SD"
    }

    fun bitrateText(): String = when {
        bandwidth >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f Mbps", bandwidth / 1_000_000.0)
        bandwidth > 0L -> "${bandwidth / 1000} kbps"
        else -> ""
    }

    /** "4.1 Mbps · 1920x1080 · avc1.640028" */
    val info: String get() = listOf(
        bitrateText(),
        if (width > 0 && height > 0) "${width}x$height" else "",
        videoCodec
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

/** One alternate rendition (EXT-X-MEDIA) of a master playlist that has its own playlist: an audio language or a subtitle. */
data class HlsRendition(
    /** "AUDIO" or "SUBTITLES". */
    val type: String,
    val groupId: String,
    val name: String,
    val language: String,
    val uri: String,
    val isDefault: Boolean
)

/** The qualities and the audio / subtitle renditions of a master playlist. */
data class HlsMaster(val variants: List<HlsVariant>, val renditions: List<HlsRendition>)

/** Reads the renditions (the quality list) out of an HLS master playlist. */
object HlsVariants {
    fun looksLikeHls(link: String, type: String?): Boolean {
        val path = link.substringBefore('?').substringBefore('#').lowercase()
        val t = type?.lowercase().orEmpty()
        return t == "m3u8" || t == "hls" || path.endsWith(".m3u8") || path.contains(".m3u8/")
    }

    /**
     * Downloads [url] and returns its variants, best first. Empty when it is not a master playlist (a single media
     * playlist, a plain file, or a request that failed): the player then simply has no quality choice.
     */
    fun fetch(url: String, headers: Map<String, String>, proxy: Proxy? = null, timeoutMs: Int = 7000): List<HlsVariant> =
        fetchResult(url, headers, proxy, timeoutMs) ?: emptyList()

    /** Like [fetch], but null when the request itself failed (so the caller can try another route); empty = answered, no master playlist. */
    fun fetchResult(url: String, headers: Map<String, String>, proxy: Proxy? = null, timeoutMs: Int = 7000): List<HlsVariant>? {
        val (text, base) = fetchText(url, headers, proxy, timeoutMs) ?: return null
        return parseMaster(text, base)
    }

    /** The quality list together with the audio / subtitle renditions; null when the request itself failed. */
    fun fetchMaster(url: String, headers: Map<String, String>, proxy: Proxy? = null, timeoutMs: Int = 7000): HlsMaster? {
        val (text, base) = fetchText(url, headers, proxy, timeoutMs) ?: return null
        return HlsMaster(parseMaster(text, base), parseRenditions(text, base))
    }

    /** Length of a media playlist in seconds (sum of its EXTINF entries); 0 when it cannot be read. */
    fun fetchDurationSec(url: String, headers: Map<String, String>, timeoutMs: Int = 6000): Double {
        val (text, _) = fetchText(url, headers, null, timeoutMs, maxBytes = 8 * 1024 * 1024) ?: return 0.0
        var sum = 0.0
        for (line in text.lineSequence()) {
            if (line.startsWith("#EXTINF:")) sum += line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
        }
        return sum
    }

    private fun fetchText(url: String, headers: Map<String, String>, proxy: Proxy?, timeoutMs: Int, maxBytes: Int = 2 * 1024 * 1024): Pair<String, String>? {
        return try {
            val conn = (URL(url).openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                for ((k, v) in headers) {
                    if (k.isNotBlank() && v.isNotBlank() && !k.equals("Accept-Encoding", true) && !k.equals("Host", true)) {
                        try { setRequestProperty(k, v) } catch (_: Throwable) {}
                    }
                }
                if (headers.keys.none { it.equals("User-Agent", true) }) {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                }
            }
            try {
                if (conn.responseCode !in 200..299) return null
                val text = conn.inputStream.use { ins ->
                    val bos = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (bos.size() < maxBytes) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        bos.write(buf, 0, n)
                    }
                    bos.toString("UTF-8")
                }
                text to conn.url.toString()
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** The EXT-X-MEDIA audio / subtitle renditions that have their own playlist (renditions muxed into the variant are not listed). */
    fun parseRenditions(text: String, baseUrl: String): List<HlsRendition> {
        if (!text.trimStart().startsWith("#EXTM3U")) return emptyList()
        val out = ArrayList<HlsRendition>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (!line.startsWith("#EXT-X-MEDIA:")) continue
            val attrs = parseAttributes(line.substringAfter(':'))
            val type = attrs["TYPE"].orEmpty().uppercase()
            if (type != "AUDIO" && type != "SUBTITLES") continue
            val uri = attrs["URI"].orEmpty()
            if (uri.isBlank()) continue
            val abs = try { URI(baseUrl).resolve(uri).toString() } catch (_: Throwable) { uri }
            val lang = attrs["LANGUAGE"].orEmpty()
            val name = attrs["NAME"].orEmpty().ifBlank { lang }.ifBlank { if (type == "AUDIO") "Audio" else "Subtitle" }
            out.add(HlsRendition(type, attrs["GROUP-ID"].orEmpty(), name, lang, abs, attrs["DEFAULT"].equals("YES", true)))
        }
        return out
    }

    fun parseMaster(text: String, baseUrl: String): List<HlsVariant> {
        if (!text.trimStart().startsWith("#EXTM3U") || !text.contains("#EXT-X-STREAM-INF")) return emptyList()
        val out = ArrayList<HlsVariant>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseAttributes(line.substringAfter(':'))
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                val uriLine = lines.getOrNull(j)?.trim().orEmpty()
                if (uriLine.isNotEmpty()) {
                    val bw = attrs["BANDWIDTH"]?.toLongOrNull() ?: attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0L
                    val res = attrs["RESOLUTION"].orEmpty()
                    val w = res.substringBefore('x', "").toIntOrNull() ?: 0
                    val h = res.substringAfter('x', "").toIntOrNull() ?: 0
                    val fps = attrs["FRAME-RATE"]?.toDoubleOrNull() ?: 0.0
                    val codecs = attrs["CODECS"].orEmpty().split(',').map { it.trim() }
                    val video = codecs.firstOrNull { c ->
                        val l = c.lowercase()
                        l.startsWith("avc") || l.startsWith("hvc") || l.startsWith("hev") || l.startsWith("vp0") ||
                            l.startsWith("vp9") || l.startsWith("av01") || l.startsWith("dvh")
                    }.orEmpty()
                    val abs = try { URI(baseUrl).resolve(uriLine).toString() } catch (_: Throwable) { uriLine }
                    out.add(HlsVariant(bw, w, h, fps, video, abs, attrs["AUDIO"].orEmpty(), attrs["SUBTITLES"].orEmpty()))
                }
                i = j
            }
            i++
        }
        // best first; the same resolution at two bitrates stays as two entries
        return out.distinctBy { Triple(it.height, it.bandwidth, it.width) }
            .sortedWith(compareByDescending<HlsVariant> { it.height }.thenByDescending { it.bandwidth })
    }

    private fun parseAttributes(s: String): Map<String, String> {
        val map = HashMap<String, String>()
        val re = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
        for (m in re.findAll(s)) map[m.groupValues[1]] = m.groupValues[2].trim().trim('"')
        return map
    }
}
