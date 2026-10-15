package com.example.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Moves a finished download from the app's private download folder into the public Movies/Lumora (or Music/Lumora)
 * folder through MediaStore, so it shows up in the app's library and in the gallery and survives an app uninstall.
 */
object DownloadFileSaver {
    private const val FOLDER = "Lumora"

    data class Saved(val path: String, val uri: String, val size: Long)

    fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return cleaned.take(120).ifBlank { "download" }
    }

    private fun mimeFor(name: String, isAudio: Boolean): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val fromMap = if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
        if (fromMap != null) return fromMap
        return when (ext) {
            "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            else -> if (isAudio) "audio/mp4" else "video/mp4"
        }
    }

    fun saveToPublic(context: Context, src: File, displayName: String, isAudio: Boolean): Saved? {
        val app = context.applicationContext
        val name = safeName(displayName)
        val mime = mimeFor(name, isAudio)
        val dirName = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = app.contentResolver
                val collection = if (isAudio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirName/$FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { input -> input.copyTo(out, 1 shl 20) }
                    } ?: run {
                        resolver.delete(uri, null, null)
                        return null
                    }
                } catch (t: Throwable) {
                    try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                    throw t
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                var path: String? = null
                try {
                    resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                        if (c.moveToFirst()) path = c.getString(0)
                    }
                } catch (_: Throwable) {}
                Saved(path ?: uri.toString(), uri.toString(), src.length())
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(dirName), FOLDER).apply { mkdirs() }
                var target = File(dir, name)
                var n = 1
                val base = name.substringBeforeLast('.', name)
                val ext = name.substringAfterLast('.', "")
                while (target.exists()) {
                    target = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
                    n++
                }
                src.copyTo(target, overwrite = false, bufferSize = 1 shl 20)
                MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf(mime), null)
                Saved(target.absolutePath, Uri.fromFile(target).toString(), target.length())
            }
        } catch (t: Throwable) {
            android.util.Log.e("DownloadFileSaver", "could not save $name", t)
            null
        }
    }

    /** true while the finished file of [item] still exists (the user may have deleted it with a file manager). */
    fun exists(context: Context, item: DownloadItem): Boolean {
        val uri = item.fileUri
        if (uri != null && uri.startsWith("content://")) {
            return try {
                context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false
            } catch (_: Throwable) {
                false
            }
        }
        val path = item.filePath ?: return false
        return File(path).exists()
    }

    fun delete(context: Context, item: DownloadItem) {
        val uri = item.fileUri
        if (uri != null && uri.startsWith("content://")) {
            try { context.contentResolver.delete(Uri.parse(uri), null, null) } catch (_: Throwable) {}
        }
        val path = item.filePath
        if (path != null && !path.startsWith("content://")) {
            try { File(path).delete() } catch (_: Throwable) {}
        }
    }
}
