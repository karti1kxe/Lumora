/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Built on the libtorrent4j session / metadata handling of mpvRx's TorrentStreamingEngine (AGPL-3.0), reused here for
 * a plain "download the chosen file" flow. mpvRx itself has no separate torrent download engine.
 */

package com.example.torrent

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Runs INSIDE [TorrentDownloadService] (its own ":torrent" process, so a native libtorrent crash can never take the app
 * down). Downloads ONE selected file of a torrent (all other files are ignored) into [saveDir]. Pause = the loop is stopped
 * and the session closed WITHOUT deleting the partial data; calling [download] again with the same directory continues
 * (libtorrent re-checks the pieces that are already on disk).
 */
object TorrentDownloadWorker {
    private const val TAG = "TorrentDownloadWorker"
    private const val METADATA_TIMEOUT_MS = 120_000L
    private const val MAX_METADATA_BYTES = 16L * 1024L * 1024L
    private const val POLL_MS = 1_000L

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBps: Long,
        val peers: Int,
        val seeds: Int,
        /** Non-null while no payload flows yet ("Fetching torrent metadata…"). */
        val phase: String?
    )

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .callTimeout(45L, TimeUnit.SECONDS)
            .build()
    }

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
        if (hasV2OnlyMagnet(raw)) throw TorrentStreamException("BitTorrent v2-only torrents are not supported yet.")
        val normalized = normalizeTorrentSource(raw)
            ?: if (isMetadataUri(raw)) raw else throw TorrentStreamException("Unsupported torrent source.")
        if (!saveDir.isDirectory && !saveDir.mkdirs()) throw TorrentStreamException("Couldn't create the download folder.")

        val session = SessionManager()
        var handle: TorrentHandle? = null
        try {
            onProgress(Progress(0L, 0L, 0L, 0, 0, "Starting torrent engine…"))
            val settings = SettingsPack().apply {
                setMaxMetadataSize(MAX_METADATA_BYTES.toInt())
                setEnableDht(true)
                setEnableLsd(true)
            }
            synchronized(TorrentStreaming.nativeLock) { session.start(SessionParams(settings)) }

            val info: TorrentInfo
            if (normalized.startsWith("magnet:?", ignoreCase = true)) {
                val parsed = parseMagnet(normalized)
                    ?: throw TorrentStreamException("Magnet link does not contain a supported v1 info hash.")
                val hash = runCatching { Sha1Hash.parseHex(parsed.infoHash) }.getOrNull()
                    ?: throw TorrentStreamException("Magnet link contains an invalid v1 info hash.")
                session.download(parsed.cleanMagnetUri, saveDir, TorrentFlags.UPLOAD_MODE)
                handle = waitForHandle(session, hash, isCancelled)
                if (parsed.trackers.isEmpty()) {
                    DEFAULT_TORRENT_TRACKERS.forEach { handle.addTracker(AnnounceEntry(it)) }
                    handle.forceReannounce()
                }
                info = waitForMetadata(handle, isCancelled, onProgress)
            } else {
                val payload = readMetadata(app, normalized)
                val endpoints = runCatching { extractTorrentMetadataEndpoints(payload) }.getOrElse {
                    throw TorrentStreamException("The selected file contains invalid torrent metadata.")
                }
                info = runCatching { TorrentInfo(payload) }.getOrElse {
                    throw TorrentStreamException("The selected file is not valid torrent metadata.")
                }
                if (!info.isValid) throw TorrentStreamException("The selected file is not valid torrent metadata.")
                if (!info.hasV1()) throw TorrentStreamException("BitTorrent v2-only torrents are not supported yet.")
                val ignoreAll = Array(info.files().numFiles()) { Priority.IGNORE }
                session.download(info, saveDir, null, ignoreAll, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
                handle = waitForHandle(session, info.infoHash(), isCancelled)
                endpoints.trackers.forEach { handle.addTracker(AnnounceEntry(it)) }
                endpoints.webSeeds.forEach(handle::addUrlSeed)
                if (endpoints.trackers.isNotEmpty()) handle.forceReannounce()
            }

            val storage = info.files()
            if (fileIndex < 0 || fileIndex >= storage.numFiles()) {
                throw TorrentStreamException("The selected torrent file does not exist any more.")
            }
            val relativePath = storage.filePath(fileIndex)
            val size = storage.fileSize(fileIndex)
            if (relativePath.isBlank() || File(relativePath).isAbsolute ||
                relativePath.replace('\\', '/').split('/').any { it == ".." }
            ) throw TorrentStreamException("Torrent metadata contains an unsafe file path.")

            val priorities = Array(storage.numFiles()) { Priority.IGNORE }
            priorities[fileIndex] = Priority.TOP_PRIORITY
            handle.prioritizeFiles(priorities)
            handle.unsetFlags(TorrentFlags.UPLOAD_MODE)
            handle.resume()

            while (true) {
                if (isCancelled()) return null
                if (!handle.isValid) throw TorrentStreamException("Torrent session stopped unexpectedly.")
                val status = handle.status()
                val done = (handle.fileProgress().getOrNull(fileIndex) ?: 0L).coerceIn(0L, size)
                onProgress(
                    Progress(
                        downloadedBytes = done,
                        totalBytes = size,
                        speedBps = status.downloadPayloadRate().toLong(),
                        peers = status.numPeers(),
                        seeds = status.numSeeds(),
                        phase = if (done == 0L && status.numPeers() == 0) "Searching for torrent peers…" else null
                    )
                )
                if (size == 0L || done >= size) break
                delay(POLL_MS)
            }
            val out = File(saveDir, relativePath)
            return if (out.isFile) out else null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: TorrentStreamException) {
            throw error
        } catch (error: LinkageError) {
            Log.e(TAG, "Torrent native runtime is unavailable", error)
            throw TorrentStreamException("Torrent engine is not available on this device.", error)
        } catch (error: Throwable) {
            Log.e(TAG, "Torrent download failed (${error::class.java.simpleName})")
            throw TorrentStreamException("Couldn't download this torrent file.", error)
        } finally {
            // keep the partial data on disk: pause / retry continues from it, cancel deletes the folder itself
            synchronized(TorrentStreaming.nativeLock) { runCatching { session.stop() } }
        }
    }

    private suspend fun waitForHandle(session: SessionManager, hash: Sha1Hash, isCancelled: () -> Boolean): TorrentHandle {
        val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) throw CancellationException("Torrent download stopped")
            session.find(hash)?.takeIf { it.isValid }?.let { return it }
            delay(100L)
        }
        throw TorrentStreamException("Timed out while adding the torrent.")
    }

    private suspend fun waitForMetadata(
        handle: TorrentHandle,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): TorrentInfo {
        val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) throw CancellationException("Torrent download stopped")
            if (!handle.isValid) throw TorrentStreamException("Torrent session stopped before metadata was received.")
            handle.torrentFile()?.takeIf { it.isValid }?.let { return it }
            runCatching {
                val status = handle.status()
                onProgress(
                    Progress(
                        0L, 0L, status.downloadPayloadRate().toLong(), status.numPeers(), status.numSeeds(),
                        if (status.numPeers() > 0) "Downloading torrent metadata…" else "Searching for torrent peers…"
                    )
                )
            }
            delay(250L)
        }
        throw TorrentStreamException("Timed out while fetching torrent metadata. Check that the torrent has active peers.")
    }

    private fun isMetadataUri(source: String): Boolean =
        Uri.parse(source).scheme?.lowercase() in setOf("content", "file", "http", "https")

    private fun readMetadata(app: Context, source: String): ByteArray {
        val uri = Uri.parse(source)
        val input: InputStream = when (uri.scheme?.lowercase()) {
            "content" -> app.contentResolver.openInputStream(uri)
                ?: throw TorrentStreamException("Couldn't open the selected torrent metadata.")
            "file" -> FileInputStream(File(uri.path ?: throw TorrentStreamException("Torrent metadata path is invalid.")))
            "http", "https" -> return readRemote(source)
            else -> throw TorrentStreamException("Only magnet links and content, file, HTTP or HTTPS torrent metadata are supported.")
        }
        return input.use { readBounded(it) }
    }

    private fun readRemote(source: String): ByteArray {
        val request = runCatching { Request.Builder().url(source).get().build() }.getOrElse {
            throw TorrentStreamException("Torrent metadata URL is invalid.")
        }
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TorrentStreamException("Couldn't download torrent metadata (HTTP ${response.code}).")
                val body = response.body ?: throw TorrentStreamException("Couldn't download torrent metadata.")
                if (body.contentLength() > MAX_METADATA_BYTES) throw TorrentStreamException("Torrent metadata is too large.")
                body.byteStream().use { readBounded(it) }
            }
        } catch (error: TorrentStreamException) {
            throw error
        } catch (error: IOException) {
            throw TorrentStreamException("Couldn't download torrent metadata.", error)
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_METADATA_BYTES) throw TorrentStreamException("Torrent metadata is too large.")
            output.write(buffer, 0, read)
        }
        if (total == 0L) throw TorrentStreamException("Torrent metadata is empty.")
        return output.toByteArray()
    }
}
