package com.example.ai

/**
 * Cloud AI providers selectable from Settings > Advanced > AI Features > Provider.
 * `Default` lets the user paste any OpenAI-compatible base URL/key without
 * Lumora assuming a specific vendor.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    /** OpenAI-compatible base URL used to build /models and /chat/completions calls. */
    val defaultBaseUrl: String
) {
    DEFAULT("default", "Default", ""),
    GOOGLE("google", "Google", "https://generativelanguage.googleapis.com/v1beta/openai"),
    OPENCODE("opencode", "OpenCode", "https://opencode.ai/zen/v1"),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1"),
    ANTHROPIC("anthropic", "Anthropic", "https://api.anthropic.com/v1"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
    TOGETHER("together", "Together", "https://api.together.xyz/v1");

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** A model returned by a provider's /models discovery call, or picked by hand. */
data class AiModelInfo(
    val id: String,
    val label: String = id
)

/**
 * Picks a model automatically out of a provider's discovered list, so the user isn't forced to
 * hand-pick one. "Best" here means "most likely to actually work for translation" rather than
 * "most capable": a free-tier/flash/mini-class model that responds is strictly better than a
 * flagship model the account can't afford, since an unaffordable model just fails every request
 * with HTTP 402 (Payment Required). Order matters: the first pattern that matches any model wins.
 */
object BestModelSelector {
    private val preferenceOrder: List<Regex> = listOf(
        Regex(""":free$""", RegexOption.IGNORE_CASE),   // OpenRouter free-tier variants, e.g. "...:free"
        Regex("""flash""", RegexOption.IGNORE_CASE),     // Gemini flash: fast, cheap, generous free quota
        Regex("""8b|9b|small|lite|mini""", RegexOption.IGNORE_CASE),
        Regex("""haiku""", RegexOption.IGNORE_CASE),
        Regex("""gpt-4o(?!-)|gpt-4o$""", RegexOption.IGNORE_CASE),
        Regex("""sonnet""", RegexOption.IGNORE_CASE),
        Regex("""pro""", RegexOption.IGNORE_CASE)
    )

    fun pickBest(models: List<AiModelInfo>): AiModelInfo? {
        if (models.isEmpty()) return null
        for (pattern in preferenceOrder) {
            models.firstOrNull { pattern.containsMatchIn(it.id) }?.let { return it }
        }
        return models.first()
    }
}

/** Result of tapping "Verify Key". */
sealed class ApiKeyVerificationState {
    data object Idle : ApiKeyVerificationState()
    data object Verifying : ApiKeyVerificationState()
    data class Verified(val models: List<AiModelInfo>) : ApiKeyVerificationState()
    data class Failed(val message: String) : ApiKeyVerificationState()
}

/** Per-track state shown in the subtitle track sheet while/after AI translation. */
sealed class TrackTranslationStatus {
    data object Idle : TrackTranslationStatus()
    data class Translating(val progressPercent: Int, val etaSeconds: Int? = null) : TrackTranslationStatus()
    data class Completed(val languageLabel: String, val translatedTrackId: Int) : TrackTranslationStatus()
    data class Failed(val message: String) : TrackTranslationStatus()
}

/** World-language list for "Select Translate Language". Code follows BCP-47 where practical. */
object TranslateLanguages {
    data class Entry(val code: String, val label: String)

    val ALL: List<Entry> = listOf(
        Entry("en", "English"),
        Entry("hi", "Hindi (हिन्दी)"),
        Entry("es", "Spanish (Español)"),
        Entry("fr", "French (Français)"),
        Entry("de", "German (Deutsch)"),
        Entry("it", "Italian (Italiano)"),
        Entry("pt", "Portuguese (Português)"),
        Entry("pt-BR", "Portuguese (Brasil)"),
        Entry("ru", "Russian (Русский)"),
        Entry("ja", "Japanese (日本語)"),
        Entry("ko", "Korean (한국어)"),
        Entry("zh", "Chinese (简体中文)"),
        Entry("zh-TW", "Chinese (繁體中文)"),
        Entry("ar", "Arabic (العربية)"),
        Entry("bn", "Bengali (বাংলা)"),
        Entry("ur", "Urdu (اردو)"),
        Entry("mr", "Marathi (मराठी)"),
        Entry("te", "Telugu (తెలుగు)"),
        Entry("ta", "Tamil (தமிழ்)"),
        Entry("gu", "Gujarati (ગુજરાતી)"),
        Entry("kn", "Kannada (ಕನ್ನಡ)"),
        Entry("ml", "Malayalam (മലയാളം)"),
        Entry("pa", "Punjabi (ਪੰਜਾਬੀ)"),
        Entry("ne", "Nepali (नेपाली)"),
        Entry("tr", "Turkish (Türkçe)"),
        Entry("vi", "Vietnamese (Tiếng Việt)"),
        Entry("th", "Thai (ไทย)"),
        Entry("id", "Indonesian (Bahasa Indonesia)"),
        Entry("ms", "Malay (Bahasa Melayu)"),
        Entry("fa", "Persian (فارسی)"),
        Entry("pl", "Polish (Polski)"),
        Entry("nl", "Dutch (Nederlands)"),
        Entry("sv", "Swedish (Svenska)"),
        Entry("no", "Norwegian (Norsk)"),
        Entry("da", "Danish (Dansk)"),
        Entry("fi", "Finnish (Suomi)"),
        Entry("el", "Greek (Ελληνικά)"),
        Entry("he", "Hebrew (עברית)"),
        Entry("uk", "Ukrainian (Українська)"),
        Entry("cs", "Czech (Čeština)"),
        Entry("sk", "Slovak (Slovenčina)"),
        Entry("ro", "Romanian (Română)"),
        Entry("hu", "Hungarian (Magyar)"),
        Entry("bg", "Bulgarian (Български)"),
        Entry("hr", "Croatian (Hrvatski)"),
        Entry("sr", "Serbian (Српски)"),
        Entry("sl", "Slovenian (Slovenščina)"),
        Entry("lt", "Lithuanian (Lietuvių)"),
        Entry("lv", "Latvian (Latviešu)"),
        Entry("et", "Estonian (Eesti)"),
        Entry("si", "Sinhala (සිංහල)"),
        Entry("my", "Burmese (မြန်မာ)"),
        Entry("km", "Khmer (ភាសាខ្មែរ)"),
        Entry("lo", "Lao (ພາສາລາວ)"),
        Entry("sw", "Swahili (Kiswahili)"),
        Entry("am", "Amharic (አማርኛ)"),
        Entry("af", "Afrikaans"),
        Entry("sq", "Albanian (Shqip)"),
        Entry("az", "Azerbaijani (Azərbaycan)"),
        Entry("ka", "Georgian (ქართული)"),
        Entry("hy", "Armenian (Հայերեն)"),
        Entry("kk", "Kazakh (Қазақша)"),
        Entry("uz", "Uzbek (Oʻzbek)"),
        Entry("mn", "Mongolian (Монгол)"),
        Entry("tl", "Filipino (Tagalog)"),
        Entry("is", "Icelandic (Íslenska)"),
        Entry("mt", "Maltese (Malti)"),
        Entry("cy", "Welsh (Cymraeg)"),
        Entry("ga", "Irish (Gaeilge)"),
        Entry("eu", "Basque (Euskara)"),
        Entry("ca", "Catalan (Català)"),
        Entry("gl", "Galician (Galego)")
    )
}
