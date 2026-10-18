package com.example.util.media

import android.content.Context
import android.net.Uri
import com.example.ui.screens.VideoItem
import com.example.util.DownloadManager
import com.example.util.DownloadRequest
import com.example.util.PlaylistManager
import com.example.util.StreamEntryMeta
import com.example.util.StreamEntryStore
import com.example.util.TorrentFileInfo
import com.example.util.TorrentLibrary
import com.example.util.TorrentSource
import com.example.util.TorrentStreamEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private val MAGNET_RE = Regex("magnet:\\?\\S+", RegexOption.IGNORE_CASE)

    /** A magnet link or a .torrent file link: it has to be opened with the torrent engine first. */
    fun needsTorrentEngine(s: MediaStream): Boolean {
        if (s.link.startsWith("torrent://", ignoreCase = true)) return false
        return s.link.startsWith("magnet:", ignoreCase = true) ||
            s.link.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true)
    }

    private val EPISODE_RE = Regex("(?i)s0*(\\d{1,2})\\s*[._ -]?e0*(\\d{1,3})")
    private val EPISODE_ONLY_RE = Regex("(?i)(?:episode|ep|e)\\s*[._ -]?0*(\\d{1,3})(?![0-9])")

    private fun episodeOf(text: String): Pair<Int?, Int>? {
        EPISODE_RE.find(text)?.let { m ->
            val e = m.groupValues[2].toIntOrNull() ?: return null
            return m.groupValues[1].toIntOrNull() to e
        }
        EPISODE_ONLY_RE.find(text)?.let { m ->
            val e = m.groupValues[1].toIntOrNull() ?: return null
            return null to e
        }
        return null
    }

    /** The video of a torrent to play: the wanted episode of a season pack, else the biggest video (the movie itself). */
    private fun pickTorrentFile(videos: List<TorrentFileInfo>, episodeTitle: String, episodeIndex: Int): TorrentFileInfo? {
        if (videos.size <= 1) return videos.firstOrNull()
        if (episodeIndex >= 0) {
            val want = episodeOf(episodeTitle)
            if (want != null) {
                val match = videos.firstOrNull { f ->
                    val got = episodeOf(f.displayName)
                    got != null && got.second == want.second && (want.first == null || got.first == null || got.first == want.first)
                }
                if (match != null) return match
            }
            videos.getOrNull(episodeIndex)?.let { return it }
        }
        return videos.maxByOrNull { it.size }
    }

    /** Opens the torrent behind a magnet / .torrent link and returns the stream of its video as a "torrent://" path. */
    private suspend fun resolveTorrentStream(context: Context, s: MediaStream, episodeTitle: String, episodeIndex: Int): Result<MediaStream> {
        val magnet = MAGNET_RE.find(s.link)?.value
        val source = if (magnet != null) TorrentSource.Magnet(magnet) else TorrentSource.RemoteFile(s.link)
        val (summary, error) = TorrentStreamEngine.fetchSummary(context, source)
        if (summary == null) return Result.failure(PluginException(error ?: "could not open the torrent"))
        val file = pickTorrentFile(summary.videoFiles, episodeTitle, episodeIndex)
            ?: return Result.failure(PluginException("no video file was found in this torrent"))
        return Result.success(
            s.copy(
                link = TorrentLibrary.pathFor(summary.infoHash, file.index),
                type = "torrent",
                headers = emptyMap(),
                alternatives = emptyList()
            )
        )
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
                val i = ranked.indexOfFirst { it.server == proven.server && it.quality == proven.quality && !needsTorrentEngine(it) }
                    .takeIf { it >= 0 } ?: ranked.indexOfFirst { it.server == proven.server && !needsTorrentEngine(it) }
                if (i > 0) ranked = listOf(ranked[i]) + ranked.filterIndexed { idx, _ -> idx != i }
            }
            if (verify) {
                val direct = ranked.filter { !needsTorrentEngine(it) && !it.link.startsWith("torrent://", true) }
                if (direct.isNotEmpty()) {
                    val outcome = StreamProbe.verify(context, direct)
                    val hasTorrent = ranked.size > direct.size
                    if (!outcome.anyWorks) MediaProviderRepository.forgetStreams(plugin.value, link)
                    if (requireWorking && !outcome.anyWorks && !hasTorrent) {
                        return Result.failure(PluginException("This video does not play on any server right now. Try again in a moment."))
                    }
                    ranked = outcome.ordered + ranked.filter { it !in direct }
                }
            }
            // direct links first; magnet / .torrent links (torrent providers) are opened with the torrent engine
            var best: MediaStream? = null
            var torrentError: Throwable? = null
            for (candidate in ranked) {
                if (!needsTorrentEngine(candidate)) { best = candidate; break }
                val converted = resolveTorrentStream(context, candidate, episodeTitle, episodeIndex)
                val ok = converted.getOrNull()
                if (ok != null) { best = ok; break }
                torrentError = converted.exceptionOrNull()
            }
            val chosen = best
            if (chosen == null) {
                Result.failure(torrentError ?: PluginException("no playable link was found"))
            } else {
                // every subtitle the plugin listed (on any server) is offered in the player, without duplicates
                val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.uri }
                // the other servers stay available: the player's Server list, and the fallback when this one cannot be played
                val others = ranked.filter { it !== chosen && it.link != chosen.link && !needsTorrentEngine(it) }.distinctBy { it.link }
                Result.success(chosen.copy(subtitles = if (allSubs.size > chosen.subtitles.size) allSubs else chosen.subtitles, alternatives = others))
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
            PluginPlaybackSessions.put(stream.link, session)
        }
        val isTorrent = stream.link.startsWith("torrent://", ignoreCase = true)
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
        if (!isTorrent) {
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
        }
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
            isDirectStream = !isTorrent,
            isTorrentStream = isTorrent
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
            s.link.startsWith("torrent://", ignoreCase = true) || needsTorrentEngine(s) -> "Torrent"
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

    private fun probeSize(link: String, headers: Map<String, String>): Long {
        fun open(method: String, range: Boolean): HttpURLConnection = (URL(link).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            for ((k, v) in headers) if (k.isNotBlank() && v.isNotBlank() && !k.equals("Host", true) && !k.equals("Accept-Encoding", true)) { try { setRequestProperty(k, v) } catch (_: Throwable) {} }
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

    private fun optionsOf(s: MediaStream): List<DownloadOption> {
        val format = formatOf(s)
        val headers = s.headers
        if (format == "HLS") {
            // HLS is a playlist plus many changing segments, not one file with a reliable fixed size.
            // a master playlist is opened to list its qualities (each one downloads its own rendition)
            val variants = HlsVariants.fetch(s.link, headers, null, 4000)
            if (variants.isNotEmpty()) {
                return variants.map { v ->
                    DownloadOption(
                        stream = s.copy(link = v.uri, type = "m3u8", quality = v.label, subtitles = emptyList(), alternatives = emptyList()),
                        title = s.server,
                        detail = listOf(v.label, "HLS", v.bitrateText(), "size varies (adaptive stream)").filter { it.isNotBlank() }.joinToString(" · "),
                        quality = v.label
                    )
                }
            }
        }
        val quality = qualityOf(s)
        val size = if (format == "Torrent" || format == "HLS" || format == "DASH") -1L else probeSize(s.link, headers)
        val detail = listOf(quality, format, size.takeIf { it > 0L }?.let { formatSize(it) } ?: "size unavailable")
            .filter { !it.isNullOrBlank() }.joinToString(" · ")
        return listOf(DownloadOption(s.copy(subtitles = emptyList(), alternatives = emptyList()), s.server, detail, quality))
    }

    /** The download list of a movie / episode: every file / quality the website offers, best quality first, with sizes when known. */
    suspend fun downloadOptions(streams: List<MediaStream>): List<DownloadOption> = coroutineScope {
        val all = streams.map { s -> async(Dispatchers.IO) { try { optionsOf(s) } catch (_: Throwable) { emptyList() } } }
            .awaitAll()
            .flatten()
            .distinctBy { it.stream.link }
        all.sortedByDescending { heightOf(it.quality) }
    }

    /** Puts the video at the top of a playlist named after the website (created on first use). Returns true if it was created. */
    fun addToPluginPlaylist(context: Context, plugin: MediaPluginInfo, item: VideoItem): Boolean {
        val existing = PlaylistManager.getPlaylists(context)
            .firstOrNull { it.name.equals(plugin.name, ignoreCase = true) && !it.isAudio }
        val playlist = existing ?: PlaylistManager.createPlaylist(context, plugin.name, "VIDEO", true)
        PlaylistManager.updatePlaylistVideosOrder(context, playlist.id, listOf(item.path) + playlist.videoPaths.filter { it != item.path })
        return existing == null
    }

    /**
     * Torrent download (magnet / .torrent link of a torrent provider): the torrent's file list is read, the wanted video
     * (the episode of a season pack, else the movie) is picked and downloaded with the app's torrent downloader.
     */
    suspend fun startTorrentDownload(
        context: Context,
        plugin: MediaPluginInfo,
        title: String,
        poster: String,
        stream: MediaStream,
        episodeTitle: String,
        episodeIndex: Int
    ): String {
        val result = resolveTorrentStream(context, stream, episodeTitle, episodeIndex)
        val resolved = result.getOrNull() ?: return "Could not start the torrent: " + (result.exceptionOrNull()?.message ?: "unknown error")
        return try {
            com.example.util.DownloadManager.enqueueFromRecent(
                context,
                com.example.util.RecentStreamEntry(
                    url = resolved.link,
                    title = title,
                    subtitle = plugin.name,
                    playedAt = System.currentTimeMillis(),
                    thumbnail = poster.ifBlank { null }
                )
            )
        } catch (_: Throwable) {
            "Could not start the download"
        }
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
            headers = stream.headers
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
)

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
            isDirectStream = true,
            isTorrentStream = false
        )
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
