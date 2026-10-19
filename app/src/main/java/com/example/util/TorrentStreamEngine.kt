package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

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
 * Torrent / magnet playback on top of libtorrent4j (github.com/aldenml/libtorrent4j), built the same way as the
 * torrent engine of mpvRx:
 *
 *  - streams run in ONE long-lived stream session of their own ([streamSession]: default settings + DHT + local peer
 *    discovery), separate from the Download page session. Every played video is its own torrent handle inside it, and stopping
 *    a stream removes that handle and its data. Before, a brand-new session was created and torn down for every tap: its DHT
 *    started cold (no peers for a long time, "Buffering" without any traffic) and the native start / stop on every tap was
 *    heavy. The session is pre-warmed while the torrent info is being fetched, so peers are found much sooner;
 *  - only the chosen file is wanted (the others are IGNORE); it is downloaded in order (SEQUENTIAL_DOWNLOAD +
 *    setSequentialRange), the first 16 pieces and the last 8 pieces are TOP priority, with deadlines;
 *  - the file is handed to mpv through [TorrentProxyServer], a loopback HTTP server with byte ranges: a read waits for
 *    the pieces it needs, moves them to the front and schedules the next 16 MiB behind them;
 *  - the life cycle is serialized like mpvRx's lifecycleMutex + generation counter: ONE stream session exists at any time.
 *    A new tap stops the previous session first (inside the mutex), a request that a newer one overtook gives up at once,
 *    and the player stops the stream when it closes. Before, quick repeated taps started several libtorrent sessions at
 *    the same time and lost track of all but the last one (memory / thread / disk pressure until the phone gave up).
 *
 * Whole-file downloads (Download page) run in a separate long-lived session ([dlSession]).
 */
object TorrentStreamEngine {
    private const val TAG = "TorrentStreamEngine"
    private const val MAGNET_TIMEOUT_SEC = 90
    private const val MAX_TORRENT_FILE_BYTES = 16 * 1024 * 1024
    private const val MAX_METADATA_BYTES = 16 * 1024 * 1024
    private const val HANDLE_WAIT_MS = 10_000L
    /** Subtitle files next to the video (srt / ass ...) are fetched too, when they are small. */
    private val SUBTITLE_EXT = setOf("srt", "ass", "ssa", "vtt", "sub", "sup")
    private const val SUBTITLE_MAX_BYTES = 5L * 1024 * 1024
    /** Head of the file (container header, track list) that must be there before mpv opens the stream. */
    private const val PREBUFFER_HEAD_BYTES = 4L * 1024 * 1024
    private const val PREBUFFER_MAX_HEAD_PIECES = 8
    /** A slow swarm must not keep the screen on "Buffering" forever: playback opens the stream anyway after this. */
    private const val PREBUFFER_SOFT_TIMEOUT_MS = 25_000L
    /** With NO peer connected at all the stream is not opened (it would only hang): the start fails after this. */
    private const val PREBUFFER_NO_PEER_TIMEOUT_MS = 90_000L

    /** Public trackers: added to every PUBLIC torrent (many ISPs block the torrent's own trackers), never to private torrents. */
    private val DEFAULT_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://opentracker.io:6969/announce",
        "https://tracker.tamersunion.org:443/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "https://tracker.opentrackr.org:443/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.filemail.com:6969/announce",
        "http://tracker.opentrackr.org:1337/announce"
    )

    private val playableMimeTypes = mapOf(
        "3g2" to "video/3gpp2", "3gp" to "video/3gpp", "asf" to "video/x-ms-asf", "avi" to "video/x-msvideo",
        "divx" to "video/*", "f4v" to "video/mp4", "flv" to "video/x-flv", "m2ts" to "video/mp2t",
        "m2v" to "video/mpeg", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "mov" to "video/quicktime",
        "mp4" to "video/mp4", "mpeg" to "video/mpeg", "mpg" to "video/mpeg", "mts" to "video/mp2t",
        "ogm" to "video/ogg", "ogv" to "video/ogg", "rm" to "video/*", "rmvb" to "video/*", "ts" to "video/mp2t",
        "vob" to "video/mpeg", "webm" to "video/webm", "wmv" to "video/x-ms-wmv", "xvid" to "video/*"
    )

    private fun mimeFor(path: String): String =
        playableMimeTypes[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    private val lock = Any()

    /** One stream start / stop at a time (mpvRx: lifecycleMutex). */
    private val streamMutex = Mutex()
    /** Bumped by every prepare / stop request: an older request that sees a newer number stops working. */
    private val generation = AtomicLong(0L)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Priority for the small side files (subtitles) and the Download page. The constant names of libtorrent4j's Priority
     * differ between releases, so a "normal" one is looked up by name at runtime instead of hard-coding it.
     */
    private val normalPriority: Priority by lazy {
        try {
            val all = Priority::class.java.enumConstants?.toList().orEmpty().filter { it != Priority.IGNORE }
            val wanted = listOf("DEFAULT_PRIORITY", "DEFAULT", "NORMAL", "FOUR")
            wanted.firstNotNullOfOrNull { n -> all.firstOrNull { it.toString().equals(n, ignoreCase = true) } }
                ?: all.firstOrNull() ?: Priority.TOP_PRIORITY
        } catch (_: Throwable) {
            Priority.TOP_PRIORITY
        }
    }

    // ---------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------

    /** The one torrent video that is streaming right now: its own session, handle, proxy server and data folder. */
    private class ActiveStream(
        val infoHash: String,
        val fileIndex: Int,
        val session: SessionManager,
        val info: TorrentInfo,
        val handle: TorrentHandle,
        val proxy: TorrentProxyServer,
        val saveDir: File,
        val url: String,
        val subtitleIndexes: Set<Int>
    ) {
        /** 0..100: how far the start-up buffer (head of the file) is filled; 100 = mpv may open the stream. */
        @Volatile var prebufferPct: Int = 0
    }

    @Volatile private var active: ActiveStream? = null

    /** The long-lived libtorrent session that every stream runs in (see the class comment). */
    @Volatile private var streamSession: SessionManager? = null

    /** Short reason of the last failed [prepareStream], shown to the user instead of a generic message. */
    @Volatile var lastStartError: String? = null
        private set

    @Volatile private var dataRoot: File? = null
    @Volatile private var dataRootCleaned = false

    /** Session of the Download page + torrent info lookups (magnet metadata). Streams never use it. */
    @Volatile private var dlSession: SessionManager? = null

    // ---------------------------------------------------------------------------------------------
    // Opening (metadata only - nothing is downloaded here)
    // ---------------------------------------------------------------------------------------------

    suspend fun open(context: Context, source: TorrentSource): TorrentOpenResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!NetworkSettings.p2pEnabled(app)) {
            return@withContext TorrentOpenResult.Failure("P2P torrent streaming is turned off (Settings > Network)")
        }
        // start the stream session now: its DHT / tracker connections are warm by the time the video is played
        try { ensureStreamSession() } catch (t: Throwable) { Log.w(TAG, "stream session pre-warm failed", t) }
        try {
            val bytes: ByteArray = when (source) {
                is TorrentSource.Bytes -> source.data
                is TorrentSource.LocalFile -> readUriBytes(app, source.uri)
                    ?: return@withContext TorrentOpenResult.Failure("Could not read the torrent file")
                is TorrentSource.RemoteFile -> downloadBytes(source.url)
                    ?: return@withContext TorrentOpenResult.Failure("Could not download the torrent file")
                is TorrentSource.Magnet -> {
                    if (isV2OnlyMagnet(source.uri)) {
                        return@withContext TorrentOpenResult.Failure("BitTorrent v2-only torrents are not supported yet")
                    }
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

    /** Magnet metadata through the shared session. Public trackers are added only when the magnet carries none. */
    private fun fetchMagnet(context: Context, magnet: String): ByteArray? {
        val sm = ensureDlSession() ?: return null
        val tmp = File(context.cacheDir, "torrent-magnet").apply { mkdirs() }
        val full = if (magnetTrackerCount(magnet) >= 3) magnet else withDefaultTrackers(magnet)
        // a second try: the first one often only wakes up DHT / the trackers of a fresh session
        for (attempt in 0 until 2) {
            try {
                val bytes = sm.fetchMagnet(full, if (attempt == 0) MAGNET_TIMEOUT_SEC else 60, tmp)
                if (bytes != null) return bytes
            } catch (t: Throwable) {
                Log.w(TAG, "fetchMagnet failed", t)
            }
        }
        return null
    }

    private fun magnetTrackerCount(magnet: String): Int = Regex("(^|[?&])tr=", RegexOption.IGNORE_CASE).findAll(magnet).count()

    private fun withDefaultTrackers(magnet: String): String {
        val sb = StringBuilder(magnet)
        for (t in DEFAULT_TRACKERS) sb.append("&tr=").append(URLEncoder.encode(t, "UTF-8"))
        return sb.toString()
    }

    /** 40-char hex info hash of a magnet link (lower case) - null for base32 / v2-only links. */
    fun magnetInfoHash(magnet: String): String? =
        Regex("xt=urn:btih:([0-9a-fA-F]{40})").find(magnet)?.groupValues?.get(1)?.lowercase()

    private fun isV2OnlyMagnet(magnet: String): Boolean {
        val lower = magnet.lowercase()
        return lower.contains("xt=urn:btmh:") && !lower.contains("xt=urn:btih:")
    }

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
    // Streaming (one isolated session per played video, like mpvRx)
    // ---------------------------------------------------------------------------------------------

    /**
     * Folder of the streamed data: the app's INTERNAL cache, exactly like mpvRx. libtorrent 2.x writes the pieces through
     * memory-mapped files; on the external app folder (a FUSE mount on Android 11+) that makes every written piece go
     * through the slow FUSE daemon and piles up dirty pages, which freezes - and on some phones reboots - the device.
     */
    private fun streamRoot(context: Context): File {
        val app = context.applicationContext
        val root = dataRoot ?: File(app.cacheDir, "torrent-data").also { dataRoot = it }
        if (!dataRootCleaned) {
            // data of an earlier run is never reused: remove it so it cannot fill the storage
            dataRootCleaned = true
            try { root.deleteRecursively() } catch (_: Throwable) {}
            // streams of older versions of this app were kept in the external app folder: free that space too
            try { app.getExternalFilesDir("torrent-data")?.deleteRecursively() } catch (_: Throwable) {}
        }
        root.mkdirs()
        return root
    }

    private fun newStreamSession(): SessionManager {
        val settings = SettingsPack().apply {
            setMaxMetadataSize(MAX_METADATA_BYTES)
            setEnableDht(true)
            setEnableLsd(true)
        }
        val sm = SessionManager()
        sm.start(SessionParams(settings))
        return sm
    }

    /** The shared stream session (created on first use, then reused: warm DHT, no native start / stop per video). */
    private fun ensureStreamSession(): SessionManager = synchronized(lock) {
        streamSession ?: newStreamSession().also { streamSession = it }
    }

    /** Drops the shared stream session (after an error, or when the app closes); the next stream creates a fresh one. */
    private fun discardStreamSession() {
        val sm = synchronized(lock) { streamSession.also { streamSession = null } } ?: return
        try { sm.stop() } catch (_: Throwable) {}
    }

    /** Index of the video itself plus the small subtitle files that sit in the same folder of the torrent. */
    private fun wantedSideFiles(info: TorrentInfo, videoIndex: Int): Set<Int> {
        val out = HashSet<Int>()
        try {
            val fs = info.files()
            val dir = fs.filePath(videoIndex).replace('\\', '/').substringBeforeLast('/', "")
            for (i in 0 until fs.numFiles()) {
                if (i == videoIndex) continue
                val path = fs.filePath(i).replace('\\', '/')
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext in SUBTITLE_EXT && fs.fileSize(i) <= SUBTITLE_MAX_BYTES) {
                    // same folder, or the common "Subs" folder one level down
                    val d = path.substringBeforeLast('/', "")
                    if (d == dir || d.startsWith("$dir/")) out.add(i)
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun addDefaultTrackers(h: TorrentHandle) {
        for (t in DEFAULT_TRACKERS) { try { h.addTracker(AnnounceEntry(t)) } catch (_: Throwable) {} }
    }

    /** Waits (suspending) until libtorrent has the added torrent as a valid handle. */
    private suspend fun waitForHandle(sm: SessionManager, info: TorrentInfo): TorrentHandle? {
        val deadline = System.currentTimeMillis() + HANDLE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val h = sm.find(info.infoHash())
            if (h != null && h.isValid) return h
            delay(50)
        }
        return null
    }

    /**
     * mpvRx's configureStreaming: the played file is downloaded in order with TOP priority, the sequential range covers
     * exactly that file, the first 16 pieces (container header, all tracks) and the last 8 (mp4 index / mkv cues) are
     * requested first, with deadlines.
     */
    private fun configureStreaming(a: ActiveStream) {
        val handle = a.handle
        val fs = a.info.files()
        val n = fs.numFiles()
        try { handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD) } catch (_: Throwable) {}
        val priorities = Array(n) { i ->
            when {
                i == a.fileIndex -> Priority.TOP_PRIORITY
                i in a.subtitleIndexes -> normalPriority
                else -> Priority.IGNORE
            }
        }
        handle.prioritizeFiles(priorities)

        val pieceLength = a.info.pieceLength().toLong()
        val size = fs.fileSize(a.fileIndex)
        val fileOffset = fs.fileOffset(a.fileIndex)
        val firstPiece = (fileOffset / pieceLength).toInt()
        val lastPiece = ((fileOffset + size - 1L).coerceAtLeast(fileOffset) / pieceLength).toInt()
        try { handle.setSequentialRange(firstPiece, lastPiece) } catch (_: Throwable) {}

        val headEnd = (firstPiece + 15).coerceAtMost(lastPiece)
        for (piece in firstPiece..headEnd) {
            try {
                handle.piecePriority(piece, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(piece, (piece - firstPiece) * 100)
            } catch (_: Throwable) {}
        }
        val tailStart = (lastPiece - 7).coerceAtLeast(firstPiece)
        for (piece in tailStart..lastPiece) {
            try { handle.piecePriority(piece, Priority.TOP_PRIORITY) } catch (_: Throwable) {}
        }
        // not auto-managed = never put into libtorrent's download queue
        try { handle.unsetFlags(TorrentFlags.AUTO_MANAGED) } catch (_: Throwable) {}
        try { handle.unsetFlags(TorrentFlags.UPLOAD_MODE) } catch (_: Throwable) {}
        try { handle.resume() } catch (_: Throwable) {}
    }

    /** Called with [streamMutex] held. [gen] is this request's generation: a newer request makes it return null. */
    private suspend fun startStream(context: Context, infoHash: String, fileIndex: Int, gen: Long): ActiveStream? {
        val app = context.applicationContext
        val bytes = TorrentLibrary.loadBytes(app, infoHash)
        if (bytes == null) {
            lastStartError = "The saved torrent info is missing. Open the magnet / .torrent again."
            return null
        }
        val info = try { TorrentInfo(bytes) } catch (_: Throwable) {
            lastStartError = "The saved torrent info is damaged. Open the magnet / .torrent again."
            return null
        }
        if (!info.isValid) {
            lastStartError = "The saved torrent info is not valid. Open the magnet / .torrent again."
            return null
        }
        val fs = info.files()
        if (fileIndex < 0 || fileIndex >= fs.numFiles()) {
            lastStartError = "This video is not part of the torrent any more."
            return null
        }

        // The previous stream (another video) is removed COMPLETELY first (handle, proxy, data) - never two streams at once.
        stopActiveBlocking()
        if (generation.get() != gen) return null

        val saveDir = File(streamRoot(app), "${UUID.randomUUID()}").apply { mkdirs() }
        var session: SessionManager? = null
        var handleRef: TorrentHandle? = null
        var proxy: TorrentProxyServer? = null
        try {
            session = ensureStreamSession()
            val nFiles = fs.numFiles()
            // nothing is wanted until configureStreaming picks the played file (mpvRx order)
            val none = Array(nFiles) { Priority.IGNORE }
            session.download(info, saveDir, null, none, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
            val handle = waitForHandle(session, info)
            handleRef = handle
            if (handle == null || generation.get() != gen) {
                if (handle == null) lastStartError = "The torrent engine did not accept this torrent."
                cleanupStream(session, handle, null, saveDir)
                return null
            }
            // public torrents: extra public trackers (the torrent's own ones are often dead or blocked by the ISP)
            if (!info.isPrivate) addDefaultTrackers(handle)
            try { handle.forceReannounce() } catch (_: Throwable) {}

            val side = wantedSideFiles(info, fileIndex)
            val pieceLength = info.pieceLength()
            val size = fs.fileSize(fileIndex)
            val fileOffset = fs.fileOffset(fileIndex)
            val firstPiece = (fileOffset / pieceLength).toInt()
            val lastPiece = ((fileOffset + size - 1L).coerceAtLeast(fileOffset) / pieceLength).toInt()
            val relPath = fs.filePath(fileIndex)
            val server = TorrentProxyServer(
                target = TorrentProxyServer.Target(
                    handle = handle,
                    file = File(saveDir, relPath),
                    fileOffset = fileOffset,
                    fileSize = size,
                    pieceLength = pieceLength,
                    firstPiece = firstPiece,
                    lastPiece = lastPiece,
                    mimeType = mimeFor(relPath)
                )
            )
            server.startServer()
            proxy = server
            val a = ActiveStream(infoHash, fileIndex, session, info, handle, server, saveDir, server.serverUrl, side)
            configureStreaming(a)
            if (generation.get() != gen) {
                // overtaken while the stream was being set up: this one is not wanted any more
                cleanupStream(session, handle, server, saveDir)
                return null
            }
            synchronized(lock) { active = a }
            return a
        } catch (t: Throwable) {
            if (t !is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "startStream failed", t)
                lastStartError = if (t is UnsatisfiedLinkError || t is NoClassDefFoundError) "The torrent engine is not available on this phone."
                else "The torrent engine could not start (${t.javaClass.simpleName})."
            }
            cleanupStream(session, handleRef, proxy, saveDir)
            // a session that threw is not trusted again: the next stream starts a fresh one
            if (t !is kotlinx.coroutines.CancellationException) discardStreamSession()
            if (t is kotlinx.coroutines.CancellationException) throw t
            return null
        }
    }

    /**
     * Makes the torrent video behind [torrentPath] ("torrent://<hash>/<index>") ready and returns the local http URL the
     * player opens - null when the torrent could not be started. The call returns once the head of the file arrived (or
     * after a short time on a slow swarm); the rest keeps loading behind the playhead.
     */
    suspend fun prepareStream(context: Context, torrentPath: String): String? = withContext(Dispatchers.IO) {
        val (hash, index) = TorrentLibrary.parsePath(torrentPath) ?: return@withContext null
        lastStartError = null
        // This request is the newest one: an older start / prebuffer that is still running sees the new number and stops.
        val gen = generation.incrementAndGet()
        try {
            streamMutex.withLock {
                if (generation.get() != gen) return@withLock null
                val current = active
                val a = if (current != null && current.infoHash == hash && current.fileIndex == index && current.handle.isValid) {
                    current
                } else {
                    startStream(context, hash, index, gen)
                } ?: return@withLock null
                val connected = prebuffer(a, gen)
                if (generation.get() != gen) null
                else if (!connected) {
                    lastStartError = "No peers found for this torrent (no seeders, or the trackers / DHT are blocked on this network)."
                    // the stream that found nobody is removed: it must not keep a dead session entry around
                    stopActiveBlocking()
                    null
                } else a.url
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.e(TAG, "prepareStream failed", t)
            null
        }
    }

    /**
     * Stops the stream that is playing (called when the player closes or another kind of video opens): non-blocking, the
     * session, proxy and downloaded data go away in the background. A newer prepareStream() that arrives before the
     * clean-up runs (the same video opened again at once) keeps the stream instead.
     */
    fun stopStream() {
        val stopGen = generation.incrementAndGet()
        engineScope.launch {
            streamMutex.withLock {
                if (generation.get() == stopGen) stopActiveBlocking()
            }
        }
    }

    /** Stops the active stream and waits until its session is gone (runs on a background thread, under [streamMutex]). */
    private fun stopActiveBlocking() {
        val a = synchronized(lock) { active?.also { active = null } } ?: return
        cleanupStream(a.session, a.handle, a.proxy, a.saveDir)
    }

    /** Waits for the head of the played file so mpv can read its header at once; never longer than [PREBUFFER_SOFT_TIMEOUT_MS]. */
    /** Returns false when no peer could be found at all (the caller then reports a failed start instead of hanging). */
    private suspend fun prebuffer(a: ActiveStream, gen: Long): Boolean {
        a.prebufferPct = 0
        val fs = a.info.files()
        val size = fs.fileSize(a.fileIndex)
        if (size <= 0L) { a.prebufferPct = 100; return true }
        val pieceLen = a.info.pieceLength().toLong()
        val first = (fs.fileOffset(a.fileIndex) / pieceLen).toInt()
        val last = ((fs.fileOffset(a.fileIndex) + size - 1) / pieceLen).toInt().coerceAtMost(a.info.numPieces() - 1)
        val total = last - first + 1
        val headPieces = Math.ceil(PREBUFFER_HEAD_BYTES.toDouble() / pieceLen).toInt()
            .coerceIn(1, PREBUFFER_MAX_HEAD_PIECES).coerceAtMost(total)
        val started = System.currentTimeMillis()
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (active !== a || !a.handle.isValid || generation.get() != gen) return true
            var have = 0
            try {
                for (i in 0 until headPieces) if (a.handle.havePiece(first + i)) have++
            } catch (_: Throwable) {
                a.prebufferPct = 100
                return true
            }
            if (have >= headPieces) { a.prebufferPct = 100; return true }
            a.prebufferPct = ((have * 100L) / headPieces).toInt().coerceIn(0, 99)
            val elapsed = System.currentTimeMillis() - started
            if (elapsed > PREBUFFER_SOFT_TIMEOUT_MS) {
                // A slow swarm that HAS peers: open the stream anyway, the rest keeps loading behind the playhead.
                // A swarm without any peer: keep looking for a while (trackers / DHT need time), then give up cleanly.
                val peers = try { a.handle.status().numPeers() } catch (_: Throwable) { 1 }
                if (peers > 0 || have > 0) { a.prebufferPct = 100; return true }
                if (elapsed > PREBUFFER_NO_PEER_TIMEOUT_MS) { a.prebufferPct = 0; return false }
                try { a.handle.forceReannounce() } catch (_: Throwable) {}
                delay(2000)
            }
            delay(200)
        }
    }

    /** Share of the video file (0..100) that is already downloaded and verified: shown as "Buffering N%" in the player. */
    fun fileProgressPercent(torrentPath: String): Int {
        val (hash, index) = TorrentLibrary.parsePath(torrentPath) ?: return 0
        val a = active?.takeIf { it.infoHash == hash && it.fileIndex == index } ?: return 0
        if (!a.handle.isValid) return 0
        if (a.prebufferPct in 0..99) return a.prebufferPct
        return try {
            val size = a.info.files().fileSize(index)
            if (size <= 0L) return 100
            // ONE status call (only the played file + its subtitles are wanted, so this is the file's progress). The old
            // loop asked libtorrent about every single piece - hundreds to thousands of blocking calls per refresh.
            (a.handle.status().progress() * 100f).toInt().coerceIn(0, 100)
        } catch (_: Throwable) { 0 }
    }

    /** "12 peers · 1.4 MB/s" of the torrent that is streaming: shown next to "Buffering" so a slow start is understandable. */
    fun statusLine(torrentPath: String): String {
        val (hash, _) = TorrentLibrary.parsePath(torrentPath) ?: return ""
        val a = active?.takeIf { it.infoHash == hash } ?: return ""
        if (!a.handle.isValid) return ""
        return try {
            val st = a.handle.status()
            val rate = st.downloadPayloadRate().toLong()
            val speed = when {
                rate >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", rate / 1048576.0)
                else -> "${rate / 1024} KB/s"
            }
            val peers = st.numPeers()
            if (peers <= 0) "Searching for peers…" else "$peers peers (${st.numSeeds()} seeds) · $speed"
        } catch (_: Throwable) { "" }
    }

    /** Subtitle files that came with the played video (same folder of the torrent) and are completely downloaded. */
    fun readySubtitleFiles(torrentPath: String): List<File> {
        val (hash, index) = TorrentLibrary.parsePath(torrentPath) ?: return emptyList()
        val a = active?.takeIf { it.infoHash == hash && it.fileIndex == index } ?: return emptyList()
        return try {
            val fs = a.info.files()
            val pieceLen = a.info.pieceLength().toLong()
            a.subtitleIndexes.toList().mapNotNull { i ->
                val size = fs.fileSize(i)
                if (size <= 0L) return@mapNotNull null
                val first = (fs.fileOffset(i) / pieceLen).toInt()
                val last = ((fs.fileOffset(i) + size - 1) / pieceLen).toInt().coerceAtMost(a.info.numPieces() - 1)
                for (p in first..last) if (!a.handle.havePiece(p)) return@mapNotNull null
                File(a.saveDir, fs.filePath(i)).takeIf { it.exists() && it.length() > 0L }
            }
        } catch (_: Throwable) { emptyList() }
    }

    /** Removes ONE stream (proxy, torrent handle, data). The shared stream session itself keeps running (warm DHT). */
    private fun cleanupStream(session: SessionManager?, handle: TorrentHandle?, proxy: TorrentProxyServer?, dir: File) {
        try { proxy?.close() } catch (_: Throwable) {}
        if (session != null) {
            try { if (handle != null && handle.isValid) session.remove(handle, SessionHandle.DELETE_FILES) } catch (_: Throwable) {}
        }
        try { dir.deleteRecursively() } catch (_: Throwable) {}
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

    /**
     * Session of the Download page: default libtorrent timeouts (the old shortened ones kept dropping slow peers) and
     * enough connection slots for several torrents at once.
     */
    private fun ensureDlSession(): SessionManager? = synchronized(lock) {
        dlSession ?: try {
            val sm = SessionManager()
            sm.start(SessionParams(SettingsPack().apply {
                setMaxMetadataSize(MAX_METADATA_BYTES)
                setEnableDht(true)
                setEnableLsd(true)
            }))
            try {
                val sp = SettingsPack()
                fun int(t: settings_pack.int_types, v: Int) { try { sp.setInteger(t.swigValue(), v) } catch (_: Throwable) {} }
                fun bool(t: settings_pack.bool_types, v: Boolean) { try { sp.setBoolean(t.swigValue(), v) } catch (_: Throwable) {} }
                int(settings_pack.int_types.active_downloads, 50)
                int(settings_pack.int_types.active_seeds, 50)
                int(settings_pack.int_types.active_limit, 200)
                int(settings_pack.int_types.connections_limit, 400)
                int(settings_pack.int_types.download_rate_limit, 0)
                int(settings_pack.int_types.upload_rate_limit, 0)
                bool(settings_pack.bool_types.enable_upnp, true)
                bool(settings_pack.bool_types.enable_natpmp, true)
                bool(settings_pack.bool_types.announce_to_all_trackers, true)
                bool(settings_pack.bool_types.announce_to_all_tiers, true)
                sm.applySettings(sp)
            } catch (t: Throwable) {
                Log.w(TAG, "could not tune the download session", t)
            }
            dlSession = sm
            sm
        } catch (t: Throwable) {
            Log.e(TAG, "torrent session could not start", t)
            null
        }
    }

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
                    if (isV2OnlyMagnet(source.uri)) {
                        return@withContext Pair<TorrentSummary?, String?>(null, "BitTorrent v2-only torrents are not supported yet")
                    }
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
        try { h.filePriority(fileIndex, Priority.TOP_PRIORITY) } catch (_: Throwable) {}
    }

    /**
     * Starts (or continues) downloading ONE file of a saved torrent into [saveDir]; every other file is skipped.
     * Several files of the same torrent share one torrent handle and the same [saveDir]. The file is fetched in
     * libtorrent's normal rarest-first order (the fastest way to get a whole file) with every connection slot free.
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
                val sm = ensureDlSession() ?: return false
                val bytes = TorrentLibrary.loadBytes(app, infoHash) ?: return false
                val info = TorrentInfo(bytes)
                val n = info.files().numFiles()
                if (fileIndex < 0 || fileIndex >= n) return false
                saveDir.mkdirs()
                val priorities = Array(n) { i -> if (i == fileIndex) Priority.TOP_PRIORITY else Priority.IGNORE }
                sm.download(info, saveDir, null, priorities, null, org.libtorrent4j.swig.torrent_flags_t())
                var handle: TorrentHandle? = null
                var waited = 0
                while (waited < 5000) {
                    val h = sm.find(info.infoHash())
                    if (h != null && h.isValid) { handle = h; break }
                    Thread.sleep(50)
                    waited += 50
                }
                val found = handle ?: return false
                setDownloadPriority(found, fileIndex)
                try { found.unsetFlags(TorrentFlags.AUTO_MANAGED) } catch (_: Throwable) {}
                try { found.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD) } catch (_: Throwable) {}
                try { found.resume() } catch (_: Throwable) {}
                if (!info.isPrivate) addDefaultTrackers(found)
                try { found.forceReannounce() } catch (_: Throwable) {}
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
                try { dlSession?.remove(dl.handle) } catch (_: Throwable) {}
            } else {
                try { dl.handle.filePriority(fileIndex, Priority.IGNORE) } catch (_: Throwable) {}
            }
        }
    }

    /** Stops the streaming session and deletes its data (called when the app closes). Downloads in progress keep running. */
    fun release() {
        generation.incrementAndGet()
        val last = synchronized(lock) { active?.also { active = null } }
        // the app is closing: the clean-up must not be tied to a coroutine that dies with the process
        Thread {
            if (last != null) cleanupStream(last.session, last.handle, last.proxy, last.saveDir)
            discardStreamSession()
        }.apply { isDaemon = true; name = "torrent-stop" }.start()
        synchronized(lock) {
            // Videos of the Download page are still downloading: keep their session (the download service keeps the app alive).
            if (dlTorrents.isNotEmpty()) return
            val sm = dlSession
            dlSession = null
            if (sm != null) Thread { try { sm.stop() } catch (_: Throwable) {} }.apply { isDaemon = true }.start()
        }
    }
}
