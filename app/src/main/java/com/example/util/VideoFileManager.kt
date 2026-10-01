package com.example.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.ui.screens.VideoFolder
import com.example.ui.screens.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object VideoFileManager {

    val KNOWN_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "mov", "flv", "ts", "m4v",
        "3gp", "wmv", "mpg", "mpeg", "m2ts", "mts", "vob", "ogv",
        "3g2", "f4v", "asf", "rm", "rmvb", "divx"
    )

    fun isVideoFile(file: File): Boolean {
        if (file.isDirectory) return false
        val ext = file.extension.lowercase(Locale.ROOT)
        return KNOWN_VIDEO_EXTENSIONS.contains(ext)
    }

    /**
     * Delete video files within a folder safely.
     * CRITICAL USER REQUIREMENT: Only video files within the folder are deleted.
     * Any non-video files are preserved. If the folder becomes empty of everything, it may be removed.
     */
    suspend fun deleteFolderVideos(
        context: Context,
        folder: VideoFolder,
        onProgress: ((fraction: Float, completedCount: Int, totalCount: Int, itemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        val targetDir = File(folder.path)
        if (targetDir.exists() && targetDir.isDirectory) {
            val videoFiles = mutableListOf<File>()
            targetDir.walkTopDown().forEach { file ->
                if (file.isFile && isVideoFile(file)) {
                    videoFiles.add(file)
                }
            }
            val total = videoFiles.size.coerceAtLeast(1)
            videoFiles.forEachIndexed { index, file ->
                val path = file.absolutePath
                onProgress?.invoke(index.toFloat() / total, index, total, file.name)
                val deleted = try {
                    file.delete()
                } catch (_: Exception) {
                    false
                }
                if (deleted) {
                    deletedCount++
                    cleanMediaStoreRecord(context, path)
                }
                onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, file.name)
            }
            try {
                if (targetDir.listFiles()?.isEmpty() == true) {
                    targetDir.delete()
                }
            } catch (_: Exception) {}
        } else {
            val allVids = folder.getAllVideos()
            val total = allVids.size.coerceAtLeast(1)
            allVids.forEachIndexed { index, vid ->
                onProgress?.invoke(index.toFloat() / total, index, total, vid.displayName)
                val f = File(vid.path)
                if (f.exists() && isVideoFile(f)) {
                    if (f.delete()) {
                        deletedCount++
                        cleanMediaStoreRecord(context, vid.path)
                    }
                }
                onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, vid.displayName)
            }
        }
        deletedCount
    }

    /**
     * Delete single or multiple video files.
     */
    suspend fun deleteVideos(
        context: Context,
        videos: List<VideoItem>,
        onProgress: ((fraction: Float, completedCount: Int, totalCount: Int, itemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        val total = videos.size.coerceAtLeast(1)
        videos.forEachIndexed { index, video ->
            onProgress?.invoke(index.toFloat() / total, index, total, video.displayName)
            val file = File(video.path)
            var deleted = false
            if (file.exists() && isVideoFile(file)) {
                deleted = try {
                    file.delete()
                } catch (_: Exception) {
                    false
                }
            }
            if (!deleted && video.id > 0) {
                try {
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        video.id
                    )
                    val rows = context.contentResolver.delete(contentUri, null, null)
                    if (rows > 0) deleted = true
                } catch (_: Exception) {}
            }

            if (deleted) {
                count++
                cleanMediaStoreRecord(context, video.path)
            }
            onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, video.displayName)
        }
        count
    }

    /**
     * Rename a single video file.
     */
    suspend fun renameVideo(context: Context, video: VideoItem, newNameWithExt: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(video.path)
        if (!src.exists()) return@withContext false

        val parent = src.parentFile ?: return@withContext false
        val dest = File(parent, newNameWithExt)
        if (dest.exists() && dest.absolutePath != src.absolutePath) {
            return@withContext false
        }

        val success = try {
            java.nio.file.Files.move(src.toPath(), dest.toPath())
            true
        } catch (_: Exception) {
            src.renameTo(dest)
        }
        if (success) {
            scanNewMediaFile(context, dest.absolutePath)
            scanNewMediaFile(context, src.absolutePath)
        }
        success
    }

    /**
     * Rename a folder.
     */
    suspend fun renameFolder(context: Context, folder: VideoFolder, newFolderName: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(folder.path)
        if (!src.exists()) return@withContext false

        val parent = src.parentFile ?: return@withContext false
        val dest = File(parent, newFolderName)
        if (dest.exists() && dest.absolutePath != src.absolutePath) {
            return@withContext false
        }

        val success = try {
            java.nio.file.Files.move(src.toPath(), dest.toPath())
            true
        } catch (_: Exception) {
            src.renameTo(dest)
        }
        if (success) {
            scanNewMediaFile(context, dest.absolutePath)
            scanNewMediaFile(context, src.absolutePath)
        }
        success
    }

    /**
     * Move video files to destination directory with continuous accurate progress.
     */
    suspend fun moveVideos(
        context: Context,
        videos: List<VideoItem>,
        destDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val totalBytes = videos.sumOf {
            val f = File(it.path)
            if (f.exists()) f.length().coerceAtLeast(1024L) else 1024L
        }.coerceAtLeast(1L)

        var bytesDone = 0L
        var moved = 0

        onProgress?.invoke(0f, 0L, totalBytes, if (videos.isNotEmpty()) videos.first().displayName else "")

        for (vid in videos) {
            val src = File(vid.path)
            val fileLen = if (src.exists()) src.length().coerceAtLeast(1024L) else 1024L
            if (src.exists() && isVideoFile(src)) {
                var dest = File(destDir, src.name)
                if (dest.exists()) {
                    val base = src.nameWithoutExtension
                    val ext = src.extension
                    var index = 1
                    do {
                        dest = File(destDir, if (ext.isBlank()) "${base}_$index" else "${base}_$index.$ext")
                        index++
                    } while (dest.exists())
                }
                val ok = if (src.renameTo(dest)) {
                    bytesDone += fileLen
                    onProgress?.invoke(
                        (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f),
                        bytesDone,
                        totalBytes,
                        vid.displayName
                    )
                    true
                } else {
                    // Fallback copy + delete
                    val copied = copySingleFile(src, dest) { chunk ->
                        bytesDone += chunk
                        onProgress?.invoke(
                            (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f),
                            bytesDone,
                            totalBytes,
                            vid.displayName
                        )
                    }
                    if (copied) {
                        src.delete()
                        true
                    } else false
                }
                if (ok) {
                    moved++
                    cleanMediaStoreRecord(context, src.absolutePath)
                    scanNewMediaFile(context, dest.absolutePath)
                }
            } else {
                bytesDone += fileLen
            }
        }
        onProgress?.invoke(1f, totalBytes, totalBytes, "")
        moved
    }

    /**
     * Copy video files to destination directory with continuous accurate progress.
     */
    suspend fun copyVideos(
        context: Context,
        videos: List<VideoItem>,
        destDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val totalBytes = videos.sumOf {
            val f = File(it.path)
            if (f.exists()) f.length().coerceAtLeast(1024L) else 1024L
        }.coerceAtLeast(1L)

        var bytesDone = 0L
        var copied = 0

        onProgress?.invoke(0f, 0L, totalBytes, if (videos.isNotEmpty()) videos.first().displayName else "")

        for (vid in videos) {
            val src = File(vid.path)
            if (src.exists() && isVideoFile(src)) {
                var targetName = src.name
                var dest = File(destDir, targetName)
                if (dest.exists()) {
                    val nameWithoutExt = src.nameWithoutExtension
                    val ext = src.extension
                    targetName = "${nameWithoutExt}_copy.${ext}"
                    dest = File(destDir, targetName)
                }
                if (copySingleFile(src, dest) { chunk ->
                    bytesDone += chunk
                    onProgress?.invoke(
                        (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f),
                        bytesDone,
                        totalBytes,
                        vid.displayName
                    )
                }) {
                    copied++
                    scanNewMediaFile(context, dest.absolutePath)
                }
            }
        }
        onProgress?.invoke(1f, totalBytes, totalBytes, "")
        copied
    }

    /**
     * Move an entire folder to target directory with continuous accurate progress.
     */
    suspend fun moveFolder(
        context: Context,
        folder: VideoFolder,
        targetParentDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val src = File(folder.path)
        if (!src.exists()) return@withContext false
        val dest = File(targetParentDir, src.name)
        if (dest.exists()) return@withContext false

        val videoFiles = if (src.isDirectory) {
            src.walkTopDown().filter { it.isFile && isVideoFile(it) }.toList()
        } else emptyList()

        val totalBytes = videoFiles.sumOf { it.length().coerceAtLeast(1024L) }.coerceAtLeast(1024L)
        onProgress?.invoke(0f, 0L, totalBytes, folder.name)

        val ok = if (src.renameTo(dest)) {
            onProgress?.invoke(1f, totalBytes, totalBytes, folder.name)
            scanNewMediaFile(context, dest.absolutePath)
            true
        } else {
            // Cross-volume move: copy videos then delete originals
            dest.mkdirs()
            var bytesDone = 0L
            var anyCopied = false
            for (file in videoFiles) {
                val rel = file.relativeTo(src)
                val targetFile = File(dest, rel.path)
                targetFile.parentFile?.mkdirs()
                if (copySingleFile(file, targetFile) { chunk ->
                    bytesDone += chunk
                    onProgress?.invoke(
                        (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f),
                        bytesDone,
                        totalBytes,
                        file.name
                    )
                }) {
                    anyCopied = true
                    scanNewMediaFile(context, targetFile.absolutePath)
                    file.delete()
                    cleanMediaStoreRecord(context, file.absolutePath)
                }
            }
            if (anyCopied) {
                src.walkBottomUp().forEach {
                    if (it.isDirectory && it.listFiles()?.isEmpty() == true) {
                        try { it.delete() } catch (_: Exception) {}
                    }
                }
                try { if (src.listFiles()?.isEmpty() == true) src.delete() } catch (_: Exception) {}
                true
            } else false
        }
        onProgress?.invoke(1f, totalBytes, totalBytes, folder.name)
        ok
    }

    /**
     * Copy an entire folder to target directory with continuous accurate progress.
     */
    suspend fun copyFolder(
        context: Context,
        folder: VideoFolder,
        targetParentDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val src = File(folder.path)
        if (!src.exists()) return@withContext false
        var dest = File(targetParentDir, src.name)
        if (dest.exists()) {
            dest = File(targetParentDir, "${src.name}_copy")
        }
        dest.mkdirs()

        val videoFiles = if (src.isDirectory) {
            src.walkTopDown().filter { it.isFile && isVideoFile(it) }.toList()
        } else emptyList()

        val totalBytes = videoFiles.sumOf { it.length().coerceAtLeast(1024L) }.coerceAtLeast(1024L)
        var bytesDone = 0L
        var anyCopied = false

        onProgress?.invoke(0f, 0L, totalBytes, folder.name)

        for (file in videoFiles) {
            val rel = file.relativeTo(src)
            val targetFile = File(dest, rel.path)
            targetFile.parentFile?.mkdirs()
            if (copySingleFile(file, targetFile) { chunk ->
                bytesDone += chunk
                onProgress?.invoke(
                    (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f),
                    bytesDone,
                    totalBytes,
                    file.name
                )
            }) {
                anyCopied = true
                scanNewMediaFile(context, targetFile.absolutePath)
            }
        }
        onProgress?.invoke(1f, totalBytes, totalBytes, folder.name)
        anyCopied
    }

    private fun copySingleFile(
        src: File,
        dest: File,
        onChunkCopied: ((bytesCopiedDelta: Int) -> Unit)? = null
    ): Boolean {
        return try {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        onChunkCopied?.invoke(bytesRead)
                    }
                    output.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanMediaStoreRecord(context: Context, path: String) {
        try {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.delete(
                uri,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path)
            )
        } catch (_: Exception) {}
    }

    private fun scanNewMediaFile(context: Context, path: String) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(path),
                null
            ) { _, _ -> }
        } catch (_: Exception) {}
    }

    /**
     * Share selected videos via Android share sheet.
     */
    fun shareVideos(context: Context, videos: List<VideoItem>) {
        if (videos.isEmpty()) return

        try {
            if (videos.size == 1) {
                val video = videos.first()
                val file = File(video.path)
                val shareUri = if (file.exists()) {
                    try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                    } catch (_: Exception) {
                        video.uri
                    }
                } else {
                    video.uri
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, video.displayName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
            } else {
                val uris = ArrayList<Uri>()
                for (v in videos) {
                    val f = File(v.path)
                    val uri = if (f.exists()) {
                        try {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                f
                            )
                        } catch (_: Exception) {
                            v.uri
                        }
                    } else {
                        v.uri
                    }
                    uris.add(uri)
                }

                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "video/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share ${videos.size} Videos"))
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
