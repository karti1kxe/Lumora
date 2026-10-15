package com.example.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Download page engine.
 *  - links (YouTube, Instagram, direct files, ... everything yt-dlp supports) are downloaded with yt-dlp, 16 fragments in
 *    parallel; the original streams are kept (no conversion, no ffmpeg: keeps the app small). Qualities that exist only
 *    as separate video + audio streams (1080p60 ... 2160p60 of YouTube) are downloaded as two files and joined without
 *    re-encoding by [MediaMuxHelper] (Android's MediaMuxer); audio files get their thumbnail embedded as cover art,
 *  - torrent / magnet videos are downloaded with libtorrent4j (only the chosen file),
 *  - the finished file is moved to Movies/Lumora (Music/Lumora for audio) so it appears in the app's library.
 * Progress, speed and the time left are measured here (bytes on disk / verified torrent pieces), not parsed from tool output.
 * A foreground service ([DownloadService]) keeps the process alive and shows the notification.
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val MAX_PARALLEL = 3
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
        val item = when {
            TorrentLibrary.parsePath(source) != null -> DownloadItem(
                id = id,
                source = source,
                isTorrent = true,
                title = TorrentLibrary.fileDisplayName(app, source) ?: entry.title,
                subtitle = entry.subtitle ?: "Torrent",
                totalBytes = TorrentLibrary.fileSize(app, source)
            )
            isTorrentLink(source) -> DownloadItem(
                id = id,
                source = source,
                isTorrent = true,
                title = entry.title,
                subtitle = "Torrent",
                note = "Fetching torrent info…"
            )
            else -> DownloadItem(
                id = id,
                source = source,
                isTorrent = false,
                title = entry.title,
                subtitle = entry.subtitle,
                isAudio = looksAudio(source),
                thumbnailUrl = entry.thumbnail?.takeIf { it.startsWith("http") }
            )
        }
        DownloadStore.add(app, item)
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
                isTorrent = false,
                title = req.title.ifBlank { source },
                subtitle = req.author,
                isAudio = req.isAudio,
                totalBytes = req.estimatedBytes,
                formatSpec = req.formatSpec,
                container = req.container,
                thumbnailUrl = req.thumbnailUrl,
                headers = req.headers
            )
        )
        start(app, id)
        return "Download started"
    }

    fun pause(context: Context, id: String) {
        val app = context.applicationContext
        val item = DownloadStore.get(id) ?: return
        if (!item.isActive) return
        stops[id] = Stop.PAUSE
        killProcesses(id)
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
        val item = DownloadStore.get(id) ?: return
        stops[id] = Stop.CANCEL
        killProcesses(id)
        DownloadStore.remove(app, id)
        DownloadService.clearDone(app, id)
        scope.launch {
            delay(800L)
            try {
                val torrent = if (item.isTorrent) TorrentLibrary.parsePath(item.source) else null
                if (torrent != null) {
                    TorrentStreamEngine.endDownload(torrent.first, torrent.second)
                    cleanupTorrentDir(app, torrent.first)
                } else {
                    linkDir(app, id).deleteRecursively()
                }
            } catch (_: Throwable) {
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Job handling
    // ---------------------------------------------------------------------------------------------

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun processId(id: String) = "lumora-dl-$id"

    private fun baseDir(app: Context): File =
        File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads").apply { mkdirs() }

    private fun linkDir(app: Context, id: String): File = File(baseDir(app), id)

    private fun torrentDir(app: Context, hash: String): File = File(baseDir(app), "torrent-$hash")

    fun isTorrentLink(url: String): Boolean =
        Regex("magnet:\\?\\S+", RegexOption.IGNORE_CASE).containsMatchIn(url) ||
            url.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true)

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
                    if (!stops.containsKey(id)) runDownload(app, id)
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

    private suspend fun runDownload(app: Context, id: String) {
        val item = DownloadStore.get(id) ?: return
        DownloadStore.update(app, id, persist = true) { it.copy(status = DownloadStatus.DOWNLOADING) }
        if (item.isTorrent) {
            if (TorrentLibrary.parsePath(item.source) == null) fetchTorrentInfo(app, id)
            if (stops.containsKey(id)) return
            val current = DownloadStore.get(id) ?: return
            if (TorrentLibrary.parsePath(current.source) == null) return
            runTorrent(app, id)
        } else {
            runLink(app, id)
        }
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
        DownloadStore.get(id)?.let { DownloadService.notifyDone(app, it) }
    }

    private fun killProcesses(id: String) {
        val pid = processId(id)
        for (p in listOf(pid, "$pid-probe")) {
            try { YoutubeDL.getInstance().destroyProcessById(p) } catch (_: Throwable) {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Links (yt-dlp)
    // ---------------------------------------------------------------------------------------------

    /** Single-file formats only (video and sound already together, or audio only): nothing has to be merged or converted. */
    private fun formatFor(audio: Boolean): String =
        if (audio) "ba[ext=m4a]/ba/b" else "b[height<=1080]/b"

    /** "videoId+audioId" chosen in the options sheet: two separate streams that the app joins itself (MediaMuxer). */
    private fun splitSpec(item: DownloadItem): Pair<String, String>? {
        val custom = item.formatSpec ?: return null
        if (item.isAudio || !custom.contains('+')) return null
        val v = custom.substringBefore('+').trim()
        val a = custom.substringAfter('+').substringBefore('/').trim()
        return if (v.isNotEmpty() && a.isNotEmpty()) v to a else null
    }

    private fun finished(dir: File, prefix: String): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$prefix.") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.contains(".part-") }
            ?.maxByOrNull { it.length() }

    private suspend fun runLink(app: Context, id: String) {
        val item = DownloadStore.get(id) ?: return
        val dir = linkDir(app, id).apply { mkdirs() }

        val ready = withContext(Dispatchers.IO) { NetworkStreamResolver.prepareYtDlpForDownload(app) }
        if (!ready) throw IllegalStateException(NetworkStreamResolver.lastError ?: "yt-dlp could not start")
        if (stops.containsKey(id)) return
        val split = splitSpec(item)
        val format = effectiveFormat(item)
        val pid = processId(id)

        val total = AtomicLong(item.totalBytes)
        val mergeNote = AtomicReference<String?>(null)
        val monitor = scope.launch { monitorLoop(app, id, dir, total, mergeNote) }
        val probe = scope.launch { probeSize(item.source, if (split != null) item.formatSpec!! else format, "$pid-probe", total) }
        try {
            withContext(Dispatchers.IO) {
                fun runOnce() {
                    if (split != null) {
                        downloadOnce(item, split.first, dir, pid, mergeNote, "video")
                        if (stops.containsKey(id)) return
                        downloadOnce(item, split.second, dir, pid, mergeNote, "audio")
                    } else {
                        downloadOnce(item, format, dir, pid, mergeNote, "media")
                    }
                }
                try {
                    runOnce()
                } catch (t: Throwable) {
                    // sites change all the time: one more try with the newest yt-dlp before giving up
                    if (stops.containsKey(id)) throw t
                    if (!NetworkStreamResolver.forceYtDlpUpdate(app)) throw t
                    runOnce()
                }
            }
        } finally {
            monitor.cancel()
            probe.cancel()
            try { YoutubeDL.getInstance().destroyProcessById("$pid-probe") } catch (_: Throwable) {}
        }
        if (stops.containsKey(id)) return

        val isCancelled = { stops.containsKey(id) }
        var out: File
        if (split != null) {
            val v = finished(dir, "video") ?: throw IllegalStateException("The video part was not downloaded")
            val a = finished(dir, "audio") ?: throw IllegalStateException("The audio part was not downloaded")
            DownloadStore.update(app, id, persist = true) {
                it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Merging video and audio…")
            }
            val ext = if (v.extension.equals("webm", ignoreCase = true)) "webm" else "mp4"
            out = File(dir, "media.$ext")
            withContext(Dispatchers.IO) {
                try {
                    MediaMuxHelper.mux(v, a, out, isCancelled)
                } catch (c: MediaMuxHelper.Cancelled) {
                    out.delete()
                    return@withContext
                } catch (t: Throwable) {
                    out.delete()
                    throw IllegalStateException("Could not join video and audio: " + (t.message ?: t.javaClass.simpleName))
                }
                v.delete()
                a.delete()
            }
            if (stops.containsKey(id)) return
        } else {
            out = dir.listFiles()
                ?.filter { it.isFile && Regex("^media\\.[A-Za-z0-9]+$").matches(it.name) }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("Nothing was downloaded")
        }

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

    private fun effectiveFormat(item: DownloadItem): String {
        val custom = item.formatSpec
        // "video+audio" is downloaded as two files (see splitSpec); here it is only used to ask for the size
        return if (custom.isNullOrBlank()) formatFor(item.isAudio)
        else if (custom.contains('+') && item.isAudio) custom.substringBefore('+').substringBefore('/') + "/" + formatFor(true)
        else custom
    }

    private fun downloadOnce(
        item: DownloadItem,
        format: String,
        dir: File,
        pid: String,
        mergeNote: AtomicReference<String?>,
        outName: String
    ) {
        val req = YoutubeDLRequest(item.source)
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        req.addOption("--no-mtime")
        req.addOption("--newline")
        req.addOption("--no-check-certificates")
        req.addOption("--socket-timeout", 30)
        req.addOption("--retries", 10)
        req.addOption("--fragment-retries", 10)
        // full speed: 16 fragments of a DASH / HLS video at the same time, big HTTP chunks for everything else
        req.addOption("-N", 16)
        req.addOption("--http-chunk-size", "10M")
        req.addOption("-f", format)
        for ((hName, hValue) in item.headers) {
            if (hName.isBlank() || hValue.isBlank()) continue
            if (hName.equals("User-Agent", ignoreCase = true)) req.addOption("--user-agent", hValue)
            else req.addOption("--add-header", "$hName:$hValue")
        }
        req.addOption("-o", File(dir, "$outName.%(ext)s").absolutePath)
        YoutubeDL.getInstance().execute(req, pid) { _, _, line ->
            val l = line.orEmpty()
            if (l.contains("[Merger]") || l.contains("[VideoRemuxer]") || l.contains("[Fixup")) {
                mergeNote.set("Merging video and audio…")
            }
        }
    }

    /** Asks yt-dlp (in parallel with the download) how big the chosen format is, so the page can show "x MB of y MB". */
    private fun probeSize(url: String, format: String, pid: String, total: AtomicLong) {
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--dump-single-json")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            req.addOption("--no-check-certificates")
            req.addOption("--socket-timeout", 20)
            req.addOption("-f", format)
            val out = YoutubeDL.getInstance().execute(req, pid, null).out
            val size = sizeOf(JSONObject(out.trim()))
            if (size > 0L) total.set(size)
        } catch (_: Throwable) {
        }
    }

    private fun sizeOf(root: JSONObject): Long {
        val duration = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
        fun one(f: JSONObject): Long {
            val exact = f.optLong("filesize", 0L)
            if (exact > 0L) return exact
            val approx = f.optLong("filesize_approx", 0L)
            if (approx > 0L) return approx
            val tbr = f.optDouble("tbr", 0.0)
            return if (!tbr.isNaN() && tbr > 0.0 && duration > 0.0) (tbr * 125.0 * duration).toLong() else 0L
        }
        val req = root.optJSONArray("requested_formats")
        if (req != null && req.length() > 0) {
            var sum = 0L
            for (i in 0 until req.length()) {
                val f = req.optJSONObject(i) ?: continue
                sum += one(f)
            }
            if (sum > 0L) return sum
        }
        return one(root)
    }

    private fun dirSize(dir: File): Long =
        try { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } catch (_: Throwable) { 0L }

    private suspend fun monitorLoop(
        app: Context,
        id: String,
        dir: File,
        total: AtomicLong,
        note: AtomicReference<String?>
    ) {
        var last = dirSize(dir)
        var lastTime = SystemClock.elapsedRealtime()
        var speed = 0.0
        while (true) {
            delay(500L)
            val now = SystemClock.elapsedRealtime()
            val size = dirSize(dir)
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

    // ---------------------------------------------------------------------------------------------
    // Torrents (libtorrent4j)
    // ---------------------------------------------------------------------------------------------

    /** Magnet / .torrent link: loads the file list; the first video becomes this item, further videos become new items. */
    private suspend fun fetchTorrentInfo(app: Context, id: String) {
        val item = DownloadStore.get(id) ?: return
        DownloadStore.update(app, id) { it.copy(note = "Fetching torrent info… (can take up to a minute)") }
        val magnet = Regex("magnet:\\?\\S+", RegexOption.IGNORE_CASE).find(item.source)?.value
        val source = if (magnet != null) TorrentSource.Magnet(magnet) else TorrentSource.RemoteFile(item.source)
        val (summary, error) = TorrentStreamEngine.fetchSummary(app, source)
        if (summary == null) throw IllegalStateException(error ?: "Could not get the torrent info")
        val videos = summary.videoFiles
        if (videos.isEmpty()) throw IllegalStateException("No video files found in this torrent")

        val first = videos.first()
        val firstPath = TorrentLibrary.pathFor(summary.infoHash, first.index)
        val duplicate = DownloadStore.items.value.any {
            it.id != id && it.source == firstPath && it.status != DownloadStatus.FAILED
        }
        if (duplicate) {
            DownloadStore.remove(app, id)
            return
        }
        DownloadStore.update(app, id, persist = true) {
            it.copy(source = firstPath, title = first.displayName, subtitle = summary.name, totalBytes = first.size, note = null)
        }
        for (v in videos.drop(1)) {
            val path = TorrentLibrary.pathFor(summary.infoHash, v.index)
            if (DownloadStore.items.value.any { it.source == path && it.status != DownloadStatus.FAILED }) continue
            val nid = newId()
            DownloadStore.add(
                app,
                DownloadItem(
                    id = nid,
                    source = path,
                    isTorrent = true,
                    title = v.displayName,
                    subtitle = summary.name,
                    totalBytes = v.size
                )
            )
            start(app, nid)
        }
    }

    private suspend fun runTorrent(app: Context, id: String) {
        val item = DownloadStore.get(id) ?: return
        val (hash, index) = TorrentLibrary.parsePath(item.source) ?: throw IllegalStateException("Invalid torrent link")
        val dir = torrentDir(app, hash)
        val started = withContext(Dispatchers.IO) { TorrentStreamEngine.beginDownload(app, hash, index, dir) }
        if (!started) throw IllegalStateException("Could not start the torrent download")

        var finished = false
        try {
            var lastDone = -1L
            var lastTime = SystemClock.elapsedRealtime()
            var speed = 0.0
            while (true) {
                if (stops.containsKey(id)) return
                delay(500L)
                val p = TorrentStreamEngine.downloadProgress(hash, index)
                    ?: throw IllegalStateException("The torrent was stopped")
                val now = SystemClock.elapsedRealtime()
                if (lastDone >= 0L) {
                    val dt = (now - lastTime).coerceAtLeast(1L)
                    val inst = (p.doneBytes - lastDone).coerceAtLeast(0L) * 1000.0 / dt
                    speed = if (speed == 0.0) inst else speed * 0.6 + inst * 0.4
                }
                lastDone = p.doneBytes
                lastTime = now
                val sp = speed.toLong()
                val eta = if (sp > 0L && p.totalBytes > p.doneBytes) (p.totalBytes - p.doneBytes) / sp else -1L
                DownloadStore.update(app, id) {
                    if (it.status == DownloadStatus.DOWNLOADING) {
                        it.copy(downloadedBytes = p.doneBytes, totalBytes = p.totalBytes, speedBps = sp, etaSec = eta, note = null)
                    } else it
                }
                if (p.complete) break
            }
            DownloadStore.update(app, id, persist = true) {
                it.copy(status = DownloadStatus.PROCESSING, speedBps = 0L, etaSec = -1L, note = "Saving to your library…")
            }
            val file = TorrentStreamEngine.downloadedFile(hash, index)
                ?: throw IllegalStateException("The downloaded file is missing")
            val saved = withContext(Dispatchers.IO) { DownloadFileSaver.saveToPublic(app, file, item.title, false) }
                ?: throw IllegalStateException("Could not save the video (is the storage full?)")
            finished = true
            complete(app, id, saved)
        } finally {
            TorrentStreamEngine.endDownload(hash, index)
            if (finished) cleanupTorrentDir(app, hash)
        }
    }

    /** The shared torrent folder is deleted once no unfinished download of that torrent is left. */
    private fun cleanupTorrentDir(app: Context, hash: String) {
        val prefix = TorrentLibrary.PATH_PREFIX + hash + "/"
        val stillNeeded = DownloadStore.items.value.any {
            it.isTorrent && it.source.startsWith(prefix) && it.status != DownloadStatus.COMPLETED
        }
        if (!stillNeeded) {
            try { torrentDir(app, hash).deleteRecursively() } catch (_: Throwable) {}
        }
    }
}
