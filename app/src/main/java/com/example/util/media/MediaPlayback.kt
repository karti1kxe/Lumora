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

    /** Picks the stream most likely to play: plain files first, then HLS. */
    fun pickBest(streams: List<MediaStream>): MediaStream? {
        fun rank(s: MediaStream): Int {
            val path = s.link.substringBefore('?').substringBefore('#').lowercase()
            return when {
                s.type == "mp4" || path.endsWith(".mp4") -> 0
                s.type == "mkv" || path.endsWith(".mkv") -> 1
                s.type == "m3u8" || s.type == "hls" || path.endsWith(".m3u8") -> 2
                s.type == "webm" || path.endsWith(".webm") -> 3
                else -> 4
            }
        }
        return streams.minByOrNull { rank(it) }
    }

    /** Asks the plugin for the playable link of [link]. [forDownload] lets the plugin return a downloadable server. */
    suspend fun resolve(context: Context, plugin: MediaPluginInfo, link: String, type: String, forDownload: Boolean = false): Result<MediaStream> {
        return try {
            val streams = MediaProviderRepository.loadStreams(context, plugin.value, link, type, forDownload)
            val best = pickBest(streams)
            if (best == null) Result.failure(PluginException("no playable link was found")) else Result.success(best)
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
    fun toVideoItem(context: Context, plugin: MediaPluginInfo, title: String, poster: String, stream: MediaStream): VideoItem {
        val ua = stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        val ref = stream.headers.entries.firstOrNull { it.key.equals("Referer", true) || it.key.equals("Referrer", true) }?.value
        val extra = LinkedHashMap<String, String>()
        for ((k, v) in stream.headers) {
            if (k.equals("User-Agent", true) || k.equals("Referer", true) || k.equals("Referrer", true)) continue
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
