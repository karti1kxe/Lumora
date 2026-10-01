package com.example.player

import android.content.Context
import com.example.ui.state.AppLanguage
import java.io.File
import java.util.Locale

/**
 * Audio channel configuration modes for MPV player and audio output.
 */
enum class AudioChannelMode(
    val id: String,
    val mpvValue: String,
    val defaultTitle: String,
    val defaultDescription: String
) {
    AUTO(
        id = "auto",
        mpvValue = "auto",
        defaultTitle = "Auto",
        defaultDescription = "Automatic audio channels configuration"
    ),
    AUTO_SAFE(
        id = "auto-safe",
        mpvValue = "auto-safe",
        defaultTitle = "Auto Safe",
        defaultDescription = "Default multi-channel passthrough with safe downmix fallback"
    ),
    MONO(
        id = "mono",
        mpvValue = "mono",
        defaultTitle = "Mono",
        defaultDescription = "Combines left and right channels centered across all speakers"
    ),
    STEREO(
        id = "stereo",
        mpvValue = "stereo",
        defaultTitle = "Stereo",
        defaultDescription = "Standard Left and Right 2-channel stereo soundstage"
    ),
    REVERSED_STEREO(
        id = "reversed-stereo",
        mpvValue = "reversed-stereo",
        defaultTitle = "Reversed Stereo",
        defaultDescription = "Swaps Left and Right audio channels"
    ),
    LEFT(
        id = "left",
        mpvValue = "left",
        defaultTitle = "Left Channel Only",
        defaultDescription = "Plays only the left channel sound through both speakers"
    ),
    RIGHT(
        id = "right",
        mpvValue = "right",
        defaultTitle = "Right Channel Only",
        defaultDescription = "Plays only the right channel sound through both speakers"
    );

    fun getDisplayName(lang: AppLanguage = AppLanguage.ENGLISH): String = when (this) {
        AUTO -> "Auto"
        AUTO_SAFE -> "Auto Safe"
        MONO -> "Mono"
        STEREO -> "Stereo"
        REVERSED_STEREO -> "Reversed Stereo"
        LEFT -> "Left Channel Only"
        RIGHT -> "Right Channel Only"
    }

    fun getDescription(lang: AppLanguage = AppLanguage.ENGLISH): String = defaultDescription
}

enum class AudioSurroundMode(
    val id: String,
    val displayName: String,
    val filterString: String
) {
    OFF("off", "Off", ""),
    HEADPHONE_SURROUND("headphone_3d", "Headphone 3D", "lavfi=[bs2b=profile=default]"),
    VIRTUAL_SURROUND("virtual_room", "Virtual Room", "lavfi=[surround=chl_out=stereo:level_in=1.0:level_out=1.0]"),
    CROSSFEED("crossfeed", "Crossfeed", "lavfi=[bs2b=fcut=700:feed=45]");
}

data class TrackAudioConfig(
    val channelMode: AudioChannelMode = AudioChannelMode.AUTO_SAFE,
    val volumeNormalization: Boolean = false,
    val dynamicRangeCompression: Boolean = false
)

/**
 * Intelligent Language normalization and matching engine.
 * Handles ISO 639-1 (2-letter), ISO 639-2/3 (3-letter), regional locales, full names,
 * and track title heuristics safely with case-insensitivity and zero crashes.
 */
object AudioLanguageMatcher {

    val POPULAR_LANGUAGES = listOf(
        "hin" to "Hindi (हिन्दी)",
        "eng" to "English",
        "jpn" to "Japanese (日本語)",
        "kor" to "Korean (한국어)",
        "spa" to "Spanish (Español)",
        "fra" to "French (Français)",
        "deu" to "German (Deutsch)",
        "tam" to "Tamil (தமிழ்)",
        "tel" to "Telugu (తెలుగు)",
        "ben" to "Bengali (বাংলা)",
        "pan" to "Punjabi (ਪੰਜਾਬੀ)",
        "mar" to "Marathi (मराठी)",
        "guj" to "Gujarati (ગુજરાતી)",
        "kan" to "Kannada (ಕನ್ನಡ)",
        "mal" to "Malayalam (മലയാളം)",
        "rus" to "Russian (Русский)",
        "ara" to "Arabic (العربية)",
        "zho" to "Chinese (中文)",
        "por" to "Portuguese (Português)",
        "ita" to "Italian (Italiano)",
        "urd" to "Urdu (اردو)",
        "tur" to "Turkish (Türkçe)",
        "vie" to "Vietnamese (Tiếng Việt)",
        "ind" to "Indonesian (Bahasa)"
    )

    private val LANGUAGE_ALIASES: Map<String, Set<String>> = mapOf(
        // Hindi
        "hin" to setOf("hin", "hi", "hi-in", "hin-in", "hindi", "hindustani", "हिन्दी"),
        "hi" to setOf("hin", "hi", "hi-in", "hin-in", "hindi", "hindustani", "हिन्दी"),
        "hindi" to setOf("hin", "hi", "hi-in", "hin-in", "hindi", "hindustani", "हिन्दी"),

        // English
        "eng" to setOf("eng", "en", "en-us", "en-gb", "en-ca", "en-au", "en-in", "english"),
        "en" to setOf("eng", "en", "en-us", "en-gb", "en-ca", "en-au", "en-in", "english"),
        "english" to setOf("eng", "en", "en-us", "en-gb", "en-ca", "en-au", "en-in", "english"),

        // Japanese
        "jpn" to setOf("jpn", "ja", "ja-jp", "japanese", "jap", "nihongo", "日本語"),
        "ja" to setOf("jpn", "ja", "ja-jp", "japanese", "jap", "nihongo", "日本語"),
        "japanese" to setOf("jpn", "ja", "ja-jp", "japanese", "jap", "nihongo", "日本語"),

        // Korean
        "kor" to setOf("kor", "ko", "ko-kr", "korean", "hangul", "한국어"),
        "ko" to setOf("kor", "ko", "ko-kr", "korean", "hangul", "한국어"),
        "korean" to setOf("kor", "ko", "ko-kr", "korean", "hangul", "한국어"),

        // Spanish
        "spa" to setOf("spa", "es", "es-es", "es-mx", "es-419", "es-la", "spanish", "español", "castellano"),
        "es" to setOf("spa", "es", "es-es", "es-mx", "es-419", "es-la", "spanish", "español", "castellano"),
        "spanish" to setOf("spa", "es", "es-es", "es-mx", "es-419", "es-la", "spanish", "español", "castellano"),

        // French
        "fra" to setOf("fra", "fre", "fr", "fr-fr", "fr-ca", "french", "français"),
        "fre" to setOf("fra", "fre", "fr", "fr-fr", "fr-ca", "french", "français"),
        "fr" to setOf("fra", "fre", "fr", "fr-fr", "fr-ca", "french", "français"),
        "french" to setOf("fra", "fre", "fr", "fr-fr", "fr-ca", "french", "français"),

        // German
        "deu" to setOf("deu", "ger", "de", "de-de", "german", "deutsch"),
        "ger" to setOf("deu", "ger", "de", "de-de", "german", "deutsch"),
        "de" to setOf("deu", "ger", "de", "de-de", "german", "deutsch"),
        "german" to setOf("deu", "ger", "de", "de-de", "german", "deutsch"),

        // Tamil
        "tam" to setOf("tam", "ta", "ta-in", "tamil", "தமிழ்"),
        "ta" to setOf("tam", "ta", "ta-in", "tamil", "தமிழ்"),
        "tamil" to setOf("tam", "ta", "ta-in", "tamil", "தமிழ்"),

        // Telugu
        "tel" to setOf("tel", "te", "te-in", "telugu", "తెలుగు"),
        "te" to setOf("tel", "te", "te-in", "telugu", "తెలుగు"),
        "telugu" to setOf("tel", "te", "te-in", "telugu", "తెలుగు"),

        // Bengali
        "ben" to setOf("ben", "bn", "bn-in", "bn-bd", "bengali", "bangla", "বাংলা"),
        "bn" to setOf("ben", "bn", "bn-in", "bn-bd", "bengali", "bangla", "বাংলা"),
        "bengali" to setOf("ben", "bn", "bn-in", "bn-bd", "bengali", "bangla", "বাংলা"),

        // Punjabi
        "pan" to setOf("pan", "pa", "pa-in", "punjabi", "ਪੰਜਾਬੀ"),
        "pa" to setOf("pan", "pa", "pa-in", "punjabi", "ਪੰਜਾਬੀ"),
        "punjabi" to setOf("pan", "pa", "pa-in", "punjabi", "ਪੰਜਾਬੀ"),

        // Marathi
        "mar" to setOf("mar", "mr", "mr-in", "marathi", "मराठी"),
        "mr" to setOf("mar", "mr", "mr-in", "marathi", "मराठी"),
        "marathi" to setOf("mar", "mr", "mr-in", "marathi", "मराठी"),

        // Gujarati
        "guj" to setOf("guj", "gu", "gu-in", "gujarati", "ગુજરાતી"),
        "gu" to setOf("guj", "gu", "gu-in", "gujarati", "ગુજરાતી"),
        "gujarati" to setOf("guj", "gu", "gu-in", "gujarati", "ગુજરાતી"),

        // Kannada
        "kan" to setOf("kan", "kn", "kn-in", "kannada", "ಕನ್ನಡ"),
        "kn" to setOf("kan", "kn", "kn-in", "kannada", "ಕನ್ನಡ"),
        "kannada" to setOf("kan", "kn", "kn-in", "kannada", "ಕನ್ನಡ"),

        // Malayalam
        "mal" to setOf("mal", "ml", "ml-in", "malayalam", "മലയാളം"),
        "ml" to setOf("mal", "ml", "ml-in", "malayalam", "മലയാളം"),
        "malayalam" to setOf("mal", "ml", "ml-in", "malayalam", "മലയാളം"),

        // Russian
        "rus" to setOf("rus", "ru", "ru-ru", "russian", "русский"),
        "ru" to setOf("rus", "ru", "ru-ru", "russian", "русский"),
        "russian" to setOf("rus", "ru", "ru-ru", "russian", "русский"),

        // Arabic
        "ara" to setOf("ara", "ar", "ar-sa", "ar-ae", "arabic", "العربية"),
        "ar" to setOf("ara", "ar", "ar-sa", "ar-ae", "arabic", "العربية"),
        "arabic" to setOf("ara", "ar", "ar-sa", "ar-ae", "arabic", "العربية"),

        // Chinese
        "zho" to setOf("zho", "chi", "zh", "zh-cn", "zh-tw", "zh-hk", "chinese", "mandarin", "cantonese", "中文"),
        "chi" to setOf("zho", "chi", "zh", "zh-cn", "zh-tw", "zh-hk", "chinese", "mandarin", "cantonese", "中文"),
        "zh" to setOf("zho", "chi", "zh", "zh-cn", "zh-tw", "zh-hk", "chinese", "mandarin", "cantonese", "中文"),
        "chinese" to setOf("zho", "chi", "zh", "zh-cn", "zh-tw", "zh-hk", "chinese", "mandarin", "cantonese", "中文"),

        // Portuguese
        "por" to setOf("por", "pt", "pt-br", "pt-pt", "portuguese", "português"),
        "pt" to setOf("por", "pt", "pt-br", "pt-pt", "portuguese", "português"),
        "portuguese" to setOf("por", "pt", "pt-br", "pt-pt", "portuguese", "português"),

        // Italian
        "ita" to setOf("ita", "it", "it-it", "italian", "italiano"),
        "it" to setOf("ita", "it", "it-it", "italian", "italiano"),
        "italian" to setOf("ita", "it", "it-it", "italian", "italiano"),

        // Urdu
        "urd" to setOf("urd", "ur", "ur-pk", "ur-in", "urdu", "اردو"),
        "ur" to setOf("urd", "ur", "ur-pk", "ur-in", "urdu", "اردو"),
        "urdu" to setOf("urd", "ur", "ur-pk", "ur-in", "urdu", "اردو"),

        // Turkish
        "tur" to setOf("tur", "tr", "turkish", "türkçe"),
        "tr" to setOf("tur", "tr", "turkish", "türkçe"),
        "turkish" to setOf("tur", "tr", "turkish", "türkçe")
    )

    /**
     * Splits comma-separated input, trims whitespace, drops empties, and converts to lowercase.
     * Example: " hin , eng, JPN , , spa " -> ["hin", "eng", "jpn", "spa"]
     */
    fun normalizePreferredLanguages(rawInput: String): List<String> {
        if (rawInput.isBlank()) return emptyList()
        return rawInput.split(',', ';', '|', '/', ' ')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Returns a human-friendly display label for a language code.
     */
    fun getLanguageDisplayName(code: String): String {
        val clean = code.trim().lowercase(Locale.ROOT)
        POPULAR_LANGUAGES.firstOrNull { it.first == clean }?.let { return it.second }
        return try {
            val loc = Locale(clean)
            val name = loc.displayLanguage
            if (!name.isNullOrBlank() && !name.equals(clean, ignoreCase = true)) {
                "${name.replaceFirstChar { it.uppercase() }} ($clean)"
            } else {
                clean.uppercase(Locale.ROOT)
            }
        } catch (_: Throwable) {
            clean.uppercase(Locale.ROOT)
        }
    }

    /**
     * Checks if a PlayerMediaTrack matches a preferred language token.
     */
    fun doesTrackMatchLanguage(track: PlayerMediaTrack, preferredToken: String): Boolean {
        val token = preferredToken.trim().lowercase(Locale.ROOT)
        if (token.isBlank()) return false

        val aliases = LANGUAGE_ALIASES[token] ?: setOf(token)
        val trackLang = track.language.trim().lowercase(Locale.ROOT)
        val trackTitle = track.title.trim().lowercase(Locale.ROOT)
        val trackFilename = track.originalFilename.trim().lowercase(Locale.ROOT)

        // 1. Direct language code or alias match
        if (trackLang.isNotBlank()) {
            if (aliases.contains(trackLang)) return true
            if (trackLang.startsWith("$token-") || trackLang.startsWith("${token}_")) return true
            // Inverse check if track language has aliases
            val trackLangAliases = LANGUAGE_ALIASES[trackLang]
            if (trackLangAliases != null && (trackLangAliases.contains(token) || trackLangAliases.any { aliases.contains(it) })) {
                return true
            }
        }

        // 2. Title token matching (e.g. "Hindi [Audio 1]", "English Stereo", "[JPN]", "Hindi")
        for (alias in aliases) {
            if (alias.length >= 2) {
                // Word boundary or bracket check
                val regex = Regex("(?i)(^|[^a-zA-Z0-9])${Regex.escape(alias)}([^a-zA-Z0-9]|\$)")
                if (regex.containsMatchIn(trackTitle) || regex.containsMatchIn(trackFilename)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Finds the best audio track based on strict user-defined priority:
     * 1. Explicit current-session manual selection (if user explicitly switched track during current playback).
     * 2. Selected folder/video restriction (if enabled and video belongs to selected paths).
     * 3. Remembered audio track (if "Remember selected audio track" is enabled and a saved selection exists).
     * 4. Global preferred languages (evaluating tokens in priority order 0..N).
     * 5. Default track / first audio track.
     */
    fun findBestAudioTrack(
        tracks: List<PlayerMediaTrack>,
        videoPath: String = "",
        preferredLanguages: String = "",
        applyToSelectedContentOnly: Boolean = false,
        selectedFolders: Set<String> = emptySet(),
        selectedVideos: Set<String> = emptySet(),
        rememberSelectedTrack: Boolean = false,
        rememberedTrackId: Int? = null,
        rememberedTrackTitle: String? = null,
        isCurrentSessionExplicit: Boolean = false,
        currentSessionTrackId: Int = -1
    ): PlayerMediaTrack? {
        if (tracks.isEmpty()) return null

        // 1. Explicit manual track selection in current session takes absolute top precedence
        if (isCurrentSessionExplicit && currentSessionTrackId > 0) {
            tracks.firstOrNull { it.id == currentSessionTrackId }?.let { return it }
        }

        // Determine if preferred languages should be evaluated for this video
        val isVideoInSelectedScope = if (applyToSelectedContentOnly && videoPath.isNotBlank()) {
            val file = File(videoPath)
            val parentPath = file.parentFile?.absolutePath ?: ""
            val matchesFolder = selectedFolders.any { sf ->
                sf.isNotBlank() && (videoPath.startsWith(sf) || parentPath.equals(sf, ignoreCase = true))
            }
            val matchesVideo = selectedVideos.any { sv ->
                sv.isNotBlank() && (sv.equals(videoPath, ignoreCase = true) || sv.equals(file.name, ignoreCase = true))
            }
            matchesFolder || matchesVideo
        } else {
            true
        }

        val tokens = normalizePreferredLanguages(preferredLanguages)

        // 2. If video is within preferred language scope, evaluate preferred language list in priority order
        if (isVideoInSelectedScope && tokens.isNotEmpty()) {
            for (token in tokens) {
                val matched = tracks.firstOrNull { doesTrackMatchLanguage(it, token) }
                if (matched != null) {
                    return matched
                }
            }
        }

        // 3. Remembered track preference (if enabled and present)
        if (rememberSelectedTrack) {
            if (rememberedTrackId != null && rememberedTrackId > 0) {
                tracks.firstOrNull { it.id == rememberedTrackId }?.let { return it }
            }
            if (!rememberedTrackTitle.isNullOrBlank()) {
                tracks.firstOrNull { it.title.equals(rememberedTrackTitle, ignoreCase = true) }?.let { return it }
            }
        }

        // 4. Default: track marked selected, or first track
        return tracks.firstOrNull { it.isSelected } ?: tracks.firstOrNull()
    }

    /**
     * Simplified helper finding the best audio track by preferred language tokens.
     */
    fun findBestAudioTrack(
        tracks: List<PlayerMediaTrack>,
        preferredLanguages: String
    ): PlayerMediaTrack? {
        return findBestAudioTrack(
            tracks = tracks,
            videoPath = "",
            preferredLanguages = preferredLanguages,
            applyToSelectedContentOnly = false,
            selectedFolders = emptySet(),
            selectedVideos = emptySet(),
            rememberSelectedTrack = false,
            rememberedTrackId = null,
            rememberedTrackTitle = null,
            isCurrentSessionExplicit = false,
            currentSessionTrackId = -1
        )
    }
}

/**
 * 5-Band Equalizer Presets for real-time sound sculpting:
 * 1. 60 Hz
 * 2. 230 Hz
 * 3. 910 Hz
 * 4. 3.6 kHz
 * 5. 14 kHz
 */
enum class EqualizerPreset(
    val id: String,
    val displayName: String,
    val gains: List<Float>
) {
    FLAT("Flat", "Flat", listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
    ROCK("Rock", "Rock", listOf(4.5f, 2.5f, -0.5f, 2.0f, 4.0f)),
    POP("Pop", "Pop", listOf(-1.0f, 2.0f, 4.0f, 2.0f, -1.0f)),
    JAZZ("Jazz", "Jazz", listOf(3.5f, 2.0f, -1.5f, 1.5f, 3.0f)),
    CLASSICAL("Classical", "Classical", listOf(4.0f, 2.5f, -1.0f, 2.0f, 3.5f)),
    ELECTRONIC("Electronic", "Electronic", listOf(4.0f, 3.0f, 0.0f, 2.0f, 4.5f)),
    BASS_BOOST("Bass Boost", "Bass Boost", listOf(6.0f, 4.0f, 1.0f, 0.0f, 0.0f)),
    TREBLE_BOOST("Treble Boost", "Treble Boost", listOf(0.0f, 0.0f, 1.0f, 4.0f, 6.0f)),
    VOICE_BOOST("Voice Boost", "Voice Boost", listOf(-2.0f, 1.0f, 5.0f, 3.5f, 0.0f)),
    LOUDNESS("Loudness", "Loudness", listOf(5.0f, 2.0f, -1.0f, 2.0f, 4.0f)),
    CINEMA_3D("Cinema 3D", "Cinema 3D", listOf(5.5f, 1.5f, 2.0f, 3.0f, 5.0f)),
    DEEP_CLUB_BASS("Deep Bass", "Deep Bass", listOf(7.5f, 5.0f, 0.0f, 1.5f, 3.0f)),
    VOCAL_CLARITY("Vocal Clarity", "Vocal Clarity", listOf(-3.0f, 0.5f, 6.0f, 4.0f, 1.5f)),
    ACOUSTIC("Acoustic", "Acoustic", listOf(3.0f, 4.0f, 2.5f, 1.0f, 2.5f)),
    HIFI_MASTER("Hi-Fi Master", "Hi-Fi Master", listOf(2.5f, -1.0f, 1.0f, 2.0f, 4.0f)),
    NIGHT_DIALOG("Night Dialog", "Night Dialog", listOf(-4.0f, -1.0f, 4.5f, 2.0f, -2.0f));

    companion object {
        fun matchPreset(
            eq60: Float,
            eq230: Float,
            eq910: Float,
            eq3600: Float,
            eq14000: Float
        ): EqualizerPreset? {
            return entries.firstOrNull { preset ->
                kotlin.math.abs(preset.gains[0] - eq60) < 0.2f &&
                        kotlin.math.abs(preset.gains[1] - eq230) < 0.2f &&
                        kotlin.math.abs(preset.gains[2] - eq910) < 0.2f &&
                        kotlin.math.abs(preset.gains[3] - eq3600) < 0.2f &&
                        kotlin.math.abs(preset.gains[4] - eq14000) < 0.2f
            }
        }
    }
}

