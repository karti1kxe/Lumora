package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.example.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Small in-app download queue backed by the yt-dlp already bundled by Lumora. */
data class NetworkDownload(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val author: String,
    val mode: DownloadMode,
    val quality: String,
    val file: File,
    val status: String = "Queued",
    val bytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L
) {
    val progress: Float get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}
enum class DownloadMode { VIDEO, AUDIO }

data class DownloadOptions(val title: String, val author: String, val mode: DownloadMode, val quality: String, val thumbnail: Boolean = true, val subtitles: Boolean = false, val chapters: Boolean = true, val sponsorBlock: Boolean = false)

object NetworkDownloadManager {
    private const val CHANNEL_ID = "lumora_downloads"
    private val counter = AtomicInteger(1000)
    private val running = ConcurrentHashMap<String, Boolean>()
    private val samples = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val _items = MutableStateFlow<List<NetworkDownload>>(emptyList())
    val items: StateFlow<List<NetworkDownload>> = _items

    fun outputDir(context: Context): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Lumora").apply { mkdirs() }

    fun start(context: Context, url: String, options: DownloadOptions) {
        val id = System.currentTimeMillis().toString()
        val safeTitle = options.title.trim().ifBlank { "Lumora download" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(150)
        val extension = if (options.mode == DownloadMode.AUDIO) "mp3" else "%(ext)s"
        val out = File(outputDir(context), "$safeTitle.$extension")
        val initial = NetworkDownload(id, url, safeTitle, options.author, options.mode, options.quality, out, "Queued")
        _items.value = listOf(initial) + _items.value
        running[id] = true
        postNotification(context, id.hashCode(), "Download queued", safeTitle, 0)
        Thread {
            update(id) { it.copy(status = "Downloading") }
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("--no-playlist")
                request.addOption("--newline")
                request.addOption("--no-warnings")
                request.addOption("--continue")
                request.addOption("--output", File(outputDir(context), safeTitle + ".%(ext)s").absolutePath)
                request.addOption("--write-info-json")
                if (options.thumbnail) { request.addOption("--write-thumbnail"); request.addOption("--embed-thumbnail") }
                if (options.subtitles) { request.addOption("--write-subs"); request.addOption("--write-auto-subs"); request.addOption("--embed-subs") }
                if (options.chapters) request.addOption("--embed-metadata")
                if (options.sponsorBlock) request.addOption("--sponsorblock-remove", "all")
                if (options.mode == DownloadMode.AUDIO) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "5")
                } else {
                    val format = when (options.quality) {
                        "Best Quality" -> "bv*+ba/b"
                        "4K (2160p)" -> "bv*[height<=2160]+ba/b[height<=2160]"
                        "2K (1440p)" -> "bv*[height<=1440]+ba/b[height<=1440]"
                        "1080p" -> "bv*[height<=1080]+ba/b[height<=1080]"
                        "720p" -> "bv*[height<=720]+ba/b[height<=720]"
                        "480p" -> "bv*[height<=480]+ba/b[height<=480]"
                        else -> "bv*+ba/b"
                    }
                    request.addOption("-f", format)
                    request.addOption("--merge-output-format", "mp4")
                }
                // Execute on a worker thread. File size is sampled by the UI's state refresh loop.
                YoutubeDL.getInstance().execute(request, null, id)
                val resultFile = outputDir(context).listFiles()?.filter { it.name.startsWith(safeTitle) && !it.name.endsWith(".part") && !it.name.endsWith(".json") }?.maxByOrNull { it.lastModified() }
                if (resultFile != null) {
                    publishToDownloads(context, resultFile)
                    update(id) { it.copy(file = resultFile, status = "Completed", bytes = resultFile.length(), totalBytes = resultFile.length(), bytesPerSecond = 0L, etaSeconds = 0L) }
                    postNotification(context, id.hashCode(), "Download complete", resultFile.name, 100)
                } else {
                    update(id) { it.copy(status = "Failed: output file not found") }
                    postNotification(context, id.hashCode(), "Download failed", safeTitle, 0)
                }
            } catch (t: Throwable) {
                update(id) { it.copy(status = "Failed: ${t.message ?: "yt-dlp error"}") }
                postNotification(context, id.hashCode(), "Download failed", t.message ?: safeTitle, 0)
            } finally { running.remove(id) }
        }.start()
    }

    fun refreshSizes() {
        val now = System.currentTimeMillis()
        _items.value = _items.value.map { item ->
            if (item.status == "Downloading") {
                val dir = item.file.parentFile
                val candidate = dir?.listFiles()?.filter { it.name.startsWith(item.title) && (it.name.endsWith(".part") || !it.name.endsWith(".json")) }?.maxByOrNull { it.lastModified() }
                val bytes = candidate?.length() ?: item.bytes
                val previous = samples.put(item.id, now to bytes)
                val speed = if (previous != null && now > previous.first) ((bytes - previous.second).coerceAtLeast(0L) * 1000L / (now - previous.first)).coerceAtLeast(0L) else 0L
                val info = dir?.listFiles()?.firstOrNull { it.name.startsWith(item.title) && it.name.endsWith(".info.json") }
                val total = try {
                    val json = info?.readText()?.let { org.json.JSONObject(it) }
                    json?.optLong("filesize", -1L)?.takeIf { it > 0 } ?: json?.optLong("filesize_approx", -1L)?.takeIf { it > 0 } ?: item.totalBytes
                } catch (_: Throwable) { item.totalBytes }
                val eta = if (total > bytes && speed > 0) (total - bytes) / speed else -1L
                item.copy(bytes = bytes, totalBytes = total, bytesPerSecond = speed, etaSeconds = eta)
            } else item
        }
    }

    fun delete(item: NetworkDownload) {
        item.file.delete()
        _items.value = _items.value.filterNot { it.id == item.id }
    }
    fun deleteCompleted() { _items.value.filter { it.status == "Completed" }.forEach { it.file.delete() }; _items.value = _items.value.filterNot { it.status == "Completed" } }
    private fun update(id: String, transform: (NetworkDownload) -> NetworkDownload) { _items.value = _items.value.map { if (it.id == id) transform(it) else it } }

    private fun publishToDownloads(context: Context, file: File) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (file.extension.equals("mp3", true)) "audio/mpeg" else "video/mp4")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lumora")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
        } catch (_: Throwable) { }
    }

    private fun postNotification(context: Context, id: Int, title: String, text: String, progress: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Lumora downloads", NotificationManager.IMPORTANCE_LOW))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_abouticon).setContentTitle(title).setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(progress in 1..99)
        if (progress in 1..99) builder.setProgress(100, progress, false)
        manager.notify(id, builder.build())
    }
}
