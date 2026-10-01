package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ui.screens.VideoFolder
import com.example.ui.screens.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CachedVideoMeta(
    val durationMs: Long,
    val resolution: String,
    val framerate: Double,
    val subtitleFormats: List<String>,
    val hasSubtitles: Boolean,
    val embeddedSubtitleFormats: List<String> = emptyList(),
    val externalSubtitleFormats: List<String> = emptyList(),
    val embeddedFontCount: Int = 0,
    val isCompleteScan: Boolean = true,
    val metadataVersion: Int = VideoLibraryCache.METADATA_VERSION
)

object VideoLibraryCache {
    private const val TAG = "VideoLibraryCache"
    private const val CACHE_FILE_NAME = "video_library_cache.json"
    private const val META_CACHE_FILE_NAME = "video_metadata_cache.json"
    const val METADATA_VERSION = 4

    private val inMemoryMetaCache = ConcurrentHashMap<String, CachedVideoMeta>()
    private var inMemoryFolders: List<VideoFolder>? = null
    private var isMetaCacheLoaded = false

    /**
     * Normalizes a file path to its canonical representation, collapsing symlinks and aliases
     * (such as /sdcard or /storage/self/primary -> /storage/emulated/0).
     */
    fun normalizeCanonicalPath(rawPath: String): String {
        if (rawPath.isBlank()) return ""
        val normalized = try {
            File(rawPath).canonicalPath
        } catch (_: Throwable) {
            File(rawPath).absolutePath
        }
        return normalized
            .replace("/storage/self/primary", "/storage/emulated/0")
            .replace("/sdcard", "/storage/emulated/0")
            .replace("/mnt/sdcard", "/storage/emulated/0")
            .replace("//", "/")
    }

    private fun getMetaKey(path: String, sizeBytes: Long, dateModified: Long): String {
        val canonical = normalizeCanonicalPath(path)
        return "v$METADATA_VERSION:$canonical:$sizeBytes:$dateModified"
    }

    fun getCachedVideoMeta(path: String, sizeBytes: Long, dateModified: Long): CachedVideoMeta? {
        val key = getMetaKey(path, sizeBytes, dateModified)
        val cached = inMemoryMetaCache[key]
        if (cached != null && cached.metadataVersion >= METADATA_VERSION) {
            return cached
        }
        return null
    }

    fun putCachedVideoMeta(path: String, sizeBytes: Long, dateModified: Long, meta: CachedVideoMeta) {
        val key = getMetaKey(path, sizeBytes, dateModified)
        inMemoryMetaCache[key] = meta
    }

    fun clearCache() {
        inMemoryMetaCache.clear()
        inMemoryFolders = null
        isMetaCacheLoaded = false
    }

    /**
     * Instantly loads cached video folders from local persistent storage (0-5ms).
     */
    fun loadCachedFolders(context: Context): List<VideoFolder>? {
        inMemoryFolders?.let { return it }

        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return null
        }

        try {
            val jsonStr = cacheFile.readText()
            if (jsonStr.isBlank()) return null

            val folders = mutableListOf<VideoFolder>()
            if (jsonStr.trimStart().startsWith("{")) {
                val rootObj = JSONObject(jsonStr)
                val ver = rootObj.optInt("version", 0)
                if (ver < METADATA_VERSION) {
                    Log.i(TAG, "Cached folders version ($ver) is older than METADATA_VERSION ($METADATA_VERSION). Invalidating.")
                    cacheFile.delete()
                    return null
                }
                val rootArray = rootObj.optJSONArray("folders") ?: return null
                for (i in 0 until rootArray.length()) {
                    val folderObj = rootArray.optJSONObject(i) ?: continue
                    val folder = parseFolderFromJson(folderObj)
                    if (folder != null) {
                        folders.add(folder)
                    }
                }
            } else {
                // Legacy JSONArray format -> invalidate to re-scan with new metadata
                Log.i(TAG, "Legacy folder cache format detected. Invalidating to run authoritative scan.")
                cacheFile.delete()
                return null
            }

            // Deduplicate folders by canonical path
            val deduplicated = deduplicateFoldersList(folders)
            inMemoryFolders = deduplicated
            Log.d(TAG, "Successfully loaded ${deduplicated.size} folders from disk cache (v$METADATA_VERSION)")
            return deduplicated
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading cached folders from disk", e)
            return null
        }
    }

    /**
     * Saves the scanned video folders to persistent disk cache with schema version.
     */
    fun saveCachedFolders(context: Context, folders: List<VideoFolder>) {
        val deduplicated = deduplicateFoldersList(folders)
        inMemoryFolders = deduplicated

        try {
            val rootObj = JSONObject()
            rootObj.put("version", METADATA_VERSION)
            val rootArray = JSONArray()
            for (folder in deduplicated) {
                rootArray.put(folderToJson(folder))
            }
            rootObj.put("folders", rootArray)

            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            tempFile.writeText(rootObj.toString())
            if (tempFile.renameTo(cacheFile)) {
                Log.d(TAG, "Persisted ${deduplicated.size} folders to disk cache (v$METADATA_VERSION)")
            } else {
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving folders to disk cache", e)
        }
    }

    /**
     * Load metadata cache into memory, invalidating old versions.
     */
    fun loadMetaCache(context: Context) {
        if (isMetaCacheLoaded) return
        val metaFile = File(context.filesDir, META_CACHE_FILE_NAME)
        if (!metaFile.exists() || metaFile.length() == 0L) {
            isMetaCacheLoaded = true
            return
        }
        try {
            val jsonStr = metaFile.readText()
            val obj = JSONObject(jsonStr)
            val rootVer = obj.optInt("__version__", 0)
            if (rootVer < METADATA_VERSION) {
                Log.i(TAG, "Metadata cache version ($rootVer) < current ($METADATA_VERSION). Invalidating stale metadata cache.")
                metaFile.delete()
                inMemoryMetaCache.clear()
                isMetaCacheLoaded = true
                return
            }

            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "__version__") continue
                val itemObj = obj.getJSONObject(key)

                val subArray = itemObj.optJSONArray("subs")
                val subs = mutableListOf<String>()
                if (subArray != null) {
                    for (i in 0 until subArray.length()) {
                        subs.add(subArray.getString(i))
                    }
                }

                val embArray = itemObj.optJSONArray("emb_subs")
                val embSubs = mutableListOf<String>()
                if (embArray != null) {
                    for (i in 0 until embArray.length()) {
                        embSubs.add(embArray.getString(i))
                    }
                }

                val extArray = itemObj.optJSONArray("ext_subs")
                val extSubs = mutableListOf<String>()
                if (extArray != null) {
                    for (i in 0 until extArray.length()) {
                        extSubs.add(extArray.getString(i))
                    }
                }

                val meta = CachedVideoMeta(
                    durationMs = itemObj.optLong("dur", 0L),
                    resolution = itemObj.optString("res", ""),
                    framerate = itemObj.optDouble("fps", 0.0),
                    subtitleFormats = subs,
                    hasSubtitles = subs.isNotEmpty() || embSubs.isNotEmpty() || extSubs.isNotEmpty(),
                    embeddedSubtitleFormats = embSubs,
                    externalSubtitleFormats = extSubs,
                    embeddedFontCount = itemObj.optInt("fonts", 0),
                    isCompleteScan = itemObj.optBoolean("complete", true),
                    metadataVersion = itemObj.optInt("v", rootVer)
                )
                inMemoryMetaCache[key] = meta
            }
            isMetaCacheLoaded = true
            Log.d(TAG, "Loaded ${inMemoryMetaCache.size} metadata entries into memory (v$METADATA_VERSION)")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load metadata cache", e)
            isMetaCacheLoaded = true
        }
    }

    /**
     * Persist metadata cache to disk.
     */
    fun saveMetaCache(context: Context) {
        try {
            val obj = JSONObject()
            obj.put("__version__", METADATA_VERSION)
            for ((key, meta) in inMemoryMetaCache) {
                val itemObj = JSONObject().apply {
                    put("dur", meta.durationMs)
                    put("res", meta.resolution)
                    put("fps", meta.framerate)
                    put("fonts", meta.embeddedFontCount)
                    put("complete", meta.isCompleteScan)
                    put("v", meta.metadataVersion)

                    val subArray = JSONArray()
                    meta.subtitleFormats.forEach { subArray.put(it) }
                    put("subs", subArray)

                    val embArray = JSONArray()
                    meta.embeddedSubtitleFormats.forEach { embArray.put(it) }
                    put("emb_subs", embArray)

                    val extArray = JSONArray()
                    meta.externalSubtitleFormats.forEach { extArray.put(it) }
                    put("ext_subs", extArray)
                }
                obj.put(key, itemObj)
            }
            val metaFile = File(context.filesDir, META_CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$META_CACHE_FILE_NAME.tmp")
            tempFile.writeText(obj.toString())
            if (tempFile.renameTo(metaFile)) {
                // Success
            } else {
                tempFile.copyTo(metaFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save metadata cache", e)
        }
    }

    /**
     * Strict canonical deduplication for folders and their videos.
     */
    fun deduplicateFoldersList(folders: List<VideoFolder>): List<VideoFolder> {
        val mergedMap = LinkedHashMap<String, VideoFolder>()

        for (folder in folders) {
            val canonicalPath = normalizeCanonicalPath(folder.path)
            val folderKey = when {
                folder.isCustom -> "custom_${folder.name.lowercase()}"
                canonicalPath.isNotBlank() -> canonicalPath.lowercase()
                folder.id.isNotBlank() -> "id_${folder.id.lowercase()}"
                else -> "name_${folder.name.lowercase()}"
            }

            val existing = mergedMap[folderKey]
            if (existing == null) {
                // Deduplicate videos inside this folder first
                val deduplicatedVideos = deduplicateVideosList(folder.videos)
                val totalSize = deduplicatedVideos.sumOf { it.sizeBytes }
                val newCount = deduplicatedVideos.count { it.isNew }
                val previewUri = deduplicatedVideos.firstOrNull()?.uri ?: folder.previewVideoUri

                mergedMap[folderKey] = folder.copy(
                    path = canonicalPath,
                    videoCount = deduplicatedVideos.size,
                    totalSizeBytes = totalSize,
                    newVideosCount = newCount,
                    previewVideoUri = previewUri,
                    videos = deduplicatedVideos,
                    subFolders = emptyList()
                )
            } else {
                // Merge video items from both instances
                val combinedVideos = mutableListOf<VideoItem>()
                combinedVideos.addAll(existing.videos)
                combinedVideos.addAll(folder.videos)
                val deduplicatedVideos = deduplicateVideosList(combinedVideos)

                val totalSize = deduplicatedVideos.sumOf { it.sizeBytes }
                val newCount = deduplicatedVideos.count { it.isNew }
                val previewUri = deduplicatedVideos.firstOrNull()?.uri ?: existing.previewVideoUri

                mergedMap[folderKey] = existing.copy(
                    videoCount = deduplicatedVideos.size,
                    totalSizeBytes = totalSize,
                    newVideosCount = newCount,
                    previewVideoUri = previewUri,
                    videos = deduplicatedVideos
                )
            }
        }

        return mergedMap.values.toList()
    }

    /**
     * Strict canonical deduplication for video files.
     */
    fun deduplicateVideosList(videos: List<VideoItem>): List<VideoItem> {
        val videoMap = LinkedHashMap<String, VideoItem>()
        for (v in videos) {
            val canonicalPath = normalizeCanonicalPath(v.path)
            val key = if (canonicalPath.isNotBlank()) {
                canonicalPath.lowercase()
            } else {
                "${v.displayName.lowercase()}:${v.sizeBytes}"
            }

            val existing = videoMap[key]
            if (existing == null) {
                videoMap[key] = v.copy(path = canonicalPath)
            } else {
                // Keep the one with better metadata or valid URI
                val betterUri = if (v.uri.toString().startsWith("content://")) v.uri else existing.uri
                val betterRes = if (v.resolution.isNotBlank()) v.resolution else existing.resolution
                val betterFps = if (v.framerate > 0.0) v.framerate else existing.framerate
                val betterEmbSubs = if (v.embeddedSubtitleFormats.isNotEmpty()) v.embeddedSubtitleFormats else existing.embeddedSubtitleFormats
                val betterExtSubs = if (v.externalSubtitleFormats.isNotEmpty()) v.externalSubtitleFormats else existing.externalSubtitleFormats
                val betterSubs = if (v.subtitleFormats.isNotEmpty()) v.subtitleFormats else existing.subtitleFormats
                val betterDur = if (v.durationMs > 0) v.durationMs else existing.durationMs
                val betterFonts = if (v.embeddedFontCount > 0) v.embeddedFontCount else existing.embeddedFontCount

                videoMap[key] = existing.copy(
                    uri = betterUri,
                    resolution = betterRes,
                    framerate = betterFps,
                    subtitleFormats = betterSubs,
                    embeddedSubtitleFormats = betterEmbSubs,
                    externalSubtitleFormats = betterExtSubs,
                    embeddedFontCount = betterFonts,
                    hasSubtitles = betterSubs.isNotEmpty() || betterEmbSubs.isNotEmpty() || betterExtSubs.isNotEmpty(),
                    durationMs = betterDur
                )
            }
        }
        return videoMap.values.toList()
    }

    private fun folderToJson(folder: VideoFolder): JSONObject {
        return JSONObject().apply {
            put("id", folder.id)
            put("name", folder.name)
            put("path", folder.path)
            put("videoCount", folder.videoCount)
            put("totalSizeBytes", folder.totalSizeBytes)
            put("newVideosCount", folder.newVideosCount)
            put("isCustom", folder.isCustom)
            folder.previewVideoUri?.let { put("previewUri", it.toString()) }

            val videosArray = JSONArray()
            for (v in folder.videos) {
                videosArray.put(videoToJson(v))
            }
            put("videos", videosArray)
        }
    }

    private fun parseFolderFromJson(obj: JSONObject): VideoFolder? {
        return try {
            val id = obj.getString("id")
            val name = obj.getString("name")
            val path = obj.getString("path")
            val videoCount = obj.optInt("videoCount", 0)
            val totalSizeBytes = obj.optLong("totalSizeBytes", 0L)
            val newVideosCount = obj.optInt("newVideosCount", 0)
            val isCustom = obj.optBoolean("isCustom", false)
            val previewUriStr = obj.optString("previewUri", null)
            val previewUri = if (!previewUriStr.isNullOrBlank()) Uri.parse(previewUriStr) else null

            val videosArray = obj.optJSONArray("videos")
            val videos = mutableListOf<VideoItem>()
            if (videosArray != null) {
                for (i in 0 until videosArray.length()) {
                    val vObj = videosArray.optJSONObject(i) ?: continue
                    parseVideoFromJson(vObj)?.let { videos.add(it) }
                }
            }

            VideoFolder(
                id = id,
                name = name,
                path = path,
                videoCount = if (videos.isNotEmpty()) videos.size else videoCount,
                totalSizeBytes = if (videos.isNotEmpty()) videos.sumOf { it.sizeBytes } else totalSizeBytes,
                newVideosCount = newVideosCount,
                isCustom = isCustom,
                previewVideoUri = previewUri ?: videos.firstOrNull()?.uri,
                videos = videos,
                subFolders = emptyList()
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing folder JSON", e)
            null
        }
    }

    private fun videoToJson(v: VideoItem): JSONObject {
        return JSONObject().apply {
            put("id", v.id)
            put("uri", v.uri.toString())
            put("displayName", v.displayName)
            put("path", v.path)
            put("sizeBytes", v.sizeBytes)
            put("durationMs", v.durationMs)
            put("dateModified", v.dateModified)
            put("isNew", v.isNew)
            put("resolution", v.resolution)
            put("framerate", v.framerate)
            put("hasSubtitles", v.hasSubtitles)
            put("watchedProgress", v.watchedProgress.toDouble())
            put("fonts", v.embeddedFontCount)

            val subArray = JSONArray()
            v.subtitleFormats.forEach { subArray.put(it) }
            put("subtitleFormats", subArray)

            val embArray = JSONArray()
            v.embeddedSubtitleFormats.forEach { embArray.put(it) }
            put("embeddedSubtitleFormats", embArray)

            val extArray = JSONArray()
            v.externalSubtitleFormats.forEach { extArray.put(it) }
            put("externalSubtitleFormats", extArray)
        }
    }

    private fun parseVideoFromJson(obj: JSONObject): VideoItem? {
        return try {
            val id = obj.getLong("id")
            val uriStr = obj.getString("uri")
            val displayName = obj.getString("displayName")
            val path = obj.getString("path")
            val sizeBytes = obj.optLong("sizeBytes", 0L)
            val durationMs = obj.optLong("durationMs", 0L)
            val dateModified = obj.optLong("dateModified", 0L)
            val isNew = obj.optBoolean("isNew", false)
            val resolution = obj.optString("resolution", "")
            val framerate = obj.optDouble("framerate", 0.0)
            val hasSubtitles = obj.optBoolean("hasSubtitles", false)
            val watchedProgress = obj.optDouble("watchedProgress", 0.0).toFloat()
            val fontCount = obj.optInt("fonts", 0)

            val subArray = obj.optJSONArray("subtitleFormats")
            val subs = mutableListOf<String>()
            if (subArray != null) {
                for (i in 0 until subArray.length()) {
                    subs.add(subArray.getString(i))
                }
            }

            val embArray = obj.optJSONArray("embeddedSubtitleFormats")
            val embSubs = mutableListOf<String>()
            if (embArray != null) {
                for (i in 0 until embArray.length()) {
                    embSubs.add(embArray.getString(i))
                }
            }

            val extArray = obj.optJSONArray("externalSubtitleFormats")
            val extSubs = mutableListOf<String>()
            if (extArray != null) {
                for (i in 0 until extArray.length()) {
                    extSubs.add(extArray.getString(i))
                }
            }

            VideoItem(
                id = id,
                uri = Uri.parse(uriStr),
                displayName = displayName,
                path = path,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                dateModified = dateModified,
                isNew = isNew,
                resolution = resolution,
                framerate = framerate,
                hasSubtitles = hasSubtitles || subs.isNotEmpty() || embSubs.isNotEmpty() || extSubs.isNotEmpty(),
                subtitleFormats = subs,
                watchedProgress = watchedProgress,
                embeddedSubtitleFormats = embSubs,
                externalSubtitleFormats = extSubs,
                embeddedFontCount = fontCount
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing video JSON", e)
            null
        }
    }

    fun clearCache(context: Context) {
        inMemoryFolders = null
        inMemoryMetaCache.clear()
        try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) cacheFile.delete()
            val metaFile = File(context.filesDir, META_CACHE_FILE_NAME)
            if (metaFile.exists()) metaFile.delete()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}
