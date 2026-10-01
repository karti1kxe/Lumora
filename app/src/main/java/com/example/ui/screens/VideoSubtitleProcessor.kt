package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.Locale

// ==========================================
// SUBTITLE DATA MODELS
// ==========================================

data class SubtitleCue(
    val id: String = java.util.UUID.randomUUID().toString(),
    val index: Int = 1,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 1000L,
    val text: String = "",
    val style: String = "Default",
    val actor: String = "",
    val layer: Int = 0,
    val marginL: Int = 0,
    val marginR: Int = 0,
    val marginV: Int = 0,
    val effect: String = "",
    val overrideTags: String = "",
    val rawText: String = "",
    val fontSize: Int = 18,
    val alignment: Int = 2,
    val fontName: String = "Arial",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeout: Boolean = false,
    val textColorHex: String? = null,
    val outlineColorHex: String? = null,
    val backColorHex: String? = null
)

enum class SubtitleFormat(
    val displayName: String,
    val extension: String,
    val isFrameBased: Boolean = false
) {
    SRT("SubRip (.srt)", "srt"),
    ASS("Advanced SubStation Alpha (.ass)", "ass"),
    SSA("SubStation Alpha (.ssa)", "ssa"),
    VTT("WebVTT (.vtt)", "vtt"),
    STL("Spruce STL (.stl)", "stl"),
    TTML("TTML (.ttml)", "ttml"),
    SUB("MicroDVD (.sub)", "sub"),
    SMI("SAMI (.smi)", "smi"),
    LRC("Lyrics (.lrc)", "lrc"),
    TXT("Plain Text (.txt)", "txt"),
    SBV("YouTube SBV (.sbv)", "sbv"),
    DFXP("DFXP / TTML (.dfxp)", "dfxp"),
    CLEAN_TEXT("Clean Text Only", "txt");

    companion object {
        fun fromFileName(fileName: String): SubtitleFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extension == ext } ?: SRT
        }

        fun fromExtension(ext: String): SubtitleFormat? {
            val cleanExt = ext.removePrefix(".").lowercase()
            return entries.firstOrNull { it.extension == cleanExt }
        }
    }
}

data class LoadedFontItem(
    val name: String,
    val fontFile: File? = null,
    val file: File? = null,
    val typeface: android.graphics.Typeface? = null,
    val fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
)

data class VideoMediaInfo(
    val fileName: String = "",
    val durationMs: Long = 0L,
    val fps: Double = 23.976,
    val qualityLabel: String = "1080p FHD",
    val resolutionLabel: String = "1920x1080",
    val width: Int = 1920,
    val height: Int = 1080,
    val audioTrackCount: Int = 1,
    val subtitleTrackCount: Int = 0,
    val chapterCount: Int = 0
)

data class CleanerFilters(
    val removeSdh: Boolean = false,
    val removeWatermarks: Boolean = false,
    val removeSpeakerLabels: Boolean = false,
    val removeMusicNotes: Boolean = false,
    val removeLineBreaks: Boolean = false,
    val mergeIdenticalCues: Boolean = false,
    val uppercaseToLowercase: Boolean = false,
    val removeTextFormatting: Boolean = false,
    val removeCurlyBrackets: Boolean = false,
    val removeParentheses: Boolean = false,
    val removeSquareBrackets: Boolean = false
)

data class CleanerOptions(
    val removeHtmlTags: Boolean = true,
    val removeAssOverrideTags: Boolean = true,
    val removeHearingImpairedNotes: Boolean = false,
    val removeAdvertisingLines: Boolean = true,
    val fixPunctuationSpacing: Boolean = true,
    val trimLineWhitespace: Boolean = true,
    val removeEmptyCues: Boolean = true
)

data class SavedSession(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "Untitled",
    val fileName: String = name,
    val format: String = "ASS",
    val cueCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val content: String = "",
    val selectedCueIndex: Int = 0
)

object SavedSessionManager {
    private const val PREF_NAME = "subtitle_editor_sessions"

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSessions(context: Context): List<SavedSession> {
        return getSavedSessions(context)
    }

    fun getSavedSessions(context: Context): List<SavedSession> {
        val prefs = getPrefs(context)
        val allKeys = prefs.all.keys.filter { it.startsWith("session_meta_") }
        val sessions = mutableListOf<SavedSession>()

        for (metaKey in allKeys) {
            val id = metaKey.removePrefix("session_meta_")
            val metaValue = prefs.getString(metaKey, null) ?: continue
            val content = prefs.getString("session_content_$id", "") ?: ""

            val parts = metaValue.split("|")
            if (parts.size >= 4) {
                val sessionName = parts[0]
                val formatStr = parts[1]
                val cueCount = parts[2].toIntOrNull() ?: 0
                val timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                val selectedCueIndex = if (parts.size >= 5) parts[4].toIntOrNull() ?: 0 else 0

                sessions.add(
                    SavedSession(
                        id = id,
                        name = sessionName,
                        fileName = sessionName,
                        format = formatStr,
                        cueCount = cueCount,
                        timestamp = timestamp,
                        content = content,
                        selectedCueIndex = selectedCueIndex
                    )
                )
            }
        }
        return sessions.sortedByDescending { it.timestamp }
    }

    fun saveSession(
        context: Context,
        fileName: String,
        format: SubtitleFormat,
        content: String,
        selectedCueIndex: Int = 0
    ): SavedSession {
        return saveSession(context, fileName, format.extension, content, selectedCueIndex)
    }

    fun saveSession(
        context: Context,
        fileName: String,
        formatStr: String,
        content: String,
        selectedCueIndex: Int = 0
    ): SavedSession {
        val prefs = getPrefs(context)
        val existingSessions = getSavedSessions(context)
        val existing = existingSessions.find { it.fileName.equals(fileName, ignoreCase = true) || it.name.equals(fileName, ignoreCase = true) }
        val id = existing?.id ?: System.currentTimeMillis().toString()
        val timestamp = System.currentTimeMillis()
        val cues = SubtitleEngine.parseContent(content, SubtitleFormat.fromExtension(formatStr) ?: SubtitleFormat.SRT)
        val cueCount = if (cues.isNotEmpty()) cues.size else if (content.isNotBlank()) content.lines().size else 0

        val metaValue = "$fileName|$formatStr|$cueCount|$timestamp|$selectedCueIndex"

        prefs.edit()
            .putString("session_meta_$id", metaValue)
            .putString("session_content_$id", content)
            .apply()

        return SavedSession(id, fileName, fileName, formatStr, cueCount, timestamp, content, selectedCueIndex)
    }

    fun saveSession(context: Context, session: SavedSession): SavedSession {
        return saveSession(context, session.name, session.format, session.content, session.selectedCueIndex)
    }

    fun deleteSession(context: Context, id: String) {
        getPrefs(context).edit()
            .remove("session_meta_$id")
            .remove("session_content_$id")
            .apply()
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun clearAllSessions(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}

// ==========================================
// ASS SUBTITLE PARSER & EXPORTER
// ==========================================

object AssParser {
    fun parseAssColor(raw: String, defaultColor: Color): Color {
        val clean = raw.trim().removePrefix("&H").removePrefix("&h").removePrefix("#")
        return try {
            if (clean.length == 8) {
                // AABBGGRR in ASS -> AARRGGBB
                val a = clean.substring(0, 2).toInt(16)
                val b = clean.substring(2, 4).toInt(16)
                val g = clean.substring(4, 6).toInt(16)
                val r = clean.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            } else if (clean.length == 6) {
                // BBGGRR in ASS
                val b = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val r = clean.substring(4, 6).toInt(16)
                Color(r, g, b, 255)
            } else {
                defaultColor
            }
        } catch (_: Throwable) {
            defaultColor
        }
    }

    var lastParsedHeader: String = ""
    val lastParsedStyleMap = mutableMapOf<String, AssStyleDef>()
    var scriptPlayResX: Int = 1920
    var scriptPlayResY: Int = 1080

    fun getStyleMapFingerprint(): String {
        val stylePart = lastParsedStyleMap.entries.joinToString(";") { (k, v) ->
            "$k:${v.fontName}:${v.fontSize}:${v.primaryColour}:${v.secondaryColour}:${v.outlineColour}:${v.backColour}:${v.bold}:${v.italic}:${v.underline}:${v.strikeOut}:${v.outline}:${v.shadow}:${v.alignment}:${v.marginL}:${v.marginR}:${v.marginV}:${v.scaleX}:${v.scaleY}:${v.spacing}:${v.angle}:${v.borderStyle}:${v.encoding}"
        }
        return "${lastParsedHeader.hashCode()}_${stylePart.hashCode()}"
    }

    fun rebuildHeaderFromStyleMap() {
        val scriptInfoPart = if (lastParsedHeader.isNotBlank() && lastParsedHeader.contains("[Script Info]", ignoreCase = true)) {
            val stylesIdx = lastParsedHeader.indexOf("[V4+ Styles]", ignoreCase = true).takeIf { it != -1 }
                ?: lastParsedHeader.indexOf("[V4 Styles]", ignoreCase = true).takeIf { it != -1 }
                ?: lastParsedHeader.indexOf("[Events]", ignoreCase = true).takeIf { it != -1 }
            if (stylesIdx != null) {
                lastParsedHeader.substring(0, stylesIdx).trim()
            } else {
                lastParsedHeader.trim()
            }
        } else {
            "[Script Info]\n; Script created by KARTiK FUNSUB\nTitle: ASS Subtitle\nScriptType: v4.00+\nPlayResX: $scriptPlayResX\nPlayResY: $scriptPlayResY\nScaledBorderAndShadow: yes"
        }

        val sb = StringBuilder(scriptInfoPart)
        sb.append("\n\n[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        if (lastParsedStyleMap.isNotEmpty()) {
            lastParsedStyleMap.values.forEach { st ->
                sb.append("Style: ${st.name},${st.fontName},${st.fontSize},${st.primaryColour},${st.secondaryColour},${st.outlineColour},${st.backColour},${st.bold},${st.italic},${st.underline},${st.strikeOut},${st.scaleX},${st.scaleY},${st.spacing},${st.angle},${st.borderStyle},${st.outline},${st.shadow},${st.alignment},${st.marginL},${st.marginR},${st.marginV},${st.encoding}\n")
            }
        } else {
            sb.append("Style: Default,Subs,58,&H00FFFFFF,&H000000FF,&H80C3D3E6,&H00000000,-1,0,0,0,100,100,0,0,1,2.5,1.5,2,17,17,35,1\n")
        }
        lastParsedHeader = sb.toString().trim()
    }

    fun getHeaderAndStylesBlock(): String {
        if (lastParsedHeader.isNotBlank() && lastParsedHeader.contains("[Script Info]", ignoreCase = true)) {
            var header = lastParsedHeader
            if (!header.contains("[V4+ Styles]", ignoreCase = true) && !header.contains("[V4 Styles]", ignoreCase = true)) {
                val sb = StringBuilder(header)
                sb.append("\n\n[V4+ Styles]\n")
                sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
                if (lastParsedStyleMap.isNotEmpty()) {
                    lastParsedStyleMap.values.forEach { st ->
                        sb.append("Style: ${st.name},${st.fontName},${st.fontSize},${st.primaryColour},${st.secondaryColour},${st.outlineColour},${st.backColour},${st.bold},${st.italic},${st.underline},${st.strikeOut},${st.scaleX},${st.scaleY},${st.spacing},${st.angle},${st.borderStyle},${st.outline},${st.shadow},${st.alignment},${st.marginL},${st.marginR},${st.marginV},${st.encoding}\n")
                    }
                } else {
                    sb.append("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1\n")
                }
                return sb.toString().trim()
            }
            return header
        }

        val sb = StringBuilder()
        sb.appendLine("[Script Info]")
        sb.appendLine("; Script created by KARTiK FUNSUB")
        sb.appendLine("Title: ASS Subtitle")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("PlayResX: $scriptPlayResX")
        sb.appendLine("PlayResY: $scriptPlayResY")
        sb.appendLine("ScaledBorderAndShadow: yes")
        sb.appendLine()
        sb.appendLine("[V4+ Styles]")
        sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        if (lastParsedStyleMap.isNotEmpty()) {
            lastParsedStyleMap.values.forEach { st ->
                sb.appendLine("Style: ${st.name},${st.fontName},${st.fontSize},${st.primaryColour},${st.secondaryColour},${st.outlineColour},${st.backColour},${st.bold},${st.italic},${st.underline},${st.strikeOut},${st.scaleX},${st.scaleY},${st.spacing},${st.angle},${st.borderStyle},${st.outline},${st.shadow},${st.alignment},${st.marginL},${st.marginR},${st.marginV},${st.encoding}")
            }
        } else {
            sb.appendLine("Style: Default,Subs,58,&H00FFFFFF,&H000000FF,&H80C3D3E6,&H00000000,-1,0,0,0,100,100,0,0,1,2.5,1.5,2,17,17,35,1")
        }
        return sb.toString().trim()
    }

    fun updateHeaderAndStylesFromText(headerText: String) {
        val eventsIdx = headerText.indexOf("[Events]", ignoreCase = true)
        val cleanHeader = if (eventsIdx != -1) headerText.substring(0, eventsIdx).trim() else headerText.trim()
        lastParsedHeader = cleanHeader
        lastParsedStyleMap.clear()

        val lines = cleanHeader.lines()
        var inStylesSection = false
        var styleFormatFields = listOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("PlayResX:", ignoreCase = true)) {
                trimmed.substringAfter(":").trim().toIntOrNull()?.let { scriptPlayResX = it }
            } else if (trimmed.startsWith("PlayResY:", ignoreCase = true)) {
                trimmed.substringAfter(":").trim().toIntOrNull()?.let { scriptPlayResY = it }
            }

            if (trimmed.startsWith("[V4+ Styles]", ignoreCase = true) || trimmed.startsWith("[V4 Styles]", ignoreCase = true)) {
                inStylesSection = true
                continue
            }
            if (trimmed.startsWith("[") && inStylesSection) {
                inStylesSection = false
            }

            if (inStylesSection) {
                if (trimmed.startsWith("Format:", ignoreCase = true)) {
                    styleFormatFields = trimmed.substringAfter("Format:").split(",").map { it.trim().lowercase() }
                } else if (trimmed.startsWith("Style:", ignoreCase = true)) {
                    val parts = trimmed.substringAfter("Style:").split(",").map { it.trim() }
                    if (parts.isNotEmpty()) {
                        val name = parts[0]
                        fun getValue(fieldName: String, posIdx: Int, defaultVal: String): String {
                            val idx = styleFormatFields.indexOf(fieldName)
                            val actualIdx = if (idx != -1) idx else posIdx
                            return if (actualIdx < parts.size && parts[actualIdx].isNotBlank()) parts[actualIdx] else defaultVal
                        }

                        val styleDef = AssStyleDef(
                            name = name,
                            fontName = getValue("fontname", 1, "Arial"),
                            fontSize = getValue("fontsize", 2, "48"),
                            primaryColour = getValue("primarycolour", 3, "&H00FFFFFF"),
                            secondaryColour = getValue("secondarycolour", 4, "&H000000FF"),
                            outlineColour = getValue("outlinecolour", 5, "&H00000000"),
                            backColour = getValue("backcolour", 6, "&H80000000"),
                            bold = getValue("bold", 7, "0"),
                            italic = getValue("italic", 8, "0"),
                            underline = getValue("underline", 9, "0"),
                            strikeOut = getValue("strikeout", 10, "0"),
                            scaleX = getValue("scalex", 11, "100"),
                            scaleY = getValue("scaley", 12, "100"),
                            spacing = getValue("spacing", 13, "0"),
                            angle = getValue("angle", 14, "0"),
                            borderStyle = getValue("borderstyle", 15, "1"),
                            outline = getValue("outline", 16, "2"),
                            shadow = getValue("shadow", 17, "0"),
                            alignment = getValue("alignment", 18, "2").toIntOrNull() ?: 2,
                            marginL = getValue("marginl", 19, "10"),
                            marginR = getValue("marginr", 20, "10"),
                            marginV = getValue("marginv", 21, "10"),
                            encoding = getValue("encoding", 22, "1")
                        )
                        lastParsedStyleMap[name] = styleDef
                    }
                }
            }
        }
    }

    data class AssStyleDef(
        val name: String = "Default",
        val fontName: String = "Arial",
        val fontSize: String = "48",
        val primaryColour: String = "&H00FFFFFF",
        val secondaryColour: String = "&H000000FF",
        val outlineColour: String = "&H00000000",
        val backColour: String = "&H80000000",
        val bold: String = "0",
        val italic: String = "0",
        val underline: String = "0",
        val strikeOut: String = "0",
        val scaleX: String = "100",
        val scaleY: String = "100",
        val spacing: String = "0",
        val angle: String = "0",
        val borderStyle: String = "1",
        val outline: String = "2",
        val shadow: String = "0",
        val alignment: Int = 2,
        val marginL: String = "10",
        val marginR: String = "10",
        val marginV: String = "10",
        val encoding: String = "1"
    )

    fun parse(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val styleMap = mutableMapOf<String, AssStyleDef>()
        lastParsedStyleMap.clear()

        val eventsIdx = content.indexOf("[Events]", ignoreCase = true)
        if (eventsIdx != -1) {
            val headerPart = content.substring(0, eventsIdx).trim()
            if (headerPart.contains("[Script Info]", ignoreCase = true)) {
                lastParsedHeader = headerPart
            }
        }

        val lines = content.lines()

        // Parse Script Info PlayRes
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("PlayResX:", ignoreCase = true)) {
                trimmed.substringAfter(":").trim().toIntOrNull()?.let { scriptPlayResX = it }
            } else if (trimmed.startsWith("PlayResY:", ignoreCase = true)) {
                trimmed.substringAfter(":").trim().toIntOrNull()?.let { scriptPlayResY = it }
            }
        }

        // 1. Parse Style Definitions
        var inStylesSection = false
        var styleFormatFields = listOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[V4+ Styles]", ignoreCase = true) || trimmed.startsWith("[V4 Styles]", ignoreCase = true)) {
                inStylesSection = true
                continue
            }
            if (trimmed.startsWith("[") && inStylesSection) {
                inStylesSection = false
            }

            if (inStylesSection) {
                if (trimmed.startsWith("Format:", ignoreCase = true)) {
                    styleFormatFields = trimmed.substringAfter("Format:").split(",").map { it.trim().lowercase() }
                } else if (trimmed.startsWith("Style:", ignoreCase = true)) {
                    val parts = trimmed.substringAfter("Style:").split(",").map { it.trim() }
                    if (parts.isNotEmpty()) {
                        val name = parts[0]

                        fun getValue(fieldName: String, posIdx: Int, defaultVal: String): String {
                            val idx = styleFormatFields.indexOf(fieldName)
                            val actualIdx = if (idx != -1) idx else posIdx
                            return if (actualIdx < parts.size && parts[actualIdx].isNotBlank()) parts[actualIdx] else defaultVal
                        }

                        val fontName = getValue("fontname", 1, "Arial")
                        val fontSize = getValue("fontsize", 2, "48")
                        val primaryColour = getValue("primarycolour", 3, "&H00FFFFFF")
                        val secondaryColour = getValue("secondarycolour", 4, "&H000000FF")
                        val outlineColour = getValue("outlinecolour", 5, "&H00000000")
                        val backColour = getValue("backcolour", 6, "&H80000000")
                        val bold = getValue("bold", 7, "-1")
                        val italic = getValue("italic", 8, "0")
                        val underline = getValue("underline", 9, "0")
                        val strikeOut = getValue("strikeout", 10, "0")
                        val scaleX = getValue("scalex", 11, "100")
                        val scaleY = getValue("scaley", 12, "100")
                        val spacing = getValue("spacing", 13, "0")
                        val angle = getValue("angle", 14, "0")
                        val borderStyle = getValue("borderstyle", 15, "1")
                        val outline = getValue("outline", 16, "2")
                        val shadow = getValue("shadow", 17, "0")
                        val alignVal = getValue("alignment", 18, "2").toIntOrNull() ?: 2
                        val marginL = getValue("marginl", 19, "10")
                        val marginR = getValue("marginr", 20, "10")
                        val marginV = getValue("marginv", 21, "10")
                        val encoding = getValue("encoding", 22, "1")

                        val styleDef = AssStyleDef(
                            name = name,
                            fontName = fontName,
                            fontSize = fontSize,
                            primaryColour = primaryColour,
                            secondaryColour = secondaryColour,
                            outlineColour = outlineColour,
                            backColour = backColour,
                            bold = bold,
                            italic = italic,
                            underline = underline,
                            strikeOut = strikeOut,
                            scaleX = scaleX,
                            scaleY = scaleY,
                            spacing = spacing,
                            angle = angle,
                            borderStyle = borderStyle,
                            outline = outline,
                            shadow = shadow,
                            alignment = alignVal,
                            marginL = marginL,
                            marginR = marginR,
                            marginV = marginV,
                            encoding = encoding
                        )
                        styleMap[name] = styleDef
                        lastParsedStyleMap[name] = styleDef
                    }
                }
            }
        }

        // 2. Parse Dialogue Events
        var index = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val parts = trimmed.substringAfter("Dialogue:").split(",", limit = 10)
                if (parts.size >= 10) {
                    val layer = parts[0].trim().toIntOrNull() ?: 0
                    val startStr = parts[1].trim()
                    val endStr = parts[2].trim()
                    val style = parts[3].trim()
                    val actor = parts[4].trim()
                    val marginL = parts[5].trim().toIntOrNull() ?: 0
                    val marginR = parts[6].trim().toIntOrNull() ?: 0
                    val marginV = parts[7].trim().toIntOrNull() ?: 0
                    val effect = parts[8].trim()
                    val textRaw = parts[9]

                    val startMs = parseAssTimestamp(startStr)
                    val endMs = parseAssTimestamp(endStr)

                    // Extract alignment from tags or style definition
                    var cueAlignment = styleMap[style]?.alignment ?: 2

                    val anMatch = Regex("\\{\\\\an([1-9])\\}").find(textRaw)
                    if (anMatch != null) {
                        cueAlignment = anMatch.groupValues[1].toIntOrNull() ?: cueAlignment
                    } else {
                        val aMatch = Regex("\\{\\\\a([0-9]+)\\}").find(textRaw)
                        if (aMatch != null) {
                            val ssaVal = aMatch.groupValues[1].toIntOrNull() ?: 2
                            cueAlignment = when (ssaVal) {
                                5 -> 7; 6 -> 8; 7 -> 9
                                9 -> 4; 10 -> 5; 11 -> 6
                                1 -> 1; 2 -> 2; 3 -> 3
                                else -> 2
                            }
                        }
                    }

                    val overrideTags = extractOverrideTags(textRaw)
                    val cleanText = textRaw.replace("\\N", "\n").replace("\\n", "\n")

                    cues.add(
                        SubtitleCue(
                            index = index++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            text = cleanText,
                            style = if (style.isNotEmpty()) style else "Default",
                            actor = actor,
                            layer = layer,
                            marginL = marginL,
                            marginR = marginR,
                            marginV = marginV,
                            effect = effect,
                            overrideTags = overrideTags,
                            rawText = textRaw,
                            alignment = cueAlignment
                        )
                    )
                }
            }
        }
        return validateAndVerifySubtitles(cues)
    }

    fun validateAndVerifySubtitles(cues: List<SubtitleCue>): List<SubtitleCue> {
        val verifiedCues = mutableListOf<SubtitleCue>()
        for (cue in cues) {
            var validAlignment = if (cue.alignment in 1..9) cue.alignment else 2
            var textToValidate = cue.text

            // 1. Tag curly braces integrity check & auto-balance
            val openBraces = textToValidate.count { it == '{' }
            val closeBraces = textToValidate.count { it == '}' }
            if (openBraces > closeBraces) {
                textToValidate += "}".repeat(openBraces - closeBraces)
            }

            // 2. Scan override tags for explicit alignment override (\an or \a)
            val anMatch = Regex("\\{\\\\an([1-9])\\}").find(textToValidate)
            if (anMatch != null) {
                validAlignment = anMatch.groupValues[1].toIntOrNull() ?: validAlignment
            } else {
                val aMatch = Regex("\\{\\\\a([0-9]+)\\}").find(textToValidate)
                if (aMatch != null) {
                    val ssaVal = aMatch.groupValues[1].toIntOrNull() ?: 2
                    validAlignment = when (ssaVal) {
                        5 -> 7; 6 -> 8; 7 -> 9
                        9 -> 4; 10 -> 5; 11 -> 6
                        1 -> 1; 2 -> 2; 3 -> 3
                        else -> 2
                    }
                }
            }

            // 3. Scan & verify rotation/angle tags (\frz, \frx, \fry)
            val angleMatches = Regex("\\\\fr[zxy]([-+]?[0-9]*\\.?[0-9]+)").findAll(textToValidate)
            for (match in angleMatches) {
                val rawAngle = match.groupValues[1].toDoubleOrNull()
                if (rawAngle != null && (rawAngle.isNaN() || rawAngle.isInfinite())) {
                    textToValidate = textToValidate.replace(match.value, "\\frz0")
                }
            }

            val updatedOverrideTags = extractOverrideTags(textToValidate)

            verifiedCues.add(
                cue.copy(
                    text = textToValidate,
                    alignment = validAlignment,
                    overrideTags = updatedOverrideTags
                )
            )
        }
        return verifiedCues
    }

    private fun extractOverrideTags(text: String): String {
        val tags = mutableListOf<String>()
        val regex = Regex("\\{\\\\.*?\\}")
        regex.findAll(text).forEach { match ->
            tags.add(match.value)
        }
        return tags.joinToString("")
    }

    private fun parseAssTimestamp(timestamp: String): Long {
        return try {
            val parts = timestamp.split(":")
            if (parts.size < 3) return 0L
            val hours = parts[0].toLong()
            val minutes = parts[1].toLong()
            val secParts = parts[2].split(".")
            val seconds = secParts[0].toLong()
            val cs = if (secParts.size > 1) secParts[1].padEnd(2, '0').take(2).toLong() else 0L
            (hours * 3600_000L) + (minutes * 60_000L) + (seconds * 1000L) + (cs * 10L)
        } catch (e: Exception) {
            0L
        }
    }

    fun formatAssTimestamp(timeMs: Long): String {
        val totalMs = maxOf(0L, timeMs)
        val hours = totalMs / 3600_000L
        val minutes = (totalMs % 3600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1000L
        val cs = (totalMs % 1000L) / 10L
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, cs)
    }

    fun export(cues: List<SubtitleCue>): String {
        val validatedCues = validateAndVerifySubtitles(cues)
        val sb = StringBuilder()

        val styleGroups = validatedCues.groupBy { if (it.style.isNotBlank()) it.style else "Default" }
        val requiredStyles = mutableSetOf<String>()
        styleGroups.keys.forEach { requiredStyles.add(it) }
        lastParsedStyleMap.keys.forEach { requiredStyles.add(it) }
        if (requiredStyles.isEmpty()) requiredStyles.add("Default")

        if (lastParsedHeader.isNotBlank() && lastParsedHeader.contains("[Script Info]", ignoreCase = true)) {
            var headerToUse = lastParsedHeader
            if (!headerToUse.contains("ScaledBorderAndShadow:", ignoreCase = true)) {
                headerToUse = headerToUse.replace("[Script Info]", "[Script Info]\nScaledBorderAndShadow: yes", ignoreCase = true)
            }
            if (!headerToUse.contains("PlayResX:", ignoreCase = true)) {
                headerToUse = headerToUse.replace("[Script Info]", "[Script Info]\nPlayResX: $scriptPlayResX", ignoreCase = true)
            }
            if (!headerToUse.contains("PlayResY:", ignoreCase = true)) {
                headerToUse = headerToUse.replace("[Script Info]", "[Script Info]\nPlayResY: $scriptPlayResY", ignoreCase = true)
            }
            sb.appendLine(headerToUse)

            if (!headerToUse.contains("[V4+ Styles]", ignoreCase = true) && !headerToUse.contains("[V4 Styles]", ignoreCase = true)) {
                sb.appendLine()
                sb.appendLine("[V4+ Styles]")
                sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")

                if (!requiredStyles.contains("Default")) {
                    sb.appendLine("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1")
                }

                requiredStyles.forEach { name ->
                    val st = lastParsedStyleMap[name] ?: AssStyleDef(name = name)
                    sb.appendLine("Style: ${st.name},${st.fontName},${st.fontSize},${st.primaryColour},${st.secondaryColour},${st.outlineColour},${st.backColour},${st.bold},${st.italic},${st.underline},${st.strikeOut},${st.scaleX},${st.scaleY},${st.spacing},${st.angle},${st.borderStyle},${st.outline},${st.shadow},${st.alignment},${st.marginL},${st.marginR},${st.marginV},${st.encoding}")
                }
            }

            sb.appendLine()
            sb.appendLine("[Events]")
            sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        } else {
            sb.appendLine("[Script Info]")
            sb.appendLine("ScriptType: v4.00+")
            sb.appendLine("PlayResX: $scriptPlayResX")
            sb.appendLine("PlayResY: $scriptPlayResY")
            sb.appendLine("ScaledBorderAndShadow: yes")
            sb.appendLine()
            sb.appendLine("[V4+ Styles]")
            sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")

            if (!requiredStyles.contains("Default")) {
                sb.appendLine("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1")
            }

            requiredStyles.forEach { name ->
                val st = lastParsedStyleMap[name] ?: AssStyleDef(name = name)
                sb.appendLine("Style: ${st.name},${st.fontName},${st.fontSize},${st.primaryColour},${st.secondaryColour},${st.outlineColour},${st.backColour},${st.bold},${st.italic},${st.underline},${st.strikeOut},${st.scaleX},${st.scaleY},${st.spacing},${st.angle},${st.borderStyle},${st.outline},${st.shadow},${st.alignment},${st.marginL},${st.marginR},${st.marginV},${st.encoding}")
            }
            sb.appendLine()
            sb.appendLine("[Events]")
            sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        }

        validatedCues.forEach { cue ->
            val start = formatAssTimestamp(cue.startTimeMs)
            val end = formatAssTimestamp(cue.endTimeMs)

            var textToUse = when {
                cue.text.isNotBlank() -> cue.text
                cue.rawText.isNotBlank() -> cue.rawText
                else -> ""
            }

            textToUse = textToUse.replace("\n", "\\N").replace("\r", "")

            val styleName = if (cue.style.isNotBlank()) cue.style else "Default"
            val styleAlignment = lastParsedStyleMap[styleName]?.alignment ?: 2
            val hasAlignmentTag = textToUse.contains("{\\an") || textToUse.contains("{\\a") || textToUse.contains("{\\pos") || textToUse.contains("{\\move")
            if (!hasAlignmentTag && cue.alignment in 1..9 && cue.alignment != styleAlignment) {
                textToUse = "{\\an${cue.alignment}}$textToUse"
            }

            sb.appendLine("Dialogue: ${cue.layer},$start,$end,$styleName,${cue.actor},${cue.marginL},${cue.marginR},${cue.marginV},${cue.effect},$textToUse")
        }
        return sb.toString()
    }
}

// ==========================================
// GENERAL SUBTITLE ENGINE
// ==========================================

object SubtitleEngine {

    fun stripAllSubtitleTags(input: String): String {
        if (input.isBlank()) return ""
        var s = input
        // ASS/SSA override tags {\...}
        s = s.replace(Regex("\\{\\\\.*?\\}"), "")
        // MicroDVD tags {y:...}, {c:...}, {f:...}, {s:...}
        s = s.replace(Regex("\\{[a-zA-Z]:.*?\\}"), "")
        // HTML / XML tags <...>
        s = s.replace(Regex("<[^>]*>"), "")
        // Convert ASS line breaks \N, \n
        s = s.replace("\\N", "\n").replace("\\n", "\n")
        return s.trim()
    }

    fun detectFormat(content: String, fileName: String = ""): SubtitleFormat {
        if (fileName.isNotEmpty()) {
            val fmt = SubtitleFormat.fromFileName(fileName)
            if (fmt != SubtitleFormat.SRT) return fmt
        }
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("WEBVTT", ignoreCase = true) -> SubtitleFormat.VTT
            trimmed.contains("[Script Info]", ignoreCase = true) ||
                trimmed.contains("[V4+ Styles]", ignoreCase = true) -> SubtitleFormat.ASS
            trimmed.contains("[V4 Styles]", ignoreCase = true) -> SubtitleFormat.SSA
            trimmed.contains("<tt", ignoreCase = true) -> SubtitleFormat.TTML
            trimmed.contains("<SAMI>", ignoreCase = true) -> SubtitleFormat.SMI
            Regex("(?m)^\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?\\]").containsMatchIn(trimmed) -> SubtitleFormat.LRC
            Regex("(?m)^\\{\\d+\\}\\{\\d+\\}").containsMatchIn(trimmed) -> SubtitleFormat.SUB
            Regex("(?m)^\\d+:\\d{2}:\\d{2}\\.\\d{3}\\s*,\\s*\\d+:\\d{2}:\\d{2}\\.\\d{3}").containsMatchIn(trimmed) -> SubtitleFormat.SBV
            Regex("(?m)^\\d+\\s*\\r?\\n\\d{2}:\\d{2}:\\d{2},\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2},\\d{3}").containsMatchIn(trimmed) -> SubtitleFormat.SRT
            else -> SubtitleFormat.SRT
        }
    }

    fun parse(content: String, format: SubtitleFormat = SubtitleFormat.SRT): List<SubtitleCue> {
        val autoFmt = if (format == SubtitleFormat.SRT) detectFormat(content) else format
        return parseContent(content, autoFmt)
    }

    fun parse(content: String, format: SubtitleFormat, fps: Float): List<SubtitleCue> {
        val autoFmt = if (format == SubtitleFormat.SRT) detectFormat(content) else format
        return parseContent(content, autoFmt, fps.toDouble())
    }

    fun parse(content: String, format: SubtitleFormat, fps: Double): List<SubtitleCue> {
        val autoFmt = if (format == SubtitleFormat.SRT) detectFormat(content) else format
        return parseContent(content, autoFmt, fps)
    }

    fun convert(cues: List<SubtitleCue>, targetFormat: SubtitleFormat): String {
        return exportContent(cues, targetFormat)
    }

    fun convert(cues: List<SubtitleCue>, targetFormat: SubtitleFormat, fps: Float): String {
        return exportContent(cues, targetFormat, fps.toDouble())
    }

    fun convert(cues: List<SubtitleCue>, targetFormat: SubtitleFormat, fps: Double): String {
        return exportContent(cues, targetFormat, fps)
    }

    fun parseTimestamp(text: String, fps: Double = 23.976): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L

        if (trimmed.endsWith("ms", ignoreCase = true)) {
            return trimmed.dropLast(2).trim().toLongOrNull() ?: 0L
        }
        if (trimmed.endsWith("s", ignoreCase = true)) {
            return ((trimmed.dropLast(1).trim().toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
        }

        return try {
            val parts = trimmed.replace(',', '.').split(":")
            when (parts.size) {
                4 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toLong()
                    val f = parts[3].toLong()
                    val frameMs = (f * 1000.0 / fps).toLong()
                    (h * 3600_000L) + (m * 60_000L) + (s * 1000L) + frameMs
                }
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val s = secParts[0].toLong()
                    val ms = if (secParts.size > 1) {
                        val raw = secParts[1]
                        if (raw.length == 2) raw.toLong() * 10L else raw.padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (h * 3600_000L) + (m * 60_000L) + (s * 1000L) + ms
                }
                2 -> {
                    val m = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val s = secParts[0].toLong()
                    val ms = if (secParts.size > 1) {
                        val raw = secParts[1]
                        if (raw.length == 2) raw.toLong() * 10L else raw.padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (m * 60_000L) + (s * 1000L) + ms
                }
                else -> trimmed.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun formatTimestamp(timeMs: Long, format: SubtitleFormat = SubtitleFormat.ASS, fps: Double = 25.0): String {
        val totalMs = maxOf(0L, timeMs)
        val hours = totalMs / 3600_000L
        val minutes = (totalMs % 3600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1000L
        val ms = totalMs % 1000L

        return when (format) {
            SubtitleFormat.ASS, SubtitleFormat.SSA -> {
                val cs = ms / 10L
                String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, cs)
            }
            SubtitleFormat.SRT -> {
                String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
            }
            SubtitleFormat.VTT, SubtitleFormat.TTML, SubtitleFormat.DFXP -> {
                String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
            }
            SubtitleFormat.SUB -> {
                val frame = (totalMs / 1000.0 * fps).toLong()
                "$frame"
            }
            SubtitleFormat.STL -> {
                val frame = ((totalMs % 1000L) * fps / 1000.0).toLong().coerceIn(0L, (fps - 1).toLong())
                String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frame)
            }
            SubtitleFormat.LRC -> {
                val totalSec = totalMs / 1000L
                val m = totalSec / 60L
                val s = totalSec % 60L
                val cs = ms / 10L
                String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
            }
            else -> {
                String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
            }
        }
    }

    fun parseContent(content: String, format: SubtitleFormat, fps: Double = 23.976): List<SubtitleCue> {
        if (content.isBlank()) return emptyList()

        val result = when (format) {
            SubtitleFormat.ASS, SubtitleFormat.SSA -> AssParser.parse(content)
            SubtitleFormat.VTT -> parseVtt(content)
            SubtitleFormat.SUB -> parseMicroDvd(content, fps)
            SubtitleFormat.SBV -> parseSbv(content)
            SubtitleFormat.SMI -> parseSami(content)
            SubtitleFormat.LRC -> parseLrc(content)
            SubtitleFormat.TTML, SubtitleFormat.DFXP -> parseTtmlOrDfxp(content)
            SubtitleFormat.STL -> parseStl(content, fps)
            SubtitleFormat.TXT, SubtitleFormat.CLEAN_TEXT -> {
                val auto = detectFormat(content)
                if (auto != SubtitleFormat.TXT && auto != SubtitleFormat.SRT) {
                    parseContent(content, auto, fps)
                } else {
                    parseSrt(content)
                }
            }
            else -> parseSrt(content)
        }

        if (AssParser.lastParsedStyleMap.isEmpty()) {
            AssParser.lastParsedStyleMap["Default"] = AssParser.AssStyleDef(name = "Default")
            AssParser.rebuildHeaderFromStyleMap()
        }

        return result
    }

    private fun parseMicroDvd(content: String, fps: Double): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var index = 1
        val regex = Regex("^\\{(\\d+)\\}\\{(\\d+)\\}(.*)", RegexOption.MULTILINE)
        content.lines().forEach { line ->
            val trimmed = line.trim()
            val match = regex.find(trimmed)
            if (match != null) {
                val startFrame = match.groupValues[1].toLongOrNull() ?: 0L
                val endFrame = match.groupValues[2].toLongOrNull() ?: 0L
                val rawText = match.groupValues[3]

                val startMs = (startFrame * 1000.0 / fps).toLong()
                val endMs = (endFrame * 1000.0 / fps).toLong()

                val normText = rawText.replace("|", "\n")
                val cleanText = stripAllSubtitleTags(normText)

                if (cleanText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            index = index++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            text = cleanText,
                            rawText = rawText
                        )
                    )
                }
            }
        }
        return cues
    }

    private fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.replace("\r\n", "\n").replace("\r", "\n").split("\n\n")

        var autoIndex = 1
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeLineIndex = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIndex = i
                    break
                }
            }

            if (timeLineIndex != -1) {
                val timeLine = lines[timeLineIndex]
                val timeParts = timeLine.split("-->").map { it.trim() }
                if (timeParts.size >= 2) {
                    val startMs = parseTimestamp(timeParts[0])
                    val endMs = parseTimestamp(timeParts[1])
                    val textLines = lines.drop(timeLineIndex + 1)
                    val rawText = textLines.joinToString("\n")
                    val cleanText = stripAllSubtitleTags(rawText)

                    if (cleanText.isNotBlank()) {
                        cues.add(
                            SubtitleCue(
                                index = autoIndex++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = cleanText,
                                rawText = rawText
                            )
                        )
                    }
                }
            }
        }
        return cues
    }

    private fun parseVtt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").lines()

        var autoIndex = 1
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE", ignoreCase = true)) {
                i++
                continue
            }

            if (line.contains("-->")) {
                val timeParts = line.split("-->").map { it.trim() }
                if (timeParts.size >= 2) {
                    val startMs = parseTimestamp(timeParts[0])
                    val endPart = timeParts[1].split(" ")[0]
                    val endMs = parseTimestamp(endPart)

                    i++
                    val textLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(lines[i].trim())
                        i++
                    }
                    val rawText = textLines.joinToString("\n")
                    val cleanText = stripAllSubtitleTags(rawText)

                    if (cleanText.isNotBlank()) {
                        cues.add(
                            SubtitleCue(
                                index = autoIndex++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = cleanText,
                                rawText = rawText
                            )
                        )
                    }
                }
            } else {
                i++
            }
        }
        return cues
    }

    private fun parseSbv(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.replace("\r\n", "\n").split("\n\n")
        var autoIndex = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val timeLine = lines[0]
            if (timeLine.contains(",")) {
                val parts = timeLine.split(",")
                if (parts.size >= 2) {
                    val startMs = parseTimestamp(parts[0])
                    val endMs = parseTimestamp(parts[1])
                    val rawText = lines.drop(1).joinToString("\n")
                    val cleanText = stripAllSubtitleTags(rawText)

                    if (cleanText.isNotBlank()) {
                        cues.add(
                            SubtitleCue(
                                index = autoIndex++,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = cleanText,
                                rawText = rawText
                            )
                        )
                    }
                }
            }
        }
        return cues
    }

    private fun parseSami(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val syncRegex = Regex("<SYNC\\s+Start=(\\d+)>(.*?)(?=<SYNC|\\s*</BODY>|\\s*</SAMI>|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val matches = syncRegex.findAll(content).toList()

        var autoIndex = 1
        for (i in matches.indices) {
            val match = matches[i]
            val startMs = match.groupValues[1].toLongOrNull() ?: 0L
            val nextStartMs = if (i + 1 < matches.size) {
                matches[i + 1].groupValues[1].toLongOrNull() ?: (startMs + 3000L)
            } else {
                startMs + 3000L
            }

            val bodyPart = match.groupValues[2]
            val pRegex = Regex("<P[^>]*>(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val pMatch = pRegex.find(bodyPart)
            val rawHtml = pMatch?.groupValues?.get(1) ?: bodyPart

            var text = rawHtml.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace("&nbsp;", " ", ignoreCase = true)
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
            text = stripAllSubtitleTags(text)

            if (text.isNotBlank()) {
                cues.add(
                    SubtitleCue(
                        index = autoIndex++,
                        startTimeMs = startMs,
                        endTimeMs = nextStartMs,
                        text = text,
                        rawText = rawHtml
                    )
                )
            }
        }
        return cues
    }

    private fun parseLrc(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lrcRegex = Regex("\\[(\\d+):(\\d{2})[\\.:](\\d{2,3})\\](.*)")
        val lines = content.lines()

        val parsedList = mutableListOf<Pair<Long, String>>()
        for (line in lines) {
            val trimmed = line.trim()
            val match = lrcRegex.find(trimmed)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val subSecRaw = match.groupValues[3]
                val subSecMs = if (subSecRaw.length == 2) subSecRaw.toLong() * 10L else subSecRaw.take(3).padEnd(3, '0').toLong()
                val startMs = (min * 60_000L) + (sec * 1000L) + subSecMs
                val text = stripAllSubtitleTags(match.groupValues[4])
                if (text.isNotBlank()) {
                    parsedList.add(Pair(startMs, text))
                }
            }
        }

        parsedList.sortBy { it.first }
        var autoIndex = 1
        for (i in parsedList.indices) {
            val (startMs, text) = parsedList[i]
            val endMs = if (i + 1 < parsedList.size) parsedList[i + 1].first else (startMs + 3000L)
            cues.add(
                SubtitleCue(
                    index = autoIndex++,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    text = text
                )
            )
        }
        return cues
    }

    private fun parseTtmlOrDfxp(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val pRegex = Regex("<p[^>]*begin=[\"']([^\"']+)[\"'][^>]*end=[\"']([^\"']+)[\"'][^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val matches = pRegex.findAll(content).toList()

        var autoIndex = 1
        for (match in matches) {
            val startStr = match.groupValues[1]
            val endStr = match.groupValues[2]
            val innerXml = match.groupValues[3]

            val startMs = parseTimestamp(startStr)
            val endMs = parseTimestamp(endStr)

            var text = innerXml.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
            text = stripAllSubtitleTags(text)

            if (text.isNotBlank()) {
                cues.add(
                    SubtitleCue(
                        index = autoIndex++,
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        text = text,
                        rawText = innerXml
                    )
                )
            }
        }
        return cues
    }

    private fun parseStl(content: String, fps: Double): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lineRegex = Regex("^(\\d{2}:\\d{2}:\\d{2}:\\d{2})\\s*,\\s*(\\d{2}:\\d{2}:\\d{2}:\\d{2})\\s*,\\s*(.*)", RegexOption.MULTILINE)
        var autoIndex = 1

        content.lines().forEach { line ->
            val trimmed = line.trim()
            val match = lineRegex.find(trimmed)
            if (match != null) {
                val startMs = parseTimestamp(match.groupValues[1], fps)
                val endMs = parseTimestamp(match.groupValues[2], fps)
                val text = stripAllSubtitleTags(match.groupValues[3].replace("|", "\n"))

                if (text.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            index = autoIndex++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            text = text
                        )
                    )
                }
            }
        }
        return cues
    }

    fun exportContent(cues: List<SubtitleCue>, format: SubtitleFormat, fps: Double = 23.976): String {
        return when (format) {
            SubtitleFormat.ASS, SubtitleFormat.SSA -> AssParser.export(cues)
            SubtitleFormat.VTT -> exportVtt(cues)
            SubtitleFormat.SUB -> exportMicroDvd(cues, fps)
            SubtitleFormat.SBV -> exportSbv(cues)
            SubtitleFormat.SMI -> exportSami(cues)
            SubtitleFormat.LRC -> exportLrc(cues)
            SubtitleFormat.TTML, SubtitleFormat.DFXP -> exportTtmlOrDfxp(cues)
            SubtitleFormat.STL -> exportStl(cues, fps)
            SubtitleFormat.TXT, SubtitleFormat.CLEAN_TEXT -> exportTxt(cues)
            else -> exportSrt(cues)
        }
    }

    private fun exportSrt(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        cues.forEachIndexed { i, cue ->
            sb.appendLine("${i + 1}")
            val start = formatTimestamp(cue.startTimeMs, SubtitleFormat.SRT)
            val end = formatTimestamp(cue.endTimeMs, SubtitleFormat.SRT)
            sb.appendLine("$start --> $end")
            val cleanText = stripAllSubtitleTags(cue.text)
            sb.appendLine(cleanText)
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    private fun exportVtt(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        sb.appendLine("WEBVTT")
        sb.appendLine()
        cues.forEachIndexed { i, cue ->
            sb.appendLine("${i + 1}")
            val start = formatTimestamp(cue.startTimeMs, SubtitleFormat.VTT)
            val end = formatTimestamp(cue.endTimeMs, SubtitleFormat.VTT)
            sb.appendLine("$start --> $end")
            val cleanText = stripAllSubtitleTags(cue.text)
            sb.appendLine(cleanText)
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    private fun exportMicroDvd(cues: List<SubtitleCue>, fps: Double): String {
        val sb = StringBuilder()
        cues.forEach { cue ->
            val startFrame = (cue.startTimeMs * fps / 1000.0).toLong().coerceAtLeast(0L)
            val endFrame = (cue.endTimeMs * fps / 1000.0).toLong().coerceAtLeast(startFrame + 1)
            val cleanText = stripAllSubtitleTags(cue.text).replace("\n", "|")
            sb.appendLine("{$startFrame}{$endFrame}$cleanText")
        }
        return sb.toString().trimEnd()
    }

    private fun exportSbv(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        cues.forEach { cue ->
            val start = formatTimestamp(cue.startTimeMs, SubtitleFormat.VTT).let {
                if (it.startsWith("0")) it.substring(1) else it
            }
            val end = formatTimestamp(cue.endTimeMs, SubtitleFormat.VTT).let {
                if (it.startsWith("0")) it.substring(1) else it
            }
            sb.appendLine("$start,$end")
            val cleanText = stripAllSubtitleTags(cue.text)
            sb.appendLine(cleanText)
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    private fun exportSami(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        sb.appendLine("<SAMI>")
        sb.appendLine("<HEAD>")
        sb.appendLine("<TITLE>Subtitle</TITLE>")
        sb.appendLine("<STYLE TYPE=\"text/css\">")
        sb.appendLine("<!--")
        sb.appendLine("P { font-family: Arial; font-size: 18pt; text-align: center; color: white; }")
        sb.appendLine(".ENCC { Name: English; lang: en-US; SAMIType: CC; }")
        sb.appendLine("-->")
        sb.appendLine("</STYLE>")
        sb.appendLine("</HEAD>")
        sb.appendLine("<BODY>")
        cues.forEach { cue ->
            val cleanText = stripAllSubtitleTags(cue.text).replace("\n", "<br>")
            sb.appendLine("<SYNC Start=${cue.startTimeMs}><P Class=ENCC>$cleanText")
            sb.appendLine("<SYNC Start=${cue.endTimeMs}><P Class=ENCC>&nbsp;")
        }
        sb.appendLine("</BODY>")
        sb.appendLine("</SAMI>")
        return sb.toString()
    }

    private fun exportLrc(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        cues.forEach { cue ->
            val totalMs = cue.startTimeMs
            val min = totalMs / 60_000L
            val sec = (totalMs % 60_000L) / 1000L
            val cs = (totalMs % 1000L) / 10L
            val timeStr = String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, cs)
            val cleanText = stripAllSubtitleTags(cue.text).replace("\n", " ")
            sb.appendLine("$timeStr$cleanText")
        }
        return sb.toString().trimEnd()
    }

    private fun exportTtmlOrDfxp(cues: List<SubtitleCue>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\" xml:lang=\"en\">")
        sb.appendLine("  <head>")
        sb.appendLine("    <styling>")
        sb.appendLine("      <style xml:id=\"s0\" tts:fontFamily=\"Arial\" tts:fontSize=\"18px\" tts:color=\"white\" tts:textAlign=\"center\"/>")
        sb.appendLine("    </styling>")
        sb.appendLine("  </head>")
        sb.appendLine("  <body>")
        sb.appendLine("    <div>")

        cues.forEach { cue ->
            val start = formatTimestamp(cue.startTimeMs, SubtitleFormat.VTT)
            val end = formatTimestamp(cue.endTimeMs, SubtitleFormat.VTT)
            val cleanText = stripAllSubtitleTags(cue.text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br/>")

            sb.appendLine("      <p begin=\"$start\" end=\"$end\">$cleanText</p>")
        }

        sb.appendLine("    </div>")
        sb.appendLine("  </body>")
        sb.appendLine("</tt>")
        return sb.toString()
    }

    private fun exportStl(cues: List<SubtitleCue>, fps: Double): String {
        val sb = StringBuilder()
        cues.forEach { cue ->
            val start = formatTimecode(cue.startTimeMs, fps)
            val end = formatTimecode(cue.endTimeMs, fps)
            val cleanText = stripAllSubtitleTags(cue.text).replace("\n", " | ")
            sb.appendLine("$start , $end , $cleanText")
        }
        return sb.toString().trimEnd()
    }

    private fun exportTxt(cues: List<SubtitleCue>): String {
        return cues.joinToString("\n") { stripAllSubtitleTags(it.text) }
    }

    private fun formatTimecode(timeMs: Long, fps: Double): String {
        val totalMs = maxOf(0L, timeMs)
        val hours = totalMs / 3600_000L
        val minutes = (totalMs % 3600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1000L
        val remMs = totalMs % 1000L
        val frame = (remMs * fps / 1000.0).toLong().coerceIn(0L, (fps - 1).toLong())
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frame)
    }

    fun cleanCues(cues: List<SubtitleCue>, filters: CleanerFilters): List<SubtitleCue> {
        return cues.mapNotNull { cue ->
            var text = cue.text

            if (filters.removeSdh) {
                text = text.replace(Regex("\\[.*?\\]"), "").replace(Regex("\\(.*?\\)"), "")
            }
            if (filters.removeSpeakerLabels) {
                text = text.replace(Regex("^[A-Z0-9_\\s]+:\\s*"), "")
            }
            if (filters.removeMusicNotes) {
                text = text.replace("♪", "").replace("♫", "").replace("&#9834;", "")
            }
            if (filters.removeLineBreaks) {
                text = text.replace("\n", " ")
            }
            if (filters.uppercaseToLowercase) {
                text = text.lowercase()
            }
            if (filters.removeTextFormatting) {
                text = text.replace(Regex("<[^>]*>"), "").replace(Regex("\\{\\\\.*?\\}"), "")
            }
            if (filters.removeCurlyBrackets) {
                text = text.replace(Regex("\\{.*?\\}"), "")
            }
            if (filters.removeParentheses) {
                text = text.replace(Regex("\\(.*?\\)"), "")
            }
            if (filters.removeSquareBrackets) {
                text = text.replace(Regex("\\[.*?\\]"), "")
            }

            text = text.trim()
            if (text.isBlank()) null else cue.copy(text = text)
        }
    }

    fun shiftCues(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        return shiftAll(cues, offsetMs)
    }

    fun formatTimestampDisplay(timeMs: Long, format: SubtitleFormat = SubtitleFormat.ASS, fps: Double = 25.0): String {
        return formatTimestamp(timeMs, format, fps)
    }

    fun calculateAutoSyncOffset(refCues: List<SubtitleCue>, targetCues: List<SubtitleCue>): Long {
        if (refCues.isEmpty() || targetCues.isEmpty()) return 0L
        val refStart = refCues.first().startTimeMs
        val targetStart = targetCues.first().startTimeMs
        return refStart - targetStart
    }

    fun shiftAll(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        return cues.map { cue ->
            cue.copy(
                startTimeMs = maxOf(0L, cue.startTimeMs + offsetMs),
                endTimeMs = maxOf(0L, cue.endTimeMs + offsetMs)
            )
        }
    }

    fun scaleSpeed(cues: List<SubtitleCue>, factor: Double): List<SubtitleCue> {
        if (factor <= 0.0) return cues
        return cues.map { cue ->
            cue.copy(
                startTimeMs = (cue.startTimeMs * factor).toLong(),
                endTimeMs = (cue.endTimeMs * factor).toLong()
            )
        }
    }

    fun linearlyInterpolateShift(
        cues: List<SubtitleCue>,
        firstOffsetMs: Long,
        lastOffsetMs: Long
    ): List<SubtitleCue> {
        if (cues.isEmpty()) return emptyList()
        if (cues.size == 1) return shiftAll(cues, firstOffsetMs)

        val firstStart = cues.first().startTimeMs
        val lastStart = cues.last().startTimeMs
        val timeSpan = maxOf(1L, lastStart - firstStart)

        return cues.map { cue ->
            val progress = (cue.startTimeMs - firstStart).toDouble() / timeSpan.toDouble()
            val interpolatedOffset = (firstOffsetMs + progress * (lastOffsetMs - firstOffsetMs)).toLong()
            cue.copy(
                startTimeMs = maxOf(0L, cue.startTimeMs + interpolatedOffset),
                endTimeMs = maxOf(0L, cue.endTimeMs + interpolatedOffset)
            )
        }
    }
}
