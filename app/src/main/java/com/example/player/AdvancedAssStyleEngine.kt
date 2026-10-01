package com.example.player

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale

/**
 * Advanced ASS/SSA per-style editing support.
 *
 * This file is deliberately free of Android/mpv dependencies (only org.json is used) so the
 * parsing/writing rules can be unit-tested on the JVM. It NEVER re-generates a script from
 * scratch: [AdvancedAssStyleEngine.apply] takes the ORIGINAL script text and only rewrites the
 * individual `Style:` columns the user changed (plus, when a style is renamed, the style column
 * of the Dialogue/Comment lines that reference it). Dialogue text, override tags, timestamps,
 * layers, [Fonts]/[Graphics]/unknown sections, unknown Format columns and line endings are kept
 * byte-for-byte.
 */

/** Logical (format independent) names of the editable style fields. */
object AssFields {
    const val NAME = "name"
    const val FONT_NAME = "fontname"
    const val FONT_SIZE = "fontsize"
    const val PRIMARY_COLOUR = "primarycolour"
    const val SECONDARY_COLOUR = "secondarycolour"
    const val OUTLINE_COLOUR = "outlinecolour"
    const val BACK_COLOUR = "backcolour"
    const val BOLD = "bold"
    const val ITALIC = "italic"
    const val UNDERLINE = "underline"
    const val STRIKE_OUT = "strikeout"
    const val SCALE_X = "scalex"
    const val SCALE_Y = "scaley"
    const val SPACING = "spacing"
    const val ANGLE = "angle"
    const val BORDER_STYLE = "borderstyle"
    const val OUTLINE = "outline"
    const val SHADOW = "shadow"
    const val ALIGNMENT = "alignment"
    const val MARGIN_L = "marginl"
    const val MARGIN_R = "marginr"
    const val MARGIN_V = "marginv"
    const val ENCODING = "encoding"

    val COLOUR_FIELDS = setOf(PRIMARY_COLOUR, SECONDARY_COLOUR, OUTLINE_COLOUR, BACK_COLOUR)
    val FLAG_FIELDS = setOf(BOLD, ITALIC, UNDERLINE, STRIKE_OUT)

    val V4PLUS_DEFAULT_FORMAT = listOf(
        "name", "fontname", "fontsize", "primarycolour", "secondarycolour", "outlinecolour",
        "backcolour", "bold", "italic", "underline", "strikeout", "scalex", "scaley", "spacing",
        "angle", "borderstyle", "outline", "shadow", "alignment", "marginl", "marginr", "marginv",
        "encoding"
    )
    val V4_DEFAULT_FORMAT = listOf(
        "name", "fontname", "fontsize", "primarycolour", "secondarycolour", "tertiarycolour",
        "backcolour", "bold", "italic", "borderstyle", "outline", "shadow", "alignment",
        "marginl", "marginr", "marginv", "alphalevel", "encoding"
    )

    /**
     * Index of the Format column that stores [field], or -1 when this script's Style format does
     * not contain it (for example Underline/ScaleX/Angle in classic SSA "V4 Styles").
     * SSA v4 calls the outline colour column "TertiaryColour".
     */
    fun columnIndex(format: List<String>, field: String): Int {
        val direct = format.indexOf(field)
        if (direct >= 0) return direct
        if (field == OUTLINE_COLOUR) return format.indexOf("tertiarycolour")
        return -1
    }

    fun defaultValue(field: String): String = when (field) {
        FONT_NAME -> "Arial"
        FONT_SIZE -> "20"
        PRIMARY_COLOUR -> "&H00FFFFFF"
        SECONDARY_COLOUR -> "&H000000FF"
        OUTLINE_COLOUR -> "&H00000000"
        BACK_COLOUR -> "&H00000000"
        BOLD, ITALIC, UNDERLINE, STRIKE_OUT -> "0"
        SCALE_X, SCALE_Y -> "100"
        SPACING, ANGLE -> "0"
        BORDER_STYLE -> "1"
        OUTLINE -> "2"
        SHADOW -> "2"
        ALIGNMENT -> "2"
        MARGIN_L, MARGIN_R, MARGIN_V -> "10"
        ENCODING -> "1"
        else -> ""
    }

    /**
     * Validates [input] for [field] and returns the canonical string that is written into the
     * script, or null when the value is not acceptable. Callers must never persist a value for
     * which this returns null (that would silently corrupt the style line).
     */
    fun normalize(field: String, input: String): String? {
        val s = input.trim()
        return when (field) {
            NAME, FONT_NAME ->
                s.takeIf { it.isNotEmpty() && ',' !in it && '\n' !in it && '\r' !in it }
            FONT_SIZE -> number(s, 1.0, 1000.0)
            SCALE_X, SCALE_Y -> number(s, 1.0, 1000.0)
            SPACING -> number(s, -1000.0, 1000.0)
            ANGLE -> number(s, -3600.0, 3600.0)
            OUTLINE -> number(s, 0.0, 100.0)
            SHADOW -> number(s, -100.0, 100.0)
            BORDER_STYLE -> integer(s, 1, 4)
            ALIGNMENT -> integer(s, 1, 9)
            MARGIN_L, MARGIN_R, MARGIN_V -> integer(s, 0, 9999)
            ENCODING -> integer(s, -1, 255)
            BOLD, ITALIC, UNDERLINE, STRIKE_OUT -> if (s == "0" || s == "-1") s else null
            PRIMARY_COLOUR, SECONDARY_COLOUR, OUTLINE_COLOUR, BACK_COLOUR ->
                parseColourToArgb(s)?.let { argbToAssColour(it) }
            else -> null
        }
    }

    private fun number(s: String, min: Double, max: Double): String? {
        val d = s.replace(',', '.').toDoubleOrNull() ?: return null
        if (d.isNaN() || d.isInfinite() || d < min || d > max) return null
        return formatNumber(d)
    }

    private fun integer(s: String, min: Int, max: Int): String? {
        val v = s.toIntOrNull() ?: return null
        return if (v in min..max) v.toString() else null
    }

    fun formatNumber(d: Double): String {
        return BigDecimal.valueOf(d).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /**
     * Parses an ASS/SSA colour token (`&HAABBGGRR`, `&HBBGGRR&`, `&HBBGGRR`, `0xAABBGGRR` or the
     * decimal form used by old SSA scripts) into an Android style ARGB value. The ASS alpha
     * channel is inverted (00 = opaque, FF = transparent); the returned ARGB uses the usual
     * Android convention (FF = opaque). Returns the value as an unsigned 32-bit Long.
     */
    fun parseColourToArgb(raw: String): Long? {
        var s = raw.trim()
        if (s.endsWith("&")) s = s.dropLast(1)
        val radix: Int
        val digits: String
        if (s.startsWith("&h", ignoreCase = true) || s.startsWith("0x", ignoreCase = true)) {
            digits = s.substring(2)
            radix = 16
        } else {
            digits = s
            radix = 10
        }
        if (digits.isEmpty() || digits.length > 16) return null
        val parsed = digits.toLongOrNull(radix) ?: return null
        val u = parsed and 0xFFFFFFFFL
        val assAlpha = (u shr 24) and 0xFF
        val bb = (u shr 16) and 0xFF
        val gg = (u shr 8) and 0xFF
        val rr = u and 0xFF
        val alpha = 0xFF - assAlpha
        return (alpha shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    /** Inverse of [parseColourToArgb]; always emits the canonical `&HAABBGGRR` form. */
    fun argbToAssColour(argb: Long): String {
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.US, "&H%02X%02X%02X%02X", 0xFF - a, b, g, r)
    }

    /** SSA v4 numbering (1-3 bottom, 5-7 top, 9-11 middle) -> ASS numpad numbering (1-9). */
    fun ssaAlignmentToAss(value: Int): Int = when (value) {
        1, 2, 3 -> value
        5, 6, 7 -> value + 2
        9, 10, 11 -> value - 5
        else -> 2
    }

    /** ASS numpad numbering (1-9) -> SSA v4 numbering. */
    fun assAlignmentToSsa(value: Int): Int = when (value) {
        1, 2, 3 -> value
        7, 8, 9 -> value - 2
        4, 5, 6 -> value + 5
        else -> 2
    }
}

/** One parsed `Style:` line. [values] is aligned with [AssStylesDocument.formatColumns]. */
class AssStyleInfo(
    val name: String,
    val values: List<String>,
    val lineIndex: Int,
    /** How many Dialogue/Comment lines reference this style. */
    val usageCount: Int
) {
    val isDefault: Boolean get() = name.trim().equals("Default", ignoreCase = true)

    /**
     * A style can only be removed when nothing references it. Removing a referenced style would
     * silently re-route those lines to "Default", i.e. change how the subtitle looks.
     */
    val canBeRemoved: Boolean get() = !isDefault && usageCount == 0
}

class AssStylesDocument internal constructor(
    /** true for `[V4+ Styles]` / `[V4++ Styles]`, false for classic SSA `[V4 Styles]`. */
    val isPlusFormat: Boolean,
    val formatColumns: List<String>,
    val styles: List<AssStyleInfo>,
    internal val eventLineIndices: List<Int>,
    internal val eventStyleColumn: Int,
    internal val eventColumnCount: Int
) {
    /** Whether this script's Style format has a column for [field] (false = not editable). */
    fun supports(field: String): Boolean = AssFields.columnIndex(formatColumns, field) >= 0

    /**
     * Current value of [field] for [style] in canonical, format independent form:
     * colours as `&HAABBGGRR`, flags as "0"/"-1", alignment always as ASS 1-9.
     */
    fun read(style: AssStyleInfo, field: String): String {
        val idx = AssFields.columnIndex(formatColumns, field)
        val raw = if (idx in style.values.indices) style.values[idx].trim() else ""
        if (field == AssFields.NAME) return style.name
        if (raw.isEmpty()) return AssFields.defaultValue(field)
        return when (field) {
            in AssFields.COLOUR_FIELDS ->
                AssFields.parseColourToArgb(raw)?.let { AssFields.argbToAssColour(it) }
                    ?: AssFields.defaultValue(field)
            in AssFields.FLAG_FIELDS -> if ((raw.toIntOrNull() ?: 0) != 0) "-1" else "0"
            AssFields.ALIGNMENT -> {
                val v = raw.toIntOrNull() ?: 2
                val ass = if (isPlusFormat) v else AssFields.ssaAlignmentToAss(v)
                (if (ass in 1..9) ass else 2).toString()
            }
            else -> raw
        }
    }
}

/**
 * Everything the user customised for ONE subtitle (identified by [AdvancedAssStyleEngine.contentKey]
 * or an embedded-track key). Style names are the ORIGINAL names as found in the script.
 */
data class AssSubtitleOverrides(
    val styles: Map<String, Map<String, String>> = emptyMap(),
    val deleted: Set<String> = emptySet(),
    val touchedAt: Long = 0L,
    /**
     * > 0 when the user APPLIED an edited raw script/subtitle text for this subtitle. The text
     * itself lives in [AssRawTextStore] (it can be hundreds of KB, too big for the settings json);
     * the revision only marks that it exists and makes the settings json change so playback syncs.
     */
    val rawRevision: Long = 0L
) {
    /** True when there are no per-style edits (a raw text edit is tracked separately). */
    val isEmpty: Boolean get() = deleted.isEmpty() && styles.values.all { it.isEmpty() }

    /** True when nothing at all is stored (no style edits and no applied raw text). */
    val isCompletelyEmpty: Boolean get() = isEmpty && rawRevision <= 0L

    /** Same overrides without the raw text marker. */
    fun stylesOnly(): AssSubtitleOverrides = if (rawRevision == 0L) this else copy(rawRevision = 0L)

    fun fieldOf(style: String, field: String): String? = styles[style]?.get(field)

    fun withField(style: String, field: String, value: String): AssSubtitleOverrides {
        val current = styles[style] ?: emptyMap()
        return copy(styles = styles + (style to (current + (field to value))))
    }

    fun withoutField(style: String, field: String): AssSubtitleOverrides {
        val current = styles[style] ?: return this
        val updated = current - field
        return copy(styles = if (updated.isEmpty()) styles - style else styles + (style to updated))
    }

    fun resetStyle(style: String): AssSubtitleOverrides =
        copy(styles = styles - style, deleted = deleted.filterNot { it == style }.toSet())

    fun withDeleted(style: String): AssSubtitleOverrides = copy(deleted = deleted + style)

    fun restoreDeleted(): AssSubtitleOverrides = copy(deleted = emptySet())
}

/** Original ASS/SSA text of a subtitle track plus its persistence identity. */
class AdvancedAssSource(val key: String, val title: String, val text: String)

/**
 * Original, unedited text of the active subtitle (any format) for the raw text editor.
 * [extension] is the real text format ("ass", "srt", "vtt", ...), [isAss] is true for ASS/SSA scripts.
 */
class RawSubtitleSource(
    val key: String,
    val title: String,
    val text: String,
    val extension: String,
    val isAss: Boolean
)

/**
 * Persistence helper: all per-subtitle overrides are stored as ONE json string inside
 * PlayerSettings (`assStyleOverridesJson`) keyed by subtitle identity, so one subtitle's
 * customisation can never leak into another.
 */
object AssOverridesStore {
    private const val MAX_SUBTITLES = 40

    fun decode(json: String): Map<String, AssSubtitleOverrides> {
        if (json.isBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            val out = LinkedHashMap<String, AssSubtitleOverrides>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = root.optJSONObject(key) ?: continue
                val styles = LinkedHashMap<String, Map<String, String>>()
                val stylesObj = entry.optJSONObject("s")
                if (stylesObj != null) {
                    val names = stylesObj.keys()
                    while (names.hasNext()) {
                        val styleName = names.next()
                        val fieldsObj = stylesObj.optJSONObject(styleName) ?: continue
                        val fields = LinkedHashMap<String, String>()
                        val fieldKeys = fieldsObj.keys()
                        while (fieldKeys.hasNext()) {
                            val f = fieldKeys.next()
                            val v = fieldsObj.optString(f, "")
                            if (v.isNotEmpty()) fields[f] = v
                        }
                        if (fields.isNotEmpty()) styles[styleName] = fields
                    }
                }
                val deleted = LinkedHashSet<String>()
                val deletedArr = entry.optJSONArray("d")
                if (deletedArr != null) {
                    for (i in 0 until deletedArr.length()) {
                        val n = deletedArr.optString(i, "")
                        if (n.isNotEmpty()) deleted.add(n)
                    }
                }
                out[key] = AssSubtitleOverrides(styles, deleted, entry.optLong("t", 0L), entry.optLong("r", 0L))
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun encode(all: Map<String, AssSubtitleOverrides>): String {
        val root = JSONObject()
        val kept = all.filterValues { !it.isCompletelyEmpty }
            .entries
            .sortedByDescending { it.value.touchedAt }
            .take(MAX_SUBTITLES)
        for ((key, value) in kept) {
            val entry = JSONObject()
            entry.put("t", value.touchedAt)
            if (value.rawRevision > 0L) entry.put("r", value.rawRevision)
            val stylesObj = JSONObject()
            for ((styleName, fields) in value.styles) {
                if (fields.isEmpty()) continue
                val fieldsObj = JSONObject()
                for ((f, v) in fields) fieldsObj.put(f, v)
                stylesObj.put(styleName, fieldsObj)
            }
            entry.put("s", stylesObj)
            val deletedArr = JSONArray()
            value.deleted.forEach { deletedArr.put(it) }
            entry.put("d", deletedArr)
            root.put(key, entry)
        }
        return if (root.length() == 0) "" else root.toString()
    }

    fun get(json: String, key: String): AssSubtitleOverrides = decode(json)[key] ?: AssSubtitleOverrides()

    /** True when at least one subtitle has stored overrides. */
    fun hasAny(json: String): Boolean = decode(json).values.any { !it.isCompletelyEmpty }

    fun put(json: String, key: String, value: AssSubtitleOverrides): String {
        val all = LinkedHashMap(decode(json))
        all[key] = value.copy(touchedAt = System.currentTimeMillis())
        return encode(all)
    }
}

/**
 * Disk store for raw (full text) subtitle edits applied by the user, one UTF-8 file per subtitle
 * identity ([AdvancedAssStyleEngine.contentKey] / embedded key). Files live in the app's private
 * storage; they are only written when the user presses Apply in the raw editor.
 */
object AssRawTextStore {
    private const val DIR = "subtitle_raw_edits"

    private fun fileFor(context: android.content.Context, key: String): java.io.File {
        val safe = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return java.io.File(java.io.File(context.applicationContext.filesDir, DIR).apply { mkdirs() }, "$safe.txt")
    }

    fun read(context: android.content.Context, key: String): String? {
        return try {
            val f = fileFor(context, key)
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        } catch (_: Throwable) {
            null
        }
    }

    fun write(context: android.content.Context, key: String, text: String): Boolean {
        return try {
            val f = fileFor(context, key)
            val tmp = java.io.File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (f.exists()) f.delete()
            tmp.renameTo(f) || run { f.writeText(text, Charsets.UTF_8); tmp.delete(); true }
        } catch (_: Throwable) {
            false
        }
    }

    fun delete(context: android.content.Context, key: String) {
        try { fileFor(context, key).delete() } catch (_: Throwable) {}
    }
}

object AdvancedAssStyleEngine {

    private const val SECTION_OTHER = 0
    private const val SECTION_STYLES = 1
    private const val SECTION_EVENTS = 2

    /** Stable identity of a subtitle script: first 16 hex chars of the SHA-1 of its text. */
    fun contentKey(text: String): String = sha1Hex(text).take(16)

    fun sha1Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        return sb.toString()
    }

    private fun isStylesSectionName(name: String): Boolean =
        name.startsWith("v4") && name.endsWith("styles")

    private fun parseFormatColumns(line: String): List<String> {
        val body = line.substring(line.indexOf(':') + 1)
        return body.split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    }

    private fun normStyleName(name: String): String =
        name.trim().removePrefix("*").trim().lowercase(Locale.ROOT)

    /**
     * Returns the Style definitions of an ASS/SSA script, or null when [text] has no
     * `[V4+ Styles]` / `[V4 Styles]` section (i.e. it is not an ASS/SSA script). Never throws
     * for malformed input.
     */
    fun parse(text: String): AssStylesDocument? {
        return try {
            parseInternal(text)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseInternal(text: String): AssStylesDocument? {
        if (text.isBlank()) return null
        val lines = text.split("\n")
        var section = SECTION_OTHER
        var stylesSectionSeen = false
        var isPlus = true
        var styleFormat: List<String>? = null
        val styleLineIndices = ArrayList<Int>()
        var eventFormat: List<String>? = null
        val eventLineIndices = ArrayList<Int>()

        for (i in lines.indices) {
            var line = lines[i]
            if (i == 0 && line.startsWith("\uFEFF")) line = line.substring(1)
            val t = line.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("[")) {
                val close = t.indexOf(']')
                if (close > 0) {
                    val name = t.substring(1, close).trim().lowercase(Locale.ROOT)
                    section = when {
                        isStylesSectionName(name) && !stylesSectionSeen -> {
                            stylesSectionSeen = true
                            isPlus = name.contains('+')
                            SECTION_STYLES
                        }
                        name == "events" -> SECTION_EVENTS
                        else -> SECTION_OTHER
                    }
                    continue
                }
            }
            when (section) {
                SECTION_STYLES -> {
                    if (t.startsWith("format:", ignoreCase = true)) {
                        if (styleFormat == null) styleFormat = parseFormatColumns(t)
                    } else if (t.startsWith("style:", ignoreCase = true)) {
                        styleLineIndices.add(i)
                    }
                }
                SECTION_EVENTS -> {
                    if (t.startsWith("format:", ignoreCase = true)) {
                        if (eventFormat == null) eventFormat = parseFormatColumns(t)
                    } else if (t.startsWith("dialogue:", ignoreCase = true) ||
                        t.startsWith("comment:", ignoreCase = true)
                    ) {
                        eventLineIndices.add(i)
                    }
                }
            }
        }
        if (!stylesSectionSeen) return null

        val format = styleFormat?.takeIf { it.contains("name") }
            ?: if (isPlus) AssFields.V4PLUS_DEFAULT_FORMAT else AssFields.V4_DEFAULT_FORMAT
        val nameIdx = format.indexOf("name")

        val evFormat = eventFormat ?: listOf(
            "layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text"
        )
        val eventStyleCol = evFormat.indexOf("style").let { if (it >= 0) it else 3 }
        val eventColCount = evFormat.size

        val usage = HashMap<String, Int>()
        for (idx in eventLineIndices) {
            val line = lines[idx]
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val bounds = fieldBounds(line, colon + 1, eventStyleCol, eventColCount) ?: continue
            val key = normStyleName(line.substring(bounds[0], bounds[1]))
            usage[key] = (usage[key] ?: 0) + 1
        }

        val styles = ArrayList<AssStyleInfo>()
        val seen = HashSet<String>()
        for (idx in styleLineIndices) {
            val raw = lines[idx].trimEnd('\r')
            val colon = raw.indexOf(':')
            if (colon < 0) continue
            val body = raw.substring(colon + 1).trimStart(' ', '\t')
            val parts = body.split(',', limit = format.size).map { it.trim() }.toMutableList()
            while (parts.size < format.size) parts.add("")
            val name = parts[nameIdx]
            if (name.isEmpty()) continue
            val key = normStyleName(name)
            if (!seen.add(key)) continue // duplicate definition: only the first one is exposed
            styles.add(AssStyleInfo(name, parts, idx, usage[key] ?: 0))
        }
        return AssStylesDocument(isPlus, format, styles, eventLineIndices, eventStyleCol, eventColCount)
    }

    /**
     * Start (inclusive) / end (exclusive) offsets of comma separated column [index] inside [s],
     * starting to count at [from]. The last column of the format swallows any further commas
     * (that is where Dialogue text lives), so it is never split.
     */
    private fun fieldBounds(s: String, from: Int, index: Int, columnCount: Int): IntArray? {
        var start = from
        var field = 0
        var i = from
        while (i < s.length) {
            if (s[i] == ',') {
                if (field == index) return intArrayOf(start, i)
                field++
                start = i + 1
                if (field >= columnCount - 1) {
                    return if (field == index) intArrayOf(start, s.length) else null
                }
            }
            i++
        }
        return if (field == index) intArrayOf(start, s.length) else null
    }

    /**
     * Returns [text] with [overrides] applied to the Style lines. When [overrides] is empty (or
     * the text is not an ASS/SSA script) [text] is returned unchanged.
     */
    fun apply(text: String, overrides: AssSubtitleOverrides): String {
        if (overrides.isEmpty) return text
        return try {
            applyInternal(text, overrides)
        } catch (_: Throwable) {
            text
        }
    }

    private fun applyInternal(text: String, overrides: AssSubtitleOverrides): String {
        val doc = parseInternal(text) ?: return text
        val lines = text.split("\n")
        val nameIdx = doc.formatColumns.indexOf("name")

        val removedLines = HashSet<Int>()
        val deletedNorm = overrides.deleted.map { normStyleName(it) }.toSet()
        for (s in doc.styles) {
            if (normStyleName(s.name) in deletedNorm && s.canBeRemoved) removedLines.add(s.lineIndex)
        }

        // Names that exist after removals; used to reject renames that would collide.
        val taken = HashSet<String>()
        for (s in doc.styles) if (s.lineIndex !in removedLines) taken.add(normStyleName(s.name))

        val renames = HashMap<String, String>() // normalised old name -> new name
        val rewrittenStyleNames = HashMap<Int, String>() // lineIndex -> new name
        for (s in doc.styles) {
            if (s.lineIndex in removedLines || s.isDefault) continue
            val requested = overrides.fieldOf(s.name, AssFields.NAME) ?: continue
            val newName = AssFields.normalize(AssFields.NAME, requested) ?: continue
            val oldKey = normStyleName(s.name)
            val newKey = normStyleName(newName)
            if (newName == s.name || newKey.isEmpty()) continue
            if (newKey != oldKey && newKey in taken) continue
            taken.remove(oldKey)
            taken.add(newKey)
            renames[oldKey] = newName
            rewrittenStyleNames[s.lineIndex] = newName
        }

        val rewritten = HashMap<Int, String>()
        for (s in doc.styles) {
            if (s.lineIndex in removedLines) continue
            val edits = overrides.styles[s.name]
            val newName = rewrittenStyleNames[s.lineIndex]
            if ((edits == null || edits.isEmpty()) && newName == null) continue
            rewritten[s.lineIndex] = rewriteStyleLine(lines[s.lineIndex], doc, edits ?: emptyMap(), newName, nameIdx)
        }

        if (renames.isNotEmpty()) {
            for (idx in doc.eventLineIndices) {
                val line = lines[idx]
                val colon = line.indexOf(':')
                if (colon < 0) continue
                val bounds = fieldBounds(line, colon + 1, doc.eventStyleColumn, doc.eventColumnCount) ?: continue
                val current = line.substring(bounds[0], bounds[1])
                val newName = renames[normStyleName(current)] ?: continue
                val star = if (current.trim().startsWith("*")) "*" else ""
                rewritten[idx] = line.substring(0, bounds[0]) + star + newName + line.substring(bounds[1])
            }
        }

        val sb = StringBuilder(text.length + 256)
        for (i in lines.indices) {
            if (i !in removedLines) {
                sb.append(rewritten[i] ?: lines[i])
                if (i < lines.lastIndex) sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun rewriteStyleLine(
        raw: String,
        doc: AssStylesDocument,
        edits: Map<String, String>,
        newName: String?,
        nameIdx: Int
    ): String {
        val hasCr = raw.endsWith("\r")
        val line = if (hasCr) raw.dropLast(1) else raw
        val colon = line.indexOf(':')
        if (colon < 0) return raw
        val head = line.substring(0, colon + 1)
        val rest = line.substring(colon + 1)
        val lead = rest.takeWhile { it == ' ' || it == '\t' }
        val body = rest.substring(lead.length)
        val columns = doc.formatColumns.size
        val parts = body.split(',', limit = columns).toMutableList()
        while (parts.size < columns) parts.add("")

        for ((field, value) in edits) {
            if (field == AssFields.NAME) continue
            val idx = AssFields.columnIndex(doc.formatColumns, field)
            if (idx < 0) continue // this format cannot store the field: never write it elsewhere
            val normalized = AssFields.normalize(field, value) ?: continue
            parts[idx] = if (field == AssFields.ALIGNMENT && !doc.isPlusFormat) {
                AssFields.assAlignmentToSsa(normalized.toInt()).toString()
            } else {
                normalized
            }
        }
        if (newName != null && nameIdx in parts.indices) parts[nameIdx] = newName
        return head + lead + parts.joinToString(",") + (if (hasCr) "\r" else "")
    }
}
