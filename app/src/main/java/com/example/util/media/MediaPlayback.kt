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

/** Turns a plugin's stream into something the app's own player / playlists / downloader understand. */
object MediaPlayback {

    private fun rank(s: MediaStream): Int {
        val path = s.link.substringBefore('?').substringBefore('#').lowercase()
        val type = s.type.lowercase()
        return when {
            type == "m3u8" || type == "hls" || path.endsWith(".m3u8") -> 0
            type == "mpd" || type == "dash" || path.endsWith(".mpd") -> 1
            type == "mp4" || path.endsWith(".mp4") -> 2
            type == "webm" || path.endsWith(".webm") -> 3
            type == "mkv" || path.endsWith(".mkv") -> 4
            else -> 5
        }
    }

    /** Prefer adaptive streaming when a provider offers it; fall back to direct media files. */
    fun pickBest(streams: List<MediaStream>): MediaStream? = streams.minByOrNull { rank(it) }

    /** Asks the plugin for the playable link of [link]. [forDownload] lets the plugin return a downloadable server. */
    suspend fun resolve(context: Context, plugin: MediaPluginInfo, link: String, type: String, forDownload: Boolean = false): Result<MediaStream> {
        return try {
            val streams = MediaProviderRepository.loadStreams(context, plugin.value, link, type, forDownload)
            val ranked = streams.sortedBy { rank(it) }
            val best = ranked.firstOrNull()
            if (best == null) {
                Result.failure(PluginException("no playable link was found"))
            } else {
                // every subtitle the plugin listed (on any server) is offered in the player, without duplicates
                val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.uri }
                // the other servers stay available: the player's Server list, and the fallback when this one cannot be played
                val others = ranked.drop(1).filter { it.link != best.link }.distinctBy { it.link }
                Result.success(best.copy(subtitles = if (allSubs.size > best.subtitles.size) allSubs else best.subtitles, alternatives = others))
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
        val ua = stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        val ref = stream.headers.entries.firstOrNull { it.key.equals("Referer", true) || it.key.equals("Referrer", true) }?.value
        val extra = LinkedHashMap<String, String>()
        for ((k, v) in stream.headers) {
            if (k.equals("User-Agent", true) || k.equals("Referer", true) || k.equals("Referrer", true)) continue
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
            resolve(context, plugin, target.link, target.type ?: if (isSeries) "series" else "movie", forDownload)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
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
    @Volatile var selectedServer: Int = 0
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
        val result = MediaPlayback.resolve(context, ep.plugin, ep.link, ep.type, false)
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
