/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Built on the libtorrent4j session / metadata handling of mpvRx's TorrentStreamingEngine (AGPL-3.0), reused here for
 * a plain "download the chosen file" flow. mpvRx itself has no separate torrent download engine.
 */

package com.example.torrent

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Downloads ONE selected file of a torrent (all other files are ignored) into [saveDir]. The libtorrent session runs
 * in [TorrentDownloadService] (separate ":torrent" process: a native crash there cannot crash the app); this object
 * starts it, follows its state file and reports progress. Pause = the service is stopped WITHOUT deleting the partial
 * data; calling [download] again with the same directory continues (libtorrent re-checks the pieces on disk).
 */
object TorrentDownloadEngine {
    private const val POLL_MS = 700L
    /** The service rewrites its state at least every second; no news for this long = its process died. */
    private const val STALE_MS = 60_000L

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBps: Long,
        val peers: Int,
        val seeds: Int,
        /** Non-null while no payload flows yet ("Fetching torrent metadata…"). */
        val phase: String?
    )

    internal fun stateFile(saveDir: File): File = File(saveDir.parentFile ?: saveDir, "torrent_state.json")

    /** Returns the finished file (inside [saveDir]). Throws [TorrentStreamException] for torrent problems. */
    suspend fun download(
        context: Context,
        source: String,
        fileIndex: Int,
        saveDir: File,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): File? {
        val app = context.applicationContext
        val raw = source.trim()
        if (raw.isEmpty()) throw TorrentStreamException("Torrent source is empty.")
        if (!saveDir.isDirectory && !saveDir.mkdirs()) throw TorrentStreamException("Couldn't create the download folder.")
        val state = stateFile(saveDir)
        state.delete()
        onProgress(Progress(0L, 0L, 0L, 0, 0, "Starting torrent engine…"))
        try {
            TorrentDownloadService.start(app, saveDir, raw, fileIndex)
        } catch (t: Throwable) {
            throw TorrentStreamException("Torrent engine is not available.", t)
        }
        var lastSeq = -1L
        var lastChange = System.currentTimeMillis()
        try {
            while (true) {
                if (isCancelled()) {
                    TorrentDownloadService.stop(app, saveDir)
                    return null
                }
                val json = try { if (state.isFile) JSONObject(state.readText()) else null } catch (_: Throwable) { null }
                if (json != null) {
                    val seq = json.optLong("seq", -1L)
                    if (seq != lastSeq) { lastSeq = seq; lastChange = System.currentTimeMillis() }
                    when (json.optString("status")) {
                        "done" -> {
                            val rel = json.optString("file")
                            val out = File(saveDir, rel)
                            return if (rel.isNotEmpty() && out.isFile) out else null
                        }
                        "error" -> throw TorrentStreamException(
                            json.optString("error").ifBlank { "Couldn't download this torrent file." }
                        )
                        "stopped" -> if (!isCancelled()) throw IOException("Torrent engine stopped")
                        else -> onProgress(
                            Progress(
                                downloadedBytes = json.optLong("downloaded", 0L),
                                totalBytes = json.optLong("total", 0L),
                                speedBps = json.optLong("speed", 0L),
                                peers = 0,
                                seeds = 0,
                                phase = if (json.isNull("phase")) null else json.optString("phase")
                            )
                        )
                    }
                }
                if (System.currentTimeMillis() - lastChange > STALE_MS) {
                    // the torrent process died (native crash / killed): the Download page retries, partial data is kept
                    throw IOException("Torrent engine stopped unexpectedly")
                }
                delay(POLL_MS)
            }
        } catch (c: CancellationException) {
            TorrentDownloadService.stop(app, saveDir)
            throw c
        }
    }
}
