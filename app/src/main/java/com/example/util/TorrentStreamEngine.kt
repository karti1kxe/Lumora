package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
    /** Playback starts after this share of the file (max [PREBUFFER_HEAD_BYTES]) is there: ~5%, never hundreds of MB. */
    private const val PREBUFFER_PERCENT = 5
    private const val PREBUFFER_HEAD_BYTES = 32L * 1024 * 1024
    private const val PREBUFFER_MIN_HEAD_BYTES = 6L * 1024 * 1024
    /** End of the file (index of mp4 / avi, cues of mkv): needed to open some formats and to seek. */
    private const val PREBUFFER_TAIL_BYTES = 4L * 1024 * 1024
    /** After this time playback starts as soon as the first pieces (container header + tracks) are there. */
    private const val PREBUFFER_SOFT_TIMEOUT_MS = 45_000L
    private const val PREBUFFER_TIMEOUT_MS = 150_000L

    /** Public trackers added to every torrent: magnet links often carry none, which means very few peers (= slow). */
    private val EXTRA_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://opentracker.io:6969/announce",
        "https://tracker.tamersunion.org:443/announce"
    )

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
        /** 0..100: how far the start-up buffer (header + end of the file) is filled; 100 = playback can begin. */
        @Volatile var prebufferPct: Int = 0
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
            tuneSession(sm)
            session = sm
            sm
        } catch (t: Throwable) {
            Log.e(TAG, "torrent session could not start", t)
            null
        }
    }

    /**
     * Full-speed settings. libtorrent's defaults are made for a desktop client with a download queue: only 3 torrents
     * may be active at once (a stream and two downloads at the same time made one of them wait silently) and the
     * connection count is modest. The setters are looked up by name so a different libtorrent4j release can never
     * break the build; a setting that does not exist is simply skipped.
     */
    private fun tuneSession(sm: SessionManager) {
        try {
            val sp = org.libtorrent4j.SettingsPack()
            fun setInt(name: String, value: Int) {
                try { sp.javaClass.getMethod(name, Int::class.javaPrimitiveType).invoke(sp, value) } catch (_: Throwable) {}
            }
            fun setBool(name: String, value: Boolean) {
                try { sp.javaClass.getMethod(name, Boolean::class.javaPrimitiveType).invoke(sp, value) } catch (_: Throwable) {}
            }
            // no queueing: every torrent the app starts really runs
            setInt("activeDownloads", 50)
            setInt("activeSeeds", 50)
            setInt("activeLimit", 200)
            setInt("activeChecking", 4)
            // as fast as the line allows
            setInt("downloadRateLimit", 0)
            setInt("uploadRateLimit", 0)
            setInt("connectionsLimit", 400)
            setInt("connectionSpeed", 60)
            setInt("maxPeerlistSize", 3000)
            setInt("maxQueuedDiskBytes", 16 * 1024 * 1024)
            setInt("sendBufferWatermark", 1024 * 1024)
            setInt("requestTimeout", 20)
            setInt("peerConnectTimeout", 10)
            setBool("setEnableDht", true)
            setBool("setEnableLsd", true)
            sm.applySettings(sp)
        } catch (t: Throwable) {
            Log.w(TAG, "could not tune the torrent session", t)
        }
    }

    private fun addExtraTrackers(h: TorrentHandle) {
        try {
            val cls = Class.forName("org.libtorrent4j.AnnounceEntry")
            val ctor = cls.getConstructor(String::class.java)
            val add = h.javaClass.getMethod("addTracker", cls)
            for (t in EXTRA_TRACKERS) {
                try { add.invoke(h, ctor.newInstance(t)) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        }
        try { h.javaClass.getMethod("forceReannounce").invoke(h) } catch (_: Throwable) {}
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
        try { found.resume() } catch (_: Throwable) {}
        addExtraTrackers(found)
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
            prebuffer(a, index)
            val srv = synchronized(lock) { server ?: StreamServer().also { server = it } }
            val name = a.info.files().filePath(index).substringAfterLast('/')
            "http://127.0.0.1:${srv.port}/t/$hash/$index/${URLEncoder.encode(name, "UTF-8").replace("+", "%20")}"
        } catch (t: Throwable) {
            Log.e(TAG, "prepareStream failed", t)
            null
        }
    }

    /** Share of the video file (0..100) that is already downloaded and verified: shown as "Buffering N%" in the player. */
    fun fileProgressPercent(torrentPath: String): Int {
        val (hash, index) = TorrentLibrary.parsePath(torrentPath) ?: return 0
        val a = active?.takeIf { it.infoHash == hash } ?: return 0
        if (a.prebufferPct in 0..99 && a.selectedFile == index) return a.prebufferPct
        return try {
            val fs = a.info.files()
            val size = fs.fileSize(index)
            if (size <= 0L) return 100
            val pieceLen = a.info.pieceLength().toLong()
            val first = (fs.fileOffset(index) / pieceLen).toInt()
            val last = ((fs.fileOffset(index) + size - 1) / pieceLen).toInt().coerceAtMost(a.info.numPieces() - 1)
            var have = 0
            for (p in first..last) if (a.handle.havePiece(p)) have++
            ((have * 100L) / (last - first + 1)).toInt().coerceIn(0, 100)
        } catch (_: Throwable) { 0 }
    }

    /**
     * Start-up buffer: waits until the first ~[PREBUFFER_PERCENT]% of the file (at most [PREBUFFER_HEAD_BYTES]) and the
     * end of the file are downloaded. The head holds the container header with ALL audio / subtitle tracks, the chapter
     * list and the embedded fonts, so the player opens the file with every track at once. The rest keeps downloading in
     * the background while the video plays.
     *
     * The old version waited for 5% of the WHOLE file (hundreds of MB for a big movie) and for the last pieces of the
     * file, up to 3 minutes - and when a piece never came, playback was started blind. Now the wait is limited in bytes
     * and it never blocks longer than needed: after [PREBUFFER_SOFT_TIMEOUT_MS] playback starts as soon as the container
     * header is there; the loopback server then waits for each further piece with top priority.
     */
    private suspend fun prebuffer(a: ActiveTorrent, fileIndex: Int) {
        a.prebufferPct = 0
        val fs = a.info.files()
        val size = fs.fileSize(fileIndex)
        if (size <= 0L) { a.prebufferPct = 100; return }
        val pieceLen = a.info.pieceLength().toLong()
        val first = (fs.fileOffset(fileIndex) / pieceLen).toInt()
        val last = ((fs.fileOffset(fileIndex) + size - 1) / pieceLen).toInt().coerceAtMost(a.info.numPieces() - 1)
        val total = last - first + 1
        val headBytes = (size * PREBUFFER_PERCENT / 100L).coerceIn(minOf(PREBUFFER_MIN_HEAD_BYTES, size), PREBUFFER_HEAD_BYTES)
        val headPieces = Math.ceil(headBytes.toDouble() / pieceLen).toInt().coerceIn(minOf(3, total), total)
        // the very first pieces hold the track list: these are the minimum to open the file
        val coreHead = minOf(headPieces, maxOf(1, Math.ceil(2L * 1024 * 1024 / pieceLen.toDouble()).toInt()))
        val tailPieceCount = Math.ceil(PREBUFFER_TAIL_BYTES.toDouble() / pieceLen).toInt().coerceIn(1, 4)
        val tailFirst = maxOf(first + headPieces, last - tailPieceCount + 1)
        val tailPieces = if (tailFirst <= last) (tailFirst..last).toList() else emptyList()
        val name = fs.filePath(fileIndex).lowercase()
        // mp4 / mov / avi keep their index at the end: without it they cannot start. mkv / webm / ts play without it.
        val needTail = name.endsWith(".mp4") || name.endsWith(".m4v") || name.endsWith(".mov") || name.endsWith(".avi")

        fun schedule() {
            for (i in 0 until headPieces) {
                try { if (!a.handle.havePiece(first + i)) a.handle.setPieceDeadline(first + i, 100 + i * 60) } catch (_: Throwable) {}
            }
            for ((k, p) in tailPieces.withIndex()) {
                try { if (!a.handle.havePiece(p)) a.handle.setPieceDeadline(p, 1500 + k * 100) } catch (_: Throwable) {}
            }
        }
        try { a.handle.clearPieceDeadlines() } catch (_: Throwable) {}
        schedule()

        val started = System.currentTimeMillis()
        var lastSchedule = started
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (active !== a) return
            val elapsed = System.currentTimeMillis() - started
            var haveHead = 0
            var haveCore = 0
            var haveTail = 0
            try {
                for (i in 0 until headPieces) if (a.handle.havePiece(first + i)) { haveHead++; if (i < coreHead) haveCore++ }
                for (p in tailPieces) if (a.handle.havePiece(p)) haveTail++
            } catch (_: Throwable) {
                a.prebufferPct = 100
                return
            }
            val tailNeeded = if (needTail) tailPieces.size else 0
            val wanted = headPieces + tailNeeded
            val have = haveHead + minOf(haveTail, tailNeeded)
            a.prebufferPct = if (wanted <= 0) 100 else ((have * 100L) / wanted).toInt().coerceIn(0, 99)
            val complete = haveHead >= headPieces && haveTail >= tailNeeded
            // a slow swarm: the container header (all tracks) is enough to open the file
            val softReady = elapsed > PREBUFFER_SOFT_TIMEOUT_MS && haveCore >= coreHead && (tailNeeded == 0 || haveTail >= tailNeeded)
            if (complete || softReady || elapsed > PREBUFFER_TIMEOUT_MS) {
                a.prebufferPct = 100
                return
            }
            if (System.currentTimeMillis() - lastSchedule > 2000L) {
                // deadlines can expire while peers are slow: ask again
                schedule()
                lastSchedule = System.currentTimeMillis()
            }
            kotlinx.coroutines.delay(200)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Downloads (Download page): a whole file is downloaded into a folder of the app, independent of the streamed torrent
    // ---------------------------------------------------------------------------------------------

    /** Progress of one downloading torrent file, counted in verified pieces. */
    class TorrentDlProgress(val doneBytes: Long, val totalBytes: Long, val complete: Boolean)

    private class DlTorrent(
        val infoHash: String,
        val info: TorrentInfo,
        val handle: TorrentHandle,
        val saveDir: File
    ) {
        val wanted: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet<Int>())
    }

    private val dlTorrents = java.util.concurrent.ConcurrentHashMap<String, DlTorrent>()

    /** Torrent info (name + files) of a magnet / .torrent link WITHOUT creating a playlist: used by the Download page. */
    suspend fun fetchSummary(context: Context, source: TorrentSource): Pair<TorrentSummary?, String?> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            val bytes: ByteArray = when (source) {
                is TorrentSource.Bytes -> source.data
                is TorrentSource.LocalFile -> readUriBytes(app, source.uri)
                    ?: return@withContext Pair<TorrentSummary?, String?>(null, "Could not read the torrent file")
                is TorrentSource.RemoteFile -> downloadBytes(source.url)
                    ?: return@withContext Pair<TorrentSummary?, String?>(null, "Could not download the torrent file")
                is TorrentSource.Magnet -> {
                    val wanted = magnetInfoHash(source.uri)
                    val saved = wanted?.let { TorrentLibrary.loadBytes(app, it) }
                    saved ?: fetchMagnet(app, source.uri)
                        ?: return@withContext Pair<TorrentSummary?, String?>(null, "Could not get the torrent info. No peers found or no internet.")
                }
            }
            val info = try { TorrentInfo(bytes) } catch (t: Throwable) {
                Log.w(TAG, "invalid torrent data", t)
                return@withContext Pair<TorrentSummary?, String?>(null, "This is not a valid torrent")
            }
            val summary = summaryOf(info)
            TorrentLibrary.save(app, summary, bytes)
            Pair<TorrentSummary?, String?>(summary, null)
        } catch (t: Throwable) {
            Log.e(TAG, "fetchSummary failed", t)
            Pair<TorrentSummary?, String?>(null, t.message?.takeIf { it.isNotBlank() }?.take(120) ?: "Could not open the torrent")
        }
    }

    private fun setDownloadPriority(h: TorrentHandle, fileIndex: Int) {
        val p = playPriority ?: return
        try { h.filePriority(fileIndex, p) } catch (_: Throwable) {}
    }

    /**
     * Starts (or continues) downloading ONE file of a saved torrent into [saveDir]; every other file is skipped.
     * Several files of the same torrent share one torrent handle and the same [saveDir].
     */
    fun beginDownload(context: Context, infoHash: String, fileIndex: Int, saveDir: File): Boolean {
        try {
            synchronized(lock) {
                val existing = dlTorrents[infoHash]
                if (existing != null) {
                    if (fileIndex < 0 || fileIndex >= existing.info.files().numFiles()) return false
                    existing.wanted.add(fileIndex)
                    setDownloadPriority(existing.handle, fileIndex)
                    return true
                }
                val app = context.applicationContext
                val sm = ensureSession(app) ?: return false
                val bytes = TorrentLibrary.loadBytes(app, infoHash) ?: return false
                val info = TorrentInfo(bytes)
                val n = info.files().numFiles()
                if (fileIndex < 0 || fileIndex >= n) return false
                // a stream of the same torrent would clash with the download
                if (active?.infoHash == infoHash) dropActive(sm)
                saveDir.mkdirs()
                sm.download(info, saveDir)
                var handle: TorrentHandle? = null
                var waited = 0
                while (waited < 5000) {
                    val h = sm.find(info.infoHash())
                    if (h != null && h.isValid) { handle = h; break }
                    Thread.sleep(50)
                    waited += 50
                }
                val found = handle ?: return false
                for (i in 0 until n) {
                    if (i != fileIndex) {
                        try { found.filePriority(i, Priority.IGNORE) } catch (_: Throwable) {}
                    }
                }
                setDownloadPriority(found, fileIndex)
                try { found.resume() } catch (_: Throwable) {}
                addExtraTrackers(found)
                val dl = DlTorrent(infoHash, info, found, saveDir)
                dl.wanted.add(fileIndex)
                dlTorrents[infoHash] = dl
                return true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "beginDownload failed", t)
            return false
        }
    }

    fun downloadProgress(infoHash: String, fileIndex: Int): TorrentDlProgress? {
        val dl = dlTorrents[infoHash] ?: return null
        try {
            val fs = dl.info.files()
            val size = fs.fileSize(fileIndex)
            if (size <= 0L) return TorrentDlProgress(0L, 0L, true)
            val pieceLen = dl.info.pieceLength().toLong()
            val numPieces = dl.info.numPieces()
            val offset = fs.fileOffset(fileIndex)
            val first = (offset / pieceLen).toInt()
            val last = ((offset + size - 1) / pieceLen).toInt().coerceAtMost(numPieces - 1)
            var have = 0L
            for (p in first..last) {
                if (dl.handle.havePiece(p)) have++
            }
            val totalPieces = (last - first + 1).toLong()
            val complete = have >= totalPieces
            val done = if (complete) size else minOf(size, have * pieceLen)
            return TorrentDlProgress(done, size, complete)
        } catch (_: Throwable) {
            return null
        }
    }

    /** The downloaded file on disk (only meaningful once [downloadProgress] says it is complete). */
    fun downloadedFile(infoHash: String, fileIndex: Int): File? {
        val dl = dlTorrents[infoHash] ?: return null
        return try {
            File(dl.saveDir, dl.info.files().filePath(fileIndex)).takeIf { it.exists() }
        } catch (_: Throwable) {
            null
        }
    }

    /** Stops downloading one file; the torrent leaves the session when its last wanted file is released. */
    fun endDownload(infoHash: String, fileIndex: Int) {
        synchronized(lock) {
            val dl = dlTorrents[infoHash] ?: return
            dl.wanted.remove(fileIndex)
            if (dl.wanted.isEmpty()) {
                dlTorrents.remove(infoHash)
                try { session?.remove(dl.handle) } catch (_: Throwable) {}
            } else {
                try { dl.handle.filePriority(fileIndex, Priority.IGNORE) } catch (_: Throwable) {}
            }
        }
    }

    /** Stops the torrent session and deletes the downloaded data (called when the app closes). */
    fun release() {
        synchronized(lock) {
            val sm = session
            try { if (sm != null) dropActive(sm) } catch (_: Throwable) {}
            try { server?.close() } catch (_: Throwable) {}
            server = null
            // Videos of the Download page are still downloading: keep the session (the download service keeps the app alive).
            if (dlTorrents.isNotEmpty()) return
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

            val windowPieces = (48L * 1024 * 1024 / pieceLen).toInt().coerceIn(4, 96)
            val lastPieceOfFile = ((fileOffset + size - 1) / pieceLen).toInt().coerceAtMost(numPieces - 1)
            var scheduledPiece = -1
            val buf = ByteArray(256 * 1024)
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
                        Thread.sleep(100)
                        waitedMs += 100
                        // the wanted piece must never be starved by background pieces: keep it at the very front
                        if (waitedMs % 1500L == 0L) {
                            try {
                                a.handle.setPieceDeadline(piece, 0)
                                for (i in 1 until 6) {
                                    val p = piece + i
                                    if (p > lastPieceOfFile) break
                                    if (!a.handle.havePiece(p)) a.handle.setPieceDeadline(p, 200 + i * 150)
                                }
                            } catch (_: Throwable) {}
                        }
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
