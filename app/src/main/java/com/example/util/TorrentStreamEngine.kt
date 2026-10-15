package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Where a torrent comes from. */
sealed class TorrentSource {
    data class Magnet(val uri: String) : TorrentSource()
    /** A .torrent file handed over by a file manager / share sheet (content:// or file://). */
    data class LocalFile(val uri: Uri) : TorrentSource()
    /** A direct http(s) link to a .torrent file. */
    data class RemoteFile(val url: String) : TorrentSource()
    /** Raw bytes of a .torrent file. */
    data class Bytes(val data: ByteArray) : TorrentSource()
}

sealed class TorrentOpenResult {
    data class Failure(val message: String) : TorrentOpenResult()
    /** Exactly one video inside: play it right away. */
    data class Single(val summary: TorrentSummary, val file: TorrentFileInfo) : TorrentOpenResult()
    /** Several videos: a playlist was created, every video is one tap away inside it. */
    data class Multiple(val summary: TorrentSummary, val playlistId: String, val playlistName: String, val videoCount: Int) : TorrentOpenResult()
}

/**
 * Torrent / magnet playback on top of libtorrent4j (github.com/aldenml/libtorrent4j).
 *
 * The video is NOT downloaded first: the chosen file is downloaded in playback order (libtorrent piece deadlines) and
 * handed to the normal mpv player through a tiny loopback HTTP server that understands Range requests, so seeking,
 * duration and every player feature work exactly like for any other http video. Only the torrent that is playing
 * keeps its data on disk; switching torrents / closing the app deletes it.
 */
object TorrentStreamEngine {
    private const val TAG = "TorrentStreamEngine"
    private const val MAGNET_TIMEOUT_SEC = 90
    private const val MAX_TORRENT_FILE_BYTES = 16 * 1024 * 1024

    private val lock = Any()

    /**
     * Priority of the file being streamed. The constant names of libtorrent4j's Priority differ between releases
     * (SEVEN / NORMAL / DEFAULT ... are not present everywhere), so it is looked up by name at runtime instead of
     * hard-coding one that may not exist in the bundled version.
     */
    private val playPriority: Priority? by lazy {
        try {
            val all = Priority::class.java.enumConstants?.toList().orEmpty().filter { it != Priority.IGNORE }
            val wanted = listOf("TOP_PRIORITY", "TOP", "SEVEN", "HIGH", "DEFAULT_PRIORITY", "DEFAULT", "NORMAL", "FOUR")
            val picked = wanted.firstNotNullOfOrNull { n -> all.firstOrNull { it.toString().equals(n, ignoreCase = true) } }
                ?: all.lastOrNull()
            Log.i("TorrentStreamEngine", "play priority = ${picked} (available: ${all.joinToString()})")
            picked
        } catch (_: Throwable) {
            null
        }
    }
    @Volatile private var session: SessionManager? = null
    @Volatile private var active: ActiveTorrent? = null
    @Volatile private var server: StreamServer? = null
    @Volatile private var dataRoot: File? = null

    private class ActiveTorrent(
        val infoHash: String,
        val info: TorrentInfo,
        val handle: TorrentHandle,
        val saveDir: File
    ) {
        @Volatile var selectedFile: Int = -1
    }

    // ---------------------------------------------------------------------------------------------
    // Opening (metadata only - nothing is downloaded here)
    // ---------------------------------------------------------------------------------------------

    suspend fun open(context: Context, source: TorrentSource): TorrentOpenResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            val bytes: ByteArray = when (source) {
                is TorrentSource.Bytes -> source.data
                is TorrentSource.LocalFile -> readUriBytes(app, source.uri)
                    ?: return@withContext TorrentOpenResult.Failure("Could not read the torrent file")
                is TorrentSource.RemoteFile -> downloadBytes(source.url)
                    ?: return@withContext TorrentOpenResult.Failure("Could not download the torrent file")
                is TorrentSource.Magnet -> {
                    val wanted = magnetInfoHash(source.uri)
                    val saved = wanted?.let { TorrentLibrary.loadBytes(app, it) }
                    saved ?: fetchMagnet(app, source.uri)
                        ?: return@withContext TorrentOpenResult.Failure("Could not get the torrent info. No peers found or no internet.")
                }
            }
            val info = try { TorrentInfo(bytes) } catch (t: Throwable) {
                Log.w(TAG, "invalid torrent data", t)
                return@withContext TorrentOpenResult.Failure("This is not a valid torrent")
            }
            val summary = summaryOf(info)
            TorrentLibrary.save(app, summary, bytes)

            val videos = summary.videoFiles
            when {
                videos.isEmpty() -> TorrentOpenResult.Failure("No video files found in this torrent")
                videos.size == 1 -> TorrentOpenResult.Single(summary, videos.first())
                else -> {
                    val paths = videos.map { TorrentLibrary.pathFor(summary.infoHash, it.index) }
                    val existing = PlaylistManager.getPlaylists(app).firstOrNull { it.videoPaths == paths }
                    val playlist = existing ?: PlaylistManager.createPlaylistWithPaths(
                        context = app,
                        name = summary.name,
                        mediaType = "VIDEO",
                        isOnline = true,
                        paths = paths
                    )
                    TorrentOpenResult.Multiple(summary, playlist.id, playlist.name, videos.size)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "open failed", t)
            TorrentOpenResult.Failure(
                if (t is UnsatisfiedLinkError || t is NoClassDefFoundError) "Torrent engine is not available on this device"
                else t.message?.takeIf { it.isNotBlank() }?.take(120) ?: "Could not open the torrent"
            )
        }
    }

    private fun summaryOf(info: TorrentInfo): TorrentSummary {
        val fs = info.files()
        val files = (0 until fs.numFiles()).map { i ->
            TorrentFileInfo(index = i, path = fs.filePath(i).replace('\\', '/'), size = fs.fileSize(i))
        }
        return TorrentSummary(
            infoHash = info.infoHash().toHex().lowercase(),
            name = info.name().ifBlank { info.infoHash().toHex() },
            files = files
        )
    }

    private fun fetchMagnet(context: Context, magnet: String): ByteArray? {
        val sm = ensureSession(context) ?: return null
        val tmp = File(context.cacheDir, "torrent-magnet").apply { mkdirs() }
        return try {
            sm.fetchMagnet(magnet, MAGNET_TIMEOUT_SEC, tmp)
        } catch (t: Throwable) {
            Log.w(TAG, "fetchMagnet failed", t)
            null
        }
    }

    /** 40-char hex info hash of a magnet link (lower case) - null for base32 / v2-only links. */
    fun magnetInfoHash(magnet: String): String? =
        Regex("xt=urn:btih:([0-9a-fA-F]{40})").find(magnet)?.groupValues?.get(1)?.lowercase()

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? = try {
        val stream: InputStream? = context.contentResolver.openInputStream(uri)
        stream?.use { readLimited(it) }
    } catch (t: Throwable) {
        Log.w(TAG, "cannot read $uri", t)
        null
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_TORRENT_FILE_BYTES) return null
        }
        return out.toByteArray()
    }

    private fun downloadBytes(url: String): ByteArray? = try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.byteStream()?.let { readLimited(it) }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "torrent download failed", t)
        null
    }

    /** true when [uri] points to a .torrent file (by name, MIME type, or - for generic MIME types - by content). */
    fun looksLikeTorrent(context: Context, uri: Uri, mimeType: String?): Boolean {
        if (mimeType.equals("application/x-bittorrent", true)) return true
        val name = try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } else null
        } catch (_: Throwable) { null } ?: uri.lastPathSegment
        if (name != null && name.endsWith(".torrent", ignoreCase = true)) return true
        val generic = mimeType.isNullOrBlank() || mimeType.equals("application/octet-stream", true) || mimeType == "*/*"
        if (!generic) return false
        if (name != null && TorrentLibrary.isVideoName(name)) return false
        // a bencoded torrent starts with "d<digit>:" (e.g. "d8:announce"); no video / audio container does
        return try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val head = ByteArray(2)
                s.read(head) == 2 && head[0] == 'd'.code.toByte() && head[1] in '0'.code.toByte()..'9'.code.toByte()
            } ?: false
        } catch (_: Throwable) { false }
    }

    // ---------------------------------------------------------------------------------------------
    // Session / active torrent
    // ---------------------------------------------------------------------------------------------

    private fun ensureSession(context: Context): SessionManager? = synchronized(lock) {
        session ?: createSession(context)
    }

    private fun createSession(context: Context): SessionManager? {
        return try {
            val app = context.applicationContext
            val root = app.getExternalFilesDir("torrent-data") ?: File(app.filesDir, "torrent-data")
            // data of an earlier run is never reused: remove it so it cannot fill the storage
            root.deleteRecursively()
            root.mkdirs()
            dataRoot = root
            val sm = SessionManager()
            sm.start()
            session = sm
            sm
        } catch (t: Throwable) {
            Log.e(TAG, "torrent session could not start", t)
            null
        }
    }

    private fun activate(context: Context, infoHash: String): ActiveTorrent? = synchronized(lock) {
        val current = active
        if (current != null && current.infoHash == infoHash) current else buildActive(context, infoHash)
    }

    private fun buildActive(context: Context, infoHash: String): ActiveTorrent? {
        val sm = ensureSession(context) ?: return null
        val bytes = TorrentLibrary.loadBytes(context, infoHash) ?: return null
        val info = try { TorrentInfo(bytes) } catch (_: Throwable) { return null }
        dropActive(sm)

        val root = dataRoot ?: return null
        val saveDir = File(root, infoHash).apply { mkdirs() }
        sm.download(info, saveDir)
        var handle: TorrentHandle? = null
        var waited = 0
        while (waited < 5000) {
            val h = sm.find(info.infoHash())
            if (h != null && h.isValid) { handle = h; break }
            Thread.sleep(50)
            waited += 50
        }
        val found = handle ?: return null
        val a = ActiveTorrent(infoHash, info, found, saveDir)
        active = a
        return a
    }

    private fun dropActive(sm: SessionManager) {
        val old = active ?: return
        active = null
        try { sm.remove(old.handle) } catch (_: Throwable) {}
        val dir = old.saveDir
        Thread {
            try { Thread.sleep(2500) } catch (_: Throwable) {}
            try { dir.deleteRecursively() } catch (_: Throwable) {}
        }.apply { isDaemon = true }.start()
    }

    /** Only the file being played is downloaded; every other file of the torrent is skipped. */
    private fun selectFile(a: ActiveTorrent, fileIndex: Int) {
        if (a.selectedFile == fileIndex) return
        val n = a.info.files().numFiles()
        for (i in 0 until n) {
            try {
                val wanted = i == fileIndex
                // no usable "download" priority found -> leave libtorrent's default for the played file
                if (wanted && playPriority == null) continue
                a.handle.filePriority(i, if (wanted) playPriority!! else Priority.IGNORE)
            } catch (_: Throwable) {}
        }
        try { a.handle.clearPieceDeadlines() } catch (_: Throwable) {}
        a.selectedFile = fileIndex
    }

    /**
     * Makes the torrent video behind [torrentPath] ("torrent://<hash>/<index>") ready and returns the local http URL the
     * player opens - null when the torrent could not be started. Playback starts as soon as the first pieces arrived.
     */
    suspend fun prepareStream(context: Context, torrentPath: String): String? = withContext(Dispatchers.IO) {
        val (hash, index) = TorrentLibrary.parsePath(torrentPath) ?: return@withContext null
        try {
            val a = activate(context.applicationContext, hash) ?: return@withContext null
            if (index < 0 || index >= a.info.files().numFiles()) return@withContext null
            selectFile(a, index)
            val srv = synchronized(lock) { server ?: StreamServer().also { server = it } }
            val name = a.info.files().filePath(index).substringAfterLast('/')
            "http://127.0.0.1:${srv.port}/t/$hash/$index/${URLEncoder.encode(name, "UTF-8").replace("+", "%20")}"
        } catch (t: Throwable) {
            Log.e(TAG, "prepareStream failed", t)
            null
        }
    }

    /** Stops the torrent session and deletes the downloaded data (called when the app closes). */
    fun release() {
        synchronized(lock) {
            val sm = session
            try { if (sm != null) dropActive(sm) } catch (_: Throwable) {}
            try { server?.close() } catch (_: Throwable) {}
            server = null
            session = null
            try { sm?.stop() } catch (_: Throwable) {}
            val root = dataRoot
            if (root != null) Thread { try { Thread.sleep(2500) } catch (_: Throwable) {}; root.deleteRecursively() }
                .apply { isDaemon = true }.start()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Loopback HTTP server (Range capable) that serves the torrent file while it is still downloading
    // ---------------------------------------------------------------------------------------------

    private class StreamServer {
        private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port: Int = serverSocket.localPort
        private val pool = Executors.newCachedThreadPool { r -> Thread(r, "torrent-http").apply { isDaemon = true } }
        private val activeRequests = AtomicInteger(0)
        @Volatile private var closed = false

        init {
            Thread({
                while (!closed) {
                    try {
                        val s = serverSocket.accept()
                        pool.execute { handle(s) }
                    } catch (_: Throwable) {
                        if (closed) break
                    }
                }
            }, "torrent-http-accept").apply { isDaemon = true }.start()
        }

        fun close() {
            closed = true
            try { serverSocket.close() } catch (_: Throwable) {}
            pool.shutdownNow()
        }

        private fun handle(socket: Socket) {
            activeRequests.incrementAndGet()
            try {
                socket.soTimeout = 0
                socket.tcpNoDelay = true
                val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                var rangeHeader: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) rangeHeader = line.substringAfter(':').trim()
                }
                val segs = parts[1].substringBefore('?').split('/').filter { it.isNotEmpty() }
                // /t/<hash>/<fileIndex>/<name>
                if (segs.size < 3 || segs[0] != "t") { sendStatus(socket.getOutputStream(), 404, "Not Found"); return }
                val hash = segs[1]
                val index = segs[2].toIntOrNull() ?: run { sendStatus(socket.getOutputStream(), 404, "Not Found"); return }
                val a = active
                if (a == null || a.infoHash != hash) { sendStatus(socket.getOutputStream(), 404, "Not Found"); return }
                serve(socket, a, index, method, rangeHeader)
            } catch (_: Throwable) {
                // the player closed the connection (seek / stop): nothing to report
            } finally {
                activeRequests.decrementAndGet()
                try { socket.close() } catch (_: Throwable) {}
            }
        }

        private fun sendStatus(out: OutputStream, code: Int, text: String) {
            out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }

        private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "ts", "m2ts", "mts" -> "video/mp2t"
            else -> "application/octet-stream"
        }

        private fun serve(socket: Socket, a: ActiveTorrent, fileIndex: Int, method: String, rangeHeader: String?) {
            val out = socket.getOutputStream()
            val fs = a.info.files()
            if (fileIndex < 0 || fileIndex >= fs.numFiles()) { sendStatus(out, 404, "Not Found"); return }
            val size = fs.fileSize(fileIndex)
            val fileOffset = fs.fileOffset(fileIndex)
            val pieceLen = a.info.pieceLength().toLong()
            val numPieces = a.info.numPieces()
            val relPath = fs.filePath(fileIndex)
            val file = File(a.saveDir, relPath)

            // ---- range ----
            var start = 0L
            var end = size - 1
            var partial = false
            if (rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
                val spec = rangeHeader.substringAfter('=').substringBefore(',').trim()
                val first = spec.substringBefore('-')
                val last = spec.substringAfter('-', "")
                if (first.isEmpty()) {
                    val suffix = last.toLongOrNull() ?: 0L
                    start = (size - suffix).coerceAtLeast(0L)
                } else {
                    start = first.toLongOrNull() ?: 0L
                    if (last.isNotEmpty()) end = (last.toLongOrNull() ?: end).coerceAtMost(size - 1)
                }
                partial = true
            }
            if (start >= size || start > end) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                return
            }
            val length = end - start + 1
            val sb = StringBuilder()
            sb.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            sb.append("Content-Type: ").append(mimeFor(relPath)).append("\r\n")
            sb.append("Accept-Ranges: bytes\r\n")
            sb.append("Content-Length: ").append(length).append("\r\n")
            if (partial) sb.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(size).append("\r\n")
            sb.append("Connection: close\r\n\r\n")
            out.write(sb.toString().toByteArray())
            out.flush()
            if (method == "HEAD") return

            // a request that starts while nothing else is being served is a fresh seek: forget old deadlines
            if (activeRequests.get() <= 1) { try { a.handle.clearPieceDeadlines() } catch (_: Throwable) {} }

            val windowPieces = (12L * 1024 * 1024 / pieceLen).toInt().coerceIn(4, 40)
            val lastPieceOfFile = ((fileOffset + size - 1) / pieceLen).toInt().coerceAtMost(numPieces - 1)
            var scheduledPiece = -1
            val buf = ByteArray(64 * 1024)
            var pos = start
            var raf: RandomAccessFile? = null
            try {
                while (pos <= end) {
                    if (closed || active !== a || socket.isClosed) return
                    val abs = fileOffset + pos
                    val piece = (abs / pieceLen).toInt()
                    if (piece != scheduledPiece) {
                        scheduledPiece = piece
                        for (i in 0 until windowPieces) {
                            val p = piece + i
                            if (p > lastPieceOfFile) break
                            try {
                                if (!a.handle.havePiece(p)) a.handle.setPieceDeadline(p, 300 + i * 250)
                            } catch (_: Throwable) {}
                        }
                    }
                    // wait until this piece is on disk
                    var waitedMs = 0L
                    while (!a.handle.havePiece(piece)) {
                        if (closed || active !== a || socket.isClosed) return
                        Thread.sleep(120)
                        waitedMs += 120
                        if (waitedMs > 15 * 60 * 1000L) return
                    }
                    if (raf == null) {
                        var tries = 0
                        while (!file.exists() && tries < 50) { Thread.sleep(100); tries++ }
                        raf = RandomAccessFile(file, "r")
                    }
                    val pieceEndAbs = (piece + 1).toLong() * pieceLen - 1
                    val n = minOf(buf.size.toLong(), pieceEndAbs - abs + 1, end - pos + 1).toInt()
                    raf.seek(pos)
                    raf.readFully(buf, 0, n)
                    out.write(buf, 0, n)
                    pos += n
                }
                out.flush()
            } finally {
                try { raf?.close() } catch (_: Throwable) {}
            }
        }
    }
}
