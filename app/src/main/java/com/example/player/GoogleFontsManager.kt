package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.util.MkvManagerEngine
import com.example.util.MkvTrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Automatic Google Fonts support for ASS/SSA subtitles.
 *
 * 1. The fonts a subtitle script really uses (styles that are used by dialogue lines + inline
 *    `\fn` overrides, including bold / italic flags) are identified.
 * 2. Fonts that nobody provides (not the video / subtitle, not the sub_fonts folder, not the
 *    device) are looked up on Google Fonts and downloaded when the device is online.
 * 3. Downloaded fonts are kept in a persistent store (filesDir/google_fonts) and copied into the
 *    libass fonts folder (sub_fonts, which lives in the cache and can be wiped by Android).
 *    Fonts that were downloaded earlier are therefore used even when the device is offline.
 * 4. If a font is neither downloaded nor reachable, nothing changes: the existing fallback to
 *    the user's selected font / bundled default font keeps working.
 *
 * Nothing here blocks playback: downloads run on a background scope and listeners are told when
 * new fonts arrived so the player can reload the subtitle.
 */
object GoogleFontsManager {

    private const val TAG = "GoogleFontsManager"
    private const val STORE_DIR_NAME = "google_fonts"
    private const val PREFS_NAME = "lumora_google_fonts"
    private const val FILE_PREFIX = "gf_"
    private const val CSS_ENDPOINT = "https://fonts.googleapis.com/css2"

    // An old Android WebKit user agent makes the Google Fonts CSS API answer with plain .ttf files
    // (newer agents get woff2, which libass/FreeType in mpv cannot always load).
    private const val TTF_USER_AGENT =
        "Mozilla/5.0 (Linux; U; Android 4.0.3; ko-kr; LG-L160L Build/IML74K) " +
            "AppleWebkit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val MAX_FONT_BYTES = 40L * 1024 * 1024
    private const val MISSING_TTL_MS = 14L * 24 * 60 * 60 * 1000 // "not a Google font" is remembered
    private const val RETRY_COOLDOWN_MS = 90_000L // network trouble: do not hammer the server
    private const val MAX_FAMILIES_PER_RUN = 12

    private val GENERIC_FAMILIES = setOf(
        "sans-serif", "serif", "monospace", "cursive", "fantasy", "system-ui", "default", "sans", "system"
    )

    data class FontRequest(val family: String, val bold: Boolean, val italic: Boolean) {
        val variant: String
            get() = when {
                bold && italic -> "bolditalic"
                bold -> "bold"
                italic -> "italic"
                else -> "regular"
            }
        val key: String get() = family.lowercase(Locale.ROOT) + "|" + variant
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** Called (on a background thread) after new Google fonts were placed into the fonts folder. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (l in listeners) {
            try { l() } catch (_: Throwable) {}
        }
    }

    // ---------------------------------------------------------------------------------------
    // 1. Which fonts does the subtitle use?
    // ---------------------------------------------------------------------------------------

    /**
     * Reads the font families of an ASS/SSA script. Only styles that dialogue lines really use are
     * considered (all styles when the script has no dialogue yet), plus every inline `\fn`.
     */
    fun collectFontRequests(assContent: String): LinkedHashSet<FontRequest> {
        val result = LinkedHashSet<FontRequest>()
        if (assContent.isBlank()) return result

        class StyleFont(val name: String, val family: String, val bold: Boolean, val italic: Boolean)

        val styles = ArrayList<StyleFont>()
        val usedStyles = HashSet<String>()
        var section = ""
        var styleFormat = emptyList<String>()
        var eventFormat = emptyList<String>()

        fun flag(value: String?): Boolean {
            val v = value?.trim() ?: return false
            return v == "-1" || v == "1"
        }

        for (rawLine in assContent.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[")) {
                section = line.lowercase(Locale.ROOT)
                continue
            }
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1)

            when {
                section.startsWith("[v4") && section.contains("styles") -> {
                    if (key == "format") {
                        styleFormat = value.split(",").map { it.trim().lowercase(Locale.ROOT) }
                    } else if (key == "style") {
                        val parts = value.split(",")
                        val nameIdx = styleFormat.indexOf("name").takeIf { it >= 0 } ?: 0
                        val fontIdx = styleFormat.indexOf("fontname").takeIf { it >= 0 } ?: 1
                        val boldIdx = styleFormat.indexOf("bold")
                        val italicIdx = styleFormat.indexOf("italic")
                        if (fontIdx in parts.indices && nameIdx in parts.indices) {
                            val family = cleanFamily(parts[fontIdx])
                            if (family != null) {
                                styles.add(
                                    StyleFont(
                                        name = parts[nameIdx].trim().removePrefix("*").lowercase(Locale.ROOT),
                                        family = family,
                                        bold = boldIdx in parts.indices && flag(parts[boldIdx]),
                                        italic = italicIdx in parts.indices && flag(parts[italicIdx])
                                    )
                                )
                            }
                        }
                    }
                }
                section == "[events]" -> {
                    if (key == "format") {
                        eventFormat = value.split(",").map { it.trim().lowercase(Locale.ROOT) }
                    } else if (key == "dialogue") {
                        val styleIdx = eventFormat.indexOf("style").takeIf { it >= 0 } ?: 3
                        val fields = (eventFormat.size.takeIf { it > 0 } ?: 10)
                        val parts = value.split(",", limit = fields)
                        if (styleIdx in parts.indices) {
                            usedStyles.add(parts[styleIdx].trim().removePrefix("*").lowercase(Locale.ROOT))
                        }
                    }
                }
            }
        }

        val relevant = if (usedStyles.isEmpty()) styles else styles.filter { it.name in usedStyles }
        for (s in relevant) result.add(FontRequest(s.family, s.bold, s.italic))

        // Inline \fn overrides (regular weight; the base style decides bold / italic for libass).
        if (assContent.contains("\\fn")) {
            val fnPattern = Regex("\\\\fn([^\\\\}]+)")
            for (m in fnPattern.findAll(assContent)) {
                val family = cleanFamily(m.groupValues[1]) ?: continue
                result.add(FontRequest(family, bold = false, italic = false))
            }
        }
        return result
    }

    private fun cleanFamily(raw: String): String? {
        val name = raw.trim().removePrefix("@").trim()
        if (name.isEmpty() || name.length > 80) return null
        val lower = name.lowercase(Locale.ROOT)
        if (lower in GENERIC_FAMILIES) return null
        if (lower == SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY.lowercase(Locale.ROOT)) return null
        // Font names are plain text; anything else is a broken tag, not a font.
        if (name.any { it.isISOControl() || it == '{' || it == '}' || it == '/' }) return null
        return name
    }

    // ---------------------------------------------------------------------------------------
    // 2. Install from cache / download
    // ---------------------------------------------------------------------------------------

    private fun storeDir(context: Context): File =
        File(context.applicationContext.filesDir, STORE_DIR_NAME).apply { mkdirs() }

    private fun safeName(family: String): String =
        family.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "font" }

    private fun fileName(req: FontRequest): String = FILE_PREFIX + safeName(req.family) + "_" + req.variant + ".ttf"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isKnownMissing(context: Context, req: FontRequest): Boolean {
        val at = prefs(context).getLong("missing|" + req.key, 0L)
        return at > 0L && System.currentTimeMillis() - at < MISSING_TTL_MS
    }

    private fun markMissing(context: Context, req: FontRequest) {
        prefs(context).edit().putLong("missing|" + req.key, System.currentTimeMillis()).apply()
    }

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isProvidedElsewhere(context: Context, family: String): Boolean {
        return try {
            SubtitleFontManager.isSuppliedByCurrentVideo(context, family) ||
                SubtitleFontManager.isFontAvailable(context, family)
        } catch (_: Throwable) {
            false
        }
    }

    /** 0 = not in the store, 1 = already in the fonts folder, 2 = copied into it just now. */
    private fun installFromStore(context: Context, req: FontRequest, fontsDir: File): Int {
        val name = fileName(req)
        val dest = File(fontsDir, name)
        if (dest.isFile && dest.length() > 0L) return 1
        val src = File(storeDir(context), name)
        if (!src.isFile || src.length() <= 0L) return 0
        return try {
            fontsDir.mkdirs()
            src.copyTo(dest, overwrite = true)
            2
        } catch (e: Exception) {
            Log.w(TAG, "Could not install cached font ${req.family}", e)
            0
        }
    }

    /**
     * Makes the Google fonts of [assContent] available to libass.
     *
     * Fonts that were downloaded before are copied into [fontsDir] right away (synchronously, so
     * the caller can judge availability afterwards). Missing fonts are downloaded in the
     * background when the device is online; listeners are notified once they are in place.
     */
    fun ensureForScript(context: Context, assContent: String, fontsDir: File) {
        if (assContent.isBlank()) return
        val requests = try { collectFontRequests(assContent) } catch (_: Throwable) { return }
        ensureRequests(context, requests, fontsDir, notifyForCached = false)
    }

    /**
     * Embedded MKV subtitle tracks are rendered by libass directly, so their scripts are read
     * here (in the background) and the fonts they use are prepared the same way.
     */
    fun ensureForMkv(context: Context, videoPath: String, fontsDir: File) {
        val file = File(videoPath)
        if (!file.isFile || file.extension.lowercase(Locale.ROOT) !in setOf("mkv", "mka", "webm")) return
        val app = context.applicationContext
        scope.launch {
            try {
                val (_, tracks, _) = MkvManagerEngine.parseMkvFile(app, Uri.fromFile(file))
                val requests = LinkedHashSet<FontRequest>()
                tracks.filter {
                    it.trackType == MkvTrackType.SUBTITLE &&
                        (it.codecId.contains("ASS", ignoreCase = true) || it.codecId.contains("SSA", ignoreCase = true))
                }.take(8).forEach { track ->
                    val text = MkvManagerEngine.extractSubtitleTrackToString(app, Uri.fromFile(file), track)
                    if (text.isNotBlank()) requests.addAll(collectFontRequests(text))
                }
                if (requests.isNotEmpty()) ensureRequests(app, requests, fontsDir, notifyForCached = true)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not read fonts of embedded subtitles", t)
            }
        }
    }

    private fun ensureRequests(
        context: Context,
        requests: Collection<FontRequest>,
        fontsDir: File,
        notifyForCached: Boolean
    ) {
        if (requests.isEmpty()) return
        val app = context.applicationContext
        val families = requests.map { it.family }.distinct().take(MAX_FAMILIES_PER_RUN)

        var installedNow = 0
        val work = ArrayList<FontRequest>()
        for (family in families) {
            val regular = FontRequest(family, bold = false, italic = false)
            val wanted = LinkedHashSet<FontRequest>()
            wanted.add(regular)
            requests.filter { it.family == family }.forEach { wanted.add(it) }

            val regularState = installFromStore(app, regular, fontsDir)
            if (regularState == 2) installedNow++
            // A family that is already provided by the video / device is left alone.
            if (regularState == 0 && isProvidedElsewhere(app, family)) continue

            for (req in wanted) {
                if (req == regular) {
                    if (regularState == 0 && !isKnownMissing(app, req)) work.add(req)
                    continue
                }
                if (regularState == 0 && isKnownMissing(app, regular)) continue
                when (installFromStore(app, req, fontsDir)) {
                    2 -> installedNow++
                    1 -> {}
                    else -> if (!isKnownMissing(app, req)) work.add(req)
                }
            }
        }

        if (work.isEmpty() || !isOnline(app)) {
            if (notifyForCached && installedNow > 0) notifyListeners()
            return
        }

        scope.launch {
            var changed = if (notifyForCached) installedNow else 0
            for (req in work) {
                val key = req.key
                if (!inFlight.add(key)) continue
                try {
                    if (req.variant != "regular") {
                        // Bold / italic faces only make sense next to a downloaded regular face.
                        val regularFile = File(storeDir(app), fileName(FontRequest(req.family, false, false)))
                        if (!regularFile.isFile) continue
                    }
                    if (download(app, req, fontsDir)) changed++
                } finally {
                    inFlight.remove(key)
                }
            }
            if (changed > 0) notifyListeners()
        }
    }

    // ---------------------------------------------------------------------------------------
    // 3. Google Fonts CSS API + file download
    // ---------------------------------------------------------------------------------------

    private fun cssUrl(req: FontRequest): String {
        val family = URLEncoder.encode(req.family, "UTF-8") // spaces become '+'
        val axis = when (req.variant) {
            "bold" -> ":wght@700"
            "italic" -> ":ital@1"
            "bolditalic" -> ":ital,wght@1,700"
            else -> ""
        }
        return "$CSS_ENDPOINT?family=$family$axis&display=swap"
    }

    /** `src: url(https://fonts.gstatic.com/....ttf) format('truetype')` -> the file URL. */
    fun parseFontUrlFromCss(css: String): String? {
        val m = Regex("url\\(([^)]+)\\)\\s*format\\(\\s*['\"]?(?:truetype|opentype)['\"]?\\s*\\)")
            .find(css) ?: return null
        val url = m.groupValues[1].trim().trim('\'', '"')
        return url.takeIf { it.startsWith("https://") }
    }

    private fun download(context: Context, req: FontRequest, fontsDir: File): Boolean {
        val key = req.key
        val now = System.currentTimeMillis()
        retryAfter[key]?.let { if (now < it) return false }
        try {
            val cssConn = openConnection(cssUrl(req))
            val code = cssConn.responseCode
            if (code == 400 || code == 404) {
                // Not a Google font (or that face does not exist): do not ask again for a while.
                cssConn.disconnect()
                markMissing(context, req)
                return false
            }
            if (code !in 200..299) {
                cssConn.disconnect()
                retryAfter[key] = now + RETRY_COOLDOWN_MS
                return false
            }
            val css = cssConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            cssConn.disconnect()
            val fontUrl = parseFontUrlFromCss(css)
            if (fontUrl == null) {
                retryAfter[key] = now + RETRY_COOLDOWN_MS
                return false
            }

            val store = storeDir(context)
            val target = File(store, fileName(req))
            val tmp = File(store, target.name + ".part")
            val fontConn = openConnection(fontUrl)
            if (fontConn.responseCode !in 200..299) {
                fontConn.disconnect()
                retryAfter[key] = now + RETRY_COOLDOWN_MS
                return false
            }
            var total = 0L
            fontConn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FONT_BYTES) throw java.io.IOException("font too large")
                        out.write(buf, 0, n)
                    }
                }
            }
            fontConn.disconnect()
            if (!looksLikeFont(tmp)) {
                tmp.delete()
                retryAfter[key] = now + RETRY_COOLDOWN_MS
                return false
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val installed = installFromStore(context, req, fontsDir) != 0
            if (installed) Log.i(TAG, "Downloaded Google font ${req.family} (${req.variant})")
            return installed
        } catch (e: Exception) {
            Log.w(TAG, "Google font download failed for ${req.family}: ${e.message}")
            retryAfter[key] = now + RETRY_COOLDOWN_MS
            return false
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", TTF_USER_AGENT)
        return conn
    }

    private fun looksLikeFont(file: File): Boolean {
        if (!file.isFile || file.length() < 1024L) return false
        val head = ByteArray(4)
        file.inputStream().use { if (it.read(head) < 4) return false }
        val tag = String(head, Charsets.ISO_8859_1)
        return (head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()) ||
            tag == "OTTO" || tag == "true" || tag == "ttcf"
    }
}
