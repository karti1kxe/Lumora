package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.example.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Persistent in-app queue for downloads handled by the yt-dlp binary already bundled with Lumora. */
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
    val etaSeconds: Long = -1L,
    val reportedProgress: Float = 0f,
    val thumbnail: Boolean = true,
    val subtitles: Boolean = false,
    val chapters: Boolean = true,
    val sponsorBlock: Boolean = false
) {
    val progress: Float get() = if (totalBytes > 0L) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else reportedProgress.coerceIn(0f, 1f)
}

enum class DownloadMode { VIDEO, AUDIO }

data class DownloadOptions(
    val title: String,
    val author: String,
    val mode: DownloadMode,
    val quality: String,
    val thumbnail: Boolean = true,
    val subtitles: Boolean = false,
    val chapters: Boolean = true,
    val sponsorBlock: Boolean = false
)

object NetworkDownloadManager {
    private const val CHANNEL_ID = "lumora_downloads"
    private const val PREFS = "lumora_network_downloads"
    private const val HISTORY_KEY = "history_v2"
    private val _items = MutableStateFlow<List<NetworkDownload>>(emptyList())
    val items: StateFlow<List<NetworkDownload>> = _items
    private val contexts = ConcurrentHashMap<String, Context>()
    private val running = ConcurrentHashMap<String, Boolean>()
    private val samples = ConcurrentHashMap<String, Pair<Long, Long>>()

    /** yt-dlp works in app-private staging; successful files are also published to Download/Lumora. */
    fun outputDir(context: Context): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
        "Lumora"
    ).apply { mkdirs() }

    @Synchronized
    fun restore(context: Context) {
        contexts["app"] = context.applicationContext
        if (_items.value.isNotEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(HISTORY_KEY, null)
            ?: prefs.getString("history_v1", null)
            ?: return
        try {
            val array = JSONArray(raw)
            _items.value = (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val path = o.optString("path").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val file = File(path)
                val savedStatus = o.optString("status", "Paused")
                val safeStatus = if (savedStatus == "Downloading" || savedStatus == "Queued") "Paused" else savedStatus
                NetworkDownload(
                    id = o.optString("id", System.currentTimeMillis().toString() + index),
                    sourceUrl = o.optString("url"), title = o.optString("title", file.nameWithoutExtension),
                    author = o.optString("author"),
                    mode = runCatching { DownloadMode.valueOf(o.optString("mode", "VIDEO")) }.getOrDefault(DownloadMode.VIDEO),
                    quality = o.optString("quality", "Best Quality"), file = file, status = safeStatus,
                    bytes = o.optLong("bytes", file.length()), totalBytes = o.optLong("total", -1L),
                    bytesPerSecond = 0L, etaSeconds = o.optLong("eta", -1L),
                    reportedProgress = o.optDouble("progress", 0.0).toFloat(),
                    thumbnail = o.optBoolean("thumbnail", true), subtitles = o.optBoolean("subtitles", false),
                    chapters = o.optBoolean("chapters", true), sponsorBlock = o.optBoolean("sponsorBlock", false)
                )
            }
        } catch (_: Exception) { _items.value = emptyList() }
    }

    @Synchronized
    private fun persist() {
        val context = contexts["app"] ?: return
        val array = JSONArray()
        _items.value.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id).put("url", item.sourceUrl).put("title", item.title).put("author", item.author)
                .put("mode", item.mode.name).put("quality", item.quality).put("path", item.file.absolutePath)
                .put("status", item.status).put("bytes", item.bytes).put("total", item.totalBytes)
                .put("eta", item.etaSeconds).put("progress", item.progress.toDouble())
                .put("thumbnail", item.thumbnail).put("subtitles", item.subtitles).put("chapters", item.chapters)
                .put("sponsorBlock", item.sponsorBlock))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    @Synchronized
    fun start(context: Context, url: String, options: DownloadOptions) {
        restore(context)
        contexts["app"] = context.applicationContext
        val id = System.currentTimeMillis().toString() + "_" + (url.hashCode().toLong().and(0xffffL))
        val title = safeName(options.title.ifBlank { "Lumora download" })
        val file = File(outputDir(context), "$title.%(ext)s")
        val item = NetworkDownload(
            id, url, title, options.author, options.mode, options.quality, file, "Queued",
            thumbnail = options.thumbnail, subtitles = options.subtitles, chapters = options.chapters,
            sponsorBlock = options.sponsorBlock
        )
        _items.value = listOf(item) + _items.value
        persist()
        runDownload(context.applicationContext, item)
    }

    private fun runDownload(context: Context, item: NetworkDownload) {
        if (running.putIfAbsent(item.id, true) != null) return
        contexts["app"] = context
        update(item.id) { it.copy(status = "Downloading", bytesPerSecond = 0L) }
        postNotification(context, item.id.hashCode(), "Download started", item.title, 0)
        Thread({
            try {
                val request = YoutubeDLRequest(item.sourceUrl)
                request.addOption("--no-playlist")
                request.addOption("--newline")
                request.addOption("--no-warnings")
                request.addOption("--continue")
                request.addOption("--no-overwrites")
                request.addOption("--output", File(outputDir(context), safeName(item.title) + ".%(ext)s").absolutePath)
                request.addOption("--write-info-json")
                if (item.thumbnail) { request.addOption("--write-thumbnail"); request.addOption("--embed-thumbnail") }
                if (item.subtitles) { request.addOption("--write-subs"); request.addOption("--write-auto-subs"); request.addOption("--embed-subs") }
                if (item.chapters) request.addOption("--embed-metadata")
                if (item.sponsorBlock) request.addOption("--sponsorblock-remove", "all")
                if (item.mode == DownloadMode.AUDIO) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "5")
                } else {
                    val format = when (item.quality) {
                        "4K (2160p)" -> "bv*[height<=2160]+ba/b[height<=2160]"
                        "2K (1440p)" -> "bv*[height<=1440]+ba/b[height<=1440]"
                        "1080p" -> "bv*[height<=1080]+ba/b[height<=1080]"
                        "720p" -> "bv*[height<=720]+ba/b[height<=720]"
                        "480p" -> "bv*[height<=480]+ba/b[height<=480]"
                        "360p" -> "bv*[height<=360]+ba/b[height<=360]"
                        else -> "bv*+ba/b"
                    }
                    request.addOption("-f", format)
                    request.addOption("--merge-output-format", "mp4")
                }
                // Positional arguments avoid overload-resolution issues across youtubedl-android versions.
                YoutubeDL.getInstance().execute(request, item.id, true) { percent, eta, _ ->
                    update(item.id) { current ->
                        current.copy(reportedProgress = (percent / 100f).coerceIn(0f, 1f), etaSeconds = eta.coerceAtLeast(0L))
                    }
                    val current = _items.value.firstOrNull { it.id == item.id }
                    if (current != null) postNotification(context, item.id.hashCode(), "Downloading · ${current.title}", "${percent.toInt()}% · ${current.etaSeconds.coerceAtLeast(0L)}s left", percent.toInt().coerceIn(0, 100))
                }
                val resultFile = outputDir(context).listFiles()?.filter {
                    it.name.startsWith(safeName(item.title)) && !it.name.endsWith(".part") &&
                        !it.name.endsWith(".json") && !it.name.endsWith(".ytdl") && it.isFile
                }?.maxByOrNull(File::lastModified)
                if (resultFile != null && resultFile.length() > 0L) {
                    publishToDownloads(context, resultFile)
                    update(item.id) { it.copy(file = resultFile, status = "Completed", bytes = resultFile.length(), totalBytes = resultFile.length(), bytesPerSecond = 0L, etaSeconds = 0L, reportedProgress = 1f) }
                    postNotification(context, item.id.hashCode(), "Download complete", resultFile.name, 100)
                } else {
                    update(item.id) { it.copy(status = "Failed: output file not found") }
                    postNotification(context, item.id.hashCode(), "Download failed", item.title, 0)
                }
            } catch (t: Throwable) {
                val status = _items.value.firstOrNull { it.id == item.id }?.status
                if (status != "Paused") {
                    update(item.id) { it.copy(status = "Failed: ${t.message ?: "yt-dlp error"}") }
                    postNotification(context, item.id.hashCode(), "Download failed", t.message ?: item.title, 0)
                }
            } finally {
                running.remove(item.id)
                samples.remove(item.id)
                persist()
            }
        }, "LumoraDownload-${item.id}").start()
    }

    /** Samples the actual growing partial/output file once per second for byte speed and ETA. */
    fun refreshSizes() {
        val now = System.currentTimeMillis()
        _items.value = _items.value.map { item ->
            if (item.status != "Downloading") item else {
                val dir = item.file.parentFile
                val candidate = dir?.listFiles()?.filter {
                    it.name.startsWith(safeName(item.title)) && (it.name.endsWith(".part") ||
                        (!it.name.endsWith(".json") && !it.name.endsWith(".ytdl") && it.isFile))
                }?.maxByOrNull(File::lastModified)
                val bytes = candidate?.length() ?: item.bytes
                val previous = samples.put(item.id, now to bytes)
                val speed = if (previous != null && now > previous.first) ((bytes - previous.second).coerceAtLeast(0L) * 1000L / (now - previous.first)) else 0L
                val info = dir?.listFiles()?.firstOrNull { it.name.startsWith(safeName(item.title)) && it.name.endsWith(".info.json") }
                val total = try {
                    val json = info?.readText()?.let(::JSONObject)
                    json?.optLong("filesize", -1L)?.takeIf { it > 0L }
                        ?: json?.optLong("filesize_approx", -1L)?.takeIf { it > 0L }
                        ?: item.totalBytes
                } catch (_: Exception) { item.totalBytes }
                val eta = if (total > bytes && speed > 0L) (total - bytes) / speed else item.etaSeconds
                item.copy(bytes = bytes, totalBytes = total, bytesPerSecond = speed, etaSeconds = eta)
            }
        }
        persist()
    }

    fun pause(item: NetworkDownload) {
        if (item.status != "Downloading" && item.status != "Queued") return
        update(item.id) { it.copy(status = "Paused", bytesPerSecond = 0L) }
        runCatching { YoutubeDL.getInstance().destroyProcessById(item.id) }
        running.remove(item.id)
        contexts["app"]?.let { postNotification(it, item.id.hashCode(), "Download paused", item.title, (item.progress * 100).toInt()) }
        persist()
    }

    fun resume(context: Context, item: NetworkDownload) {
        if (item.status != "Paused" && !item.status.startsWith("Failed")) return
        val updated = item.copy(status = "Queued")
        update(item.id) { updated }
        runDownload(context.applicationContext, updated)
    }

    fun delete(item: NetworkDownload) {
        if (running.containsKey(item.id)) pause(item)
        item.file.delete()
        File(item.file.absolutePath + ".part").delete()
        outputDir(contexts["app"] ?: return).listFiles()?.filter { it.name.startsWith(safeName(item.title)) }?.forEach { it.delete() }
        _items.value = _items.value.filterNot { it.id == item.id }
        persist()
        contexts["app"]?.let { cancelNotification(it, item.id.hashCode()) }
    }

    fun deleteSelected(ids: Set<String>) { _items.value.filter { it.id in ids }.toList().forEach(::delete) }
    fun deleteCompleted() { _items.value.filter { it.status == "Completed" }.toList().forEach(::delete) }

    private fun update(id: String, transform: (NetworkDownload) -> NetworkDownload) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private fun safeName(value: String): String = value.trim().ifBlank { "Lumora download" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(140)

    private fun publishToDownloads(context: Context, file: File) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val mime = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; else -> "video/mp4"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lumora")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
        } catch (_: Exception) { }
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

    private fun cancelNotification(context: Context, id: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }
}
