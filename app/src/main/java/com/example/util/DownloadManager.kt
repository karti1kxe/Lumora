package com.example.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.example.torrent.TorrentDownloadEngine
import com.example.ytdlp.YtdlpDownloader
import com.example.ytdlp.YtdlpManager
import com.example.ytdlp.YtdlpProbe
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Download page engine.
 *  - plain http(s) file links (media-plugin links, direct files) are downloaded with [ParallelDownloader] (many
 *    connections, resumable) or, when the server has no byte ranges, as one stream; audio files get their thumbnail
 *    embedded as cover art. The old yt-dlp / torrent engines were removed (to be written again later),
 *  - the finished file is moved to Movies/Lumora (Music/Lumora for audio) so it appears in the app's library.
 * Progress, speed and the time left are measured here (bytes on disk).
 * A foreground service ([DownloadService]) keeps the process alive and shows the notification.
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val MAX_PARALLEL = 3
    private const val MAX_AUTO_RETRIES = 8
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    private enum class Stop { PAUSE, CANCEL }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(MAX_PARALLEL)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val stops = ConcurrentHashMap<String, Stop>()

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /** Starts the download of one "Recent Streams" entry. Returns the short message to show to the user. */
    fun enqueueFromRecent(context: Context, entry: RecentStreamEntry): String {
        val app = context.applicationContext
        DownloadStore.init(app)
        val source = entry.url.trim()
        for (old in DownloadStore.items.value.filter { it.source == source }) {
            when (old.status) {
                DownloadStatus.COMPLETED -> {
                    if (DownloadFileSaver.exists(app, old)) return "Already downloaded"
                    DownloadStore.remove(app, old.id)
                }
                DownloadStatus.FAILED -> DownloadStore.remove(app, old.id)
                DownloadStatus.PAUSED -> {
                    resume(app, old.id)
                    return "Download resumed"
                }
                else -> return "Already downloading"
            }
        }
        val id = newId()
        val item = DownloadItem(
            id = id,
            source = source,
            title = entry.title,
            subtitle = entry.subtitle,
            isAudio = looksAudio(source),
            thumbnailUrl = entry.thumbnail?.takeIf { it.startsWith("http") }
        )
        DownloadStore.add(app, item)
        start(app, id)
        return "Download started"
    }

    /**
     * Starts the download of ONE file of a torrent (chosen in the torrent picker). Only that file is fetched; it is moved
     * to Movies/Lumora (Music/Lumora for audio) when it is complete.
     */
    fun enqueueTorrent(
        context: Context,
        source: String,
        fileIndex: Int,
        fileName: String,
        sizeBytes: Long,
        torrentName: String?
    ): String {
        val app = context.applicationContext
        DownloadStore.init(app)
        val src = source.trim()
        for (old in DownloadStore.items.value.filter {
            it.sourceType == DownloadSourceType.TORRENT && it.source == src && it.torrentFileIndex == fileIndex
        }) {
            when (old.status) {
                DownloadStatus.COMPLETED -> {
                    if (DownloadFileSaver.exists(app, old)) return "Already downloaded"
                    DownloadStore.remove(app, old.id)
                }
                DownloadStatus.FAILED -> DownloadStore.remove(app, old.id)
                DownloadStatus.PAUSED -> {
                    resume(app, old.id)
                    return "Download resumed"
                }
                else -> return "Already downloading"
            }
        }
        val id = newId()
        DownloadStore.add(
            app,
            DownloadItem(
                id = id,
                source = src,
                title = fileName.ifBlank { torrentName ?: src },
                subtitle = torrentName?.takeIf { it.isNotBlank() && it != fileName },
                isAudio = looksAudio(fileName),
                totalBytes = sizeBytes,
                sourceType = DownloadSourceType.TORRENT,
                torrentFileIndex = fileIndex
            )
        )
        start(app, id)
        return "Download started"
    }

    /** Starts a link download with the quality / name / container chosen in the download options sheet. */
    fun enqueueCustom(context: Context, req: DownloadRequest): String {
        val app = context.applicationContext
        DownloadStore.init(app)
        val source = req.source.trim()
        for (old in DownloadStore.items.value.filter {
            it.source == source && it.formatSpec == req.formatSpec && it.isAudio == req.isAudio && it.container == req.container
        }) {
            when (old.status) {
                DownloadStatus.COMPLETED -> {
                    if (DownloadFileSaver.exists(app, old)) return "Already downloaded"
                    DownloadStore.remove(app, old.id)
                }
                DownloadStatus.FAILED -> DownloadStore.remove(app, old.id)
                DownloadStatus.PAUSED -> {
                    resume(app, old.id)
                    return "Download resumed"
                }
                else -> return "Already downloading"
            }
        }
        val id = newId()
        DownloadStore.add(
            app,
            DownloadItem(
                id = id,
                source = source,
                title = req.title.ifBlank { source },
                subtitle = req.author,
                isAudio = req.isAudio,
                totalBytes = req.estimatedBytes,
                formatSpec = req.formatSpec,
                container = req.container,
                thumbnailUrl = req.thumbnailUrl,
                headers = req.headers,
                subtitles = req.subtitles
            )
        )
        // Direct provider links often have no size metadata in the plugin response. Probe the origin in
        // the background so the Downloads tab can show a real total as soon as the server exposes it.
        if (req.estimatedBytes <= 0L && (source.startsWith("http://", true) || source.startsWith("https://", true))) {
            scope.launch { probeDirectSize(app, id, source, req.headers) }
        }
        start(app, id)
        return "Download started"
    }

    private fun probeDirectSize(context: Context, id: String, source: String, headers: Map<String, String>) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(source).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                headers.forEach { (name, value) -> if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value) }
            }
            var length = if (connection.responseCode in 200..399) connection.getHeaderFieldLong("Content-Length", -1L) else -1L
            connection.disconnect()
            connection = null
            if (length <= 0L) {
                connection = (URL(source).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=0-0")
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    headers.forEach { (name, value) -> if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value) }
                }
                if (connection.responseCode == 206) {
                    val range = connection.getHeaderField("Content-Range").orEmpty()
                    length = range.substringAfterLast('/', "").toLongOrNull() ?: -1L
                } else if (connection.responseCode in 200..399) {
                    length = connection.getHeaderFieldLong("Content-Length", -1L)
                }
            }
            if (length > 0L && DownloadStore.get(id) != null) {
                DownloadStore.update(context, id, persist = true) { item ->
                    if (item.totalBytes > 0L) item else item.copy(totalBytes = length)
                }
            }
        } catch (_: Throwable) {
            // Some CDNs deliberately hide Content-Length; progress still uses actual downloaded bytes.
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }

    fun pause(context: Context, id: String) {
        val app = context.applicationContext
        val item = DownloadStore.get(id) ?: return
        if (!item.isActive) return
        stops[id] = Stop.PAUSE
        DownloadStore.update(app, id, persist = true) {
            it.copy(status = DownloadStatus.PAUSED, speedBps = 0L, etaSec = -1L, note = null)
        }
    }

    fun pauseAll(context: Context) {
        for (item in DownloadStore.items.value.filter { it.isActive }) pause(context, item.id)
    }

    /** Resume a paused download or retry a failed one. */
    fun resume(context: Context, id: String) {
        val app = context.applicationContext
        val item = DownloadStore.get(id) ?: return
        if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) start(app, id)
    }

    /** Stops a running / paused / failed download and throws its partial data away. A finished one is deleted with its file. */
    fun remove(context: Context, id: String) {
        val app = context.applicationContext
        val item = DownloadStore.get(id) ?: return
        if (item.status == DownloadStatus.COMPLETED) {
            DownloadFileSaver.delete(app, item)
            DownloadStore.remove(app, id)
            DownloadService.clearDone(app, id)
            return
        }
        cancel(app, id)
    }

    fun cancel(context: Context, id: String) {
        val app = context.applicationContext
        stops[id] = Stop.CANCEL
        DownloadStore.remove(app, id)
        DownloadService.clearDone(app, id)
        scope.launch {
            delay(800L)
            try {
                linkDir(app, id).deleteRecursively()
            } catch (_: Throwable) {
            }
            try {
                torrentDir(app, id).deleteRecursively()
            } catch (_: Throwable) {
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Job handling
    // ---------------------------------------------------------------------------------------------

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun baseDir(app: Context): File =
        File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads").apply { mkdirs() }

    private fun linkDir(app: Context, id: String): File = File(baseDir(app), id)

    /**
     * Torrent data lives in the app's INTERNAL storage (a real ext4 / f2fs filesystem), not in the FUSE-backed external
     * files folder: libtorrent's memory-mapped writes are not safe there. It survives app restarts / crashes, so a
     * torrent continues from the pieces that are already on disk.
     */
    private fun torrentDir(app: Context, id: String): File =
        File(File(app.filesDir, "torrent_downloads"), id)

    private fun looksAudio(url: String): Boolean {
        val host = try { java.net.URI(url).host?.lowercase() ?: "" } catch (_: Throwable) { "" }
        if (host == "music.youtube.com") return true
        return url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase() in AUDIO_EXT
    }

    private fun start(app: Context, id: String) {
        if (jobs[id]?.isActive == true) return
        stops.remove(id)
        DownloadStore.update(app, id, persist = true) {
            it.copy(
                status = DownloadStatus.QUEUED,
                speedBps = 0L,
                etaSec = -1L,
                note = if (it.status == DownloadStatus.FAILED || it.status == DownloadStatus.PAUSED) null else it.note
            )
        }
        DownloadService.start(app)
        jobs[id] = scope.launch {
            try {
                permits.withPermit {
                    // A lost connection / a changed link must never throw the download away: it is retried (the data
                    // that is already on disk is kept and continued) and only fails for good after several tries.
                    var attempt = 0
                    var resets = 0
                    while (true) {
                        val bytesAtStart = DownloadStore.get(id)?.downloadedBytes ?: 0L
                        try {
                            if (!stops.containsKey(id)) runDownload(app, id)
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            if (stops.containsKey(id)) break
                            // a try that still brought new data is not a failed try: a slow / flaky line that keeps making
                            // progress must never use up its retries (that is what ended downloads right before the end)
                            val progressed = (DownloadStore.get(id)?.downloadedBytes ?: 0L) > bytesAtStart
                            if (progressed && resets < 40) { attempt = 0; resets++ }
                            attempt++
                            if (attempt >= MAX_AUTO_RETRIES || isPermanentFailure(t)) {
                                fail(app, id, t)
                                break
                            }
                            Log.w(TAG, "download retry $attempt", t)
                            DownloadStore.update(app, id) {
                                it.copy(status = DownloadStatus.DOWNLOADING, speedBps = 0L, etaSec = -1L, note = "Connection problem - continuing…")
                            }
                            delay(minOf(3000L * attempt, 15_000L))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!stops.containsKey(id)) fail(app, id, t)
            } finally {
                jobs.remove(id)
            }
        }
    }

    private fun isPermanentFailure(t: Throwable): Boolean {
        val m = (t.message ?: "").lowercase()
        return listOf(
            "unsupported url", "video unavailable", "private video", "is private", "sign in",
            "not a valid", "storage is full", "has been removed", "copyright", "age-restricted", "can not be downloaded",
            "requested format is not available", "can not join the downloaded", "v2-only", "unsafe file path", "does not exist any more", "engine is not available", "invalid torrent metadata"
        ).any { m.contains(it) }
    }

    /** Called when the app starts: downloads that were running when the app was closed continue where they stopped. */
    fun resumeInterrupted(context: Context) {
        val app = context.applicationContext
        DownloadStore.init(app)
        for (id in DownloadStore.takeInterrupted()) {
            try { start(app, id) } catch (_: Throwable) {}
        }
    }

    /**
     * The part of an unfinished link download that is already on disk (null = nothing yet). It can be played like a
     * normal video: it plays as far as it has been downloaded.
     */
    fun partialFile(context: Context, item: DownloadItem): File? {
        val files = linkDir(context.applicationContext, item.id).listFiles() ?: return null
        val parts = files.filter {
            it.isFile && it.length() > 0L &&
                (it.name.endsWith("." + ParallelDownloader.PART_EXT) || it.name.endsWith(".part"))
        }
        return parts.firstOrNull { it.name.startsWith("video.") } ?: parts.maxByOrNull { it.length() }
    }

    private suspend fun runDownload(app: Context, id: String) {
        if (DownloadStore.get(id) == null) return
        DownloadStore.update(app, id, persist = true) { it.copy(status = DownloadStatus.DOWNLOADING) }
        if (DownloadStore.get(id)?.sourceType == DownloadSourceType.TORRENT) runTorrent(app, id) else runLink(app, id)
    }

    private fun fail(app: Context, id: String, t: Throwable) {
        Log.w(TAG, "download failed", t)
        val msg = reasonOf(t)
        DownloadStore.update(app, id, persist = true) {
            it.copy(status = DownloadStatus.FAILED, speedBps = 0L, etaSec = -1L, note = msg)
        }
        DownloadStore.get(id)?.let { DownloadService.notifyFailed(app, it) }
    }

    private fun reasonOf(t: Throwable): String {
        val raw = t.message?.trim().orEmpty()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { it.contains("ERROR", ignoreCase = true) } ?: lines.firstOrNull() ?: ""
        val cleaned = line.removePrefix("ERROR:").trim()
        return (if (cleaned.isNotBlank()) cleaned else t.javaClass.simpleName).take(160)
    }

    private fun complete(app: Context, id: String, saved: DownloadFileSaver.Saved) {
        DownloadStore.update(app, id, persist = true) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = saved.size,
                totalBytes = saved.size,
                speedBps = 0L,
                etaSec = -1L,
                note = null,
                filePath = saved.path,
                fileUri = saved.uri
            )
        }
        DownloadStore.get(id)?.let {
            DownloadService.notifyDone(app, it)
            attachSubtitles(app, it, saved)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------------------------------

    private fun finished(dir: File, prefix: String): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$prefix.") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.contains(".part-") &&
                !it.name.endsWith("." + ParallelDownloader.PART_EXT) && !it.name.endsWith("." + ParallelDownloader.STATE_EXT) &&
                !it.name.endsWith(".tmp") }
            ?.maxByOrNull { it.length() }

    /**
     * Web pages (YouTube, extractor sites) and HLS / DASH manifests can not be fetched as one plain file: they go
     * through the bundled yt-dlp. Links with their own headers (media-plugin streams) and plain media files keep the
     * ParallelDownloader path.
     */
    private fun usesYtdlp(item: DownloadItem): Boolean {
        val src = item.source
        if (!com.example.BuildConfig.ENABLE_YTDLP) return false
        if (!src.startsWith("http://", ignoreCase = true) && !src.startsWith("https://", ignoreCase = true)) return false
        val path = src.substringBefore('?').substringBefore('#').lowercase()
        // HLS / DASH can never be fetched as one file: they go through yt-dlp WITH the provider's headers
        if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.contains(".m3u8/")) return true
        if (item.headers.isNotEmpty()) return false
        return YtdlpManager.requiresYtdlp(src)
    }

    private fun ytdlpFormats(item: DownloadItem): String? {
        val spec = item.formatSpec?.trim()?.takeIf { it.isNotEmpty() }
        // Keep yt-dlp format expressions intact. Rewriting "+" as a comma routes them through
        // Android MediaMuxer, which cannot handle many codec/container pairs or subtitle attachments.
        if (spec != null) return spec
        val youtube = Regex("^https?://([a-z0-9-]+\\.)*(youtube\\.com|youtu\\.be)/", RegexOption.IGNORE_CASE).containsMatchIn(item.source)
        return when {
            item.isAudio -> YtdlpDownloader.AUDIO_FORMAT
            youtube -> YtdlpDownloader.YOUTUBE_SPLIT_FORMAT
            else -> null
        }
    }

    /**
     * HLS / DASH link without a chosen quality: the best video with every audio language the manifest offers
     * (null = yt-dlp's own default). Only formats the manifest really lists are used.
     */
    private suspend fun autoManifestFormats(app: Context, item: DownloadItem): String? {
        if (item.isAudio) return null
        val path = item.source.substringBefore('?').substringBefore('#').lowercase()
        if (!(path.endsWith(".m3u8") || path.endsWith(".mpd") || path.contains(".m3u8/"))) return null
        return try {
            YtdlpProbe.autoSpec(app, item.source, item.headers)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    /** Provider subtitles of a finished download become subtitle tracks of the saved video. */
    private fun attachSubtitles(app: Context, item: DownloadItem, saved: DownloadFileSaver.Saved) {
        if (item.subtitles.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val used = HashSet<String>()
            for (sub in item.subtitles) {
                try {
                    val local = com.example.util.media.SubtitleDownloader.download(app, sub.uri, item.headers) ?: continue
                    val kept = CustomSubtitlePersistenceManager.persistSubtitleFromFile(app, local)
                    val base = sub.title.ifBlank { sub.language }.ifBlank { "Subtitle" }
                    var name = base
                    var n = 2
                    while (!used.add(name.lowercase())) name = "$base ${n++}"
                    CustomSubtitlePersistenceManager.addAttachedSubtitle(
                        context = app,
                        path = saved.path,
                        uriString = saved.uri,
                        title = item.title,
                        filePath = kept.absolutePath,
                        originalName = name,
                        isSelected = false
                    )
                } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun runLink(app: Context, id: String) {
        val item = DownloadStore.get(id) ?: return
        val dir = linkDir(app, id).apply { mkdirs() }
        if (usesYtdlp(item)) {
            runYtdlp(app, id, item, dir)
            return
        }

        // The data may already be complete (an earlier try finished the file and only a later step failed): then
        // nothing has to be fetched and the download goes straight on to saving.
        val alreadyComplete = finished(dir, "media") != null

        val total = AtomicLong(item.totalBytes)
        val mergeNote = AtomicReference<String?>(null)
        // -1 = no live byte counter (the folder size is measured)
        val live = AtomicLong(-1L)
        val monitor = scope.launch { monitorLoop(app, id, dir, total, mergeNote, live) }
        try {
            withContext(Dispatchers.IO) {
                if (alreadyComplete) return@withContext
                val plan = planDirect(item)
                    ?: throw IllegalStateException("This link can not be downloaded right now")
                total.set(plan.sumOf { it.sizeEstimate })
                live.set(0L)
                for (f in plan) {
                    if (stops.containsKey(id)) return@withContext
                    val dest = File(dir, f.role + "." + f.ext)
                    try {
                        ParallelDownloader.download(
                            url = f.url,
                            headers = f.headers,
                            dest = dest,
                            live = live,
                            onTotal = { actual -> total.addAndGet(actual - f.sizeEstimate) },
                            isCancelled = { stops.containsKey(id) }
                        )
                    } catch (c: ParallelDownloader.CancelledException) {
                        return@withContext
                    } catch (u: ParallelDownloader.UnsupportedException) {
                        // no byte ranges / unknown size: the file is fetched as one plain stream
                        Log.w(TAG, "fast download not possible: ${u.message}")
                        downloadSingleStream(f, dest, live, total) { stops.containsKey(id) }
                    }
                }
            }
        } finally {
            monitor.cancel()
        }
        if (stops.containsKey(id)) return

        val isCancelled = { stops.containsKey(id) }
        var out: File = dir.listFiles()
            ?.filter { it.isFile && Regex("^media\\.[A-Za-z0-9]+$").matches(it.name) }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("Nothing was downloaded")

        if (item.isAudio) {
            DownloadStore.update(app, id, persist = true) {
                it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Adding cover picture…")
            }
            out = withContext(Dispatchers.IO) { addCover(out, item, dir, isCancelled) }
            if (stops.containsKey(id)) return
        }

        val ext = out.extension
        DownloadStore.update(app, id, persist = true) {
            it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Saving to your library…")
        }
        val base = DownloadFileSaver.safeName(item.title)
        val name = if (ext.isEmpty() || base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
        val saved = withContext(Dispatchers.IO) { DownloadFileSaver.saveToPublic(app, out, name, item.isAudio) }
            ?: throw IllegalStateException("Could not save the file (is the storage full?)")
        complete(app, id, saved)
        try { dir.deleteRecursively() } catch (_: Throwable) {}
    }

    /** yt-dlp download of one link: progress / speed from yt-dlp's own output, then saved like every other download. */
    private suspend fun runYtdlp(app: Context, id: String, item: DownloadItem, dir: File) {
        val isCancelled = { stops.containsKey(id) }
        var speed = 0.0
        val file: File? = try {
            YtdlpDownloader.download(
                context = app,
                url = item.source,
                formats = ytdlpFormats(item) ?: autoManifestFormats(app, item),
                workDir = dir,
                headers = item.headers,
                isCancelled = isCancelled
            ) { p ->
                speed = if (speed == 0.0) p.speedBps.toDouble() else speed * 0.6 + p.speedBps * 0.4
                DownloadStore.update(app, id) {
                    if (it.status == DownloadStatus.DOWNLOADING) {
                        it.copy(
                            downloadedBytes = if (p.totalBytes > 0L) p.downloadedBytes else it.downloadedBytes,
                            totalBytes = if (p.totalBytes > it.totalBytes) p.totalBytes else it.totalBytes,
                            speedBps = speed.toLong(),
                            etaSec = p.etaSec,
                            // a note hides the progress bar / speed of the Download page: only while no data flows
                            note = p.phase
                        )
                    } else it
                }
            }
        } catch (c: MediaMuxHelper.Cancelled) {
            return
        }
        if (stops.containsKey(id)) return
        var out = file ?: throw IllegalStateException("Nothing was downloaded")

        if (item.isAudio) {
            DownloadStore.update(app, id, persist = true) {
                it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Adding cover picture…")
            }
            out = withContext(Dispatchers.IO) { addCover(out, item, dir, isCancelled) }
            if (stops.containsKey(id)) return
        }
        DownloadStore.update(app, id, persist = true) {
            it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Saving to your library…")
        }
        val ext = out.extension
        val base = DownloadFileSaver.safeName(item.title)
        val name = if (ext.isEmpty() || base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
        val saved = withContext(Dispatchers.IO) { DownloadFileSaver.saveToPublic(app, out, name, item.isAudio) }
            ?: throw IllegalStateException("Could not save the file (is the storage full?)")
        complete(app, id, saved)
        try { dir.deleteRecursively() } catch (_: Throwable) {}
    }

    // ---------------------------------------------------------------------------------------------
    // Torrents
    // ---------------------------------------------------------------------------------------------

    /**
     * One file of a torrent: libtorrent downloads only the chosen file into the download's own folder (pause keeps the
     * data, resume continues it), the finished file is then moved to the public Movies / Music folder.
     */
    private suspend fun runTorrent(app: Context, id: String) {
        val item = DownloadStore.get(id) ?: return
        val dir = torrentDir(app, id).apply { mkdirs() }
        val isCancelled = { stops.containsKey(id) }

        var speed = 0.0
        var lastPersistAt = 0L
        val file: File? = TorrentDownloadEngine.download(
            context = app,
            source = item.source,
            fileIndex = item.torrentFileIndex,
            saveDir = dir,
            isCancelled = isCancelled
        ) { p ->
            if (p.totalBytes > 0L || p.phase != null) {
                // speed is libtorrent's own payload rate, lightly smoothed like the link downloads
                speed = if (speed == 0.0) p.speedBps.toDouble() else speed * 0.6 + p.speedBps * 0.4
                val sp = speed.toLong()
                val total = if (p.totalBytes > 0L) p.totalBytes else item.totalBytes
                val eta = if (sp > 0L && total > p.downloadedBytes) (total - p.downloadedBytes) / sp else -1L
                // progress is written to disk every few seconds (not only on state changes), so after a crash / kill the
                // Download page and the resumed download continue from the saved progress instead of 0
                val now = System.currentTimeMillis()
                val persistNow = now - lastPersistAt >= 4_000L
                if (persistNow) lastPersistAt = now
                DownloadStore.update(app, id, persist = persistNow) {
                    if (it.status == DownloadStatus.DOWNLOADING) {
                        // libtorrent re-verifies the data already on disk after a restart and reports 0 meanwhile: the
                        // shown progress never goes backwards
                        val shown = maxOf(p.downloadedBytes, it.downloadedBytes)
                        it.copy(
                            downloadedBytes = shown,
                            totalBytes = total,
                            speedBps = sp,
                            etaSec = eta,
                            // a note hides the progress bar / speed of the Download page: only used while no data flows yet
                            note = p.phase ?: if (p.downloadedBytes < it.downloadedBytes) "Resuming from saved progress…" else null
                        )
                    } else it
                }
            }
        }
        if (stops.containsKey(id)) return
        val out = file ?: throw IllegalStateException("Nothing was downloaded")

        DownloadStore.update(app, id, persist = true) {
            it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Saving to your library…")
        }
        val ext = out.extension
        val base = DownloadFileSaver.safeName(item.title)
        val name = if (ext.isEmpty() || base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
        val saved = withContext(Dispatchers.IO) { DownloadFileSaver.saveToPublic(app, out, name, item.isAudio) }
            ?: throw IllegalStateException("Could not save the file (is the storage full?)")
        complete(app, id, saved)
        try { linkDir(app, id).deleteRecursively() } catch (_: Throwable) {}
        try { torrentDir(app, id).deleteRecursively() } catch (_: Throwable) {}
    }

    /**
     * Audio downloads keep their thumbnail: the picture is written into the file itself (m4a / mp3) so every player
     * and this app's own library show it. Never fails the download: without a cover the plain file is saved.
     */
    private suspend fun addCover(file: File, item: DownloadItem, dir: File, isCancelled: () -> Boolean): File {
        val ext = file.extension.lowercase()
        if (ext != "m4a" && ext != "mp3") return file
        val bitmap = try {
            val url = item.thumbnailUrl ?: return file
            com.example.ui.components.loadRemoteAudioThumbnail(url, 1000)
        } catch (_: Throwable) { null } ?: return file
        val cover = MediaMuxHelper.coverJpeg(bitmap) ?: return file
        val artist = item.subtitle
        return try {
            if (ext == "mp3") {
                MediaMuxHelper.embedMp3Cover(file, cover, item.title, artist)
                file
            } else {
                // a clean (non fragmented) m4a first: the cover can then be added without touching any other data
                val clean = File(dir, "media_clean.m4a")
                MediaMuxHelper.remuxAudio(file, clean, isCancelled)
                if (isCancelled()) { clean.delete(); return file }
                if (MediaMuxHelper.embedCoverAndTags(clean, cover, item.title, artist)) {
                    file.delete()
                    val finalFile = File(dir, "media.m4a")
                    if (clean.renameTo(finalFile)) finalFile else clean
                } else {
                    clean.delete()
                    file
                }
            }
        } catch (_: Throwable) {
            File(dir, "media_clean.m4a").delete()
            file
        }
    }

    private class DirectFile(
        val url: String,
        val headers: Map<String, String>,
        val ext: String,
        val sizeEstimate: Long,
        /** "media" (one file) or "video" / "audio" (two streams that are joined afterwards). */
        val role: String
    )

    private val DIRECT_EXT = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "flv", "wmv", "mpg", "mpeg", "3gp",
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac"
    )

    private fun directLinkExt(url: String): String? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in DIRECT_EXT }
    }

    /** The file to fetch for a link: plain http(s) files only (HLS / DASH manifests and other link types need an engine that was removed). */
    private fun planDirect(item: DownloadItem): List<DirectFile>? {
        val src = item.source
        if (!src.startsWith("http://", ignoreCase = true) && !src.startsWith("https://", ignoreCase = true)) return null
        val path = src.substringBefore('?').substringBefore('#').lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return null
        val ext = directLinkExt(src) ?: if (item.isAudio) "m4a" else "mp4"
        return listOf(DirectFile(src, item.headers, ext, item.totalBytes, "media"))
    }

    /** One plain GET stream into [dest] (servers without byte ranges). The partial data is thrown away when it stops. */
    private fun downloadSingleStream(
        f: DirectFile,
        dest: File,
        live: AtomicLong,
        total: AtomicLong,
        isCancelled: () -> Boolean
    ) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(f.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                f.headers.forEach { (name, value) -> if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value) }
            }
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            val length = connection.contentLengthLong
            if (length > 0L) total.set(length)
            live.set(0L)
            var written = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (isCancelled()) return
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        live.set(written)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IllegalStateException("Could not save the downloaded file")
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun dirSize(dir: File): Long =
        try { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } catch (_: Throwable) { 0L }

    private suspend fun monitorLoop(
        app: Context,
        id: String,
        dir: File,
        total: AtomicLong,
        note: AtomicReference<String?>,
        live: AtomicLong
    ) {
        fun measured(): Long = if (live.get() >= 0L) live.get() else dirSize(dir)
        var last = measured()
        var lastTime = SystemClock.elapsedRealtime()
        var speed = 0.0
        while (true) {
            delay(500L)
            val now = SystemClock.elapsedRealtime()
            val size = measured()
            val dt = (now - lastTime).coerceAtLeast(1L)
            val inst = (size - last).coerceAtLeast(0L) * 1000.0 / dt
            speed = if (speed == 0.0) inst else speed * 0.6 + inst * 0.4
            last = size
            lastTime = now
            val totalBytes = total.get()
            val done = if (totalBytes > 0L) minOf(size, totalBytes) else size
            val merging = note.get()
            val sp = if (merging != null) 0L else speed.toLong()
            val eta = if (merging == null && sp > 0L && totalBytes > done) (totalBytes - done) / sp else -1L
            DownloadStore.update(app, id) {
                if (it.status == DownloadStatus.DOWNLOADING) {
                    it.copy(
                        downloadedBytes = done,
                        totalBytes = if (totalBytes > 0L) totalBytes else it.totalBytes,
                        speedBps = sp,
                        etaSec = eta,
                        note = merging
                    )
                } else it
            }
        }
    }
}
