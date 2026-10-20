package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** A subtitle track that the site offers for a pasted link ([auto] = machine generated, e.g. YouTube speech-to-text). */
data class NetworkSubtitle(val language: String, val name: String, val url: String, val auto: Boolean)

/** A pasted network link, resolved to something the app's players can open. */
data class NetworkStream(
    val pageUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSec: Long,
    /** Direct media URL (video, or the audio stream itself when [isAudio]). */
    val videoUrl: String,
    /** Separate audio URL (adaptive streams: video-only + audio-only), or null when the video already has sound. */
    val audioUrl: String?,
    /** HTTP headers (User-Agent, Referer, ...) the host expects for [videoUrl]. */
    val headers: Map<String, String>,
    /** true = a plain media URL / live stream that is opened directly (no known duration up front). */
    val isDirect: Boolean,
    /** true = music / audio only: it belongs in the audio player, not the video player. */
    val isAudio: Boolean = false,
    /** Spotify / playlist links: the tracks to queue in the audio player (resolved to audio when each one starts). */
    val tracks: List<OnlineTrackResult> = emptyList(),
    /**
     * All selectable qualities (+ audio / chapters) of a video link, so the player's three-dot menu can offer
     * "Video Quality" for pasted links exactly like for Online Videos. Null = nothing to choose (plain files, live TV).
     */
    val playback: OnlineVideoPlayback? = null,
    /** Subtitle tracks the site offers (manual and auto-generated); Settings > Network decides which ones are loaded. */
    val subtitles: List<NetworkSubtitle> = emptyList(),
    /** true = [videoUrl] is the web page itself: mpv's ytdl_hook (bundled yt-dlp) resolves and plays it. */
    val viaYtdlp: Boolean = false
)

/**
 * Network tab: turns a pasted link into a playable stream.
 *  - plain media links (.mp4 / .mkv / .m3u8 / .mp3 ...) are used directly,
 *  - YouTube / YouTube Music links go to NewPipe (same engine as the app's Online tabs),
 *  - Spotify links are identified and played from YouTube Music.
 * The resolved stream is cached for a short time so the player can resolve the same link again instantly.
 */
object NetworkStreamResolver {
    private const val TAG = "NetworkStreamResolver"
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val PREFS = "lumora_network_tab"


    private val cache = ConcurrentHashMap<String, Pair<Long, NetworkStream>>()
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv", "mpg", "mpeg", "3gp", "ts", "m2ts", "m3u8", "mpd"
    )


    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Links that are being resolved right now (shared by the background prefetch and the Play button). */
    private val inFlight = ConcurrentHashMap<String, Deferred<NetworkStream?>>()

    /** Short, human-readable reason of the last failed [resolve] (shown to the user). */
    @Volatile var lastError: String? = null
        private set

    /** Adds https:// when needed and returns null when the text is not a usable web link. */
    fun normalizeUrl(raw: String): String? {
        // Magnet links, info hashes and .torrent links are handled by the torrent picker.
        com.example.torrent.normalizeTorrentSource(raw)?.let { return it }
        val t = raw.trim().replace("\\s".toRegex(), "")
        if (t.isEmpty()) return null
        val withScheme = when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            t.contains("://") -> return null
            t.contains('.') && !t.startsWith(".") && !t.endsWith(".") -> "https://$t"
            else -> return null
        }
        val uri = try { java.net.URI(withScheme) } catch (_: Throwable) { return null }
        return if (uri.host.isNullOrBlank()) null else withScheme
    }

    private fun extensionOf(url: String): String {
        val path = try { java.net.URI(url).path.orEmpty() } catch (_: Throwable) { url.substringBefore('?') }
        return path.substringAfterLast('.', "").lowercase()
    }

    fun isDirectMediaUrl(url: String): Boolean {
        val ext = extensionOf(url)
        return ext in audioExtensions || ext in videoExtensions
    }

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host.orEmpty().lowercase() } catch (_: Throwable) { "" }

    private fun isMusicHost(url: String): Boolean = hostOf(url) == "music.youtube.com"

    private fun isYoutubeHost(url: String): Boolean {
        val h = hostOf(url)
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com") || h.endsWith("youtube-nocookie.com")
    }

    fun cached(url: String): NetworkStream? =
        cache[url]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.second

    suspend fun resolve(context: Context, url: String, forceRefresh: Boolean = false): NetworkStream? {
        if (!forceRefresh) cached(url)?.let { return it }
        val appCtx = context.applicationContext
        // A link that is already being resolved (background prefetch right after it was pasted) is shared with
        // the caller instead of resolving it a second time.
        val deferred = inFlight.computeIfAbsent(url) { key ->
            resolveScope.async {
                try {
                    resolveNow(appCtx, key)
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return deferred.await()
    }

    /**
     * Resolves a pasted link in the background (nothing is shown) so the result is cached by the time the user
     * presses Play. Failures are silent: Play simply tries again.
     */
    fun prefetch(context: Context, url: String) {
        val normalized = normalizeUrl(url) ?: return
        if (com.example.torrent.isTorrentSource(normalized)) return
        if (isSpotifyUrl(normalized) || isDirectMediaUrl(normalized)) return
        if (cached(normalized) != null || inFlight.containsKey(normalized)) return
        val appCtx = context.applicationContext
        resolveScope.launch {
            try { resolve(appCtx, normalized) } catch (_: Throwable) {}
        }
    }

    private suspend fun resolveNow(appCtx: Context, url: String): NetworkStream? {
        lastError = null
        val result: NetworkStream? = try {
            if (isSpotifyUrl(url)) {
                resolveSpotify(url)
            } else if (isDirectMediaUrl(url)) {
                directStream(url)
            } else if (isYoutubeHost(url)) {
                // the NewPipe path is what Online Music/Video already use
                resolveWithNewPipe(appCtx, url)
            } else {
                // Extensionless direct file URLs are common on CDNs; inspect Content-Type before treating them as pages.
                val contentType = probeDirectMediaContentType(url)
                if (contentType != null) {
                    directStream(url, contentType)
                } else if (com.example.BuildConfig.ENABLE_YTDLP && com.example.ytdlp.YtdlpManager.requiresYtdlp(url)) {
                    // other sites: the bundled yt-dlp (through mpv's ytdl_hook) resolves the page when it plays
                    ytdlpStream(url)
                } else {
                    lastError = "This link is not supported right now"
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "resolve failed for $url", t)
            if (lastError == null) lastError = shortReason(t)
            null
        }
        if (result != null) cache[url] = System.currentTimeMillis() to result
        else if (lastError == null) lastError = "No playable stream found"
        return result
    }

    private fun ytdlpStream(url: String): NetworkStream {
        val host = hostOf(url).removePrefix("www.")
        val last = try {
            java.net.URLDecoder.decode(url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/'), "UTF-8")
        } catch (_: Throwable) { "" }
        return NetworkStream(
            pageUrl = url,
            title = last.takeIf { it.isNotBlank() && it != host }?.let { "$it · $host" } ?: host.ifBlank { url },
            uploader = host.ifBlank { null },
            thumbnailUrl = null,
            durationSec = 0L,
            videoUrl = url,
            audioUrl = null,
            headers = emptyMap(),
            isDirect = false,
            viaYtdlp = true
        )
    }

    private suspend fun probeDirectMediaContentType(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                .build()
            spotifyHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                type.takeIf {
                    it.startsWith("video/") || it.startsWith("audio/") ||
                        it in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl",
                            "application/dash+xml", "application/octet-stream")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Direct-media HEAD probe unavailable: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun directStream(url: String, contentType: String? = null): NetworkStream {
        val name = try {
            java.net.URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), "UTF-8")
        } catch (_: Throwable) { url.substringAfterLast('/') }
        return NetworkStream(
            pageUrl = url,
            title = name.ifBlank { url },
            uploader = null,
            thumbnailUrl = null,
            durationSec = 0L,
            videoUrl = url,
            audioUrl = null,
            headers = emptyMap(),
            isDirect = true,
            isAudio = extensionOf(url) in audioExtensions || contentType?.startsWith("audio/") == true
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Spotify: DRM protected, so it is never "downloaded". Like the app's Online Music, the track is identified from
    // the Spotify page (name + artist) and the matching song is played from YouTube Music in the audio player.
    // ---------------------------------------------------------------------------------------------

    private val spotifyHttp by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun isSpotifyUrl(url: String): Boolean {
        val h = hostOf(url)
        return h.endsWith("spotify.com") || h == "spotify.link"
    }

    private fun httpGet(url: String): Pair<String, String>? = try {
        val req = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .build()
        spotifyHttp.newCall(req).execute().use { resp -> (resp.body?.string() ?: "") to resp.request.url.toString() }
    } catch (t: Throwable) {
        Log.w(TAG, "GET failed: $url", t)
        null
    }

    private suspend fun resolveSpotify(url: String): NetworkStream? {
        var finalUrl = url
        if (hostOf(url) == "spotify.link") {
            finalUrl = httpGet(url)?.second ?: url
        }
        val clean = finalUrl.substringBefore('?').substringBefore('#')
        if (clean.contains("/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/")
            if (id.isBlank()) { lastError = "Invalid Spotify link"; return null }
            var name = ""
            var artist = ""
            var durationMs = 0L
            var cover: String? = null
            val html = httpGet("https://open.spotify.com/embed/track/$id")?.first.orEmpty()
            val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>").find(html)?.groupValues?.get(1)
            if (json != null) {
                try {
                    val entity = JSONObject(json).getJSONObject("props").getJSONObject("pageProps")
                        .getJSONObject("state").getJSONObject("data").getJSONObject("entity")
                    name = entity.optString("name").ifBlank { entity.optString("title") }
                    artist = entity.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
                        .ifBlank { entity.optString("subtitle") }
                    durationMs = entity.optLong("duration", 0L)
                    cover = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                        ?.let { arr -> arr.optJSONObject(arr.length() - 1)?.optString("url") }?.ifBlank { null }
                } catch (t: Throwable) {
                    Log.w(TAG, "Spotify embed parse failed", t)
                }
            }
            if (name.isBlank()) {
                // fallback: Spotify's public oEmbed gives at least the title
                val o = httpGet("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$id")?.first
                try {
                    val j = JSONObject(o.orEmpty())
                    name = j.optString("title")
                    if (cover == null) cover = j.optString("thumbnail_url").ifBlank { null }
                } catch (_: Throwable) {}
            }
            if (name.isBlank()) { lastError = "Could not read this Spotify track"; return null }
            val track = OnlineTrackResult(
                videoUrl = "https://open.spotify.com/track/$id",
                title = name,
                artist = artist,
                durationSec = (durationMs / 1000L).coerceAtLeast(0L),
                thumbnailUrl = cover
            )
            return NetworkStream(
                pageUrl = url, title = name, uploader = artist.ifBlank { null }, thumbnailUrl = cover,
                durationSec = track.durationSec, videoUrl = track.videoUrl, audioUrl = null, headers = emptyMap(),
                isDirect = false, isAudio = true, tracks = listOf(track)
            )
        }
        // album / playlist
        val detail = OnlineMusicService.fetchPlaylistDetails(clean)
        if (detail == null || detail.tracks.isEmpty()) { lastError = "Could not read this Spotify playlist"; return null }
        val first = detail.tracks.first()
        return NetworkStream(
            pageUrl = url, title = detail.title, uploader = detail.author, thumbnailUrl = detail.coverUrl,
            durationSec = 0L, videoUrl = first.videoUrl, audioUrl = null, headers = emptyMap(),
            isDirect = false, isAudio = true, tracks = detail.tracks.take(200)
        )
    }

    // ---------------------------------------------------------------------------------------------
    // NewPipe (YouTube only)
    // ---------------------------------------------------------------------------------------------

    private suspend fun resolveWithNewPipe(context: Context, url: String): NetworkStream? {
        val host = hostOf(url)
        val isYoutube = host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("youtube-nocookie.com")
        if (!isYoutube) return null
        return try {
            val pb = OnlineVideoService.resolvePlayback(url) ?: return null
            if (isMusicHost(url)) {
                val audio = pb.audios.firstOrNull()?.url ?: return null
                return NetworkStream(
                    pageUrl = url,
                    title = pb.title.ifBlank { url },
                    uploader = pb.uploader.ifBlank { null },
                    thumbnailUrl = null,
                    durationSec = pb.durationSec,
                    videoUrl = audio,
                    audioUrl = null,
                    headers = emptyMap(),
                    isDirect = false,
                    isAudio = true
                )
            }
            val q = OnlineVideoService.pickAutoQuality(context, pb.qualities)
                ?: OnlineVideoService.pickDefaultQuality(pb.qualities, null)
                ?: return null
            NetworkStream(
                pageUrl = url,
                title = pb.title.ifBlank { url },
                uploader = pb.uploader.ifBlank { null },
                thumbnailUrl = null,
                durationSec = pb.durationSec,
                videoUrl = q.url,
                audioUrl = if (q.isVideoOnly) pb.audios.firstOrNull()?.url else null,
                headers = emptyMap(),
                isDirect = false,
                playback = pb
            )
        } catch (t: Throwable) {
            Log.w(TAG, "NewPipe fallback failed for $url", t)
            null
        }
    }

    /** Readable "Class: message < Cause: message" chain (max 3 levels) so a failure can be diagnosed from the toast. */
    private fun describeFailure(t: Throwable?): String {
        if (t == null) return "unknown error"
        val parts = ArrayList<String>()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 3) {
            val name = cur.javaClass.name.substringAfterLast('.')
            val cause = cur.cause?.takeIf { it !== cur }
            val msg = cur.message?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
            // Throwable(cause) copies cause.toString() into its message: do not print the same thing twice.
            val duplicate = msg != null && cause != null && msg == cause.toString().lineSequence().firstOrNull()?.trim()
            parts += if (msg.isNullOrBlank() || duplicate || msg == cur.javaClass.name) name else "$name: $msg"
            cur = cause
            depth++
        }
        return parts.joinToString(" < ").take(220)
    }

    /** First meaningful line of an error, capped so it fits a toast. */
    private fun shortReason(t: Throwable): String {
        val raw = (t.message ?: t.javaClass.simpleName).trim()
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("ERROR", true) }
            ?: raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: raw
        return line.removePrefix("ERROR:").trim().take(140)
    }
}
