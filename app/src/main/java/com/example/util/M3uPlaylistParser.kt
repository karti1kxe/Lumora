package com.example.util

/**
 * One playable entry of an M3U / M3U8 playlist (IPTV channel or plain media URL).
 * Everything the file carries is kept: name, real stream URL, logo, group/category and the
 * per-stream HTTP options (User-Agent, referrer, extra headers) that IPTV lists often need.
 */
data class M3uEntry(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val extraHeaders: Map<String, String> = emptyMap()
)

data class M3uParseResult(
    val entries: List<M3uEntry>,
    val title: String? = null,
    /** Non-comment lines that were not a usable URL (kept only for diagnostics). */
    val skippedLines: Int = 0
)

/**
 * Pure Kotlin/JVM parser (no Android classes) so it can be unit-tested on the JVM.
 * It parses the *content* of the playlist - a URL or a file name alone never becomes a playlist.
 */
object M3uPlaylistParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9_\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val ALLOWED_SCHEMES = listOf("http://", "https://", "rtmp://", "rtmps://", "rtsp://", "rtp://", "udp://", "mms://", "mmsh://", "srt://")

    /** True when [text] looks like an M3U/M3U8 playlist (header or at least one #EXTINF). */
    fun looksLikePlaylist(text: String): Boolean {
        val head = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return head.startsWith("#EXTM3U", ignoreCase = true) || head.contains("#EXTINF", ignoreCase = true)
    }

    /**
     * True for an HLS *media/master* playlist (one stream, not a channel list):
     * contains #EXT-X-STREAM-INF / #EXT-X-TARGETDURATION / #EXT-X-MEDIA-SEQUENCE and no #EXTINF-with-name channel list.
     */
    fun isHlsStreamManifest(text: String): Boolean {
        return text.contains("#EXT-X-TARGETDURATION") || text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-MEDIA-SEQUENCE")
    }

    /**
     * [baseUrl] (optional): when the playlist was downloaded from a URL, relative stream lines are resolved
     * against it. Not applied to HLS manifests, whose relative lines are segments/variants, not channels.
     */
    fun parse(text: String, baseUrl: String? = null): M3uParseResult {
        val resolveRelative = !baseUrl.isNullOrBlank() && !isHlsStreamManifest(text)
        val entries = ArrayList<M3uEntry>()
        var playlistTitle: String? = null
        var skipped = 0

        // pending state for the next URL line
        var pName: String? = null
        var pLogo: String? = null
        var pGroup: String? = null
        var pTvgId: String? = null
        var pUa: String? = null
        var pRef: String? = null
        var pHeaders = LinkedHashMap<String, String>()

        fun reset() {
            pName = null; pLogo = null; pGroup = null; pTvgId = null; pUa = null; pRef = null
            pHeaders = LinkedHashMap()
        }

        val lines = text.replace("\uFEFF", "").split('\n')
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                val upper = line.uppercase()
                when {
                    upper.startsWith("#EXTM3U") -> {
                        // header attributes (e.g. x-tvg-url) are not needed for playback
                    }
                    upper.startsWith("#PLAYLIST:") -> {
                        playlistTitle = line.substring(line.indexOf(':') + 1).trim().ifBlank { null }
                    }
                    upper.startsWith("#EXTINF:") -> {
                        // a new #EXTINF without a URL in between replaces the previous (broken) one
                        reset()
                        val body = line.substring("#EXTINF:".length)
                        val commaIdx = firstCommaOutsideQuotes(body)
                        val attrPart = if (commaIdx >= 0) body.substring(0, commaIdx) else body
                        val titlePart = if (commaIdx >= 0) body.substring(commaIdx + 1).trim() else ""
                        for (m in ATTR_REGEX.findAll(attrPart)) {
                            val key = m.groupValues[1].lowercase()
                            val value = (m.groupValues[2].ifEmpty { m.groupValues[3] }).trim()
                            when (key) {
                                "tvg-logo", "logo", "tvg_logo" -> if (value.isNotBlank()) pLogo = value
                                "group-title", "group" -> if (value.isNotBlank()) pGroup = value
                                "tvg-id", "tvg_id" -> if (value.isNotBlank()) pTvgId = value
                                "tvg-name" -> if (pName == null && value.isNotBlank()) pName = value
                                "http-user-agent", "user-agent" -> if (value.isNotBlank()) pUa = value
                                "http-referrer", "http-referer", "referrer", "referer" -> if (value.isNotBlank()) pRef = value
                            }
                        }
                        if (titlePart.isNotBlank()) pName = titlePart
                    }
                    upper.startsWith("#EXTGRP:") -> {
                        val g = line.substring(line.indexOf(':') + 1).trim()
                        if (pGroup.isNullOrBlank() && g.isNotBlank()) pGroup = g
                    }
                    upper.startsWith("#EXTVLCOPT:") -> {
                        val opt = line.substring(line.indexOf(':') + 1)
                        val eq = opt.indexOf('=')
                        if (eq > 0) {
                            val k = opt.substring(0, eq).trim().lowercase()
                            val v = opt.substring(eq + 1).trim()
                            when (k) {
                                "http-user-agent" -> if (v.isNotBlank()) pUa = v
                                "http-referrer", "http-referer" -> if (v.isNotBlank()) pRef = v
                            }
                        }
                    }
                    upper.startsWith("#EXTHTTP:") -> {
                        // {"User-Agent":"x","Referer":"y"} - tiny flat-object reader, no org.json (JVM-testable)
                        val json = line.substring(line.indexOf(':') + 1).trim()
                        for (m in Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(json)) {
                            val k = m.groupValues[1]
                            val v = m.groupValues[2]
                            if (v.isBlank()) continue
                            when (k.lowercase()) {
                                "user-agent" -> pUa = v
                                "referer", "referrer" -> pRef = v
                                else -> pHeaders[k] = v
                            }
                        }
                    }
                    else -> { /* other comments / unknown tags are ignored */ }
                }
                continue
            }

            // ---- a URL line ----
            var urlPart = line
            var uaFromUrl: String? = null
            var refFromUrl: String? = null
            val extraFromUrl = LinkedHashMap<String, String>()
            // Kodi style "url|User-Agent=x&Referer=y"
            val pipe = line.indexOf('|')
            if (pipe > 0 && (line.startsWith("http", ignoreCase = true))) {
                urlPart = line.substring(0, pipe).trim()
                for (kv in line.substring(pipe + 1).split('&')) {
                    val eq = kv.indexOf('=')
                    if (eq <= 0) continue
                    val k = kv.substring(0, eq).trim()
                    val v = decodeUrlPart(kv.substring(eq + 1).trim())
                    when (k.lowercase()) {
                        "user-agent" -> uaFromUrl = v
                        "referer", "referrer" -> refFromUrl = v
                        else -> extraFromUrl[k] = v
                    }
                }
            }

            var lower = urlPart.lowercase()
            if (ALLOWED_SCHEMES.none { lower.startsWith(it) } && resolveRelative && !lower.contains("://")) {
                val resolved = try { java.net.URI(baseUrl ?: "").resolve(urlPart.replace(" ", "%20")).toString() } catch (_: Throwable) { null }
                if (resolved != null) {
                    urlPart = resolved
                    lower = urlPart.lowercase()
                }
            }
            if (ALLOWED_SCHEMES.none { lower.startsWith(it) }) {
                skipped++
                reset()
                continue
            }

            val displayName = (pName?.takeIf { it.isNotBlank() })
                ?: urlPart.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { urlPart }
            val headers = LinkedHashMap<String, String>(pHeaders)
            headers.putAll(extraFromUrl)
            entries.add(
                M3uEntry(
                    name = displayName,
                    url = urlPart,
                    logo = pLogo,
                    group = pGroup,
                    tvgId = pTvgId,
                    userAgent = uaFromUrl ?: pUa,
                    referrer = refFromUrl ?: pRef,
                    extraHeaders = headers
                )
            )
            reset()
        }
        return M3uParseResult(entries = entries, title = playlistTitle, skippedLines = skipped)
    }

    private fun firstCommaOutsideQuotes(s: String): Int {
        var quote: Char? = null
        for (i in s.indices) {
            val c = s[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == ',') {
                return i
            }
        }
        return -1
    }

    private fun decodeUrlPart(v: String): String =
        try { java.net.URLDecoder.decode(v.replace("+", "%2B"), "UTF-8") } catch (_: Throwable) { v }
}
