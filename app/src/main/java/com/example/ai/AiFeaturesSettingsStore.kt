package com.example.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * All persisted state for Settings > Advanced > AI Features.
 * Kept as its own SharedPreferences file (separate from the main PlayerSettings blob)
 * so this feature can't accidentally corrupt/collide with the existing settings JSON,
 * and so it is intentionally left out of Export/Import Settings XML (the API key must
 * never be written into a shareable XML file).
 */
data class AiFeaturesSettings(
    val aiFeaturesEnabled: Boolean = false,
    val providerId: String = AiProvider.DEFAULT.id,
    val customBaseUrl: String = "",
    val hasApiKey: Boolean = false,
    val selectedModelId: String = "",
    val discoveredModelIds: List<String> = emptyList(),
    val autoSelectBestModel: Boolean = true,

    val subFeaturesEnabled: Boolean = false,
    val subtitleTranslateEnabled: Boolean = false,
    val bulkRenameEnabled: Boolean = false,
    val sourceLanguageCode: String = "auto",
    val translateLanguageCode: String = "hi",
    val customPrompt: String = ""
) {
    val provider: AiProvider get() = AiProvider.fromId(providerId)

    /** Translate is only allowed once a cloud model is actually selected and API key is present. */
    val hasUsableModel: Boolean
        get() = selectedModelId.isNotBlank() && hasApiKey
}

object AiFeaturesSettingsStore {
    private const val PREF = "lumora_ai_features_settings"

    private object Keys {
        const val AI_ENABLED = "ai_features_enabled"
        const val PROVIDER_ID = "provider_id"
        const val CUSTOM_BASE_URL = "custom_base_url"
        const val API_KEY_ENC = "api_key_enc"
        const val SELECTED_MODEL = "selected_model_id"
        const val DISCOVERED_MODELS = "discovered_model_ids"
        const val AUTO_SELECT_BEST_MODEL = "auto_select_best_model"

        const val SUB_FEATURES_ENABLED = "sub_features_enabled"
        const val SUBTITLE_TRANSLATE = "subtitle_translate_enabled"
        const val BULK_RENAME = "bulk_rename_enabled"
        const val SOURCE_LANG = "source_language_code"
        const val TRANSLATE_LANG = "translate_language_code"
        const val CUSTOM_PROMPT = "custom_prompt"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun load(context: Context): AiFeaturesSettings {
        val p = prefs(context)
        val encKey = p.getString(Keys.API_KEY_ENC, null)
        return AiFeaturesSettings(
            aiFeaturesEnabled = p.getBoolean(Keys.AI_ENABLED, false),
            providerId = p.getString(Keys.PROVIDER_ID, AiProvider.DEFAULT.id) ?: AiProvider.DEFAULT.id,
            customBaseUrl = p.getString(Keys.CUSTOM_BASE_URL, "") ?: "",
            hasApiKey = !encKey.isNullOrBlank(),
            selectedModelId = p.getString(Keys.SELECTED_MODEL, "") ?: "",
            discoveredModelIds = (p.getString(Keys.DISCOVERED_MODELS, "") ?: "")
                .split("\u0001").filter { it.isNotBlank() },
            autoSelectBestModel = p.getBoolean(Keys.AUTO_SELECT_BEST_MODEL, true),
            subFeaturesEnabled = p.getBoolean(Keys.SUB_FEATURES_ENABLED, false),
            subtitleTranslateEnabled = p.getBoolean(Keys.SUBTITLE_TRANSLATE, false),
            bulkRenameEnabled = p.getBoolean(Keys.BULK_RENAME, false),
            sourceLanguageCode = p.getString(Keys.SOURCE_LANG, "auto") ?: "auto",
            translateLanguageCode = p.getString(Keys.TRANSLATE_LANG, "hi") ?: "hi",
            customPrompt = p.getString(Keys.CUSTOM_PROMPT, "") ?: ""
        )
    }

    fun save(context: Context, settings: AiFeaturesSettings) {
        prefs(context).edit().apply {
            putBoolean(Keys.AI_ENABLED, settings.aiFeaturesEnabled)
            putString(Keys.PROVIDER_ID, settings.providerId)
            putString(Keys.CUSTOM_BASE_URL, settings.customBaseUrl)
            putString(Keys.SELECTED_MODEL, settings.selectedModelId)
            putString(Keys.DISCOVERED_MODELS, settings.discoveredModelIds.joinToString("\u0001"))
            putBoolean(Keys.AUTO_SELECT_BEST_MODEL, settings.autoSelectBestModel)
            putBoolean(Keys.SUB_FEATURES_ENABLED, settings.subFeaturesEnabled)
            putBoolean(Keys.SUBTITLE_TRANSLATE, settings.subtitleTranslateEnabled)
            putBoolean(Keys.BULK_RENAME, settings.bulkRenameEnabled)
            putString(Keys.SOURCE_LANG, settings.sourceLanguageCode)
            putString(Keys.TRANSLATE_LANG, settings.translateLanguageCode)
            putString(Keys.CUSTOM_PROMPT, settings.customPrompt)
            apply()
        }
    }

    /** API key is stored separately (encrypted) so callers can update it without re-saving everything. */
    fun saveApiKey(context: Context, plainApiKey: String) {
        val encrypted = ApiKeyCrypto.encrypt(plainApiKey)
        prefs(context).edit().putString(Keys.API_KEY_ENC, encrypted).apply()
    }

    fun readApiKey(context: Context): String =
        ApiKeyCrypto.decrypt(prefs(context).getString(Keys.API_KEY_ENC, null))

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(Keys.API_KEY_ENC).apply()
    }
}
