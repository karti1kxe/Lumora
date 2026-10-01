package com.example.ui.screens

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ui.components.hasVideoPermission
import com.example.ui.components.hasAllFilesAccess
import com.example.util.CachedVideoMeta
import com.example.util.ExtractedMediaMetadata
import com.example.util.MediaMetadataExtractor
import com.example.util.VideoLibraryCache
import com.example.util.accessibleStorageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateModified: Long,
    val isNew: Boolean = false,
    val resolution: String = "",
    val framerate: Double = 0.0,
    val hasSubtitles: Boolean = false,
    val subtitleFormats: List<String> = emptyList(),
    val watchedProgress: Float = 0.0f,
    val embeddedSubtitleFormats: List<String> = emptyList(),
    val externalSubtitleFormats: List<String> = emptyList(),
    val embeddedFontCount: Int = 0,
    val isOnline: Boolean = false,
    val thumbnailUrl: String? = null,
    val channelName: String? = null,
    val viewCountText: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)

    val durationFormatted: String
        get() = formatDuration(durationMs)

    val formattedFramerate: String
        get() {
            if (framerate <= 0.0) return ""
            val roundInt = Math.round(framerate).toDouble()
            return if (Math.abs(framerate - roundInt) < 0.0001) {
                "${roundInt.toLong()} FPS"
            } else {
                val df = java.text.DecimalFormat("#.###", java.text.DecimalFormatSymbols(java.util.Locale.US))
                "${df.format(framerate)} FPS"
            }
        }

    val extension: String
        get() {
            val ext = displayName.substringAfterLast('.', "")
            return if (ext.isNotEmpty()) ".${ext.lowercase()}" else ""
        }

    val extensionTag: String
        get() = displayName.substringAfterLast('.', "MP4").uppercase()

    val nameWithoutExtension: String
        get() = if (displayName.contains('.')) displayName.substringBeforeLast('.') else displayName

    val formattedDate: String
        get() {
            if (dateModified <= 0) return ""
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(dateModified))
        }

    val effectiveSubtitleFormats: List<String>
        get() = if (embeddedSubtitleFormats.isNotEmpty()) embeddedSubtitleFormats else if (subtitleFormats.isNotEmpty()) subtitleFormats else externalSubtitleFormats
}

/**
 * Extracts the exact original video framerate using authoritative demuxing and multi-strategy fallback.
 */
fun extractVideoFramerate(
    path: String,
    context: Context? = null,
    uri: Uri? = null,
    displayName: String = ""
): Double {
    val meta = MediaMetadataExtractor.extractMetadata(
        filePath = path,
        context = context,
        uri = uri,
        displayName = displayName
    )
    return meta.framerate
}

/**
 * Detects all subtitle formats (authoritative embedded tracks & external companion files) and counts them accurately.
 * Returns formatted labels with counts, e.g. ["7ASS", "2SRT"], ["1ASS"], ["3VTT"]
 */
fun detectSubtitleFormats(
    videoPath: String,
    displayName: String,
    context: Context? = null,
    videoUri: Uri? = null
): List<String> {
    val meta = MediaMetadataExtractor.extractMetadata(
        filePath = videoPath,
        context = context,
        uri = videoUri,
        displayName = displayName
    )
    return meta.effectiveSubtitleFormats
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

data class VideoFolder(
    val id: String,
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val newVideosCount: Int,
    val isCustom: Boolean = false,
    val previewVideoUri: Uri? = null,
    val videos: List<VideoItem> = emptyList(),
    val subFolders: List<VideoFolder> = emptyList()
) {
    val formattedSize: String
        get() = formatFileSize(totalSizeBytes)

    val totalDurationFormatted: String
        get() {
            val totalMs = getAllVideos().sumOf { it.durationMs }
            return formatDuration(totalMs)
        }

    val maxResolution: String
        get() {
            val allVids = getAllVideos()
            return when {
                allVids.any { it.resolution.contains("4k", true) || it.displayName.contains("4k", true) || it.displayName.contains("2160", true) } -> "4K"
                allVids.any { it.resolution.contains("1080", true) || it.displayName.contains("1080", true) } -> "1080p"
                allVids.any { it.resolution.contains("720", true) || it.displayName.contains("720", true) } -> "720p"
                allVids.any { it.resolution.contains("480", true) || it.displayName.contains("480", true) } -> "480p"
                else -> "1080p"
            }
        }

    val formattedDate: String
        get() {
            val date = lastModifiedDate
            if (date <= 0) return ""
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(date))
        }

    val lastModifiedDate: Long
        get() = (videos.maxOfOrNull { it.dateModified } ?: 0L).coerceAtLeast(
            subFolders.maxOfOrNull { it.lastModifiedDate } ?: 0L
        )

    val allVideosCount: Int
        get() = videos.size + subFolders.sumOf { it.allVideosCount }

    /**
     * Returns all video items inside this folder
     */
    fun getAllVideos(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        list.addAll(videos)
        for (sub in subFolders) {
            list.addAll(sub.getAllVideos())
        }
        return list
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    val df = DecimalFormat("#,##0.#")
    return "${df.format(value)} ${units[digitGroups]}"
}

object VideoFolderScanner {

    private const val TAG = "VideoFolderScanner"

    // All supported video container formats (Modern, Standard & Legacy)
    val supportedExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "3gp", "ts", "m4v",
        "m3u8", "mpg", "mpeg", "vob", "ogv", "rmvb", "asf", "divx", "f4v",
        "m2ts", "mts", "tp", "dat", "rm", "wtv", "h264", "h265", "hevc",
        "264", "265", "3g2", "3gpp", "iso", "amv", "yuv"
    )

    private val scanMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun scanVideoFolders(
        context: Context,
        customFolders: List<String> = emptyList()
    ): List<VideoFolder> = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val folderMap = mutableMapOf<String, MutableList<VideoItem>>()
            val folderNameMap = mutableMapOf<String, String>()

        // Initialize metadata cache from disk if needed
        VideoLibraryCache.loadMetaCache(context)

        // 1. Version-aware permission validation & logging
        val hasPerm = hasVideoPermission(context)
        val sdk = Build.VERSION.SDK_INT
        Log.i(TAG, "scanVideoFolders invoked | SDK=$sdk | hasVideoPermission=$hasPerm")

        // 2. Query MediaStore API (Scoped Storage compliant across all Android versions & OEMs)
        var mediaStoreVideoCount = 0
        try {
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            val projection = if (sdk >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.MediaColumns.RELATIVE_PATH
                )
            } else {
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.MIME_TYPE
                )
            }

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val relPathCol = if (sdk >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else cursor.position.toLong()
                    val rawPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val path = if (rawPath.isNotBlank()) VideoLibraryCache.normalizeCanonicalPath(rawPath) else ""
                    val rawName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val name = rawName?.takeIf { it.isNotBlank() }
                        ?: if (path.isNotBlank()) File(path).name else "Video_$id.mp4"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else if (path.isNotBlank()) File(path).length() else 0L
                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val dateRaw = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val date = if (dateRaw > 0) dateRaw * 1000L else if (path.isNotBlank()) File(path).lastModified() else System.currentTimeMillis()
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val bucketName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) else null
                    val bucketId = if (bucketIdCol != -1) cursor.getString(bucketIdCol) else null
                    val relPath = if (relPathCol != -1) cursor.getString(relPathCol) else null

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    // Determine folder display name & bucket key without hardcoded paths
                    val folderDisplayName = when {
                        !bucketName.isNullOrBlank() -> bucketName
                        !relPath.isNullOrBlank() -> {
                            val cleanRel = relPath.trim().trimEnd('/')
                            if (cleanRel.contains('/')) cleanRel.substringAfterLast('/') else cleanRel
                        }
                        path.isNotBlank() && File(path).parentFile != null -> File(path).parentFile!!.name
                        else -> "Videos"
                    }

                    val folderKey = when {
                        path.isNotBlank() && File(path).parent != null -> VideoLibraryCache.normalizeCanonicalPath(File(path).parent!!)
                        !relPath.isNullOrBlank() -> "/storage/emulated/0/${relPath.trim().trimEnd('/')}"
                        !bucketId.isNullOrBlank() -> "bucket_$bucketId"
                        else -> "folder_${folderDisplayName.lowercase()}"
                    }

                    folderNameMap.putIfAbsent(folderKey, folderDisplayName)

                    val detectedRes = if (width > 0 && height > 0) {
                        val minDim = minOf(width, height)
                        val maxDim = maxOf(width, height)
                        when {
                            maxDim >= 3800 || minDim >= 2100 -> "4K"
                            minDim >= 1000 || maxDim >= 1900 -> "1080p"
                            minDim >= 700 || maxDim >= 1200 -> "720p"
                            minDim >= 450 || maxDim >= 800 -> "480p"
                            else -> "${width}x${height}"
                        }
                    } else {
                        when {
                            name.contains("4k", ignoreCase = true) || name.contains("2160p", ignoreCase = true) -> "4K"
                            name.contains("1080p", ignoreCase = true) || name.contains("1080", ignoreCase = true) -> "1080p"
                            name.contains("720p", ignoreCase = true) -> "720p"
                            name.contains("480p", ignoreCase = true) -> "480p"
                            else -> ""
                        }
                    }

                    // Authoritative metadata extraction & cache pipeline
                    val cacheKey = if (path.isNotBlank()) path else contentUri.toString()
                    val cachedMeta = VideoLibraryCache.getCachedVideoMeta(cacheKey, size, date)
                    val finalDuration: Long
                    val finalResolution: String
                    val finalFps: Double
                    val realSubFormats: List<String>
                    val embSubFormats: List<String>
                    val extSubFormats: List<String>
                    val fontCount: Int

                    if (cachedMeta != null) {
                        finalDuration = if (dur > 0) dur else cachedMeta.durationMs
                        finalResolution = if (cachedMeta.resolution.isNotBlank()) cachedMeta.resolution else detectedRes
                        finalFps = cachedMeta.framerate
                        realSubFormats = cachedMeta.subtitleFormats
                        embSubFormats = cachedMeta.embeddedSubtitleFormats
                        extSubFormats = cachedMeta.externalSubtitleFormats
                        fontCount = cachedMeta.embeddedFontCount
                    } else {
                        val meta = try {
                            MediaMetadataExtractor.extractMetadata(
                                filePath = path,
                                context = context,
                                uri = contentUri,
                                displayName = name,
                                sizeBytes = size,
                                dateModified = date
                            )
                        } catch (_: Throwable) { null }
                        finalDuration = if (dur > 0) dur else (meta?.durationMs ?: 0L)
                        finalResolution = if (!meta?.resolution.isNullOrBlank()) meta!!.resolution else detectedRes
                        finalFps = meta?.framerate ?: 0.0
                        embSubFormats = meta?.embeddedSubtitleFormats ?: emptyList()
                        extSubFormats = meta?.externalSubtitleFormats ?: emptyList()
                        realSubFormats = meta?.effectiveSubtitleFormats ?: (embSubFormats + extSubFormats)
                        fontCount = meta?.embeddedFontCount ?: 0
                    }

                    val videoItem = VideoItem(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        path = if (path.isNotBlank()) path else contentUri.toString(),
                        sizeBytes = size,
                        durationMs = finalDuration,
                        dateModified = date,
                        isNew = false,
                        resolution = finalResolution,
                        framerate = finalFps,
                        hasSubtitles = realSubFormats.isNotEmpty() || embSubFormats.isNotEmpty() || extSubFormats.isNotEmpty(),
                        subtitleFormats = realSubFormats,
                        watchedProgress = 0.0f,
                        embeddedSubtitleFormats = embSubFormats,
                        externalSubtitleFormats = extSubFormats,
                        embeddedFontCount = fontCount
                    )

                    val folderItems = folderMap.getOrPut(folderKey) { mutableListOf() }
                    // Prevent duplicate video entries inside the same folder
                    if (folderItems.none { it.id == id || (it.displayName.equals(name, ignoreCase = true) && it.sizeBytes == size) }) {
                        folderItems.add(videoItem)
                        mediaStoreVideoCount++
                    }
                }
            }
            Log.i(TAG, "MediaStore scan finished: found $mediaStoreVideoCount videos across ${folderMap.size} folders")
        } catch (e: Throwable) {
            Log.e(TAG, "MediaStore query encountered an exception", e)
        }

        // 3. Direct filesystem discovery. When All Files Access (or legacy full
        // storage access on Android 10 and below) is available, enumerate every
        // accessible shared-storage volume instead of relying only on MediaStore's
        // OEM-dependent indexing. This is what prevents folders from disappearing
        // on other manufacturers/devices.
        if (hasAllFilesAccess(context)) {
            val roots = try {
                accessibleStorageRoots(context)
            } catch (e: Throwable) {
                Log.w(TAG, "accessibleStorageRoots() failed", e)
                emptyList()
            }
            var scannedRoots = 0
            for (root in roots) {
                try {
                    scanDirectoryForVideos(context, root, folderMap, folderNameMap)
                    scannedRoots++
                } catch (e: Throwable) {
                    // A problem scanning one volume (e.g. a restricted/broken folder that only
                    // exists on this particular device/OEM) must never prevent scanning the
                    // remaining volumes — this is what previously made folders (often the SD
                    // card, since it was scanned after the primary volume) vanish entirely on
                    // some phones while working fine on others.
                    Log.w(TAG, "Full-storage filesystem scanning exception for root ${root.path}", e)
                }
            }
            Log.i(TAG, "Full-storage video scan finished across $scannedRoots/${roots.size} volume(s)")
        } else if (mediaStoreVideoCount == 0) {
            try {
                val publicDirs = listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    File("/storage/emulated/0/Videos"),
                    File("/storage/emulated/0/Movies"),
                    File("/storage/emulated/0/DCIM/Camera"),
                    File("/storage/emulated/0/Download")
                ).distinct().filter { it.exists() && it.canRead() }

                for (dir in publicDirs) {
                    scanDirectoryForVideos(context, dir, folderMap, folderNameMap)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Filesystem fallback scanning exception", e)
            }
        }

        // If still 0 and permission is not granted, check cached folders
        if (folderMap.isEmpty() && !hasPerm) {
            val cached = VideoLibraryCache.loadCachedFolders(context)
            if (!cached.isNullOrEmpty()) {
                return@withLock cached
            }
        }

        // 3. Flat Direct Folder Generation & Strict De-duplication
        val rawFolders = mutableListOf<VideoFolder>()

        for ((folderKey, items) in folderMap) {
            if (items.isEmpty()) continue
            val folderName = folderNameMap[folderKey] ?: File(folderKey).name.ifBlank { "Videos" }
            val deduplicatedVideos = VideoLibraryCache.deduplicateVideosList(items)
            val totalSize = deduplicatedVideos.sumOf { it.sizeBytes }
            val newCount = deduplicatedVideos.count { it.isNew }
            val previewUri = deduplicatedVideos.firstOrNull()?.uri

            rawFolders.add(
                VideoFolder(
                    id = folderKey,
                    name = folderName,
                    path = folderKey,
                    videoCount = deduplicatedVideos.size,
                    totalSizeBytes = totalSize,
                    newVideosCount = newCount,
                    isCustom = false,
                    previewVideoUri = previewUri,
                    videos = deduplicatedVideos.sortedByDescending { it.dateModified },
                    subFolders = emptyList()
                )
            )
        }

        // Add user-created custom folders (if any)
        for (customName in customFolders) {
            val customPath = "custom_$customName"
            if (rawFolders.none { it.name.equals(customName, ignoreCase = true) || it.path.equals(customPath, ignoreCase = true) }) {
                rawFolders.add(
                    0,
                    VideoFolder(
                        id = customPath,
                        name = customName,
                        path = customPath,
                        videoCount = 0,
                        totalSizeBytes = 0L,
                        newVideosCount = 0,
                        isCustom = true,
                        previewVideoUri = null,
                        videos = emptyList(),
                        subFolders = emptyList()
                    )
                )
            }
        }

        // 4. Final strict de-duplication pass
        val deduplicatedFolders = VideoLibraryCache.deduplicateFoldersList(rawFolders)

        // Sort: custom folders first, then folders with new videos, then video count and name
        val sortedResult = deduplicatedFolders.sortedWith(
            compareByDescending<VideoFolder> { it.isCustom }
                .thenByDescending { it.newVideosCount }
                .thenByDescending { it.videoCount }
                .thenBy { it.name.lowercase() }
        )

        // Persist to disk cache and meta cache asynchronously
        VideoLibraryCache.saveCachedFolders(context, sortedResult)
        VideoLibraryCache.saveMetaCache(context)

        Log.i(TAG, "scanVideoFolders returning ${sortedResult.size} deduplicated folders with total ${sortedResult.sumOf { it.videoCount }} videos")
        sortedResult
        }
    }

    // System/cache folders that never contain a user's own videos, but are common causes of
    // SecurityException (Android/data, Android/obb on modern Android) or of scanning a huge
    // number of irrelevant files (.thumbnails, caches). Skipping them is both faster and safer.
    private val skippedDirNames = setOf(
        ".thumbnails", ".trashed", "cache", ".cache", ".temp", ".tmp", ".thumbdata"
    )

    private fun scanDirectoryForVideos(
        context: Context,
        root: File,
        folderMap: MutableMap<String, MutableList<VideoItem>>,
        folderNameMap: MutableMap<String, String>
    ) {
        // Iterative (stack-based) traversal instead of recursion: a real device can have very
        // deep or very wide folder trees (backup apps, cloud-sync clients, other file managers),
        // and an unbounded recursive call risks a StackOverflowError that would silently abort
        // the entire scan. A canonical-path "visited" set also guards against symlink loops
        // (e.g. /storage/self/primary style bind-mounts some OEMs expose).
        val visited = HashSet<String>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()

            val canonicalDirPath = try {
                dir.canonicalPath
            } catch (_: Throwable) {
                dir.absolutePath
            }
            if (!visited.add(canonicalDirPath)) continue

            val files = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue

            for (file in files) {
                try {
                    if (file.isDirectory) {
                        val dirName = file.name
                        if (dirName.isEmpty() || dirName.startsWith(".")) continue
                        if (dir.name.equals("Android", ignoreCase = true) &&
                            (dirName.equals("data", ignoreCase = true) || dirName.equals("obb", ignoreCase = true))
                        ) continue
                        if (dirName.lowercase() in skippedDirNames) continue
                        stack.addLast(file)
                    } else if (file.isFile && !file.name.startsWith(".")) {
                        val ext = file.extension.lowercase()
                        if (supportedExtensions.contains(ext)) {
                            val path = VideoLibraryCache.normalizeCanonicalPath(file.absolutePath)
                            val parentFile = file.parentFile
                            val folderKey = if (parentFile != null) VideoLibraryCache.normalizeCanonicalPath(parentFile.absolutePath) else "Videos"
                            val folderName = parentFile?.name ?: "Videos"
                            folderNameMap.putIfAbsent(folderKey, folderName)

                            val uri = Uri.fromFile(file)
                            val size = file.length()
                            val date = file.lastModified()
                            val name = file.name

                            var cachedMeta = VideoLibraryCache.getCachedVideoMeta(path, size, date)
                            if (cachedMeta == null) {
                                val meta = try {
                                    MediaMetadataExtractor.extractMetadata(
                                        filePath = path,
                                        context = context,
                                        uri = uri,
                                        displayName = name,
                                        sizeBytes = size,
                                        dateModified = date
                                    )
                                } catch (_: Throwable) { null }
                                if (meta != null) {
                                    cachedMeta = CachedVideoMeta(
                                        durationMs = meta.durationMs,
                                        resolution = meta.resolution,
                                        framerate = meta.framerate,
                                        subtitleFormats = meta.effectiveSubtitleFormats,
                                        hasSubtitles = meta.effectiveSubtitleFormats.isNotEmpty(),
                                        embeddedSubtitleFormats = meta.embeddedSubtitleFormats,
                                        externalSubtitleFormats = meta.externalSubtitleFormats,
                                        embeddedFontCount = meta.embeddedFontCount
                                    )
                                }
                            }

                            val finalDuration = cachedMeta?.durationMs ?: 0L
                            val finalResolution = cachedMeta?.resolution ?: ""
                            val finalFps = cachedMeta?.framerate ?: 0.0
                            val embSubFormats = cachedMeta?.embeddedSubtitleFormats ?: emptyList()
                            val extSubFormats = cachedMeta?.externalSubtitleFormats ?: emptyList()
                            val realSubFormats = cachedMeta?.subtitleFormats ?: (embSubFormats + extSubFormats)
                            val fontCount = cachedMeta?.embeddedFontCount ?: 0

                            val videoItem = VideoItem(
                                id = file.hashCode().toLong(),
                                uri = uri,
                                displayName = name,
                                path = path,
                                sizeBytes = size,
                                durationMs = finalDuration,
                                dateModified = date,
                                isNew = false,
                                resolution = finalResolution,
                                framerate = finalFps,
                                hasSubtitles = realSubFormats.isNotEmpty() || embSubFormats.isNotEmpty() || extSubFormats.isNotEmpty(),
                                subtitleFormats = realSubFormats,
                                watchedProgress = 0.0f,
                                embeddedSubtitleFormats = embSubFormats,
                                externalSubtitleFormats = extSubFormats,
                                embeddedFontCount = fontCount
                            )

                            val folderItems = folderMap.getOrPut(folderKey) { mutableListOf() }
                            if (folderItems.none { it.path == path || (it.displayName.equals(name, ignoreCase = true) && it.sizeBytes == size) }) {
                                folderItems.add(videoItem)
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // One bad entry (permission-denied child, broken symlink, a file that was
                    // deleted mid-scan, etc.) must never abort the scan of its siblings.
                    continue
                }
            }
        }
    }
}
