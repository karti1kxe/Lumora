package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to whichever [AiProvider] the user configured. Every provider except Anthropic exposes
 * an OpenAI-compatible surface (`GET /models`, `POST /chat/completions`), so one code path covers
 * Google/OpenCode/DeepSeek/Groq/OpenAI/OpenRouter/Together/Default(custom URL); Anthropic gets its
 * own request/response shapes since it does not speak the OpenAI wire format.
 *
 * The same provider path is used by the production subtitle translation pipeline. Subtitle
 * formatting and structural validation are handled by SubtitleTranslationService, not by the
 * provider-specific transport layer.
 */
object AiProviderService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(provider: AiProvider, customBaseUrl: String): String =
        if (provider == AiProvider.DEFAULT) customBaseUrl.trimEnd('/') else provider.defaultBaseUrl.trimEnd('/')

    /**
     * Turns a raw HTTP status from a provider into a message that actually tells the user what
     * to do next. Providers speak plain HTTP here, so the numbers are meaningful across all of
     * them (OpenAI-compatible surfaces and Anthropic alike).
     */
    private fun friendlyHttpError(code: Int, message: String, modelId: String): String = when (code) {
        402 -> "Payment required (HTTP 402): this account has no credit/balance left for \"$modelId\", or this model requires a paid plan. Add credits, or pick a free/cheaper model (see Auto-select Best Model)."
        401 -> "Unauthorized (HTTP 401): the API key is invalid, expired, or was rejected. Re-check the key and tap Verify Key again."
        403 -> "Forbidden (HTTP 403): the key doesn't have access to \"$modelId\" in this region/plan. Try a different model."
        404 -> "Not found (HTTP 404): \"$modelId\" doesn't exist for this provider anymore. Re-verify the key to refresh the model list."
        429 -> "Rate limited (HTTP 429): too many requests or the free quota for \"$modelId\" is used up for now. Wait a bit or switch models."
        in 500..599 -> "Server error (HTTP $code): the provider is having issues right now. Try again shortly."
        else -> "HTTP $code${if (message.isNotBlank()) ": $message" else ""}"
    }

    /** Calls the provider's model-list endpoint and returns discovered model ids. */
    suspend fun verifyKeyAndListModels(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String = ""
    ): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API key is empty"))
        val base = baseUrl(provider, customBaseUrl)
        if (base.isBlank()) return@withContext Result.failure(IllegalArgumentException("Base URL is empty"))

        try {
            val request = if (provider == AiProvider.ANTHROPIC) {
                Request.Builder()
                    .url("$base/models")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .get()
                    .build()
            } else {
                Request.Builder()
                    .url("$base/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
            }

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(friendlyHttpError(response.code, response.message, "this provider"))
                    )
                }
                val models = parseModelsResponse(bodyStr)
                if (models.isEmpty()) {
                    Result.failure(Exception("Verified, but no models were returned"))
                } else {
                    Result.success(models)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    sealed class AccountBalance {
        data class DeepSeek(
            val totalBalance: String,
            val currency: String,
            val progress: Float
        ) : AccountBalance()

        data class OpenRouter(
            val remaining: String,
            val progress: Float
        ) : AccountBalance()

        data class Unsupported(val website: String) : AccountBalance()
    }

    /**
     * Checks account balance/credits for providers that expose a public balance endpoint (DeepSeek, OpenRouter).
     * For other providers, returns Unsupported with their web dashboard URL.
     */
    suspend fun fetchAccountBalance(
        provider: AiProvider,
        apiKey: String
    ): Result<AccountBalance> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API key is empty"))

        when (provider) {
            AiProvider.DEEPSEEK -> {
                try {
                    val request = Request.Builder()
                        .url("https://api.deepseek.com/user/balance")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(Exception("HTTP ${response.code}"))
                        }
                        val root = JSONObject(bodyStr)
                        val arr = root.optJSONArray("balance_infos")
                        if (arr != null && arr.length() > 0) {
                            val info = arr.getJSONObject(0)
                            val totalBalance = info.optString("total_balance", "0")
                            val currency = info.optString("currency", "")
                            val granted = info.optString("granted_balance", "0").toDoubleOrNull() ?: 0.0
                            val toppedUp = info.optString("topped_up_balance", "0").toDoubleOrNull() ?: 0.0
                            val total = totalBalance.toDoubleOrNull() ?: 0.0
                            val denom = granted + toppedUp
                            val progress = if (denom > 0.0) {
                                (total / denom).toFloat().coerceIn(0f, 1f)
                            } else if (total > 0.0) {
                                1f
                            } else {
                                0f
                            }
                            Result.success(
                                AccountBalance.DeepSeek(
                                    totalBalance = totalBalance,
                                    currency = currency,
                                    progress = progress
                                )
                            )
                        } else {
                            Result.failure(Exception("No balance information found"))
                        }
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            AiProvider.OPENROUTER -> {
                try {
                    val request = Request.Builder()
                        .url("https://openrouter.ai/api/v1/credits")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(Exception("HTTP ${response.code}"))
                        }
                        val root = JSONObject(bodyStr)
                        val dataObj = root.optJSONObject("data")
                        if (dataObj != null) {
                            val totalCredits = dataObj.optDouble("total_credits", 0.0)
                            val totalUsage = dataObj.optDouble("total_usage", 0.0)
                            val remaining = totalCredits - totalUsage
                            val progress = if (totalCredits > 0.0) {
                                (totalUsage / totalCredits).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val remainingStr = String.format(java.util.Locale.US, "%.2f", remaining)
                            Result.success(
                                AccountBalance.OpenRouter(
                                    remaining = remainingStr,
                                    progress = progress
                                )
                            )
                        } else {
                            Result.failure(Exception("No data object in credits response"))
                        }
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            AiProvider.OPENAI -> Result.success(AccountBalance.Unsupported("platform.openai.com"))
            AiProvider.ANTHROPIC -> Result.success(AccountBalance.Unsupported("console.anthropic.com"))
            AiProvider.GOOGLE -> Result.success(AccountBalance.Unsupported("aistudio.google.com/apikey"))
            AiProvider.GROQ -> Result.success(AccountBalance.Unsupported("console.groq.com"))
            AiProvider.TOGETHER -> Result.success(AccountBalance.Unsupported("api.together.ai"))
            AiProvider.OPENCODE -> Result.success(AccountBalance.Unsupported("opencode.ai"))
            AiProvider.DEFAULT -> Result.success(AccountBalance.Unsupported("provider dashboard"))
        }
    }

    private fun parseModelsResponse(bodyStr: String): List<AiModelInfo> {
        return try {
            val root = JSONObject(bodyStr)
            val arr: JSONArray = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val entry = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = entry.optString("id").ifBlank { entry.optString("name") }
                if (id.isBlank()) null else AiModelInfo(id = id, label = id)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Structured subtitle translation. The model sees only dialogue payloads plus opaque
     * formatting tokens. Timestamps, cue IDs and subtitle syntax never enter the translation
     * surface, and the response is accepted only when every ID is returned exactly once.
     */
    suspend fun translateStructured(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        customBaseUrl: String,
        sourceLanguageLabel: String,
        targetLanguageLabel: String,
        items: JSONArray,
        customPrompt: String
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || modelId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Provider/API key/model not configured"))
        }
        val base = baseUrl(provider, customBaseUrl)
        if (base.isBlank()) return@withContext Result.failure(IllegalArgumentException("Base URL is empty"))

        val system = buildString {
            append("You are Lumora's subtitle translation engine. ")
            append("Translate dialogue from ").append(sourceLanguageLabel).append(" to ")
                .append(targetLanguageLabel).append(". ")
            append("Use the surrounding subtitle entries as context, but return one result per input ID. ")
            append("Preserve meaning, speaker identity, tone, profanity level, names, numbers, URLs, emojis and line breaks. ")
            append("Do not add explanations. Do not summarize or merge cues. ")
            append("Never alter tokens matching ⟦FMT_0⟧, ⟦FMT_1⟧, etc.; they are subtitle formatting/control tags and must be returned exactly. ")
            append("Return ONLY valid JSON in this exact shape: {\"translations\":[{\"id\":0,\"text\":\"...\"}]}. ")
            if (customPrompt.isNotBlank()) {
                append(" Additional user translation rules, which apply to dialogue only: ")
                append(customPrompt.trim())
            }
        }

        suspend fun request(systemPrompt: String): Result<Map<Int, String>> = try {
            val bodyStr = if (provider == AiProvider.ANTHROPIC) {
                val payload = JSONObject().apply {
                    put("model", modelId)
                    put("max_tokens", 8192)
                    put("temperature", 0.2)
                    put("system", systemPrompt)
                    put("messages", JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "Translate this subtitle batch:\n$items")
                    ))
                }
                val request = Request.Builder()
                    .url("$base/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw Exception(friendlyHttpError(resp.code, resp.message, modelId))
                    JSONObject(text).optJSONArray("content")
                        ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("text") }.joinToString("") }
                        .orEmpty()
                }
            } else {
                val payload = JSONObject().apply {
                    put("model", modelId)
                    put("temperature", 0.2)
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", "Translate this subtitle batch:\n$items"))
                    )
                }
                val request = Request.Builder()
                    .url("$base/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw Exception(friendlyHttpError(resp.code, resp.message, modelId))
                    JSONObject(text).optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                }
            }
            parseStructuredResponse(bodyStr, items)
        } catch (e: Exception) {
            Result.failure(e)
        }

        val first = request(system)
        if (first.isSuccess) return@withContext first

        // One deterministic repair attempt. We do not silently accept malformed output.
        val repair = system +
            " This is a repair attempt. Be especially strict: output JSON only, return every input ID exactly once, and do not include markdown fences."
        request(repair)
    }

    private fun parseStructuredResponse(raw: String, input: JSONArray): Result<Map<Int, String>> {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        return try {
            val root = JSONObject(cleaned)
            val arr = root.optJSONArray("translations")
                ?: return Result.failure(IllegalStateException("Missing translations array"))
            val expectedIds = (0 until input.length()).map { input.optJSONObject(it).optInt("id", -1) }.toSet()
            val result = linkedMapOf<Int, String>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: return Result.failure(IllegalStateException("Invalid translation item"))
                val id = item.optInt("id", -1)
                val text = item.optString("text", "")
                if (id !in expectedIds || id in result || text.isEmpty()) {
                    return Result.failure(IllegalStateException("Invalid or duplicate translation id"))
                }
                result[id] = text
            }
            if (result.keys != expectedIds) {
                return Result.failure(IllegalStateException("Missing or extra subtitle translations"))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Malformed structured translation response", e))
        }
    }

    suspend fun translateText(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        customBaseUrl: String,
        text: String,
        targetLanguageLabel: String,
        customPrompt: String
    ): Result<String> {
        val items = JSONArray().put(JSONObject().put("id", 0).put("text", text))
        return translateStructured(
            provider, apiKey, modelId, customBaseUrl, "auto-detect", targetLanguageLabel, items, customPrompt
        ).map { it[0].orEmpty() }
    }

}
