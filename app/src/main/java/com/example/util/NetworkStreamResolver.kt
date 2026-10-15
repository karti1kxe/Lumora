package com.example.util

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

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
    val tracks: List<OnlineTrackResult> = emptyList()
)

/**
 * Network tab: turns a pasted link into a playable stream.
 *  - plain media links (.mp4 / .mkv / .m3u8 / .mp3 ...) are used directly,
 *  - everything else (YouTube and the many other sites yt-dlp supports) is resolved with yt-dlp
 *    (youtubedl-android), with NewPipe as a second chance for YouTube.
 * The bundled yt-dlp is updated first when it is old (sites change constantly, an old yt-dlp simply fails),
 * and a failed attempt triggers one update + retry. The resolved stream is cached for a short time so the
 * player can resolve the same link again instantly.
 */
object NetworkStreamResolver {
    private const val TAG = "NetworkStreamResolver"
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val PREFS = "lumora_network_tab"
    private const val KEY_LAST_YTDLP_UPDATE = "last_ytdlp_update"
    private const val UPDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val RETRY_UPDATE_MIN_GAP_MS = 10 * 60 * 1000L
    private const val KEY_BUNDLED_YTDLP = "bundled_ytdlp_version"


    private val cache = ConcurrentHashMap<String, Pair<Long, NetworkStream>>()
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv", "mpg", "mpeg", "3gp", "ts", "m2ts", "m3u8", "mpd"
    )

    @Volatile private var ytdlpReady = false
    private val initLock = Any()
    private val updateLock = Any()

    /** Short, human-readable reason of the last failed [resolve] (shown to the user). */
    @Volatile var lastError: String? = null
        private set

    /** Adds https:// when needed and returns null when the text is not a usable web link. */
    fun normalizeUrl(raw: String): String? {
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

    fun cached(url: String): NetworkStream? =
        cache[url]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.second

    suspend fun resolve(context: Context, url: String, forceRefresh: Boolean = false): NetworkStream? =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) cached(url)?.let { return@withContext it }
            lastError = null
            val appCtx = context.applicationContext

            val result: NetworkStream? = try {
                if (isSpotifyUrl(url)) {
                    resolveSpotify(url)
                } else if (isDirectMediaUrl(url)) {
                    directStream(url)
                } else {
                    resolveWithYtDlp(appCtx, url) ?: resolveWithNewPipe(appCtx, url)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "resolve failed for $url", t)
                if (lastError == null) lastError = shortReason(t)
                null
            }
            if (result != null) cache[url] = System.currentTimeMillis() to result
            else if (lastError == null) lastError = "No playable stream found"
            result
        }

    private fun directStream(url: String): NetworkStream {
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
            isAudio = extensionOf(url) in audioExtensions
        )
    }

    // ---------------------------------------------------------------------------------------------
    // yt-dlp
    // ---------------------------------------------------------------------------------------------

    private fun ensureYtDlp(context: Context): Boolean {
        if (ytdlpReady) return true
        synchronized(initLock) {
            if (ytdlpReady) return true
            return try {
                installBundledYtDlpIfNew(context)
                YoutubeDL.getInstance().init(context)
                ytdlpReady = true
                true
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp init failed", t)
                lastError = "yt-dlp could not start (${shortReason(t)})"
                false
            }
        }
    }

    /**
     * The library copies its yt-dlp out of the APK only once, so after an app update the OLD copy would stay in use.
     * When the yt-dlp bundled with this app build changes, the extracted copy is removed and re-extracted.
     */
    private fun installBundledYtDlpIfNew(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Version of the official yt-dlp downloaded from GitHub when this APK was built (see app/build.gradle.kts).
        val bundledVersion = try { context.getString(com.example.R.string.bundled_ytdlp_version) } catch (_: Throwable) { return }
        if (prefs.getString(KEY_BUNDLED_YTDLP, null) == bundledVersion) return
        try {
            java.io.File(context.noBackupFilesDir, "youtubedl-android/yt-dlp").deleteRecursively()
        } catch (t: Throwable) {
            Log.w(TAG, "could not remove old yt-dlp", t)
        }
        prefs.edit()
            .putString(KEY_BUNDLED_YTDLP, bundledVersion)
            // the freshly extracted copy is already the newest release
            .putLong(KEY_LAST_YTDLP_UPDATE, System.currentTimeMillis())
            .apply()
    }

    /**
     * The yt-dlp that ships inside the app is only as new as the app build, and YouTube & co. break old versions
     * every few weeks. So it is refreshed before use when older than 12 hours ([force] = right now, after a failure).
     */
    private fun updateYtDlp(context: Context, force: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(updateLock) {
            val last = prefs.getLong(KEY_LAST_YTDLP_UPDATE, 0L)
            val age = System.currentTimeMillis() - last
            if (!force && age < UPDATE_INTERVAL_MS) return false
            if (force && age < RETRY_UPDATE_MIN_GAP_MS) return false
            return try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                prefs.edit().putLong(KEY_LAST_YTDLP_UPDATE, System.currentTimeMillis()).apply()
                true
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp update failed", t)
                false
            }
        }
    }

    private fun resolveWithYtDlp(context: Context, url: String): NetworkStream? {
        if (!ensureYtDlp(context)) return null
        updateYtDlp(context, force = false)
        val audioOnly = isMusicHost(url)
        var result = runYtDlp(url, audioOnly)
        if (result == null) {
            // One more chance with the newest yt-dlp.
            if (updateYtDlp(context, force = true)) result = runYtDlp(url, audioOnly)
        }
        return result
    }

    private fun runYtDlp(url: String, audioOnly: Boolean): NetworkStream? {
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-single-json")
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", 20)
            request.addOption("--extractor-retries", 2)
            request.addOption("--no-check-certificates")
            // Prefer H.264 up to 1080p (plays on every phone); fall back to anything yt-dlp can offer.
            request.addOption(
                "-f",
                if (audioOnly) "ba[ext=m4a]/ba/b"
                else "bv*[height<=1080][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=1080]+ba/b[height<=1080]/bv*+ba/b"
            )
            val response = YoutubeDL.getInstance().execute(request, null, null)
            val parsed = parseYtDlpJson(url, response.out)
            if (parsed == null) lastError = "yt-dlp returned no stream"
            if (parsed != null && audioOnly) parsed.copy(isAudio = true) else parsed
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp could not resolve $url", t)
            lastError = shortReason(t)
            null
        }
    }

    internal fun parseYtDlpJson(pageUrl: String, out: String): NetworkStream? {
        val text = out.trim()
        if (text.isEmpty()) return null
        // yt-dlp prints one JSON document; take the last JSON line defensively.
        val jsonText = text.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") } ?: return null
        var root = JSONObject(jsonText)
        if (root.optString("_type") == "playlist") {
            root = root.optJSONArray("entries")?.optJSONObject(0) ?: return null
        }
        val title = root.optString("title").ifBlank { root.optString("fulltitle") }.ifBlank { pageUrl }
        val uploader = root.optString("uploader").ifBlank { root.optString("channel") }.ifBlank { null }
        val thumb = root.optString("thumbnail").ifBlank { null }
        val duration = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }.toLong()
        val isLive = root.optBoolean("is_live", false)

        fun headersOf(o: JSONObject?): Map<String, String> {
            val h = o?.optJSONObject("http_headers") ?: return emptyMap()
            val map = LinkedHashMap<String, String>()
            val keys = h.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = h.optString(k)
                if (v.isNotBlank()) map[k] = v
            }
            return map
        }

        val requested = root.optJSONArray("requested_formats")
        var videoUrl: String? = null
        var audioUrl: String? = null
        var headers: Map<String, String> = headersOf(root)
        var sawVideo = false
        if (requested != null && requested.length() > 0) {
            for (i in 0 until requested.length()) {
                val f = requested.optJSONObject(i) ?: continue
                val u = f.optString("url").ifBlank { null } ?: continue
                val hasVideo = f.optString("vcodec", "none") != "none"
                val hasAudio = f.optString("acodec", "none") != "none"
                if (hasVideo && videoUrl == null) {
                    sawVideo = true
                    videoUrl = u
                    headersOf(f).takeIf { it.isNotEmpty() }?.let { headers = it }
                    // a muxed single format already carries the sound
                    if (hasAudio) audioUrl = null
                } else if (hasAudio && !hasVideo && audioUrl == null) {
                    audioUrl = u
                    if (videoUrl == null) headersOf(f).takeIf { it.isNotEmpty() }?.let { headers = it }
                }
            }
            if (videoUrl == null) {
                // audio-only link (music site): the audio stream itself is what gets played
                videoUrl = audioUrl
                audioUrl = null
            }
        }
        if (videoUrl == null) {
            videoUrl = root.optString("url").ifBlank { null }
            sawVideo = root.optString("vcodec", "none") != "none"
            if (!sawVideo && root.optString("vcodec").isBlank()) {
                // some extractors give no codec info: judge by the file extension
                sawVideo = extensionOf(videoUrl.orEmpty()) !in audioExtensions
            }
        }
        val finalUrl = videoUrl ?: return null
        return NetworkStream(
            pageUrl = pageUrl,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumb,
            durationSec = duration,
            videoUrl = finalUrl,
            audioUrl = audioUrl,
            headers = headers,
            // live streams have no duration: let the player handle them like IPTV channels
            isDirect = isLive || duration <= 0L,
            isAudio = !sawVideo
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
    // NewPipe (YouTube only) as a second chance
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
                isDirect = false
            )
        } catch (t: Throwable) {
            Log.w(TAG, "NewPipe fallback failed for $url", t)
            null
        }
    }

    /** First meaningful line of an error (yt-dlp prints "ERROR: ..."), capped so it fits a toast. */
    private fun shortReason(t: Throwable): String {
        val raw = (t.message?.takeIf { it.isNotBlank() }
            ?: (t.javaClass.name.substringAfterLast('.') + (t.cause?.let { ": " + (it.message ?: it.javaClass.name.substringAfterLast('.')) } ?: ""))).trim()
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("ERROR", true) }
            ?: raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: raw
        return line.removePrefix("ERROR:").trim().take(140)
    }
}
