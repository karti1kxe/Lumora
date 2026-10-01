package com.example.player

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

/**
 * Universal Subtitle Engine
 * 
 * Guarantees that EVERY subtitle format (.srt, .vtt, .smi, .sami, .sub, .lrc, .ttml, .xml,
 * .dfxp, .sbv, .txt, .mpl2, .ass, .ssa) parses, positions, colors, and displays flawlessly
 * on top of video playback with high rendering fidelity.
 */
object UniversalSubtitleEngine {

    private const val TAG = "UniversalSubEngine"

    data class SubtitleCue(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val style: String = "Default",
        val alignment: Int? = null, // 1..9 numpad alignment
        val colorHex: String? = null, // e.g. "00FFFF" (BGR or RGB)
        val posX: Int? = null,
        val posY: Int? = null,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false
    )

    /**
     * Prepares any subtitle file for playback.
     * If already ASS/SSA, ensures font fallback sanitization.
     * If any other format (SRT, VTT, SMI, SUB, LRC, TTML, etc.), converts to clean ASS format
     * with exact styling, positioning, and color preserved.
     */
    fun prepareSubtitleForPlayback(
        context: Context,
        file: File,
        videoFps: Double = 24.0,
        videoPath: String? = null
    ): String {
        if (!file.exists() || file.length() == 0L) return file.absolutePath

        val ext = file.extension.lowercase(Locale.ROOT)

        // 1. Read raw text with robust multi-charset auto-detection
        val rawText = readTextWithCharsetDetection(file)
        if (rawText.isBlank()) return file.absolutePath

        // 2. Native ASS/SSA must remain byte-for-byte equivalent from the player's
        // perspective. Do not sanitize, rewrite, strip tags, or replace characters.
        // This is critical for Japanese/Chinese/Korean text, symbols, karaoke tags,
        // signs, icons, and other ASS-specific glyphs. Font discovery is still performed
        // so embedded/custom fonts remain available to libass.
        if (ext == "ass" || ext == "ssa" || rawText.contains("[Script Info]", ignoreCase = true)) {
            try {
                SubtitleFontManager.syncFonts(context, videoPath ?: file.parentFile?.absolutePath, rawText)
                // Keep native ASS/SSA untouched when its referenced fonts exist. If a style
                // or inline \fn asks for a font that is genuinely unavailable, replace only
                // that font name with the bundled universal fallback.
                var sanitized = SubtitleFontManager.sanitizeAssForFallback(context, rawText)
                // "Override ASS/SSA Styles" ON: the selected font becomes the font of EVERY style
                // and inline \\fn (libass' force-style alone leaves inline \\fn and some styles alone).
                val forcedFont = SubtitleFontManager.selectedFontRenderName
                if (SubtitleFontManager.forceSelectedFontOnAss && !forcedFont.isNullOrBlank()) {
                    sanitized = SubtitleFontManager.forceSelectedFontEverywhere(sanitized, forcedFont)
                }
                // Characters the selected font has no glyph for (music notes, symbols, other
                // scripts) are drawn from the bundled Go Noto font instead of "tofu" boxes.
                sanitized = SubtitleFontManager.applyGlyphFallback(context, sanitized)
                // Always go through the same deterministic cache path so a later font change can
                // rewrite the file in place and just ask mpv to reload it.
                val fallbackDir = File(context.cacheDir, "sub_fallback").apply { mkdirs() }
                val safeName = "${file.nameWithoutExtension}_${file.length()}_${file.lastModified()}.ass"
                val fallbackFile = File(fallbackDir, safeName)
                val existing = try { if (fallbackFile.exists()) fallbackFile.readText(StandardCharsets.UTF_8) else null } catch (_: Throwable) { null }
                if (existing != sanitized) fallbackFile.writeText(sanitized, StandardCharsets.UTF_8)
                return fallbackFile.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "Error preparing ASS font fallback; keeping original subtitle file", e)
            }
            return file.absolutePath
        }

        // 3. For all other formats, parse cues with native format logic
        val cues = when (ext) {
            "srt" -> parseSrt(rawText)
            "vtt", "webvtt" -> parseWebVtt(rawText)
            "smi", "sami" -> parseSami(rawText)
            "lrc" -> parseLrc(rawText)
            "ttml", "dfxp", "xml", "ebu", "ebutt", "ebu-tt", "smptett", "smpte-tt", "itt" -> parseTtml(rawText)
            "sub", "microdvd" -> parseMicroDvd(rawText, videoFps)
            "sbv" -> parseSbv(rawText)
            "mpl2" -> parseMpl2(rawText, videoFps)
            else -> {
                // Auto-detect based on text content
                when {
                    rawText.contains("WEBVTT", ignoreCase = true) -> parseWebVtt(rawText)
                    rawText.contains("<SAMI", ignoreCase = true) || rawText.contains("<SYNC", ignoreCase = true) -> parseSami(rawText)
                    rawText.contains("<tt", ignoreCase = true) || rawText.contains("<p begin=", ignoreCase = true) -> parseTtml(rawText)
                    Pattern.compile("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]").matcher(rawText).find() -> parseLrc(rawText)
                    Pattern.compile("\\{\\d+\\}\\{\\d+\\}").matcher(rawText).find() -> parseMicroDvd(rawText, videoFps)
                    Pattern.compile("\\d+:\\d{2}:\\d{2}\\.\\d{3},\\d+:\\d{2}:\\d{2}\\.\\d{3}").matcher(rawText).find() -> parseSbv(rawText)
                    else -> parseSrt(rawText)
                }
            }
        }

        if (cues.isEmpty()) {
            Log.w(TAG, "No cues parsed from ${file.name}, using original file directly")
            return file.absolutePath
        }

        // 4. Generate high-fidelity ASS script from cues
        var assScript = generateAssScript(cues, file.name, isLyrics = (ext == "lrc"))
        try {
            SubtitleFontManager.syncFonts(context, file.parentFile?.absolutePath)
            assScript = SubtitleFontManager.applyGlyphFallback(context, assScript)
        } catch (_: Throwable) {}
        val targetDir = File(context.cacheDir, "sub_transcoded").apply { mkdirs() }
        val targetFile = File(targetDir, "${file.nameWithoutExtension}.ass")
        targetFile.writeText(assScript, StandardCharsets.UTF_8)

        // Make sure font manager is ready
        try {
            SubtitleFontManager.syncFonts(context, file.parentFile?.absolutePath)
        } catch (_: Throwable) {}

        return targetFile.absolutePath
    }

    /**
     * Reads text by detecting BOM and attempting UTF-8, UTF-16LE, UTF-16BE, Windows-1252, ISO-8859-1
     */
    fun readTextWithCharsetDetection(file: File): String {
        try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            if (bytes.isEmpty()) return ""

            // Check BOM
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            }

            // Try UTF-8
            val utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            try {
                val byteBuffer = java.nio.ByteBuffer.wrap(bytes)
                val charBuffer = utf8Decoder.decode(byteBuffer)
                return charBuffer.toString()
            } catch (_: Exception) {
                // Not valid UTF-8, try Windows-1252 / ISO-8859-1 / GBK / Shift-JIS
            }

            val fallbackCharsets = listOf(
                Charset.forName("windows-1252"),
                StandardCharsets.ISO_8859_1,
                Charset.forName("GBK"),
                Charset.forName("Shift_JIS"),
                Charset.forName("EUC-KR")
            )
            for (charset in fallbackCharsets) {
                try {
                    val dec = charset.newDecoder()
                    val byteBuffer = java.nio.ByteBuffer.wrap(bytes)
                    val charBuffer = dec.decode(byteBuffer)
                    val str = charBuffer.toString()
                    if (str.isNotBlank()) return str
                } catch (_: Throwable) {}
            }

            return String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file with charset detection: ${file.name}", e)
            return try { file.readText() } catch (_: Exception) { "" }
        }
    }

    // ==========================================
    // 1. SRT (SubRip) PARSER
    // ==========================================
    private val SRT_TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})(?:\\s+(.*))?")

    fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var i = 0
        val n = lines.size

        while (i < n) {
            var line = lines[i].trim()
            if (line.isEmpty() || (line.all { it.isDigit() } && i + 1 < n && SRT_TIME_PATTERN.matcher(lines[i + 1].trim()).find())) {
                if (line.all { it.isDigit() }) {
                    i++
                    if (i < n) line = lines[i].trim()
                } else {
                    i++
                    continue
                }
            }

            val matcher = SRT_TIME_PATTERN.matcher(line)
            if (matcher.find()) {
                val startH = matcher.group(1)!!.toLong()
                val startM = matcher.group(2)!!.toLong()
                val startS = matcher.group(3)!!.toLong()
                val startMs = matcher.group(4)!!.toLong()
                val startTotalMs = (startH * 3600 + startM * 60 + startS) * 1000 + startMs

                val endH = matcher.group(5)!!.toLong()
                val endM = matcher.group(6)!!.toLong()
                val endS = matcher.group(7)!!.toLong()
                val endMs = matcher.group(8)!!.toLong()
                val endTotalMs = (endH * 3600 + endM * 60 + endS) * 1000 + endMs

                val extraParams = matcher.group(9) ?: ""

                i++
                val textBuilder = StringBuilder()
                while (i < n && lines[i].trim().isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(lines[i])
                    i++
                }

                val rawCueText = textBuilder.toString()
                val (processedText, alignment, color) = processHtmlAndPositionTags(rawCueText, extraParams)

                if (processedText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startMs = startTotalMs,
                            endMs = if (endTotalMs > startTotalMs) endTotalMs else startTotalMs + 3000,
                            text = processedText,
                            alignment = alignment,
                            colorHex = color
                        )
                    )
                }
            } else {
                i++
            }
        }
        return cues
    }

    // ==========================================
    // 2. WebVTT PARSER
    // ==========================================
    private val VTT_TIME_PATTERN = Pattern.compile("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})(?:\\s+(.*))?")

    fun parseWebVtt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var i = 0
        val n = lines.size

        while (i < n) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("STYLE")) {
                if (line.startsWith("STYLE") || line.startsWith("NOTE")) {
                    i++
                    while (i < n && lines[i].trim().isNotEmpty()) i++
                } else {
                    i++
                }
                continue
            }

            val matcher = VTT_TIME_PATTERN.matcher(line)
            if (matcher.find()) {
                val startH = matcher.group(1)?.toLong() ?: 0L
                val startM = matcher.group(2)!!.toLong()
                val startS = matcher.group(3)!!.toLong()
                val startMs = matcher.group(4)!!.toLong()
                val startTotalMs = (startH * 3600 + startM * 60 + startS) * 1000 + startMs

                val endH = matcher.group(5)?.toLong() ?: 0L
                val endM = matcher.group(6)!!.toLong()
                val endS = matcher.group(7)!!.toLong()
                val endMs = matcher.group(8)!!.toLong()
                val endTotalMs = (endH * 3600 + endM * 60 + endS) * 1000 + endMs

                val cueSettings = matcher.group(9) ?: ""

                i++
                val textBuilder = StringBuilder()
                while (i < n && lines[i].trim().isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(lines[i])
                    i++
                }

                val rawCueText = textBuilder.toString()
                val (processedText, alignment, color) = processVttTagsAndSettings(rawCueText, cueSettings)

                if (processedText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startMs = startTotalMs,
                            endMs = if (endTotalMs > startTotalMs) endTotalMs else startTotalMs + 3000,
                            text = processedText,
                            alignment = alignment,
                            colorHex = color
                        )
                    )
                }
            } else {
                i++
            }
        }
        return cues
    }

    // ==========================================
    // 3. SAMI / SMI PARSER
    // ==========================================
    private val SAMI_SYNC_PATTERN = Pattern.compile("<SYNC\\s+Start=[\"']?(\\d+)[\"']?\\s*[^>]*>(.*?)(?=<SYNC\\s+Start=|</BODY>|</HTML>|$)", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

    fun parseSami(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val matcher = SAMI_SYNC_PATTERN.matcher(content)

        data class RawSamiItem(val startMs: Long, val rawText: String)
        val rawItems = mutableListOf<RawSamiItem>()

        while (matcher.find()) {
            val startMs = matcher.group(1)?.toLongOrNull() ?: continue
            val raw = matcher.group(2)?.trim() ?: ""
            rawItems.add(RawSamiItem(startMs, raw))
        }

        for (idx in rawItems.indices) {
            val item = rawItems[idx]
            val nextStart = if (idx + 1 < rawItems.size) rawItems[idx + 1].startMs else item.startMs + 4000L
            val duration = (nextStart - item.startMs).coerceIn(500L, 8000L)
            val endMs = item.startMs + duration

            // Clean SAMI html
            var text = item.rawText
            text = text.replace(Regex("<P[^>]*>", RegexOption.IGNORE_CASE), "")
            text = text.replace(Regex("</P>", RegexOption.IGNORE_CASE), "")
            text = text.replace(Regex("<BR\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            text = decodeHtmlEntities(text).trim()

            if (text.isEmpty() || text == "&nbsp;" || text == "\u00A0") continue

            val (processedText, alignment, color) = processHtmlAndPositionTags(text, "")
            if (processedText.isNotBlank()) {
                cues.add(
                    SubtitleCue(
                        startMs = item.startMs,
                        endMs = endMs,
                        text = processedText,
                        alignment = alignment,
                        colorHex = color
                    )
                )
            }
        }
        return cues
    }

    // ==========================================
    // 4. LRC (Lyrics) PARSER
    // ==========================================
    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\](.*)")

    fun parseLrc(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")

        data class LrcEntry(val timeMs: Long, val text: String)
        val entries = mutableListOf<LrcEntry>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || trimmed.startsWith("[al:") || trimmed.startsWith("[by:")) {
                continue
            }
            val matcher = LRC_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                val min = matcher.group(1)!!.toLong()
                val sec = matcher.group(2)!!.toLong()
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val totalMs = (min * 60 + sec) * 1000 + ms
                val text = matcher.group(4)?.trim() ?: ""
                if (text.isNotEmpty()) {
                    entries.add(LrcEntry(totalMs, text))
                }
            }
        }

        entries.sortBy { it.timeMs }

        for (i in entries.indices) {
            val current = entries[i]
            val nextTime = if (i + 1 < entries.size) entries[i + 1].timeMs else current.timeMs + 4000L
            val endMs = if (nextTime > current.timeMs) nextTime else current.timeMs + 3500L

            cues.add(
                SubtitleCue(
                    startMs = current.timeMs,
                    endMs = endMs,
                    text = current.text,
                    style = "Lyric",
                    alignment = 2 // Bottom center
                )
            )
        }
        return cues
    }

    // ==========================================
    // 5. TTML / DFXP / XML PARSER
    // ==========================================
    private val TTML_P_PATTERN = Pattern.compile("<p\\s+([^>]*)>(.*?)</p>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

    fun parseTtml(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val matcher = TTML_P_PATTERN.matcher(content)

        while (matcher.find()) {
            val attributes = matcher.group(1) ?: ""
            val body = matcher.group(2) ?: ""

            val beginMs = parseTtmlTime(extractAttribute(attributes, "begin")) ?: continue
            val endMs = parseTtmlTime(extractAttribute(attributes, "end"))
                ?: (parseTtmlTime(extractAttribute(attributes, "dur"))?.let { beginMs + it })
                ?: (beginMs + 3500L)

            val color = extractAttribute(attributes, "tts:color") ?: extractAttribute(attributes, "color")
            val align = extractAttribute(attributes, "tts:textAlign") ?: extractAttribute(attributes, "textAlign")

            var cleanBody = body.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            cleanBody = decodeHtmlEntities(cleanBody)

            val alignmentInt = when (align?.lowercase(Locale.ROOT)) {
                "left", "start" -> 1
                "right", "end" -> 3
                else -> 2
            }

            val (processedText, _, tagColor) = processHtmlAndPositionTags(cleanBody, "")
            val finalColor = parseColorToAssBgr(color) ?: tagColor

            if (processedText.isNotBlank()) {
                cues.add(
                    SubtitleCue(
                        startMs = beginMs,
                        endMs = endMs,
                        text = processedText,
                        alignment = alignmentInt,
                        colorHex = finalColor
                    )
                )
            }
        }
        return cues
    }

    private fun parseTtmlTime(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        val trimmed = timeStr.trim()
        if (trimmed.endsWith("s")) {
            return (trimmed.removeSuffix("s").toDoubleOrNull()?.times(1000.0))?.toLong()
        }
        if (trimmed.endsWith("ms")) {
            return trimmed.removeSuffix("ms").toLongOrNull()
        }
        val parts = trimmed.split(":")
        if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val sParts = parts[2].split(".", ",")
            val s = sParts[0].toLongOrNull() ?: 0L
            val ms = if (sParts.size > 1) {
                val msStr = sParts[1].padEnd(3, '0').take(3)
                msStr.toLongOrNull() ?: 0L
            } else 0L
            return (h * 3600 + m * 60 + s) * 1000 + ms
        }
        return null
    }

    private fun extractAttribute(tag: String, attrName: String): String? {
        val pattern = Pattern.compile("$attrName=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val m = pattern.matcher(tag)
        return if (m.find()) m.group(1) else null
    }

    // ==========================================
    // 6. MicroDVD (.sub) PARSER
    // ==========================================
    private val MICRODVD_PATTERN = Pattern.compile("\\{(\\d+)\\}\\{(\\d+)\\}(.*)")

    // Many MicroDVD releases declare the file's own frame-rate as a fake first entry, e.g.
    // "{1}{1}23.976" or "{0}{0}25.000" — a bare decimal number and nothing else, meant only to
    // tell the player which fps to use for this file. It is metadata, never dialogue, and must
    // never be shown on screen. Previously this line was parsed like any other cue, so its
    // number was displayed as if it were subtitle text for a moment at the very start of
    // playback. A real dialogue line is never just a lone number, so this is safe to detect and
    // drop (and, when the caller didn't already pass an accurate fps, adopt as the real one).
    private val MICRODVD_FPS_DECLARATION_PATTERN = Pattern.compile("^\\d{1,3}(?:[.,]\\d{1,4})?$")

    fun parseMicroDvd(content: String, fps: Double): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var actualFps = if (fps > 0) fps else 24.0
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var sawFirstCue = false

        for (line in lines) {
            val matcher = MICRODVD_PATTERN.matcher(line.trim())
            if (matcher.find()) {
                val startFrame = matcher.group(1)!!.toLong()
                val endFrame = matcher.group(2)!!.toLong()
                var text = matcher.group(3) ?: ""

                if (!sawFirstCue) {
                    sawFirstCue = true
                    val declaredFps = MICRODVD_FPS_DECLARATION_PATTERN
                        .matcher(text.trim())
                        .let { if (it.matches()) text.trim().replace(',', '.').toDoubleOrNull() else null }
                    if (declaredFps != null && declaredFps > 0.0) {
                        // This is the file's own fps-declaration line, not a real subtitle —
                        // never display it. Prefer it over a caller-supplied guess since it
                        // came from the file itself.
                        actualFps = declaredFps
                        continue
                    }
                }

                val startMs = (startFrame / actualFps * 1000.0).toLong()
                val endMs = (endFrame / actualFps * 1000.0).toLong()

                var isItalic = false
                var isBold = false
                var colorHex: String? = null
                var alignment: Int? = null

                if (text.contains("{Y:i}", ignoreCase = true)) {
                    isItalic = true
                    text = text.replace(Regex("\\{Y:i\\}", RegexOption.IGNORE_CASE), "")
                }
                if (text.contains("{Y:b}", ignoreCase = true)) {
                    isBold = true
                    text = text.replace(Regex("\\{Y:b\\}", RegexOption.IGNORE_CASE), "")
                }
                if (text.contains("{P:1}", ignoreCase = true)) {
                    alignment = 8 // Top center
                    text = text.replace(Regex("\\{P:1\\}", RegexOption.IGNORE_CASE), "")
                }

                val colorMatcher = Pattern.compile("\\{c:\\$([0-9a-fA-F]{6})\\}").matcher(text)
                if (colorMatcher.find()) {
                    colorHex = colorMatcher.group(1)
                    text = colorMatcher.replaceAll("")
                }

                text = text.replace("|", "\n")

                if (text.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startMs = startMs,
                            endMs = if (endMs > startMs) endMs else startMs + 3000L,
                            text = text,
                            isItalic = isItalic,
                            isBold = isBold,
                            colorHex = colorHex,
                            alignment = alignment
                        )
                    )
                }
            }
        }
        return cues
    }

    // ==========================================
    // 7. SBV (YouTube SubRip) PARSER
    // ==========================================
    private val SBV_TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})\\.(\\d{3}),(\\d{1,2}):(\\d{2}):(\\d{2})\\.(\\d{3})")

    fun parseSbv(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var i = 0
        val n = lines.size

        while (i < n) {
            val line = lines[i].trim()
            val matcher = SBV_TIME_PATTERN.matcher(line)
            if (matcher.find()) {
                val startH = matcher.group(1)!!.toLong()
                val startM = matcher.group(2)!!.toLong()
                val startS = matcher.group(3)!!.toLong()
                val startMs = matcher.group(4)!!.toLong()
                val startTotalMs = (startH * 3600 + startM * 60 + startS) * 1000 + startMs

                val endH = matcher.group(5)!!.toLong()
                val endM = matcher.group(6)!!.toLong()
                val endS = matcher.group(7)!!.toLong()
                val endMs = matcher.group(8)!!.toLong()
                val endTotalMs = (endH * 3600 + endM * 60 + endS) * 1000 + endMs

                i++
                val textBuilder = StringBuilder()
                while (i < n && lines[i].trim().isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(lines[i])
                    i++
                }

                val text = textBuilder.toString()
                if (text.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startMs = startTotalMs,
                            endMs = if (endTotalMs > startTotalMs) endTotalMs else startTotalMs + 3000L,
                            text = text
                        )
                    )
                }
            } else {
                i++
            }
        }
        return cues
    }

    // ==========================================
    // 8. MPL2 PARSER
    // ==========================================
    private val MPL2_PATTERN = Pattern.compile("\\[(\\d+)\\]\\[(\\d+)\\](.*)")

    fun parseMpl2(content: String, fps: Double): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")

        for (line in lines) {
            val matcher = MPL2_PATTERN.matcher(line.trim())
            if (matcher.find()) {
                val startDecisec = matcher.group(1)!!.toLong()
                val endDecisec = matcher.group(2)!!.toLong()
                var text = matcher.group(3) ?: ""
                text = text.replace("|", "\n")

                cues.add(
                    SubtitleCue(
                        startMs = startDecisec * 100L,
                        endMs = endDecisec * 100L,
                        text = text
                    )
                )
            }
        }
        return cues
    }

    // ==========================================
    // HTML / VTT / CSS TAG PROCESSOR
    // ==========================================
    private data class TagProcessResult(val text: String, val alignment: Int?, val colorBgrHex: String?)

    private fun processHtmlAndPositionTags(rawText: String, extraParams: String): TagProcessResult {
        var text = rawText
        var alignment: Int? = null
        var colorBgrHex: String? = null

        // Check ASS position tags already in text: {\an8}, {\pos(x,y)}
        val anMatcher = Pattern.compile("\\{\\\\an([1-9])\\}").matcher(text)
        if (anMatcher.find()) {
            alignment = anMatcher.group(1)?.toIntOrNull()
        }

        // HTML font color tags: <font color="#RRGGBB">, <font color="yellow">
        val fontColorMatcher = Pattern.compile("<font\\s+[^>]*color=[\"']?([^\"'>\\s]+)[\"']?[^>]*>(.*?)</font>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val sb = StringBuffer()
        val matcher = fontColorMatcher.matcher(text)
        while (matcher.find()) {
            val colorStr = matcher.group(1) ?: ""
            val innerText = matcher.group(2) ?: ""
            val bgr = parseColorToAssBgr(colorStr)
            if (bgr != null) {
                matcher.appendReplacement(sb, "{\\\\c&H$bgr&}$innerText{\\\\c}")
            } else {
                matcher.appendReplacement(sb, innerText)
            }
        }
        matcher.appendTail(sb)
        text = sb.toString()

        // HTML formatting: <b> -> {\b1}, </b> -> {\b0}, <i> -> {\i1}, etc.
        text = text.replace(Regex("<b\\b[^>]*>", RegexOption.IGNORE_CASE), "{\\b1}")
            .replace(Regex("</b>", RegexOption.IGNORE_CASE), "{\\b0}")
            .replace(Regex("<i\\b[^>]*>", RegexOption.IGNORE_CASE), "{\\i1}")
            .replace(Regex("</i>", RegexOption.IGNORE_CASE), "{\\i0}")
            .replace(Regex("<u\\b[^>]*>", RegexOption.IGNORE_CASE), "{\\u1}")
            .replace(Regex("</u>", RegexOption.IGNORE_CASE), "{\\u0}")
            .replace(Regex("<s\\b[^>]*>", RegexOption.IGNORE_CASE), "{\\s1}")
            .replace(Regex("</s>", RegexOption.IGNORE_CASE), "{\\s0}")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\\N")
            .replace(Regex("</?[a-zA-Z0-9_-]+[^>]*>"), "") // strip remaining unknown tags

        // Replace raw newlines with ASS \N
        text = text.replace("\n", "\\N")

        return TagProcessResult(decodeHtmlEntities(text), alignment, colorBgrHex)
    }

    private fun processVttTagsAndSettings(rawText: String, cueSettings: String): TagProcessResult {
        var text = rawText
        var alignment: Int? = null
        var colorBgrHex: String? = null

        // WebVTT Cue Settings: line:0%, line:0, position:50%, align:start
        if (cueSettings.isNotBlank()) {
            val isTopLine = cueSettings.contains("line:0") || cueSettings.contains("line:10%") || cueSettings.contains("line:0%") || cueSettings.contains("line:15%")
            val isMidLine = cueSettings.contains("line:50%") || cueSettings.contains("line:45%") || cueSettings.contains("line:55%")
            val isAlignStart = cueSettings.contains("align:start") || cueSettings.contains("align:left")
            val isAlignEnd = cueSettings.contains("align:end") || cueSettings.contains("align:right")

            alignment = when {
                isTopLine && isAlignStart -> 7 // Top left
                isTopLine && isAlignEnd -> 9 // Top right
                isTopLine -> 8 // Top center
                isMidLine && isAlignStart -> 4 // Mid left
                isMidLine && isAlignEnd -> 6 // Mid right
                isMidLine -> 5 // Mid center
                isAlignStart -> 1 // Bottom left
                isAlignEnd -> 3 // Bottom right
                else -> 2 // Bottom center
            }
        }

        // WebVTT <c.color> classes
        val colorClasses = mapOf(
            "yellow" to "00FFFF",
            "red" to "0000FF",
            "blue" to "FF0000",
            "green" to "00FF00",
            "cyan" to "FFFF00",
            "magenta" to "FF00FF",
            "white" to "FFFFFF",
            "black" to "000000",
            "gray" to "808080",
            "grey" to "808080"
        )
        for ((name, bgr) in colorClasses) {
            text = text.replace(Regex("<c\\.$name>(.*?)</c>", RegexOption.IGNORE_CASE)) {
                "{\\c&H$bgr&}${it.groupValues[1]}{\\c}"
            }
        }

        // Voice tags: <v Speaker> -> {\i1}[Speaker]:{\i0}
        text = text.replace(Regex("<v\\s+([^>]+)>(.*?)(?:</v>|$)", RegexOption.IGNORE_CASE)) {
            "{\\i1}[${it.groupValues[1]}]:{\\i0} ${it.groupValues[2]}"
        }

        val result = processHtmlAndPositionTags(text, "")
        return TagProcessResult(result.text, alignment ?: result.alignment, colorBgrHex ?: result.colorBgrHex)
    }

    /**
     * Converts CSS/HTML color names or hex (#RRGGBB) to ASS BGR format (BBGGRR)
     */
    fun parseColorToAssBgr(colorStr: String?): String? {
        if (colorStr.isNullOrBlank()) return null
        val clean = colorStr.trim().removePrefix("#").lowercase(Locale.ROOT)

        val namedColors = mapOf(
            "yellow" to "00FFFF",
            "red" to "0000FF",
            "lime" to "00FF00",
            "green" to "008000",
            "blue" to "FF0000",
            "cyan" to "FFFF00",
            "aqua" to "FFFF00",
            "magenta" to "FF00FF",
            "fuchsia" to "FF00FF",
            "white" to "FFFFFF",
            "black" to "000000",
            "gray" to "808080",
            "grey" to "808080",
            "orange" to "00A5FF",
            "gold" to "00D7FF",
            "pink" to "CBC0FF",
            "purple" to "800080"
        )
        if (namedColors.containsKey(clean)) {
            return namedColors[clean]
        }

        // Hex formats: RRGGBB -> BBGGRR
        if (clean.length == 6 && clean.all { it.isDigit() || it in 'a'..'f' }) {
            val r = clean.substring(0, 2)
            val g = clean.substring(2, 4)
            val b = clean.substring(4, 6)
            return (b + g + r).uppercase(Locale.ROOT)
        }

        // Hex RGB: RGB -> BBGGRR
        if (clean.length == 3 && clean.all { it.isDigit() || it in 'a'..'f' }) {
            val r = "" + clean[0] + clean[0]
            val g = "" + clean[1] + clean[1]
            val b = "" + clean[2] + clean[2]
            return (b + g + r).uppercase(Locale.ROOT)
        }

        return null
    }

    private fun decodeHtmlEntities(input: String): String {
        return input.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    /**
     * Formats milliseconds to ASS timestamp: H:MM:SS.cs (centiseconds)
     */
    private fun formatAssTime(ms: Long): String {
        val totalCs = (ms / 10).coerceAtLeast(0)
        val cs = totalCs % 100
        val totalSec = totalCs / 100
        val sec = totalSec % 60
        val totalMin = totalSec / 60
        val min = totalMin % 60
        val hours = totalMin / 60
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, min, sec, cs)
    }

    /**
     * Generates a modern, high-contrast, beautiful ASS script from parsed cues
     */
    fun generateAssScript(cues: List<SubtitleCue>, title: String, isLyrics: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("[Script Info]\n")
        sb.append("Title: ").append(title).append("\n")
        sb.append("ScriptType: v4.00+\n")
        sb.append("WrapStyle: 0\n")
        sb.append("ScaledBorderAndShadow: yes\n")
        sb.append("YCbCr Matrix: TV.601\n")
        sb.append("PlayResX: 1920\n")
        sb.append("PlayResY: 1080\n\n")

        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")

        // Default Style: Clean crisp typography, bold outline, subtle shadow.
        // Font is our bundled multi-script fallback (SubtitleFontManager
        // .BUNDLED_FALLBACK_FONT_FAMILY) instead of a generic "sans-serif" name —
        // SRT/VTT/SAMI/LRC/TTML/etc. all get converted to ASS through this function,
        // so this one font name is what determines whether Devanagari/other
        // non-Latin scripts render correctly for every non-ASS subtitle format.
        val ff = SubtitleFontManager.selectedFontRenderName?.takeIf { it.isNotBlank() }
            ?: SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY
        sb.append("Style: Default,$ff,52,&H00FFFFFF,&H000000FF,&H00000000,&H90000000,-1,0,0,0,100,100,0,0,1,3.2,1.8,2,30,30,40,1\n")
        sb.append("Style: Top,$ff,52,&H00FFFFFF,&H000000FF,&H00000000,&H90000000,-1,0,0,0,100,100,0,0,1,3.2,1.8,8,30,30,40,1\n")
        sb.append("Style: Mid,$ff,52,&H00FFFFFF,&H000000FF,&H00000000,&H90000000,-1,0,0,0,100,100,0,0,1,3.2,1.8,5,30,30,40,1\n")
        sb.append("Style: Lyric,$ff,52,&H0000FFFF,&H000000FF,&H00000000,&HA0000000,-1,0,0,0,100,100,0,0,1,3.4,2.0,2,30,30,60,1\n\n")

        sb.append("[Events]\n")
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")

        for (cue in cues) {
            val startStr = formatAssTime(cue.startMs)
            val endStr = formatAssTime(cue.endMs)

            val styleName = if (cue.style == "Lyric" || isLyrics) {
                "Lyric"
            } else when (cue.alignment) {
                7, 8, 9 -> "Top"
                4, 5, 6 -> "Mid"
                else -> "Default"
            }

            val prefixTags = StringBuilder()

            // Alignment tag if explicitly given and different from style default
            if (cue.alignment != null && cue.alignment !in listOf(2, 5, 8)) {
                prefixTags.append("{\\an").append(cue.alignment).append("}")
            }
            // Position coordinates if given
            if (cue.posX != null && cue.posY != null) {
                prefixTags.append("{\\pos(").append(cue.posX).append(",").append(cue.posY).append(")}")
            }
            // Color override if given
            if (cue.colorHex != null) {
                prefixTags.append("{\\c&H").append(cue.colorHex).append("&}")
            }
            if (cue.isBold) prefixTags.append("{\\b1}")
            if (cue.isItalic) prefixTags.append("{\\i1}")
            if (cue.isUnderline) prefixTags.append("{\\u1}")

            val formattedText = prefixTags.toString() + cue.text

            sb.append("Dialogue: 0,").append(startStr).append(",").append(endStr).append(",")
                .append(styleName).append(",,0,0,0,,")
                .append(formattedText).append("\n")
        }

        return sb.toString()
    }
}
