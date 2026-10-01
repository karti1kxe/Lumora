package com.example.util

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing a permanently attached user-uploaded subtitle.
 */
data class AttachedSubtitleRecord(
    val id: String,
    val filePath: String,
    val originalName: String,
    val isSelected: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis(),
    /** Where the user picked the file from (file path). Used to pick up later edits of the original. */
    val sourcePath: String = "",
    /** Same, when the file was picked through the system picker (content:// uri). */
    val sourceUri: String = ""
)

/**
 * A remembered subtitle choice for ONE video. [kind] is [CustomSubtitlePersistenceManager.KIND_EMBEDDED]
 * (built into the video file) or [CustomSubtitlePersistenceManager.KIND_UPLOADED] (external file).
 * The track is identified by name/language/index, never by mpv's numeric id (ids change per load).
 */
data class SavedSubtitleRef(
    val kind: String,
    val name: String,
    val language: String = "",
    val index: Int = -1
)

/**
 * The remembered subtitle selection of ONE video: the main subtitle and the 2nd (top) subtitle.
 * A null side means "nothing selected there" (the user really chose that).
 */
data class VideoSubtitleSelection(
    val primary: SavedSubtitleRef?,
    val secondary: SavedSubtitleRef?
)

/**
 * CustomSubtitlePersistenceManager
 *
 * Implements "Persistent Memory for User-Uploaded Subtitle Files (Do Not Auto-Remove)":
 * - When a user uploads a custom subtitle file from their device for a specific video/episode,
 *   this custom subtitle remains attached to that specific episode permanently.
 * - If the user moves to the next episode and returns to the episode where they uploaded
 *   the custom subtitle, the uploaded subtitle file is restored and remains attached.
 * - The custom subtitle is ONLY removed if the user explicitly swipes to remove it or manually
 *   deletes it. The system never removes user-uploaded subtitles on its own during navigation or playback.
 */
object CustomSubtitlePersistenceManager {

    private const val PREFS_NAME = "custom_subtitles_persistence_v1"
    private const val KEY_PREFIX = "subs_"
    private const val ATTACHED_DIR_NAME = "user_attached_subtitles"
    private const val SELECTION_KEY_PREFIX = "sel_"

    const val KIND_EMBEDDED = "embedded"
    const val KIND_UPLOADED = "uploaded"

    // Fast in-memory cache to avoid repeated disk reads
    private val memoryCache = ConcurrentHashMap<String, List<AttachedSubtitleRecord>>()
    private val selectionCache = ConcurrentHashMap<String, VideoSubtitleSelection>()

    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, ATTACHED_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copies a subtitle file from a Content Uri into permanent app-private storage.
     * This ensures the file is not lost when cache is cleared.
     */
    fun persistSubtitleFromUri(context: Context, uri: Uri, fileName: String): File? {
        return try {
            // Keep read access to the ORIGINAL file so later edits of it can be picked up.
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(getStorageDir(context), "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Ensures a local file is in persistent storage or returns its path if already valid.
     */
    fun persistSubtitleFromFile(context: Context, sourceFile: File): File {
        return try {
            if (sourceFile.parentFile?.absolutePath == getStorageDir(context).absolutePath) {
                return sourceFile
            }
            val safeName = sourceFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(getStorageDir(context), "${System.currentTimeMillis()}_$safeName")
            sourceFile.copyTo(targetFile, overwrite = true)
            if (targetFile.exists() && targetFile.length() > 0) targetFile else sourceFile
        } catch (_: Throwable) {
            sourceFile
        }
    }

    /**
     * Persists subtitle content directly from an in-memory string (e.g. from clipboard).
     * Saved strictly within the app's private internal storage directory, never public system storage.
     */
    fun persistSubtitleFromText(
        context: Context,
        content: String,
        trackName: String = "PASTED-SUB",
        extension: String = "srt"
    ): File {
        val storageDir = getStorageDir(context)
        val safeName = trackName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val ext = extension.removePrefix(".")
        val targetFile = File(storageDir, "${System.currentTimeMillis()}_$safeName.$ext")
        targetFile.writeText(content, Charsets.UTF_8)
        return targetFile
    }

    /**
     * Generates all candidate keys for a video to ensure reliable lookup regardless of
     * whether path, videoId, or URI is passed.
     */
    fun generateCandidateKeys(
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null
    ): List<String> {
        val keys = ArrayList<String>(4)
        if (!path.isNullOrBlank()) {
            val normalized = path.trim().replace('\\', '/')
            keys.add("path_${normalized.hashCode()}")
            val name = File(normalized).name
            if (name.isNotBlank()) {
                keys.add("name_${name.hashCode()}")
            }
        }
        if (!uriString.isNullOrBlank()) {
            keys.add("uri_${uriString.trim().hashCode()}")
        }
        if (videoId > 0L) {
            keys.add("id_$videoId")
        }
        if (keys.isEmpty() && !title.isNullOrBlank()) {
            keys.add("title_${title.trim().hashCode()}")
        }
        return if (keys.isEmpty()) listOf("unknown_video") else keys
    }

    /**
     * Retrieves all permanently attached user-uploaded subtitles for a specific video.
     */
    fun getAttachedSubtitles(
        context: Context,
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null
    ): List<AttachedSubtitleRecord> {
        val candidateKeys = generateCandidateKeys(path, videoId, uriString, title)

        // Helper to deduplicate records by exact subtitle file path
        fun deduplicate(list: List<AttachedSubtitleRecord>): List<AttachedSubtitleRecord> {
            val seen = HashSet<String>()
            val result = ArrayList<AttachedSubtitleRecord>()
            for (item in list) {
                if (seen.add(item.filePath)) {
                    result.add(item)
                }
            }
            return result
        }

        // Check memory cache first
        for (k in candidateKeys) {
            val cached = memoryCache[k]
            if (cached != null && cached.isNotEmpty()) {
                val validCached = cached.filter { File(it.filePath).exists() && File(it.filePath).length() > 0 }
                return deduplicate(validCached)
            }
        }

        // Read from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (k in candidateKeys) {
            val jsonString = prefs.getString(KEY_PREFIX + k, null)
            if (!jsonString.isNullOrBlank()) {
                val list = deserializeRecords(jsonString)
                val validList = list.filter { File(it.filePath).exists() && File(it.filePath).length() > 0 }
                val deduplicated = deduplicate(validList)
                for (keyToCache in candidateKeys) {
                    memoryCache[keyToCache] = deduplicated
                }
                return deduplicated
            }
        }

        return emptyList()
    }

    /**
     * The app keeps its own private copy of every uploaded subtitle. If the user edited the
     * ORIGINAL file afterwards (text removed, timings changed...), overwrite the private copy with
     * the current content so the video shows the edit without removing/re-adding the subtitle.
     * The copy is only replaced when the original is readable and really differs; if the original
     * was moved/deleted the existing copy is kept (an uploaded subtitle is never dropped).
     * Returns true when the copy was updated. Call on a background thread.
     */
    fun syncAttachedFromSource(context: Context, record: AttachedSubtitleRecord): Boolean {
        return try {
            val copy = File(record.filePath)
            if (!copy.exists()) return false
            val newBytes: ByteArray? = when {
                record.sourcePath.isNotBlank() && record.sourcePath != record.filePath -> {
                    val src = File(record.sourcePath)
                    if (src.isFile && src.canRead() && src.length() > 0L) src.readBytes() else null
                }
                record.sourceUri.isNotBlank() -> {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(record.sourceUri))?.use { it.readBytes() }
                    } catch (_: Throwable) {
                        null
                    }
                }
                else -> null
            }
            if (newBytes == null || newBytes.isEmpty()) return false
            if (copy.length() == newBytes.size.toLong() && copy.readBytes().contentEquals(newBytes)) return false
            copy.writeBytes(newBytes)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Permanently attaches a custom subtitle to the given video/episode.
     */
    fun addAttachedSubtitle(
        context: Context,
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null,
        filePath: String,
        originalName: String,
        isSelected: Boolean = true,
        sourcePath: String = "",
        sourceUri: String = ""
    ): String? {
        if (filePath.isBlank()) return null
        val candidateKeys = generateCandidateKeys(path, videoId, uriString, title)
        val currentList = getAttachedSubtitles(context, path, videoId, uriString, title).toMutableList()

        // Selecting the same uploaded subtitle again must be idempotent. Earlier versions
        // created a new timestamped private copy on every selection, then MPV saw a new path
        // and another external track was added. Match by the ORIGINAL source identity first.
        fun sameSource(record: AttachedSubtitleRecord): Boolean {
            if (sourcePath.isNotBlank() && record.sourcePath.isNotBlank()) {
                if (File(record.sourcePath).absolutePath.equals(File(sourcePath).absolutePath, ignoreCase = true)) return true
            }
            if (sourceUri.isNotBlank() && record.sourceUri.isNotBlank()) {
                if (record.sourceUri == sourceUri) return true
            }
            if (record.filePath == filePath) return true
            // Clipboard/pasted subtitles have no external source identity; their fixed name
            // intentionally represents the same logical uploaded subtitle for this video.
            if (sourcePath.isBlank() && sourceUri.isBlank() && record.sourcePath.isBlank() && record.sourceUri.isBlank()) {
                return record.originalName.equals(originalName, ignoreCase = true)
            }
            return false
        }

        val matching = currentList.filter(::sameSource)
        val existing = matching.firstOrNull()
        val canonicalPath = existing?.filePath ?: filePath

        // If this is a re-selection, refresh the existing private copy from the newly imported
        // file instead of creating another persistent file/track.
        if (existing != null && canonicalPath != filePath) {
            try {
                val incoming = File(filePath)
                val canonical = File(canonicalPath)
                if (incoming.isFile && incoming.length() > 0L && canonical.parentFile?.absolutePath == getStorageDir(context).absolutePath) {
                    if (canonical.length() != incoming.length() || !canonical.readBytes().contentEquals(incoming.readBytes())) {
                        incoming.copyTo(canonical, overwrite = true)
                    }
                }
            } catch (_: Throwable) {}
            // The just-created timestamped import is no longer needed. Never delete a user's
            // original source; this path is only the app-private import.
            try {
                val incoming = File(filePath)
                if (incoming.parentFile?.absolutePath == getStorageDir(context).absolutePath && incoming.absolutePath != canonicalPath) {
                    incoming.delete()
                }
            } catch (_: Throwable) {}
        }

        // Remove all legacy duplicates for the same source identity while keeping unrelated
        // subtitles with the same display name.
        val withoutMatches = currentList.filterNot(::sameSource).toMutableList()

        val updatedList = if (isSelected) {
            withoutMatches.map { it.copy(isSelected = false) }.toMutableList()
        } else {
            withoutMatches
        }

        val record = existing?.copy(
            filePath = canonicalPath,
            originalName = originalName,
            isSelected = isSelected,
            sourcePath = if (sourcePath == canonicalPath) "" else sourcePath,
            sourceUri = sourceUri
        ) ?: AttachedSubtitleRecord(
            id = UUID.randomUUID().toString(),
            filePath = canonicalPath,
            originalName = originalName,
            isSelected = isSelected,
            addedTimestamp = System.currentTimeMillis(),
            sourcePath = if (sourcePath == canonicalPath) "" else sourcePath,
            sourceUri = sourceUri
        )
        updatedList.add(record)

        saveRecords(context, candidateKeys, updatedList)

        if (existing != null) {
            // Ensure the canonical copy reflects any edit made immediately before re-selecting.
            syncAttachedFromSource(context, record)
        }

        if (isSelected) {
            val sel = VideoSubtitleSelection(
                primary = SavedSubtitleRef(
                    kind = KIND_UPLOADED,
                    name = originalName,
                    language = "",
                    index = -1
                ),
                secondary = null
            )
            saveSubtitleSelection(context, path, videoId, uriString, title, sel)
        }
        return canonicalPath
    }

    /**
     * Updates selection state for an attached subtitle in the specified video.
     */
    fun setSubtitleSelected(
        context: Context,
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null,
        originalName: String,
        isSelected: Boolean
    ) {
        val candidateKeys = generateCandidateKeys(path, videoId, uriString, title)
        val currentList = getAttachedSubtitles(context, path, videoId, uriString, title)
        if (currentList.isEmpty()) return

        val updatedList = currentList.map { record ->
            if (record.originalName == originalName || File(record.filePath).name == originalName) {
                record.copy(isSelected = isSelected)
            } else if (isSelected) {
                record.copy(isSelected = false)
            } else {
                record
            }
        }

        saveRecords(context, candidateKeys, updatedList)

        // Also synchronize VideoSubtitleSelection so selection memory is permanently cohesive
        if (isSelected) {
            val sel = VideoSubtitleSelection(
                primary = SavedSubtitleRef(
                    kind = KIND_UPLOADED,
                    name = originalName,
                    language = "",
                    index = -1
                ),
                secondary = null
            )
            saveSubtitleSelection(context, path, videoId, uriString, title, sel)
        }
    }

    /**
     * Explicitly removes a user-uploaded subtitle from the episode when the user swipes
     * or clicks delete.
     */
    fun removeAttachedSubtitle(
        context: Context,
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null,
        trackId: Int = -1,
        originalName: String = "",
        filePath: String = ""
    ) {
        val candidateKeys = generateCandidateKeys(path, videoId, uriString, title)
        val currentList = getAttachedSubtitles(context, path, videoId, uriString, title)
        if (currentList.isEmpty()) return

        val removedRecords = currentList.filter {
            (originalName.isNotBlank() && (it.originalName == originalName || File(it.filePath).name == originalName)) ||
            (filePath.isNotBlank() && it.filePath == filePath)
        }

        val remainingList = currentList.filterNot {
            (originalName.isNotBlank() && (it.originalName == originalName || File(it.filePath).name == originalName)) ||
            (filePath.isNotBlank() && it.filePath == filePath)
        }

        // Delete underlying file if stored in our private attached subtitles directory
        for (r in removedRecords) {
            try {
                val f = File(r.filePath)
                if (f.exists() && f.parentFile?.absolutePath == getStorageDir(context).absolutePath) {
                    f.delete()
                }
            } catch (_: Throwable) {}
        }

        saveRecords(context, candidateKeys, remainingList)
    }

    private fun saveRecords(
        context: Context,
        candidateKeys: List<String>,
        records: List<AttachedSubtitleRecord>
    ) {
        for (k in candidateKeys) {
            memoryCache[k] = records
        }

        val jsonString = serializeRecords(records)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            for (k in candidateKeys) {
                if (records.isEmpty()) {
                    remove(KEY_PREFIX + k)
                } else {
                    putString(KEY_PREFIX + k, jsonString)
                }
            }
            apply()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Per-video subtitle SELECTION memory (which subtitle is selected: embedded / uploaded / both)
    // ---------------------------------------------------------------------------------------

    /**
     * Keys used for selection memory. File-NAME keys are excluded on purpose: two different videos
     * that happen to share a file name (e.g. "episode.mkv" in two folders) must never share a
     * selection. Path / uri / id keys identify exactly one video.
     */
    private fun selectionKeys(path: String?, videoId: Long, uriString: String?, title: String?): List<String> {
        val keys = generateCandidateKeys(path, videoId, uriString, title)
            .filterNot { it.startsWith("name_") || it == "unknown_video" }
        return keys
    }

    /** The saved selection of this video, or null when the user never chose one for it. */
    fun getSubtitleSelection(
        context: Context,
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null
    ): VideoSubtitleSelection? {
        val keys = selectionKeys(path, videoId, uriString, title)
        if (keys.isEmpty()) return null
        for (k in keys) {
            selectionCache[k]?.let { return it }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (k in keys) {
            val json = prefs.getString(SELECTION_KEY_PREFIX + k, null) ?: continue
            val parsed = deserializeSelection(json) ?: continue
            for (key in keys) selectionCache[key] = parsed
            return parsed
        }

        // Fallback: If no explicit VideoSubtitleSelection was saved, but the video has attached subtitles
        // where one is selected, automatically synthesize the selection so UI and playback remain in sync!
        val attached = getAttachedSubtitles(context, path, videoId, uriString, title)
        val selectedAttached = attached.firstOrNull { it.isSelected }
        if (selectedAttached != null) {
            val synthetic = VideoSubtitleSelection(
                primary = SavedSubtitleRef(
                    kind = KIND_UPLOADED,
                    name = selectedAttached.originalName.ifBlank { File(selectedAttached.filePath).name },
                    language = "",
                    index = -1
                ),
                secondary = null
            )
            for (key in keys) selectionCache[key] = synthetic
            return synthetic
        }

        return null
    }

    /** Remembers the subtitle selection of this video (replaces the previous one). */
    fun saveSubtitleSelection(
        context: Context,
        path: String?,
        videoId: Long = 0L,
        uriString: String? = null,
        title: String? = null,
        selection: VideoSubtitleSelection
    ) {
        val keys = selectionKeys(path, videoId, uriString, title)
        if (keys.isEmpty()) return
        for (k in keys) selectionCache[k] = selection
        val json = serializeSelection(selection)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            for (k in keys) putString(SELECTION_KEY_PREFIX + k, json)
            apply()
        }
    }

    private fun refToJson(ref: SavedSubtitleRef?): Any {
        if (ref == null) return JSONObject.NULL
        return JSONObject().apply {
            put("kind", ref.kind)
            put("name", ref.name)
            put("lang", ref.language)
            put("idx", ref.index)
        }
    }

    private fun refFromJson(obj: JSONObject?): SavedSubtitleRef? {
        if (obj == null) return null
        val name = obj.optString("name", "")
        if (name.isBlank()) return null
        return SavedSubtitleRef(
            kind = obj.optString("kind", KIND_EMBEDDED),
            name = name,
            language = obj.optString("lang", ""),
            index = obj.optInt("idx", -1)
        )
    }

    private fun serializeSelection(sel: VideoSubtitleSelection): String {
        return JSONObject().apply {
            put("p", refToJson(sel.primary))
            put("s", refToJson(sel.secondary))
        }.toString()
    }

    private fun deserializeSelection(json: String): VideoSubtitleSelection? {
        return try {
            val obj = JSONObject(json)
            VideoSubtitleSelection(
                primary = refFromJson(obj.optJSONObject("p")),
                secondary = refFromJson(obj.optJSONObject("s"))
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun serializeRecords(records: List<AttachedSubtitleRecord>): String {
        val jsonArray = JSONArray()
        for (r in records) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("filePath", r.filePath)
                put("originalName", r.originalName)
                put("isSelected", r.isSelected)
                put("addedTimestamp", r.addedTimestamp)
                put("sourcePath", r.sourcePath)
                put("sourceUri", r.sourceUri)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeRecords(jsonString: String): List<AttachedSubtitleRecord> {
        val list = mutableListOf<AttachedSubtitleRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AttachedSubtitleRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        filePath = obj.optString("filePath", ""),
                        originalName = obj.optString("originalName", ""),
                        isSelected = obj.optBoolean("isSelected", true),
                        addedTimestamp = obj.optLong("addedTimestamp", System.currentTimeMillis()),
                        sourcePath = obj.optString("sourcePath", ""),
                        sourceUri = obj.optString("sourceUri", "")
                    )
                )
            }
        } catch (_: Throwable) {}
        return list
    }
}
