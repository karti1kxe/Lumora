package com.example.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.ui.screens.AudioFolderItem
import com.example.ui.screens.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object AudioFileManager {

    val KNOWN_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma",
        "amr", "awb", "mid", "midi", "aiff", "aif", "ape", "alac", "ac3",
        "eac3", "mka", "3gp", "3gpp"
    )

    fun isAudioFile(file: File): Boolean {
        if (file.isDirectory) return false
        val ext = file.extension.lowercase(Locale.ROOT)
        return KNOWN_AUDIO_EXTENSIONS.contains(ext)
    }

    /**
     * Delete audio files within an audio folder safely.
     */
    suspend fun deleteFolderAudios(
        context: Context,
        folder: AudioFolderItem,
        onProgress: ((fraction: Float, completedCount: Int, totalCount: Int, itemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        val targetDir = File(folder.path)
        if (targetDir.exists() && targetDir.isDirectory) {
            val audioFiles = mutableListOf<File>()
            targetDir.walkTopDown().forEach { file ->
                if (file.isFile && isAudioFile(file)) {
                    audioFiles.add(file)
                }
            }
            val total = audioFiles.size.coerceAtLeast(1)
            audioFiles.forEachIndexed { index, file ->
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
        }
        deletedCount
    }

    /**
     * Delete single or multiple audio files.
     */
    suspend fun deleteAudioTracks(
        context: Context,
        tracks: List<AudioTrackItem>,
        onProgress: ((fraction: Float, completedCount: Int, totalCount: Int, itemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        val total = tracks.size.coerceAtLeast(1)
        tracks.forEachIndexed { index, track ->
            onProgress?.invoke(index.toFloat() / total, index, total, track.title)
            val file = File(track.path)
            var deleted = false
            if (file.exists() && isAudioFile(file)) {
                deleted = try {
                    file.delete()
                } catch (_: Exception) {
                    false
                }
            }
            if (!deleted && track.id > 0) {
                try {
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        track.id
                    )
                    val rows = context.contentResolver.delete(contentUri, null, null)
                    if (rows > 0) deleted = true
                } catch (_: Exception) {}
            }

            if (deleted) {
                count++
                cleanMediaStoreRecord(context, track.path)
            }
            onProgress?.invoke((index + 1).toFloat() / total, index + 1, total, track.title)
        }
        count
    }

    /**
     * Rename a single audio track file.
     */
    suspend fun renameAudio(context: Context, track: AudioTrackItem, newNameWithExt: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(track.path)
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
     * Rename an audio folder.
     */
    suspend fun renameFolder(context: Context, folder: AudioFolderItem, newFolderName: String): Boolean = withContext(Dispatchers.IO) {
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
     * Move audio files to destination directory with continuous accurate progress.
     */
    suspend fun moveAudioTracks(
        context: Context,
        tracks: List<AudioTrackItem>,
        destDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val totalBytes = tracks.sumOf {
            val f = File(it.path)
            if (f.exists()) f.length().coerceAtLeast(1024L) else 1024L
        }.coerceAtLeast(1L)

        var bytesDone = 0L
        var moved = 0

        onProgress?.invoke(0f, 0L, totalBytes, if (tracks.isNotEmpty()) tracks.first().title else "")

        for (track in tracks) {
            val src = File(track.path)
            val fileLen = if (src.exists()) src.length().coerceAtLeast(1024L) else 1024L
            if (src.exists() && isAudioFile(src)) {
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
                        track.title
                    )
                    true
                } else {
                    val copied = copySingleFile(src, dest) { chunk ->
                        bytesDone += chunk
                        onProgress?.invoke(
                            (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f),
                            bytesDone,
                            totalBytes,
                            track.title
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
     * Copy audio files to destination directory with continuous accurate progress.
     */
    suspend fun copyAudioTracks(
        context: Context,
        tracks: List<AudioTrackItem>,
        destDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val totalBytes = tracks.sumOf {
            val f = File(it.path)
            if (f.exists()) f.length().coerceAtLeast(1024L) else 1024L
        }.coerceAtLeast(1L)

        var bytesDone = 0L
        var copied = 0

        onProgress?.invoke(0f, 0L, totalBytes, if (tracks.isNotEmpty()) tracks.first().title else "")

        for (track in tracks) {
            val src = File(track.path)
            if (src.exists() && isAudioFile(src)) {
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
                        track.title
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
     * Move an entire audio folder to target directory with continuous accurate progress.
     */
    suspend fun moveAudioFolder(
        context: Context,
        folder: AudioFolderItem,
        targetParentDir: File,
        onProgress: ((progressFraction: Float, bytesCopied: Long, totalBytes: Long, currentItemName: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val src = File(folder.path)
        if (!src.exists()) return@withContext false
        val dest = File(targetParentDir, src.name)
        if (dest.exists()) return@withContext false

        val audioFiles = if (src.isDirectory) {
            src.walkTopDown().filter { it.isFile && isAudioFile(it) }.toList()
        } else emptyList()

        val totalBytes = audioFiles.sumOf { it.length().coerceAtLeast(1024L) }.coerceAtLeast(1024L)
        onProgress?.invoke(0f, 0L, totalBytes, folder.name)

        val ok = try {
            java.nio.file.Files.move(src.toPath(), dest.toPath())
            true
        } catch (_: Exception) {
            if (copyAudioFolder(context, folder, targetParentDir, onProgress)) {
                src.walkBottomUp().forEach { file ->
                    if (file.isFile && isAudioFile(file)) {
                        try { file.delete() } catch (_: Exception) {}
                        cleanMediaStoreRecord(context, file.absolutePath)
                    }
                }
                src.walkBottomUp().forEach { file ->
                    if (file.isDirectory && file != src) {
                        try { if (file.listFiles()?.isEmpty() == true) file.delete() } catch (_: Exception) {}
                    }
                }
                try { if (src.listFiles()?.isEmpty() == true) src.delete() } catch (_: Exception) {}
                true
            } else false
        }
        if (ok) {
            scanNewMediaFile(context, dest.absolutePath)
            onProgress?.invoke(1f, totalBytes, totalBytes, folder.name)
        }
        ok
    }

    /**
     * Copy an entire audio folder to target directory with continuous accurate progress.
     */
    suspend fun copyAudioFolder(
        context: Context,
        folder: AudioFolderItem,
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

        val audioFiles = if (src.isDirectory) {
            src.walkTopDown().filter { it.isFile && isAudioFile(it) }.toList()
        } else emptyList()

        val totalBytes = audioFiles.sumOf { it.length().coerceAtLeast(1024L) }.coerceAtLeast(1024L)
        var bytesDone = 0L
        var anyCopied = false

        onProgress?.invoke(0f, 0L, totalBytes, folder.name)

        for (file in audioFiles) {
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
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.delete(
                uri,
                "${MediaStore.Audio.Media.DATA} = ?",
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
     * Share selected audio tracks via Android share sheet.
     */
    fun shareAudio(context: Context, tracks: List<AudioTrackItem>) {
        if (tracks.isEmpty()) return

        try {
            val onlineTracks = tracks.filter { it.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) }
            val localTracks = tracks.filter { !it.path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX) }

            // If only online tracks, share as text link(s)
            if (localTracks.isEmpty() && onlineTracks.isNotEmpty()) {
                val text = if (onlineTracks.size == 1) {
                    val t = onlineTracks.first()
                    val url = t.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                    "Listen to \"${t.title}\" by ${t.artist}: $url"
                } else {
                    onlineTracks.joinToString("\n\n") { t ->
                        val url = t.path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                        "\"${t.title}\" by ${t.artist}: $url"
                    }
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, if (onlineTracks.size == 1) onlineTracks.first().title else "Music Tracks")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Music"))
                return
            }

            if (tracks.size == 1) {
                val track = tracks.first()
                val file = File(track.path)
                val shareUri = if (file.exists()) {
                    try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                    } catch (_: Exception) {
                        track.uri
                    }
                } else {
                    track.uri
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
            } else {
                val uris = ArrayList<Uri>()
                for (t in tracks) {
                    val f = File(t.path)
                    val uri = if (f.exists()) {
                        try {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                f
                            )
                        } catch (_: Exception) {
                            t.uri
                        }
                    } else {
                        t.uri
                    }
                    uris.add(uri)
                }

                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share ${tracks.size} Audio Tracks"))
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
