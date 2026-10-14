package com.example.util

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** A pasted network link, resolved to something mpv can open. */
data class NetworkStream(
    val pageUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSec: Long,
    /** Direct media URL that mpv opens. */
    val videoUrl: String,
    /** Separate audio URL (adaptive streams: video-only + audio-only), or null when the video already has sound. */
    val audioUrl: String?,
    /** HTTP headers (User-Agent, Referer, ...) the host expects for [videoUrl]. */
    val headers: Map<String, String>,
    /** true = a plain media URL / live stream that is opened directly (no known duration up front). */
    val isDirect: Boolean
)

/**
 * Network tab: turns a pasted link into a playable stream.
 *  - plain media links (.mp4 / .mkv / .m3u8 / .mp3 ...) are opened directly by mpv,
 *  - everything else (YouTube and the many other sites yt-dlp supports) is resolved with yt-dlp
 *    (youtubedl-android), with NewPipe as a second chance for YouTube.
 * The resolved stream is cached for a short time so the player can resolve the same link again instantly.
 */
object NetworkStreamResolver {
    private const val TAG = "NetworkStreamResolver"
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val PREFS = "lumora_network_tab"
    private const val KEY_LAST_YTDLP_UPDATE = "last_ytdlp_update"
    private const val UPDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L

    private val cache = ConcurrentHashMap<String, Pair<Long, NetworkStream>>()
    private val directExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv", "mpg", "mpeg", "3gp", "ts", "m2ts", "m3u8", "mpd",
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac"
    )

    @Volatile private var ytdlpReady = false
    private val initLock = Any()

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

    fun isDirectMediaUrl(url: String): Boolean {
        val path = try { java.net.URI(url).path.orEmpty() } catch (_: Throwable) { url.substringBefore('?') }
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in directExtensions
    }

    fun cached(url: String): NetworkStream? =
        cache[url]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.second

    suspend fun resolve(context: Context, url: String, forceRefresh: Boolean = false): NetworkStream? =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) cached(url)?.let { return@withContext it }
            val appCtx = context.applicationContext

            val result: NetworkStream? = if (isDirectMediaUrl(url)) {
                directStream(url)
            } else {
                resolveWithYtDlp(appCtx, url)
                    ?: resolveWithNewPipe(appCtx, url)
            }
            if (result != null) cache[url] = System.currentTimeMillis() to result
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
            isDirect = true
        )
    }

    private fun ensureYtDlp(context: Context): Boolean {
        if (ytdlpReady) return true
        synchronized(initLock) {
            if (ytdlpReady) return true
            return try {
                YoutubeDL.getInstance().init(context)
                ytdlpReady = true
                maybeUpdateInBackground(context)
                true
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp init failed", t)
                false
            }
        }
    }

    /** Sites change often; keep the bundled yt-dlp fresh (at most every 12 hours, never blocks playback). */
    private fun maybeUpdateInBackground(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_YTDLP_UPDATE, 0L)
        if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) return
        Thread {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                prefs.edit().putLong(KEY_LAST_YTDLP_UPDATE, System.currentTimeMillis()).apply()
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp update failed", t)
            }
        }.apply { isDaemon = true; name = "lumora-ytdlp-update" }.start()
    }

    private fun resolveWithYtDlp(context: Context, url: String): NetworkStream? {
        if (!ensureYtDlp(context)) return null
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-single-json")
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", 20)
            // Prefer H.264 up to 1080p (plays on every phone); fall back to anything yt-dlp can offer.
            request.addOption(
                "-f",
                "bv*[height<=1080][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=1080]+ba/b[height<=1080]/bv*+ba/b"
            )
            val response = YoutubeDL.getInstance().execute(request, null, null)
            parseYtDlpJson(url, response.out)
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp could not resolve $url", t)
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
        if (requested != null && requested.length() > 0) {
            for (i in 0 until requested.length()) {
                val f = requested.optJSONObject(i) ?: continue
                val u = f.optString("url").ifBlank { null } ?: continue
                val hasVideo = f.optString("vcodec", "none") != "none"
                val hasAudio = f.optString("acodec", "none") != "none"
                if (hasVideo && videoUrl == null) {
                    videoUrl = u
                    headersOf(f).takeIf { it.isNotEmpty() }?.let { headers = it }
                    // a muxed single format already carries the sound
                    if (hasAudio) audioUrl = null
                } else if (hasAudio && !hasVideo && audioUrl == null) {
                    audioUrl = u
                }
            }
            if (videoUrl == null) {
                // audio-only link (music site): play the audio stream itself
                videoUrl = audioUrl
                audioUrl = null
            }
        }
        if (videoUrl == null) videoUrl = root.optString("url").ifBlank { null }
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
            isDirect = isLive || duration <= 0L
        )
    }

    /** Second chance for YouTube links when yt-dlp is unavailable or fails (uses the app's existing NewPipe stack). */
    private suspend fun resolveWithNewPipe(context: Context, url: String): NetworkStream? {
        val host = try { java.net.URI(url).host.orEmpty().lowercase() } catch (_: Throwable) { "" }
        val isYoutube = host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("youtube-nocookie.com")
        if (!isYoutube) return null
        return try {
            val pb = OnlineVideoService.resolvePlayback(url) ?: return null
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
}
