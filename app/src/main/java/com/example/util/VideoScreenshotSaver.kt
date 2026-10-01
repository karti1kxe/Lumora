package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.state.PlayerSettings
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater

object VideoScreenshotSaver {
    suspend fun captureAndSaveCleanVideoFrame(
        context: Context,
        uri: Uri?,
        path: String?,
        positionMs: Long,
        settings: PlayerSettings = PlayerSettings()
    ): Result<String> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (!path.isNullOrBlank() && File(path).exists()) retriever.setDataSource(path)
            else if (uri != null) retriever.setDataSource(context, uri)
            else return@withContext Result.failure(Exception("Video source not available"))

            val timeUs = positionMs.coerceAtLeast(0L) * 1000L
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return@withContext Result.failure(Exception("Unable to decode clean video frame"))
            saveBitmapFrame(context, bitmap, settings, "Video")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    /** Saves the already-rendered player surface, preserving subtitles and other video-layer pixels. */
    suspend fun saveBitmapFrame(
        context: Context,
        frameBitmap: Bitmap,
        settings: PlayerSettings = PlayerSettings(),
        title: String = "Video",
        positionMs: Long = 0L
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val titleSafe = settings.screenshotFilenameTemplate
                .replace("{title}", title.ifBlank { "Video" })
                .replace("{time}", timeStamp)
                .replace("{seconds}", (positionMs / 1000L).toString())
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "Screenshot_$timeStamp" }
            val extension = when (settings.screenshotFormat.uppercase(Locale.US)) {
                "JPG" -> "jpg"
                "WEBP" -> "webp"
                else -> "png"
            }
            val fileName = "$titleSafe.$extension"
            val mimeType = when (extension) {
                "jpg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Screenshots")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Failed to register image in MediaStore"))
                try {
                    context.contentResolver.openOutputStream(imageUri)?.use { out ->
                        writeEncoded(frameBitmap, extension, settings, out)
                    } ?: return@withContext Result.failure(Exception("Unable to open screenshot output"))
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(imageUri, values, null, null)
                } catch (e: Throwable) {
                    context.contentResolver.delete(imageUri, null, null)
                    throw e
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val screenshotsDir = File(picturesDir, "Screenshots").apply { mkdirs() }
                val targetFile = File(screenshotsDir, fileName)
                FileOutputStream(targetFile).use { out -> writeEncoded(frameBitmap, extension, settings, out) }
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            }
            Result.success("Screenshot saved cleanly to Gallery ($fileName)")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun writeEncoded(bitmap: Bitmap, extension: String, settings: PlayerSettings, out: java.io.OutputStream) {
        when (extension) {
            "png" -> writePng(bitmap, settings.pngCompression.coerceIn(0, 9), out)
            "jpg" -> bitmap.compress(Bitmap.CompressFormat.JPEG, settings.jpegWebpQuality.coerceIn(1, 100), out)
            "webp" -> bitmap.compress(Bitmap.CompressFormat.WEBP, settings.jpegWebpQuality.coerceIn(1, 100), out)
        }
    }

    /** Android ignores Bitmap.compress() quality for PNG, so encode PNG explicitly to honor 0..9. */
    private fun writePng(bitmap: Bitmap, compressionLevel: Int, out: java.io.OutputStream) {
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val width = src.width
        val height = src.height
        val pixels = IntArray(width)
        val raw = ByteArrayOutputStream(width * 4 * height / 2)
        for (y in 0 until height) {
            src.getPixels(pixels, 0, width, 0, y, width, 1)
            raw.write(0) // PNG filter: None
            for (x in 0 until width) {
                val c = pixels[x]
                raw.write((c ushr 16) and 0xFF)
                raw.write((c ushr 8) and 0xFF)
                raw.write(c and 0xFF)
                raw.write((c ushr 24) and 0xFF)
            }
        }
        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(compressionLevel)
        try {
            deflater.setInput(raw.toByteArray())
            deflater.finish()
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count > 0) compressed.write(buffer, 0, count)
            }
        } finally {
            deflater.end()
        }

        val data = DataOutputStream(out)
        data.write(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        ))
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).use { h ->
            h.writeInt(width); h.writeInt(height); h.writeByte(8); h.writeByte(6); h.writeByte(0); h.writeByte(0); h.writeByte(0)
        }
        writePngChunk(data, "IHDR", ihdr.toByteArray())
        writePngChunk(data, "IDAT", compressed.toByteArray())
        writePngChunk(data, "IEND", ByteArray(0))
        data.flush()
        if (src !== bitmap) src.recycle()
    }

    private fun writePngChunk(out: DataOutputStream, type: String, payload: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(payload.size)
        out.write(typeBytes)
        out.write(payload)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        out.writeInt(crc.value.toInt())
    }
}
