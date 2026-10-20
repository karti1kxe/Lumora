/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Built on the libtorrent4j session / metadata handling of mpvRx's torrent engine (AGPL-3.0), reused here for
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
 * Downloads ONE selected file of a torrent (all other files are ignored) into [saveDir]. Pause = the loop is stopped
 * and the session closed WITHOUT deleting the partial data; calling [download] again with the same directory continues
 * (libtorrent re-checks the pieces that are already on disk).
 */
object TorrentDownloadEngine {
    private const val TAG = "TorrentDownloadEngine"
    private const val METADATA_TIMEOUT_MS = 120_000L
    private const val MAX_METADATA_BYTES = 16L * 1024L * 1024L
    private const val POLL_MS = 1_000L
    private const val MIN_FREE_HEADROOM_BYTES = 64L * 1024L * 1024L

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

    /** Returns the finished file (inside [saveDir]). Throws [TorrentException] for torrent problems. */
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
        if (raw.isEmpty()) throw TorrentException("Torrent source is empty.")
        if (hasV2OnlyMagnet(raw)) throw TorrentException("BitTorrent v2-only torrents are not supported yet.")
        val normalized = normalizeTorrentSource(raw)
            ?: if (isMetadataUri(raw)) raw else throw TorrentException("Unsupported torrent source.")
        if (!saveDir.isDirectory && !saveDir.mkdirs()) throw TorrentException("Couldn't create the download folder.")

        val session = SessionManager()
        var handle: TorrentHandle? = null
        try {
            onProgress(Progress(0L, 0L, 0L, 0, 0, "Starting torrent engine…"))
            val settings = SettingsPack().apply {
                setMaxMetadataSize(MAX_METADATA_BYTES.toInt())
                setEnableDht(true)
                setEnableLsd(true)
            }
            session.start(SessionParams(settings))

            val info: TorrentInfo
            if (normalized.startsWith("magnet:?", ignoreCase = true)) {
                val parsed = parseMagnet(normalized)
                    ?: throw TorrentException("Magnet link does not contain a supported v1 info hash.")
                val hash = runCatching { Sha1Hash.parseHex(parsed.infoHash) }.getOrNull()
                    ?: throw TorrentException("Magnet link contains an invalid v1 info hash.")
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
                    throw TorrentException("The selected file contains invalid torrent metadata.")
                }
                info = runCatching { TorrentInfo(payload) }.getOrElse {
                    throw TorrentException("The selected file is not valid torrent metadata.")
                }
                if (!info.isValid) throw TorrentException("The selected file is not valid torrent metadata.")
                if (!info.hasV1()) throw TorrentException("BitTorrent v2-only torrents are not supported yet.")
                val ignoreAll = Array(info.files().numFiles()) { Priority.IGNORE }
                session.download(info, saveDir, null, ignoreAll, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
                handle = waitForHandle(session, info.infoHash(), isCancelled)
                endpoints.trackers.forEach { handle.addTracker(AnnounceEntry(it)) }
                endpoints.webSeeds.forEach(handle::addUrlSeed)
                if (endpoints.trackers.isNotEmpty()) handle.forceReannounce()
            }

            val storage = info.files()
            if (fileIndex < 0 || fileIndex >= storage.numFiles()) {
                throw TorrentException("The selected torrent file does not exist any more.")
            }
            val relativePath = storage.filePath(fileIndex)
            val size = storage.fileSize(fileIndex)
            if (relativePath.isBlank() || File(relativePath).isAbsolute ||
                relativePath.replace('\\', '/').split('/').any { it == ".." }
            ) throw TorrentException("Torrent metadata contains an unsafe file path.")

            // libtorrent 2.x writes through memory-mapped files: a full disk (or a filesystem that cannot grow sparse
            // files) kills the whole process with SIGBUS instead of failing the write, which looked like "the app
            // crashes right after the download starts". So the free space is checked BEFORE any data is requested.
            val alreadyOnDisk = File(saveDir, relativePath).takeIf { it.isFile }?.length() ?: 0L
            val neededBytes = (size - alreadyOnDisk).coerceAtLeast(0L) + MIN_FREE_HEADROOM_BYTES
            if (saveDir.usableSpace < neededBytes) {
                throw TorrentException("Not enough free storage: the device storage is full.")
            }

            val priorities = Array(storage.numFiles()) { Priority.IGNORE }
            priorities[fileIndex] = Priority.TOP_PRIORITY
            handle.prioritizeFiles(priorities)
            handle.unsetFlags(TorrentFlags.UPLOAD_MODE)
            handle.resume()

            while (true) {
                if (isCancelled()) return null
                if (!handle.isValid) throw TorrentException("Torrent session stopped unexpectedly.")
                val (status, done) = try {
                    handle.status() to (handle.fileProgress().getOrNull(fileIndex) ?: 0L).coerceIn(0L, size)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    // a single failed status read must never end (or crash) the download: the next tick tries again
                    Log.w(TAG, "Unable to read torrent status (${error::class.java.simpleName})")
                    delay(POLL_MS)
                    continue
                }
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
        } catch (error: TorrentException) {
            throw error
        } catch (error: LinkageError) {
            Log.e(TAG, "Torrent native runtime is unavailable", error)
            throw TorrentException("Torrent engine is not available on this device.", error)
        } catch (error: Throwable) {
            Log.e(TAG, "Torrent download failed (${error::class.java.simpleName})")
            throw TorrentException("Couldn't download this torrent file.", error)
        } finally {
            // keep the partial data on disk: pause / retry continues from it, cancel deletes the folder itself
            runCatching { session.stop() }
        }
    }

    private suspend fun waitForHandle(session: SessionManager, hash: Sha1Hash, isCancelled: () -> Boolean): TorrentHandle {
        val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) throw CancellationException("Torrent download stopped")
            session.find(hash)?.takeIf { it.isValid }?.let { return it }
            delay(100L)
        }
        throw TorrentException("Timed out while adding the torrent.")
    }

    private suspend fun waitForMetadata(
        handle: TorrentHandle,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): TorrentInfo {
        val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) throw CancellationException("Torrent download stopped")
            if (!handle.isValid) throw TorrentException("Torrent session stopped before metadata was received.")
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
        throw TorrentException("Timed out while fetching torrent metadata. Check that the torrent has active peers.")
    }

    private fun isMetadataUri(source: String): Boolean =
        Uri.parse(source).scheme?.lowercase() in setOf("content", "file", "http", "https")

    private fun readMetadata(app: Context, source: String): ByteArray {
        val uri = Uri.parse(source)
        val input: InputStream = when (uri.scheme?.lowercase()) {
            "content" -> app.contentResolver.openInputStream(uri)
                ?: throw TorrentException("Couldn't open the selected torrent metadata.")
            "file" -> FileInputStream(File(uri.path ?: throw TorrentException("Torrent metadata path is invalid.")))
            "http", "https" -> return readRemote(source)
            else -> throw TorrentException("Only magnet links and content, file, HTTP or HTTPS torrent metadata are supported.")
        }
        return input.use { readBounded(it) }
    }

    private fun readRemote(source: String): ByteArray {
        val request = runCatching { Request.Builder().url(source).get().build() }.getOrElse {
            throw TorrentException("Torrent metadata URL is invalid.")
        }
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TorrentException("Couldn't download torrent metadata (HTTP ${response.code}).")
                val body = response.body ?: throw TorrentException("Couldn't download torrent metadata.")
                if (body.contentLength() > MAX_METADATA_BYTES) throw TorrentException("Torrent metadata is too large.")
                body.byteStream().use { readBounded(it) }
            }
        } catch (error: TorrentException) {
            throw error
        } catch (error: IOException) {
            throw TorrentException("Couldn't download torrent metadata.", error)
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
            if (total > MAX_METADATA_BYTES) throw TorrentException("Torrent metadata is too large.")
            output.write(buffer, 0, read)
        }
        if (total == 0L) throw TorrentException("Torrent metadata is empty.")
        return output.toByteArray()
    }
}
