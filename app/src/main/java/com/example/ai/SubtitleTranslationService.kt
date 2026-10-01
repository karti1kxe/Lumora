package com.example.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Format-preserving subtitle translation pipeline.
 *
 * The parser never serializes a subtitle through a generic SRT converter. It records the
 * exact source document and replaces only the human-readable cue payloads, so timing,
 * styles, margins, positioning and format-specific metadata remain byte-for-byte intact.
 */
object SubtitleTranslationService {

    data class TranslationProgress(val completed: Int, val total: Int)

    data class ResultFile(
        val file: File,
        val formatName: String,
        val translatedEntries: Int
    )

    private data class Entry(
        val id: Int,
        val original: String,
        val dialogue: String,
        val replace: (String) -> String
    )

    private data class ProtectedText(val promptText: String, val restore: (String) -> String)

    suspend fun translateFile(
        context: Context,
        sourceFile: File,
        destinationFile: File,
        settings: AiFeaturesSettings,
        onProgress: (TranslationProgress) -> Unit = {}
    ): Result<ResultFile> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Subtitle file is missing or empty"))
        }
        if (sourceFile.absolutePath == destinationFile.absolutePath) {
            return@withContext Result.failure(IllegalArgumentException("Original subtitle must not be overwritten"))
        }

        val original = sourceFile.readText(Charsets.UTF_8)
        val entries = parseEntries(original, sourceFile.extension.lowercase(Locale.US))
        if (entries.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No translatable subtitle dialogue was found"))
        }

        val target = TranslateLanguages.ALL.firstOrNull { it.code == settings.translateLanguageCode }
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported target language"))
        val source = TranslateLanguages.ALL.firstOrNull { it.code == settings.sourceLanguageCode }

        val translated = Array<String?>(entries.size) { null }
        val batchSize = 8
        var completed = 0

        entries.chunked(batchSize).forEach { batch ->
            currentCoroutineContext().ensureActive()
            val protected = batch.map { protectFormatting(it.dialogue) }
            val requestPayload = JSONArray()
            protected.forEachIndexed { index, item ->
                requestPayload.put(
                    JSONObject()
                        .put("id", batch[index].id)
                        .put("text", item.promptText)
                )
            }

            val key = AiFeaturesSettingsStore.readApiKey(context)
            val result = AiProviderService.translateStructured(
                provider = settings.provider,
                apiKey = key,
                modelId = settings.selectedModelId,
                customBaseUrl = settings.customBaseUrl,
                sourceLanguageLabel = source?.label ?: "auto-detect",
                targetLanguageLabel = target.label,
                items = requestPayload,
                customPrompt = settings.customPrompt
            )

            val map = result.getOrElse { return@withContext Result.failure(it) }
            if (map.size != batch.size || batch.any { !map.containsKey(it.id) }) {
                return@withContext Result.failure(IllegalStateException("Translation response did not preserve subtitle entry mapping"))
            }

            batch.forEachIndexed { localIndex, entry ->
                val raw = map[entry.id] ?: ""
                val restored = protected[localIndex].restore(raw)
                if (restored.isBlank() && entry.dialogue.isNotBlank()) {
                    return@withContext Result.failure(IllegalStateException("Translator returned an empty subtitle cue"))
                }
                if (!validateProtectedTokens(protected[localIndex].promptText, raw)) {
                    return@withContext Result.failure(IllegalStateException("Translator modified protected subtitle formatting"))
                }
                translated[entry.id] = restored
            }
            completed += batch.size
            onProgress(TranslationProgress(completed, entries.size))
        }

        var output = original
        var cursor = 0
        // Apply replacements in source order. This matters when two cues contain identical
        // dialogue: a global String.replace would otherwise translate the wrong occurrence.
        entries.forEach { entry ->
            val replacement = translated[entry.id] ?: entry.dialogue
            val at = output.indexOf(entry.original, cursor)
            if (at < 0) {
                return@withContext Result.failure(IllegalStateException("Could not map translated subtitle entry ${entry.id} back to the source"))
            }
            output = output.substring(0, at) + replacement + output.substring(at + entry.original.length)
            cursor = at + replacement.length
        }

        destinationFile.parentFile?.mkdirs()
        destinationFile.writeText(output, Charsets.UTF_8)

        Result.success(
            ResultFile(
                file = destinationFile,
                formatName = sourceFile.extension.uppercase(Locale.US),
                translatedEntries = entries.size
            )
        )
    }

    fun validateTranslatedSubtitle(source: File, translated: File): Result<Unit> {
        if (!translated.exists() || translated.length() == 0L) {
            return Result.failure(IllegalStateException("Translated subtitle file is empty"))
        }
        val sourceText = source.readText(Charsets.UTF_8)
        val translatedText = translated.readText(Charsets.UTF_8)
        val sourceEntries = parseEntries(sourceText, source.extension.lowercase(Locale.US))
        val translatedEntries = parseEntries(translatedText, translated.extension.lowercase(Locale.US))
        if (sourceEntries.size != translatedEntries.size) {
            return Result.failure(IllegalStateException("Subtitle entry count changed"))
        }
        sourceEntries.zip(translatedEntries).forEach { (a, b) ->
            if (a.id != b.id) return Result.failure(IllegalStateException("Subtitle entry mapping changed"))
        }
        if (timingAndStructureSignature(sourceText, source.extension) !=
            timingAndStructureSignature(translatedText, translated.extension)) {
            return Result.failure(IllegalStateException("Subtitle timing or structural metadata changed"))
        }
        return Result.success(Unit)
    }

    private fun timingAndStructureSignature(content: String, extension: String): List<String> {
        val ext = extension.lowercase(Locale.US)
        return when (ext) {
            "ass", "ssa" -> content.lineSequence()
                .filter { it.trimStart().startsWith("Dialogue:", true) || it.trimStart().startsWith("Comment:", true) }
                .map { it.substringBeforeLast(',') }
                .toList()
            "smi" -> Regex("""(?is)<SYNC\b[^>]*>""").findAll(content).map { it.value.lowercase() }.toList()
            "ttml", "dfxp", "xml" -> Regex("""(?is)<p\b[^>]*(?:begin|end|dur)=[^>]*>""").findAll(content).map { it.value.lowercase() }.toList()
            "lrc" -> content.lineSequence().mapNotNull {
                Regex("""^(?:\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\])+""").find(it)?.value
            }.toList()
            "sub" -> content.lineSequence().mapNotNull {
                Regex("""^\{\d+\}\{\d+\}""").find(it)?.value
            }.toList()
            else -> Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}""")
                .findAll(content).map { it.value.replace(Regex("""\s+"""), " ").trim() }.toList()
        }
    }

    private fun parseEntries(content: String, extension: String): List<Entry> = when (extension) {
        "ass", "ssa" -> parseAss(content)
        "vtt" -> parseVtt(content)
        "smi" -> parseSmi(content)
        "ttml", "dfxp", "xml" -> parseTtml(content)
        "lrc" -> parseLrc(content)
        "sub" -> parseMicroDvd(content)
        "sbv" -> parseSbv(content)
        "stl", "txt" -> parseGenericText(content)
        else -> parseSrt(content)
    }

    private fun parseSrt(content: String): List<Entry> {
        val blocks = Regex("""(?s)(?:^|\r?\n\r?\n)(.+?)(?=\r?\n\r?\n|\z)""")
            .findAll(content).map { it.groupValues[1] }.toList()
        val result = mutableListOf<Entry>()
        blocks.forEachIndexed { index, block ->
            val lines = block.split("\n", "\r\n").toMutableList()
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex >= 0 && timingIndex + 1 < lines.size) {
                val text = lines.subList(timingIndex + 1, lines.size).joinToString("\n")
                if (text.isNotBlank()) {
                    result += Entry(index, block, text) { translated ->
                        lines.subList(timingIndex + 1, lines.size).clear()
                        lines.addAll(translated.split("\n"))
                        lines.joinToString("\n")
                    }
                }
            }
        }
        return result
    }

    private fun parseVtt(content: String): List<Entry> {
        val blocks = content.split(Regex("""\r?\n\r?\n+"""))
        val result = mutableListOf<Entry>()
        blocks.forEach { block ->
            val lines = block.split("\n").toMutableList()
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex >= 0 && timingIndex + 1 < lines.size) {
                // The first line after the timing line is cue text; VTT cue settings remain
                // on the timing line and therefore cannot be translated.
                val text = lines.subList(timingIndex + 1, lines.size).joinToString("\n")
                if (text.isNotBlank()) {
                    val id = result.size
                    result += Entry(id, block, text) { translated ->
                        lines.subList(timingIndex + 1, lines.size).clear()
                        lines.addAll(translated.split("\n"))
                        lines.joinToString("\n")
                    }
                }
            }
        }
        return result
    }

    private fun parseAss(content: String): List<Entry> {
        val result = mutableListOf<Entry>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("Dialogue:", true) || trimmed.startsWith("Comment:", true)) {
                val colon = line.indexOf(':')
                val prefix = line.substring(0, colon + 1)
                val payload = line.substring(colon + 1)
                val commaPositions = payload.withIndex().filter { it.value == ',' }.map { it.index }
                if (commaPositions.size >= 9) {
                    val textStart = commaPositions[8] + 1
                    val dialogue = payload.substring(textStart)
                    if (dialogue.isNotBlank()) {
                        val original = line
                        val id = result.size
                        result += Entry(id, original, dialogue) { translated ->
                            prefix + payload.substring(0, textStart) + translated
                        }
                    }
                }
            }
        }
        return result
    }

    private fun parseSmi(content: String): List<Entry> {
        val result = mutableListOf<Entry>()
        val regex = Regex("""(?is)(<SYNC\b[^>]*>.*?<P\b[^>]*>)(.*?)(?=<SYNC\b|\z)""")
        regex.findAll(content).forEach { match ->
            val body = match.groupValues[2]
            val dialogue = body.replace(Regex("""(?is)<br\s*/?>"""), "\n").trim()
            if (dialogue.isNotBlank() && !dialogue.equals("&nbsp;", true)) {
                val id = result.size
                val original = match.value
                result += Entry(id, original, dialogue) { translated ->
                    match.groupValues[1] + translated.replace("\n", "<br>")
                }
            }
        }
        return result
    }

    private fun parseTtml(content: String): List<Entry> {
        val result = mutableListOf<Entry>()
        val regex = Regex("""(?is)(<p\b[^>]*>)(.*?)(</p\s*>)""")
        regex.findAll(content).forEach { match ->
            val body = match.groupValues[2]
            val dialogue = body.replace(Regex("""(?is)<br\s*/?>"""), "\n").replace(Regex("""<[^>]+>"""), "").trim()
            if (dialogue.isNotBlank()) {
                val id = result.size
                val original = match.value
                result += Entry(id, original, dialogue) { translated ->
                    match.groupValues[1] + translated.replace("\n", "<br/>") + match.groupValues[3]
                }
            }
        }
        return result
    }

    private fun parseLrc(content: String): List<Entry> {
        val result = mutableListOf<Entry>()
        content.lineSequence().forEach { line ->
            val match = Regex("""^((?:\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\])+)(.*)$""").matchEntire(line) ?: return@forEach
            val text = match.groupValues[2].trim()
            if (text.isBlank()) return@forEach
            val id = result.size
            result += Entry(id, line, text) { translated -> match.groupValues[1] + translated }
        }
        return result
    }

    private fun parseMicroDvd(content: String): List<Entry> {
        val result = mutableListOf<Entry>()
        content.lineSequence().forEach { line ->
            val match = Regex("""^(\{\d+\}\{\d+\})(.*)$""").matchEntire(line) ?: return@forEach
            val text = match.groupValues[2].replace("|", "\n")
            if (text.isBlank()) return@forEach
            val id = result.size
            result += Entry(id, line, text) { translated -> match.groupValues[1] + translated.replace("\n", "|") }
        }
        return result
    }

    private fun parseSbv(content: String): List<Entry> {
        val blocks = content.split(Regex("""\r?\n\r?\n+"""))
        val result = mutableListOf<Entry>()
        blocks.forEach { block ->
            val lines = block.split("\n").toMutableList()
            if (lines.firstOrNull()?.contains(",") == true && lines.size > 1) {
                val text = lines.drop(1).joinToString("\n")
                if (text.isNotBlank()) {
                    val id = result.size
                    result += Entry(id, block, text) { translated ->
                        lines.first() + "\n" + translated
                    }
                }
            }
        }
        return result
    }

    private fun parseGenericText(content: String): List<Entry> =
        content.lineSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            Entry(index, line, line) { it }
        }.toList()

    private fun protectFormatting(text: String): ProtectedText {
        val tokens = mutableListOf<String>()
        val pattern = Regex("""(\{\\[^}]*\}|\{[a-zA-Z]:[^}]*\}|<[^>]+>)""")
        val prompt = pattern.replace(text) {
            val token = "⟦FMT_${tokens.size}⟧"
            tokens += it.value
            token
        }
        return ProtectedText(prompt) { translated ->
            tokens.foldIndexed(translated) { index, acc, original ->
                acc.replace("⟦FMT_$index⟧", original)
            }
        }
    }

    private fun validateProtectedTokens(originalPrompt: String, translated: String): Boolean {
        val expected = Regex("""⟦FMT_\d+⟧""").findAll(originalPrompt).map { it.value }.toList()
        val actual = Regex("""⟦FMT_\d+⟧""").findAll(translated).map { it.value }.toList()
        return expected == actual
    }

    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
