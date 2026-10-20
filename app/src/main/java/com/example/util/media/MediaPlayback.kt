package com.example.util.media

import android.content.Context
import android.net.Uri
import com.example.ui.screens.VideoItem
import com.example.util.DownloadManager
import com.example.util.DownloadRequest
import com.example.util.PlaylistManager
import com.example.util.StreamEntryMeta
import com.example.util.StreamEntryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.util.NetworkBypass
import java.net.HttpURLConnection
import java.net.URL

/** Turns a plugin's stream into something the app's own player / playlists / downloader understand. */
object MediaPlayback {
    /** The browser User-Agent the plugin engine itself used to get the link; used for the player too when the plugin names none. */
    private const val STREAM_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val PLAYER_OWN_HEADERS = setOf(
        "host", "connection", "content-length", "content-type", "accept-encoding", "range", "if-range",
        "transfer-encoding", "te", "upgrade-insecure-requests"
    )

    internal fun rankOf(s: MediaStream): Int = rank(s)

    private fun rank(s: MediaStream): Int {
        val path = s.link.substringBefore('?').substringBefore('#').lowercase()
        val type = s.type.lowercase()
        return when {
            type == "m3u8" || type == "hls" || path.endsWith(".m3u8") -> 0
            type == "mp4" || path.endsWith(".mp4") -> 1
            type == "webm" || path.endsWith(".webm") -> 2
            type == "mkv" || path.endsWith(".mkv") -> 3
            // DASH is the least reliable in mpv: after the direct media files
            type == "mpd" || type == "dash" || path.endsWith(".mpd") -> 4
            else -> 5
        }
    }

    /** Prefer adaptive streaming when a provider offers it; fall back to direct media files. */
    fun pickBest(streams: List<MediaStream>): MediaStream? = streams.minByOrNull { rank(it) }

    /** Asks the plugin for the playable link of [link]. [forDownload] lets the plugin return a downloadable server. */
    suspend fun resolve(
        context: Context,
        plugin: MediaPluginInfo,
        link: String,
        type: String,
        forDownload: Boolean = false,
        episodeTitle: String = "",
        episodeIndex: Int = -1,
        /** When playing: the servers are checked for a few seconds and the first one that really delivers video is played. */
        verify: Boolean = !forDownload,
        /** When true (a movie / episode was tapped): if no server delivers video the result is a failure, so the player is not opened. */
        requireWorking: Boolean = false
    ): Result<MediaStream> {
        return try {
            val streams = MediaProviderRepository.loadStreams(context, plugin.value, link, type, forDownload)
            // like Vega: the provider's own order is kept (it lists its best server first); only the check below
            // moves a server that does not deliver video behind the ones that do
            var ranked = streams.distinctBy { it.link }
            // the server that really delivered this episode's video while its thumbnail was made is tried first, but it is
            // checked like every other one: a frame proves the website's file opens, not that the player can stream it
            val proven = if (!forDownload) EpisodeFrames.preferred(context, plugin, link) else null
            if (proven != null) {
                val i = ranked.indexOfFirst { it.server == proven.server && it.quality == proven.quality }
                    .takeIf { it >= 0 } ?: ranked.indexOfFirst { it.server == proven.server }
                if (i > 0) ranked = listOf(ranked[i]) + ranked.filterIndexed { idx, _ -> idx != i }
            }
            if (verify) {
                val direct = ranked
                if (direct.isNotEmpty()) {
                    val outcome = StreamProbe.verify(context, direct)
                    if (!outcome.anyWorks) MediaProviderRepository.forgetStreams(plugin.value, link)
                    if (requireWorking && !outcome.anyWorks) {
                        return Result.failure(PluginException("This video does not play on any server right now. Try again in a moment."))
                    }
                    ranked = outcome.ordered + ranked.filter { it !in direct }
                }
            }
            val chosen = ranked.firstOrNull()
            if (chosen == null) {
                Result.failure(PluginException("no playable link was found"))
            } else {
                // every subtitle the plugin listed (on any server) is offered in the player, without duplicates
                val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.uri }
                // the other servers stay available: the player's Server list, and the fallback when this one cannot be played
                val others = ranked.filter { it !== chosen && it.link != chosen.link }.distinctBy { it.link }
                    .map { it.copy(sourceLink = link, sourceType = type) }
                Result.success(
                    chosen.copy(
                        subtitles = if (allSubs.size > chosen.subtitles.size) allSubs else chosen.subtitles,
                        alternatives = others,
                        sourceLink = link,
                        sourceType = type
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * The card / player item of a resolved stream. It is opened like an imported IPTV stream: mpv plays the URL directly,
     * with buffering and the "could not be played" message of the existing player, using the headers the plugin gave.
     */
    fun toVideoItem(
        context: Context,
        plugin: MediaPluginInfo,
        title: String,
        poster: String,
        stream: MediaStream,
        session: PluginPlaybackSession? = null
    ): VideoItem {
        if (session != null) {
            val known = session.servers.indexOfFirst { it.link == stream.link }
            if (known >= 0) {
                session.selectedServer = known
            } else {
                // a freshly resolved movie / episode: this stream plus the other servers of the same answer
                session.servers = listOf(stream.copy(alternatives = emptyList())) + stream.alternatives
                session.selectedServer = 0
            }
            // another server of the same episode may list no subtitles of its own: the ones already known stay
            if (stream.subtitles.isNotEmpty() || known < 0) session.subtitles = stream.subtitles
            if (stream.sourceLink != null) {
                session.sourceLink = stream.sourceLink
                session.sourceType = stream.sourceType
            }
            PluginPlaybackSessions.put(stream.link, session)
        }
        val ua = stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value?.takeIf { it.isNotBlank() } ?: STREAM_UA
        val ref = stream.headers.entries.firstOrNull { it.key.equals("Referer", true) || it.key.equals("Referrer", true) }?.value
        val extra = LinkedHashMap<String, String>()
        for ((k, v) in stream.headers) {
            if (k.equals("User-Agent", true) || k.equals("Referer", true) || k.equals("Referrer", true)) continue
            // headers the player builds itself (or that only make sense for a request body) must not be forced on a media request:
            // a provider copies them from its page requests, and e.g. Accept-Encoding / Range / Host make servers answer with
            // compressed, partial or wrong data - the player then only buffers
            if (k.lowercase() in PLAYER_OWN_HEADERS) continue
            extra[k] = v
        }
        StreamEntryStore.attach(context)
        StreamEntryStore.putAll(
            listOf(
                StreamEntryMeta(
                    url = stream.link,
                    name = title,
                    logo = poster.ifBlank { null },
                    group = plugin.name,
                    userAgent = ua,
                    referrer = ref,
                    extraHeaders = extra
                )
            )
        )
        return VideoItem(
            id = -(kotlin.math.abs(("mediaplugin:" + stream.link).hashCode().toLong())) - 1L,
            uri = Uri.parse(stream.link),
            displayName = title,
            path = stream.link,
            sizeBytes = 0L,
            durationMs = 0L,
            dateModified = System.currentTimeMillis() / 1000L,
            isOnline = true,
            thumbnailUrl = poster.ifBlank { null },
            channelName = plugin.name,
            isDirectStream = true
        )
    }

    /** The same movie / episode in another quality option of the title (series: the quality's episode list is opened first). */
    suspend fun resolveChoice(
        context: Context,
        plugin: MediaPluginInfo,
        choice: PluginQualityChoice,
        episodeIndex: Int,
        episodeTitle: String,
        isSeries: Boolean,
        forDownload: Boolean
    ): Result<MediaStream> {
        return try {
            val list = choice.episodesLink?.let { MediaProviderRepository.loadEpisodes(context, plugin.value, it) } ?: choice.directLinks
            fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
            val target = list.firstOrNull { norm(it.title) == norm(episodeTitle) } ?: list.getOrNull(episodeIndex)
                ?: return Result.failure(PluginException("this quality does not have the same episode"))
            resolve(context, plugin, target.link, target.type ?: if (isSeries) "series" else "movie", forDownload, episodeTitle, if (isSeries) episodeIndex else -1)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Every playable source the plugin lists for [link] (all servers, not only the best), for the download list. */
    suspend fun resolveAll(context: Context, plugin: MediaPluginInfo, link: String, type: String, forDownload: Boolean): Result<List<MediaStream>> {
        return try {
            val streams = MediaProviderRepository.loadStreams(context, plugin.value, link, type, forDownload)
                .distinctBy { it.link }
                .sortedBy { rank(it) }
            if (streams.isEmpty()) Result.failure(PluginException("no playable link was found")) else Result.success(streams)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** [resolveAll] for the same movie / episode in another quality option of the title. */
    suspend fun resolveChoiceAll(
        context: Context,
        plugin: MediaPluginInfo,
        choice: PluginQualityChoice,
        episodeIndex: Int,
        episodeTitle: String,
        isSeries: Boolean,
        forDownload: Boolean
    ): Result<List<MediaStream>> {
        return try {
            val list = choice.episodesLink?.let { MediaProviderRepository.loadEpisodes(context, plugin.value, it) } ?: choice.directLinks
            fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
            val target = list.firstOrNull { norm(it.title) == norm(episodeTitle) } ?: list.getOrNull(episodeIndex)
                ?: return Result.failure(PluginException("this quality does not have the same episode"))
            resolveAll(context, plugin, target.link, target.type ?: if (isSeries) "series" else "movie", forDownload)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** One row of the download list: a file the website offers (server, quality, format, size). */
    data class DownloadOption(val stream: MediaStream, val title: String, val detail: String, val quality: String?)

    private val QUALITY_RE = Regex("(?i)(?<![0-9a-zA-Z])(2160|1440|1080|720|576|480|360|240)p(?![a-zA-Z])")

    private fun qualityOf(s: MediaStream): String? {
        s.quality?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val path = s.link.substringBefore('#')
        QUALITY_RE.find(path)?.let { return it.groupValues[1] + "p" }
        if (Regex("(?i)(?<![0-9a-zA-Z])4k(?![a-zA-Z])").containsMatchIn(path)) return "4K"
        return null
    }

    private fun heightOf(q: String?): Int {
        if (q == null) return 0
        if (q.equals("4K", true)) return 2160
        return Regex("(\\d{3,4})").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun formatOf(s: MediaStream): String {
        val path = s.link.substringBefore('?').substringBefore('#').lowercase()
        val type = s.type.lowercase()
        return when {
            HlsVariants.looksLikeHls(s.link, s.type) -> "HLS"
            type == "mpd" || type == "dash" || path.endsWith(".mpd") -> "DASH"
            type == "mp4" || path.endsWith(".mp4") -> "MP4"
            type == "mkv" || path.endsWith(".mkv") -> "MKV"
            type == "webm" || path.endsWith(".webm") -> "WEBM"
            type.isNotBlank() -> type.uppercase()
            else -> "File"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= (1L shl 30) -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0)
        bytes >= (1L shl 20) -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1048576.0)
        else -> String.format(java.util.Locale.US, "%d KB", bytes / 1024L)
    }

    /**
     * Total bytes of the file behind [link]: a HEAD request first, then a 1-byte ranged GET (many CDNs refuse HEAD).
     * -1 when the server does not tell. [proxy] = the app's bypass route for sites that only open through it.
     */
    internal fun probeSize(link: String, headers: Map<String, String>, proxy: java.net.Proxy? = null, timeoutMs: Int = 6000): Long {
        fun open(method: String, range: Boolean): HttpURLConnection =
            (URL(link).openConnection(proxy ?: java.net.Proxy.NO_PROXY) as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                for ((k, v) in headers) {
                    if (k.isNotBlank() && v.isNotBlank() && k.lowercase() !in PLAYER_OWN_HEADERS) { try { setRequestProperty(k, v) } catch (_: Throwable) {} }
                }
                if (headers.keys.none { it.equals("User-Agent", true) }) setRequestProperty("User-Agent", STREAM_UA)
                // Ask for the representation's actual byte length, not a compressed transfer size.
                setRequestProperty("Accept-Encoding", "identity")
                if (range) setRequestProperty("Range", "bytes=0-0")
            }
        try {
            val head = open("HEAD", false)
            try {
                val len = if (head.responseCode in 200..399) {
                    head.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0L }
                        ?: head.getHeaderField("Content-Range")?.substringAfterLast('/', "")?.toLongOrNull()?.takeIf { it > 0L }
                        ?: -1L
                } else -1L
                if (len > 0L) return len
            } catch (_: Throwable) {
                // HEAD refused / reset: the ranged GET below is asked next
            } finally { head.disconnect() }
            val get = open("GET", true)
            try {
                if (get.responseCode !in 200..299) return -1L
                val total = get.getHeaderField("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull()
                if (total != null && total > 0L) return total
                return if (get.responseCode == 200) get.getHeaderFieldLong("Content-Length", -1L) else -1L
            } finally { get.disconnect() }
        } catch (_: Throwable) {
            return -1L
        }
    }

    private fun bypassProxyOf(context: Context?): java.net.Proxy? =
        if (context == null) null else try { NetworkBypass.okHttpProxy(context) } catch (_: Throwable) { null }

    /** [probeSize] on a direct connection, then (when it told nothing) through the app's bypass route. */
    private fun probeSizeAnyRoute(context: Context?, link: String, headers: Map<String, String>, timeoutMs: Int = 6000): Long {
        val direct = probeSize(link, headers, null, timeoutMs)
        if (direct > 0L) return direct
        val bypass = bypassProxyOf(context) ?: return -1L
        return probeSize(link, headers, bypass, timeoutMs + 2000)
    }

    /**
     * Size of an HLS rendition. A playlist has no byte length, so the real size of a few of its segments is measured
     * and scaled to the full running time (the bitrate of the playlist is only the fallback). Always an estimate.
     */
    private fun estimateHlsSize(context: Context?, url: String, headers: Map<String, String>, bandwidth: Long): Long {
        val text = HlsVariants.fetchText(url, headers, null, 4000)
            ?: bypassProxyOf(context)?.let { HlsVariants.fetchText(url, headers, it, 6000) }
            ?: return -1L
        val playlist = HlsVariants.parseMediaPlaylist(text, url) ?: return -1L
        if (!playlist.isVod) return -1L
        val total = playlist.totalSeconds
        if (total <= 0.0) return -1L
        val n = playlist.segments.size
        // the first, a middle and a late segment (the very last one is usually shorter than the others)
        val picks = linkedSetOf(0, n / 2, (n - 2).coerceAtLeast(0)).filter { it in 0 until n }
        var bytes = 0L
        var seconds = 0.0
        for (i in picks) {
            val b = probeSizeAnyRoute(context, playlist.segments[i], headers, 3500)
            if (b > 0L && playlist.durations[i] > 0.0) {
                bytes += b
                seconds += playlist.durations[i]
            }
        }
        if (seconds > 0.0 && bytes > 0L) return (total * bytes.toDouble() / seconds).toLong()
        return if (bandwidth > 0L) (total * bandwidth.toDouble() / 8.0).toLong() else -1L
    }

    private fun sizeText(bytes: Long, estimated: Boolean): String? =
        if (bytes > 0L) (if (estimated) "≈ " else "") + formatSize(bytes) else null

    private suspend fun optionsOf(context: Context?, s: MediaStream): List<DownloadOption> {
        val format = formatOf(s)
        val headers = s.headers
        if (format == "HLS") {
            // a master playlist is opened to list its qualities (each one downloads its own rendition)
            val variants = HlsVariants.fetchResult(s.link, headers, null, 4000)
                ?: bypassProxyOf(context)?.let { HlsVariants.fetchResult(s.link, headers, it, 6000) }
                ?: emptyList()
            if (variants.isNotEmpty()) {
                return coroutineScope {
                    variants.map { v ->
                        async(Dispatchers.IO) {
                            val size = estimateHlsSize(context, v.uri, headers, v.bandwidth)
                            DownloadOption(
                                stream = s.copy(link = v.uri, type = "m3u8", quality = v.label, alternatives = emptyList()),
                                title = s.server,
                                detail = listOf(v.label, "HLS", v.bitrateText(), sizeText(size, true) ?: "size unavailable")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                quality = v.label
                            )
                        }
                    }.awaitAll()
                }
            }
        }
        val quality = qualityOf(s)
        val size: Long
        val estimated: Boolean
        when (format) {
            "HLS" -> { size = estimateHlsSize(context, s.link, headers, 0L); estimated = true }
            "DASH" -> { size = -1L; estimated = false }
            else -> { size = probeSizeAnyRoute(context, s.link, headers); estimated = false }
        }
        val detail = listOf(quality, format, sizeText(size, estimated) ?: "size unavailable")
            .filter { !it.isNullOrBlank() }.joinToString(" · ")
        return listOf(DownloadOption(s.copy(alternatives = emptyList()), s.server, detail, quality))
    }

    /** The download list of a movie / episode: every file / quality the website offers, best quality first, with sizes when known. */
    suspend fun downloadOptions(streams: List<MediaStream>): List<DownloadOption> = coroutineScope {
        val all = streams.map { s -> async(Dispatchers.IO) { try { optionsOf(null, s) } catch (_: Throwable) { emptyList() } } }
            .awaitAll()
            .flatten()
            .distinctBy { it.stream.link }
        val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.uri }
        all.map { o -> if (o.stream.subtitles.isEmpty() && allSubs.isNotEmpty()) o.copy(stream = o.stream.copy(subtitles = allSubs)) else o }
            .sortedByDescending { heightOf(it.quality) }
    }

    /**
     * Like [downloadOptions], but the list is shown at once (server / quality / format) and every row fills in its real size as
     * soon as it is measured: the sheet no longer waits for the slowest server. [onUpdate] is always called on the main thread.
     */
    suspend fun downloadOptionsProgressive(context: Context, streams: List<MediaStream>, onUpdate: (List<DownloadOption>) -> Unit) {
        val app = context.applicationContext
        val ordered = streams.distinctBy { it.link }.sortedByDescending { heightOf(qualityOf(it)) }
        if (ordered.isEmpty()) return
        fun row(s: MediaStream, note: String): List<DownloadOption> {
            val quality = qualityOf(s)
            return listOf(
                DownloadOption(
                    s.copy(alternatives = emptyList()),
                    s.server,
                    listOf(quality, formatOf(s), note).filter { !it.isNullOrBlank() }.joinToString(" · "),
                    quality
                )
            )
        }
        val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.uri }
        val slots = arrayOfNulls<List<DownloadOption>>(ordered.size)
        for ((i, s) in ordered.withIndex()) slots[i] = row(s, "checking size…")
        suspend fun emit() {
            val flat = synchronized(slots) { slots.flatMap { it ?: emptyList() }.distinctBy { it.stream.link } }
                .map { o -> if (o.stream.subtitles.isEmpty() && allSubs.isNotEmpty()) o.copy(stream = o.stream.copy(subtitles = allSubs)) else o }
            withContext(Dispatchers.Main) { onUpdate(flat) }
        }
        emit()
        coroutineScope {
            ordered.forEachIndexed { i, s ->
                launch(Dispatchers.IO) {
                    val rows = try {
                        optionsOf(app, s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        row(s, "size unavailable")
                    }
                    synchronized(slots) { slots[i] = rows.ifEmpty { row(s, "size unavailable") } }
                    emit()
                }
            }
        }
    }

    /** Name prefix of a server row that is a plain download file the player moved to because streaming did not work. */
    const val DIRECT_PREFIX = "Direct · "

    /**
     * The plugin's own download link (a plain mp4 / mkv file) of the same movie / episode, for when the streaming link keeps
     * buffering or does not play. Downloads from the same site work through the downloader, so the same link plays too.
     * The file of the same server is preferred, at [targetHeight] or the closest quality; every candidate is checked first.
     */
    suspend fun resolveDownloadFallback(
        context: Context,
        plugin: MediaPluginInfo,
        sourceLink: String,
        sourceType: String,
        preferServer: String?,
        targetHeight: Int
    ): MediaStream? {
        return try {
            val all = MediaProviderRepository.loadStreams(context, plugin.value, sourceLink, sourceType, true)
            val files = all.distinctBy { it.link }.filter { s ->
                !HlsVariants.looksLikeHls(s.link, s.type) && rank(s) in 1..3
            }
            if (files.isEmpty()) return null
            val clean = preferServer?.removePrefix(DIRECT_PREFIX)
            val sameServer = if (clean.isNullOrBlank()) emptyList() else files.filter { it.server.equals(clean, ignoreCase = true) }
            val pool = if (sameServer.isNotEmpty()) sameServer else files
            val known = pool.filter { heightOf(qualityOf(it)) > 0 }
            // the best file that fits the wanted height, else the smallest one above it; files of unknown quality last
            val ordered = known.filter { heightOf(qualityOf(it)) <= targetHeight }.sortedByDescending { heightOf(qualityOf(it)) } +
                known.filter { heightOf(qualityOf(it)) > targetHeight }.sortedBy { heightOf(qualityOf(it)) } +
                pool.filter { heightOf(qualityOf(it)) <= 0 }
            val candidates = (ordered + files.filter { f -> ordered.none { it.link == f.link } }).distinctBy { it.link }
            val outcome = StreamProbe.verify(context, candidates)
            if (!outcome.anyWorks) return null
            val pick = outcome.ordered.firstOrNull() ?: return null
            pick.copy(
                server = DIRECT_PREFIX + pick.server,
                subtitles = all.flatMap { it.subtitles }.distinctBy { it.uri },
                alternatives = emptyList(),
                sourceLink = sourceLink,
                sourceType = sourceType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    /** Puts the video at the top of a playlist named after the website (created on first use). Returns true if it was created. */
    fun addToPluginPlaylist(context: Context, plugin: MediaPluginInfo, item: VideoItem): Boolean {
        val existing = PlaylistManager.getPlaylists(context)
            .firstOrNull { it.name.equals(plugin.name, ignoreCase = true) && !it.isAudio }
        val playlist = existing ?: PlaylistManager.createPlaylist(context, plugin.name, "VIDEO", true)
        PlaylistManager.updatePlaylistVideosOrder(context, playlist.id, listOf(item.path) + playlist.videoPaths.filter { it != item.path })
        return existing == null
    }

    /** Starts the download right away (no sheet); the plugin's headers are sent with it. Returns the status message. */
    fun startDownload(context: Context, plugin: MediaPluginInfo, title: String, poster: String, stream: MediaStream): String {
        val request = DownloadRequest(
            source = stream.link,
            title = title,
            author = plugin.name,
            isAudio = false,
            formatSpec = null,
            container = null,
            estimatedBytes = 0L,
            thumbnailUrl = poster.ifBlank { null },
            headers = stream.headers,
            // every subtitle track the website lists for this stream is attached to the finished video
            subtitles = stream.subtitles.distinctBy { it.uri }
        )
        return try {
            DownloadManager.enqueueCustom(context, request)
        } catch (_: Throwable) {
            "Could not start the download"
        }
    }
}

/** One quality of the audio (language) a title was opened with: the movie / episodes of it, or the link to open them. */
data class PluginQualityChoice(
    val quality: String,
    val episodesLink: String?,
    val directLinks: List<MediaDirectLink>
)

/**
 * What the player needs to know about a video that came from a media plugin: the qualities that plugin really offers
 * for this movie / episode (for the player's quality menu) and the subtitles of the stream that is playing.
 */
class PluginPlaybackSession(
    val plugin: MediaPluginInfo,
    val episodeIndex: Int,
    val episodeTitle: String,
    val isSeries: Boolean,
    val choices: List<PluginQualityChoice>,
    @Volatile var selectedQuality: String,
    @Volatile var subtitles: List<MediaSubtitle> = emptyList(),
    /** The servers of this movie / episode (the playing one and its alternatives) and which one plays. */
    @Volatile var servers: List<MediaStream> = emptyList(),
    @Volatile var selectedServer: Int = 0,
    /** The person chose a server (or quality) by hand: it stays, nothing switches it by itself any more. */
    @Volatile var manualChoice: Boolean = false,
    /** The video has really started once: after that the player never moves to another server on its own. */
    @Volatile var hasPlayed: Boolean = false
) {
    /** The plugin link this movie / episode was resolved from: used to ask the plugin for a download link when streaming fails. */
    @Volatile var sourceLink: String? = null
    @Volatile var sourceType: String? = null
    /** The download-link fallback was already used for the current server: it is not tried again and again. */
    @Volatile var fallbackTried: Boolean = false
}

/** Stream URL -> session, so the player (which only gets a VideoItem) can find it. Small, in memory only. */
object PluginPlaybackSessions {
    private val map = object : LinkedHashMap<String, PluginPlaybackSession>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PluginPlaybackSession>?): Boolean = size > 24
    }

    @Synchronized fun put(url: String, session: PluginPlaybackSession) { map[url] = session }
    @Synchronized fun get(url: String): PluginPlaybackSession? = map[url]
}


/**
 * One episode of a series that is queued behind the one that was opened. It is only a placeholder (a VideoItem with a
 * "pluginqueue://" path) until the player reaches it: then [resolve] asks the plugin for its stream, like tapping it.
 */
class PluginQueuedEpisode(
    val plugin: MediaPluginInfo,
    val playerTitle: String,
    val poster: String,
    val link: String,
    val type: String,
    val episodeIndex: Int,
    val episodeTitle: String,
    val isSeries: Boolean,
    val choices: List<PluginQualityChoice>,
    val selectedQuality: String
)

object PluginQueuedEpisodes {
    const val SCHEME = "pluginqueue://"
    private val map = java.util.concurrent.ConcurrentHashMap<String, PluginQueuedEpisode>()

    fun isQueued(path: String?): Boolean = path != null && path.startsWith(SCHEME)

    /** The placeholder item shown in the player's playlist / used by Next. */
    fun placeholder(ep: PluginQueuedEpisode): VideoItem {
        val key = SCHEME + ep.plugin.value + "/" + kotlin.math.abs((ep.plugin.value + "|" + ep.link).hashCode()) + "/" + ep.episodeIndex
        map[key] = ep
        return VideoItem(
            id = -(kotlin.math.abs(key.hashCode().toLong())) - 1L,
            uri = Uri.parse(key),
            displayName = ep.playerTitle,
            path = key,
            sizeBytes = 0L,
            durationMs = 0L,
            dateModified = System.currentTimeMillis() / 1000L,
            isOnline = true,
            thumbnailUrl = ep.poster.ifBlank { null },
            channelName = ep.plugin.name,
            isDirectStream = true
        )
    }

    /** Asks the plugin for the stream links of a queued episode in the background (kept for a few minutes), so opening it later is quick. */
    suspend fun prefetch(context: Context, path: String) {
        val ep = map[path] ?: return
        try {
            MediaProviderRepository.loadStreams(context, ep.plugin.value, ep.link, ep.type, false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    /** Gets the real stream of a queued episode and returns the item the player can open. */
    suspend fun resolve(context: Context, path: String): Result<VideoItem> {
        val ep = map[path] ?: return Result.failure(PluginException("this episode is no longer queued"))
        val result = MediaPlayback.resolve(context, ep.plugin, ep.link, ep.type, false, ep.episodeTitle, if (ep.isSeries) ep.episodeIndex else -1)
        val stream = result.getOrNull() ?: return Result.failure(result.exceptionOrNull() ?: PluginException("no playable link was found"))
        val session = PluginPlaybackSession(
            plugin = ep.plugin,
            episodeIndex = ep.episodeIndex,
            episodeTitle = ep.episodeTitle,
            isSeries = ep.isSeries,
            choices = ep.choices,
            selectedQuality = ep.selectedQuality,
            subtitles = stream.subtitles
        )
        return Result.success(MediaPlayback.toVideoItem(context, ep.plugin, ep.playerTitle, ep.poster, stream, session))
    }
}
