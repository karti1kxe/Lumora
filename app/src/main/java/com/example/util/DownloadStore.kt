package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, PROCESSING, COMPLETED, FAILED }
enum class DownloadSourceType { VIDEO, AUDIO, NETWORK, MEDIA_PLUGIN, TORRENT }

/** Comprehensive download task model supporting all media types, quality, audio tracks, and persistence. */
data class DownloadItem(
    val id: String,
    val source: String,
    val title: String,
    val subtitle: String? = null,
    val isAudio: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSec: Long = -1L,
    val note: String? = null,
    val filePath: String? = null,
    val fileUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val thumbnailUrl: String? = null,
    val sourceType: DownloadSourceType = DownloadSourceType.VIDEO,
    val torrentFileIndex: Int = -1,
    val selectedQuality: String? = null,
    val audioTrackNames: List<String> = emptyList(),
    val audioStreamUrls: List<String> = emptyList(),
    val subtitleUrls: List<String> = emptyList(),
    val hasLyrics: Boolean = false,
    val formatContainer: String? = null,
    val targetCategory: String = if (isAudio) "Music" else "Videos"
) {
    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PROCESSING

    val fraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("source", source)
        put("title", title)
        put("subtitle", subtitle ?: "")
        put("isAudio", isAudio)
        put("status", status.name)
        put("downloadedBytes", downloadedBytes)
        put("totalBytes", totalBytes)
        put("speedBps", speedBps)
        put("etaSec", etaSec)
        put("note", note ?: "")
        put("filePath", filePath ?: "")
        put("fileUri", fileUri ?: "")
        put("createdAt", createdAt)
        put("thumbnailUrl", thumbnailUrl ?: "")
        put("sourceType", sourceType.name)
        put("torrentFileIndex", torrentFileIndex)
        put("selectedQuality", selectedQuality ?: "")
        put("audioTrackNames", JSONArray(audioTrackNames))
        put("audioStreamUrls", JSONArray(audioStreamUrls))
        put("subtitleUrls", JSONArray(subtitleUrls))
        put("hasLyrics", hasLyrics)
        put("formatContainer", formatContainer ?: "")
        put("targetCategory", targetCategory)
    }

    companion object {
        fun fromJson(json: JSONObject): DownloadItem {
            val audioNames = ArrayList<String>()
            json.optJSONArray("audioTrackNames")?.let { arr ->
                for (i in 0 until arr.length()) audioNames.add(arr.optString(i))
            }
            val audioUrls = ArrayList<String>()
            json.optJSONArray("audioStreamUrls")?.let { arr ->
                for (i in 0 until arr.length()) audioUrls.add(arr.optString(i))
            }
            val subUrls = ArrayList<String>()
            json.optJSONArray("subtitleUrls")?.let { arr ->
                for (i in 0 until arr.length()) subUrls.add(arr.optString(i))
            }
            val statusParsed = try {
                DownloadStatus.valueOf(json.optString("status", "QUEUED"))
            } catch (_: Throwable) {
                DownloadStatus.QUEUED
            }
            val typeParsed = try {
                DownloadSourceType.valueOf(json.optString("sourceType", "VIDEO"))
            } catch (_: Throwable) {
                DownloadSourceType.VIDEO
            }
            return DownloadItem(
                id = json.optString("id"),
                source = json.optString("source"),
                title = json.optString("title"),
                subtitle = json.optString("subtitle").takeIf { it.isNotBlank() },
                isAudio = json.optBoolean("isAudio", false),
                status = statusParsed,
                downloadedBytes = json.optLong("downloadedBytes", 0L),
                totalBytes = json.optLong("totalBytes", 0L),
                speedBps = json.optLong("speedBps", 0L),
                etaSec = json.optLong("etaSec", -1L),
                note = json.optString("note").takeIf { it.isNotBlank() },
                filePath = json.optString("filePath").takeIf { it.isNotBlank() },
                fileUri = json.optString("fileUri").takeIf { it.isNotBlank() },
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                sourceType = typeParsed,
                torrentFileIndex = json.optInt("torrentFileIndex", -1),
                selectedQuality = json.optString("selectedQuality").takeIf { it.isNotBlank() },
                audioTrackNames = audioNames,
                audioStreamUrls = audioUrls,
                subtitleUrls = subUrls,
                hasLyrics = json.optBoolean("hasLyrics", false),
                formatContainer = json.optString("formatContainer").takeIf { it.isNotBlank() },
                targetCategory = json.optString("targetCategory", "Videos")
            )
        }
    }
}

object DownloadFormat {
    fun bytes(b: Long): String {
        if (b <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = b.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.size - 1) {
            value /= 1024.0
            index++
        }
        if (index == 0) return "$b B"
        val format = if (value >= 100.0) "%.0f %s" else if (value >= 10.0) "%.1f %s" else "%.2f %s"
        return String.format(Locale.US, format, value, units[index])
    }

    fun speed(bps: Long): String = bytes(bps) + "/s"

    fun eta(sec: Long): String {
        if (sec < 0L) return "--"
        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun sizeLine(item: DownloadItem): String =
        bytes(item.downloadedBytes) + " / " + (if (item.totalBytes > 0L) bytes(item.totalBytes) else "?")

    fun speedLine(item: DownloadItem): String {
        val parts = ArrayList<String>()
        parts.add(speed(item.speedBps))
        if (item.etaSec >= 0L) parts.add(eta(item.etaSec) + " left")
        return parts.joinToString(" • ")
    }
}

/** Persistent download store with SharedPreferences JSON backing and resume restoration. */
object DownloadStore {
    private const val PREFS_NAME = "lumora_persistent_downloads_v2"
    private const val KEY_ITEMS = "stored_items_json"
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items

    @Volatile private var loaded = false
    private val lock = Any()

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_ITEMS, null)
        val restored = ArrayList<DownloadItem>()
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val item = DownloadItem.fromJson(obj)
                    // If task was in-flight when app died, reset to PAUSED so it can resume
                    val adjusted = if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.PROCESSING) {
                        item.copy(status = DownloadStatus.PAUSED, speedBps = 0L, etaSec = -1L, note = "Paused (app restarted)")
                    } else item
                    restored.add(adjusted)
                }
            } catch (_: Throwable) {}
        }
        synchronized(lock) {
            _items.value = restored
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persist(context: Context) {
        val list = _items.value
        try {
            val array = JSONArray()
            for (item in list) {
                array.put(item.toJson())
            }
            prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
        } catch (_: Throwable) {}
    }

    /** Returns tasks that were interrupted and should resume. */
    fun takeInterrupted(): List<String> =
        _items.value.filter { it.status == DownloadStatus.PAUSED && it.downloadedBytes < it.totalBytes && it.totalBytes > 0L }.map { it.id }

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun add(context: Context, item: DownloadItem) {
        synchronized(lock) {
            _items.value = listOf(item) + _items.value.filterNot { it.id == item.id }
        }
        persist(context)
    }

    fun update(context: Context, id: String, persistNow: Boolean = true, block: (DownloadItem) -> DownloadItem) {
        synchronized(lock) {
            _items.value = _items.value.map { item -> if (item.id == id) block(item) else item }
        }
        if (persistNow) {
            persist(context)
        }
    }

    fun remove(context: Context, id: String) {
        synchronized(lock) {
            _items.value = _items.value.filterNot { it.id == id }
        }
        persist(context)
    }

    fun removeMultiple(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            _items.value = _items.value.filterNot { it.id in ids }
        }
        persist(context)
    }
}
