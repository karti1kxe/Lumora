package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, PROCESSING, COMPLETED, FAILED }

/** One entry of the Download page: a link (yt-dlp) or one video of a torrent. */
data class DownloadItem(
    val id: String,
    /** The pasted link / magnet / .torrent URL, or "torrent://<hash>/<fileIndex>" once a torrent file is chosen. */
    val source: String,
    val isTorrent: Boolean,
    val title: String,
    val subtitle: String? = null,
    val isAudio: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0L,
    /** 0 = not known (yet). */
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    /** Seconds left, -1 = not known. */
    val etaSec: Long = -1L,
    /** Short status line ("Merging…") while running, the error text when [status] is FAILED. */
    val note: String? = null,
    val filePath: String? = null,
    val fileUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** yt-dlp "-f" expression chosen in the options sheet (null = default quality). */
    val formatSpec: String? = null,
    /** Chosen output container (mp4 / mkv / webm / mp3 / m4a / opus / wav), null = default. */
    val container: String? = null
) {
    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PROCESSING

    val fraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/** Human readable sizes / speeds / times shared by the Download page and the notification. */
object DownloadFormat {
    fun bytes(b: Long): String {
        if (b <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        if (i == 0) return "$b B"
        val fmt = if (v >= 100.0) "%.0f %s" else if (v >= 10.0) "%.1f %s" else "%.2f %s"
        return String.format(Locale.US, fmt, v, units[i])
    }

    fun speed(bps: Long): String = bytes(bps) + "/s"

    fun eta(sec: Long): String {
        if (sec < 0L) return "--"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** "12.4 MB / 80.2 MB" (total shown as "?" while unknown). */
    fun sizeLine(item: DownloadItem): String =
        bytes(item.downloadedBytes) + " / " + (if (item.totalBytes > 0L) bytes(item.totalBytes) else "?")

    /** "3.2 MB/s • 21s left" for a running download. */
    fun speedLine(item: DownloadItem): String {
        val parts = ArrayList<String>()
        parts.add(speed(item.speedBps))
        if (item.etaSec >= 0L) parts.add(eta(item.etaSec) + " left")
        return parts.joinToString(" • ")
    }
}

/**
 * Persistent list of downloads (one small JSON string in SharedPreferences). Progress ticks only update the in-memory
 * flow; state changes (start, pause, finish, ...) are written to disk.
 */
object DownloadStore {
    private const val PREFS = "lumora_downloads"
    private const val KEY = "items_v1"

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items

    @Volatile private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        // A download that was running when the app process ended is paused now, so it can be resumed with one tap.
        _items.value = read(context.applicationContext).map {
            if (it.isActive) it.copy(status = DownloadStatus.PAUSED, speedBps = 0L, etaSec = -1L, note = null) else it
        }
    }

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(context: Context, item: DownloadItem) {
        _items.value = listOf(item) + _items.value
        save(context)
    }

    @Synchronized
    fun update(context: Context, id: String, persist: Boolean = false, block: (DownloadItem) -> DownloadItem) {
        var changed = false
        _items.value = _items.value.map {
            if (it.id == id) {
                changed = true
                block(it)
            } else it
        }
        if (changed && persist) save(context)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        _items.value = _items.value.filter { it.id != id }
        save(context)
    }

    private fun read(context: Context): List<DownloadItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val source = o.optString("src")
                if (id.isBlank() || source.isBlank()) return@mapNotNull null
                DownloadItem(
                    id = id,
                    source = source,
                    isTorrent = o.optBoolean("tor", false),
                    title = o.optString("t").ifBlank { source },
                    subtitle = o.optString("s").ifBlank { null },
                    isAudio = o.optBoolean("au", false),
                    status = try { DownloadStatus.valueOf(o.optString("st")) } catch (_: Throwable) { DownloadStatus.PAUSED },
                    downloadedBytes = o.optLong("d", 0L),
                    totalBytes = o.optLong("tot", 0L),
                    note = o.optString("n").ifBlank { null },
                    filePath = o.optString("fp").ifBlank { null },
                    fileUri = o.optString("fu").ifBlank { null },
                    createdAt = o.optLong("c", 0L),
                    formatSpec = o.optString("fmt").ifBlank { null },
                    container = o.optString("cont").ifBlank { null }
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        for (e in _items.value) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("src", e.source)
                    .put("tor", e.isTorrent)
                    .put("t", e.title)
                    .put("s", e.subtitle ?: "")
                    .put("au", e.isAudio)
                    .put("st", e.status.name)
                    .put("d", e.downloadedBytes)
                    .put("tot", e.totalBytes)
                    .put("n", e.note ?: "")
                    .put("fp", e.filePath ?: "")
                    .put("fu", e.fileUri ?: "")
                    .put("c", e.createdAt)
                    .put("fmt", e.formatSpec ?: "")
                    .put("cont", e.container ?: "")
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
