package com.example.util.media

import android.content.Context
import android.net.Uri
import com.example.ui.screens.VideoItem
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

    /** Asks the plugin for a playable stream for [link]. */
    suspend fun resolve(
        context: Context,
        plugin: MediaPluginInfo,
        link: String,
        type: String,
        episodeTitle: String = "",
        episodeIndex: Int = -1,
        /** Servers are checked so a page is not opened for a dead stream. */
        verify: Boolean = true,
        requireWorking: Boolean = false
    ): Result<MediaStream> {
        return try {
            val streams = MediaProviderRepository.loadStreams(context, plugin.value, link, type)
            // like Vega: the provider's own order is kept (it lists its best server first); only the check below
            // moves a server that does not deliver video behind the ones that do
            var ranked = streams.distinctBy { it.link }
            // the server that really delivered this episode's video while its thumbnail was made is tried first, but it is
            // checked like every other one: a frame proves the website's file opens, not that the player can stream it
            val proven = EpisodeFrames.preferred(context, plugin, link)
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
                // the other servers stay available: the player's Server list
                val others = ranked.filter { it !== chosen && it.link != chosen.link }.distinctBy { it.link }
                Result.success(
                    chosen.copy(
                        subtitles = if (allSubs.size > chosen.subtitles.size) allSubs else chosen.subtitles,
                        alternatives = others
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
        isSeries: Boolean
    ): Result<MediaStream> {
        return try {
            val list = choice.episodesLink?.let { MediaProviderRepository.loadEpisodes(context, plugin.value, it) } ?: choice.directLinks
            fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
            val target = list.firstOrNull { norm(it.title) == norm(episodeTitle) } ?: list.getOrNull(episodeIndex)
                ?: return Result.failure(PluginException("this quality does not have the same episode"))
            resolve(context, plugin, target.link, target.type ?: if (isSeries) "series" else "movie", episodeTitle, if (isSeries) episodeIndex else -1)
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
        val result = MediaPlayback.resolve(context, ep.plugin, ep.link, ep.type, ep.episodeTitle, if (ep.isSeries) ep.episodeIndex else -1)
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
