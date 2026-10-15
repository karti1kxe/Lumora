package com.example.util

import android.content.Context
import com.example.ui.screens.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One file inside a torrent. [index] is the libtorrent file index. */
data class TorrentFileInfo(val index: Int, val path: String, val size: Long) {
    val displayName: String get() = path.substringAfterLast('/')
    val isVideo: Boolean get() = TorrentLibrary.isVideoName(path)
}

/** Light-weight description of a saved torrent (no native code needed to read it). */
data class TorrentSummary(val infoHash: String, val name: String, val files: List<TorrentFileInfo>) {
    val videoFiles: List<TorrentFileInfo>
        get() = files.filter { it.isVideo }.sortedWith { a, b -> NaturalOrderComparator.compare(a.path, b.path) }
}

/**
 * Pure-Kotlin storage of the torrents the user opened: the original .torrent bytes (so a playlist keeps working after an
 * app restart, no magnet lookup needed again) and a small JSON index (name + file list) used to rebuild playlist cards.
 * Torrent videos are addressed everywhere as "torrent://<infoHash>/<fileIndex>".
 */
object TorrentLibrary {
    const val PATH_PREFIX = "torrent://"
    private const val DIR = "torrents"
    private val VIDEO_EXT = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv", "mpg", "mpeg", "3gp", "ts", "m2ts", "mts", "ogv", "vob", "divx"
    )

    fun isVideoName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT

    fun pathFor(infoHash: String, fileIndex: Int): String = "$PATH_PREFIX$infoHash/$fileIndex"

    /** Splits "torrent://<hash>/<index>" - null when [path] is not a torrent video path. */
    fun parsePath(path: String): Pair<String, Int>? {
        if (!path.startsWith(PATH_PREFIX)) return null
        val rest = path.removePrefix(PATH_PREFIX)
        val hash = rest.substringBefore('/')
        val idx = rest.substringAfter('/', "").toIntOrNull() ?: return null
        return if (hash.isBlank()) null else hash to idx
    }

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    fun torrentFile(context: Context, infoHash: String): File = File(dir(context), "$infoHash.torrent")
    private fun indexFile(context: Context, infoHash: String): File = File(dir(context), "$infoHash.json")

    fun save(context: Context, summary: TorrentSummary, torrentBytes: ByteArray) {
        try {
            torrentFile(context, summary.infoHash).writeBytes(torrentBytes)
            val files = JSONArray()
            for (f in summary.files) files.put(JSONObject().put("i", f.index).put("p", f.path).put("s", f.size))
            indexFile(context, summary.infoHash)
                .writeText(JSONObject().put("name", summary.name).put("files", files).toString())
        } catch (_: Throwable) {
            // best effort: playback of the current session does not depend on these files
        }
    }

    fun load(context: Context, infoHash: String): TorrentSummary? {
        return try {
            val f = indexFile(context, infoHash)
            if (!f.exists()) return null
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("files") ?: return null
            val files = (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                TorrentFileInfo(e.optInt("i"), e.optString("p"), e.optLong("s"))
            }
            TorrentSummary(infoHash, o.optString("name").ifBlank { infoHash }, files)
        } catch (_: Throwable) {
            null
        }
    }

    fun loadBytes(context: Context, infoHash: String): ByteArray? =
        try { torrentFile(context, infoHash).takeIf { it.exists() }?.readBytes() } catch (_: Throwable) { null }

    /** Name of one torrent video for cards / recents, e.g. "Episode 03.mkv". */
    fun fileDisplayName(context: Context, path: String): String? {
        val (hash, idx) = parsePath(path) ?: return null
        return load(context, hash)?.files?.firstOrNull { it.index == idx }?.displayName
    }

    fun fileSize(context: Context, path: String): Long {
        val (hash, idx) = parsePath(path) ?: return 0L
        return load(context, hash)?.files?.firstOrNull { it.index == idx }?.size ?: 0L
    }

    /** The normal video card / player item of one torrent video; null when the torrent is unknown. */
    fun videoItem(context: Context, path: String): VideoItem? {
        val (hash, idx) = parsePath(path) ?: return null
        val file = load(context, hash)?.files?.firstOrNull { it.index == idx }
        return VideoItem(
            id = -(kotlin.math.abs(("torrent:" + path).hashCode().toLong())) - 1L,
            uri = android.net.Uri.parse(path),
            displayName = file?.displayName ?: path,
            path = path,
            sizeBytes = file?.size ?: 0L,
            durationMs = 0L,
            dateModified = System.currentTimeMillis() / 1000L,
            isOnline = true,
            isTorrentStream = true
        )
    }
}
