package com.example.util

import android.content.Context
import android.os.Build
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One selectable quality of a link (video or audio) as the yt-dlp format list reports it.
 * [id] is a single yt-dlp format id, or "videoId+audioId" when the video and the sound are separate streams: the app
 * downloads both and joins them itself with Android's built-in MediaMuxer (no ffmpeg, no re-encoding).
 */
data class FormatOption(
    val id: String,
    val label: String,
    val ext: String,
    val codec: String,
    val sizeBytes: Long,
    /** true = the format already contains sound (no audio has to be merged). */
    val hasAudio: Boolean,
    /** Frame height in pixels (0 = unknown / audio). */
    val height: Int = 0,
    val fps: Int = 0
)

data class LinkInfo(
    val title: String?,
    val uploader: String?,
    val durationSec: Long,
    val videoFormats: List<FormatOption>,
    val audioFormats: List<FormatOption>,
    val thumbnailUrl: String? = null
)

/** What the user chose in the download options sheet. */
data class DownloadRequest(
    val source: String,
    val title: String,
    val author: String?,
    val isAudio: Boolean,
    /** yt-dlp "-f" expression; null = the app's default (best up to 1080p / best audio). */
    val formatSpec: String?,
    /** Output container (mp4 / mkv / webm for video, mp3 / m4a / opus / wav for audio); null = default. */
    val container: String?,
    val estimatedBytes: Long,
    /** Cover / thumbnail of the link (embedded into downloaded audio, shown on the Download page). */
    val thumbnailUrl: String? = null,
    /** HTTP headers (Referer / User-Agent / ...) the link needs; used by media-plugin streams. */
    val headers: Map<String, String> = emptyMap()
)

/** Loads the quality list of a link with yt-dlp (used by the download options sheet). */
object DownloadProbe {

    suspend fun load(context: Context, url: String): Pair<LinkInfo?, String?> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        try {
            if (!NetworkStreamResolver.prepareYtDlpForDownload(app)) {
                return@withContext Pair<LinkInfo?, String?>(null, NetworkStreamResolver.lastError ?: "yt-dlp could not start")
            }
            val req = YoutubeDLRequest(url)
            req.addOption("--dump-single-json")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            req.addOption("--no-check-certificates")
            req.addOption("--socket-timeout", 20)
            val out = YoutubeDL.getInstance().execute(req, "lumora-opts-" + System.nanoTime(), null).out
            Pair<LinkInfo?, String?>(parse(JSONObject(out.trim())), null)
        } catch (t: Throwable) {
            val raw = t.message?.trim().orEmpty()
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val line = lines.lastOrNull { it.contains("ERROR", ignoreCase = true) } ?: lines.firstOrNull() ?: t.javaClass.simpleName
            Pair<LinkInfo?, String?>(null, line.removePrefix("ERROR:").trim().take(140))
        }
    }

    /**
     * The quality the app downloads when the user did not pick one ("Auto"): the best picture up to 1080p (highest
     * height, then highest frame rate, H.264 preferred because it plays everywhere). Picking the real best stream
     * matters: the old single-file selector ended up with the 360p file of YouTube, which looked blurry.
     */
    fun pickAutoVideo(info: LinkInfo): FormatOption? {
        val list = info.videoFormats
        if (list.isEmpty()) return null
        val capped = list.filter { it.height in 1..1080 }
        val pool = if (capped.isNotEmpty()) capped else list
        val topH = pool.maxOf { it.height }
        val topFps = pool.filter { it.height == topH }.maxOf { it.fps }
        return pool.filter { it.height == topH && it.fps == topFps }
            .minWithOrNull(compareBy<FormatOption> { familyRank(it.codec) }.thenByDescending { it.sizeBytes })
    }

    /** Best audio for an audio download: the highest bit rate, m4a first (its cover picture can be embedded). */
    fun pickAutoAudio(info: LinkInfo): FormatOption? =
        info.audioFormats.firstOrNull { it.ext.equals("m4a", true) } ?: info.audioFormats.firstOrNull()

    private fun str(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "")

    internal fun parse(root: JSONObject): LinkInfo {
        val duration = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
        val arr = root.optJSONArray("formats")
        val video = ArrayList<FormatOption>()
        val audio = ArrayList<FormatOption>()
        // best candidate per quality step (height / fps / codec family)
        val videoBest = LinkedHashMap<String, Pair<Double, FormatOption>>()
        val audioBest = LinkedHashMap<String, Pair<Double, FormatOption>>()
        // video-only streams (the high qualities of YouTube) and the best audio per container, joined later by the app
        val videoOnly = ArrayList<VideoOnly>()
        var bestM4a: AudioOnly? = null
        var bestWebm: AudioOnly? = null

        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val id = str(f, "format_id")
                if (id.isBlank()) continue
                val ext = str(f, "ext")
                if (ext == "mhtml" || str(f, "format_note").contains("storyboard", ignoreCase = true)) continue
                val vcodec = str(f, "vcodec")
                val acodec = str(f, "acodec")
                var hasV = vcodec.isNotEmpty() && vcodec != "none"
                var hasA = acodec.isNotEmpty() && acodec != "none"
                if (vcodec.isEmpty() && acodec.isEmpty()) {
                    // plain files: codecs are unknown, the extension tells what it is
                    if (ext in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")) hasA = true else { hasV = true; hasA = true }
                }
                val h = f.optInt("height", 0)
                val w = f.optInt("width", 0)
                val fps = f.optDouble("fps", 0.0).let { if (it.isNaN()) 0.0 else it }
                val tbr = f.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it }
                val abr = f.optDouble("abr", 0.0).let { if (it.isNaN()) 0.0 else it }
                var size = f.optLong("filesize", 0L)
                if (size <= 0L) size = f.optLong("filesize_approx", 0L)
                if (size <= 0L && tbr > 0.0 && duration > 0.0) size = (tbr * 125.0 * duration).toLong()

                val protocol = str(f, "protocol")
                val plainHttp = protocol.isEmpty() || protocol == "https" || protocol == "http" || protocol == "http_dash_segments"
                if (hasV && !hasA) {
                    if (plainHttp && h > 0) {
                        videoOnly.add(VideoOnly(id, ext, h, w, fps, codecFamily(vcodec), size, if (tbr > 0.0) tbr else size.toDouble()))
                    }
                    continue
                }
                if (hasA && !hasV && plainHttp) {
                    val kbpsNow = if (abr > 0.0) abr else tbr
                    val cand = AudioOnly(id, ext, kbpsNow, size)
                    val curM4a = bestM4a
                    val curWebm = bestWebm
                    if (ext == "m4a" && (curM4a == null || kbpsNow > curM4a.kbps)) bestM4a = cand
                    if ((ext == "webm" || ext == "opus") && (curWebm == null || kbpsNow > curWebm.kbps)) bestWebm = cand
                }
                if (hasV) {
                    val family = codecFamily(vcodec)
                    val fpsPart = if (fps > 30.0) fps.toInt().toString() else ""
                    val label = if (h > 0) "${h}p$fpsPart" + (if (w > 0) " (${w}x$h)" else "")
                    else str(f, "format_note").ifBlank { "Best available" }
                    val opt = FormatOption(id, label, ext.ifBlank { "mp4" }, family, size, hasA, h, fps.toInt())
                    val key = "$h|${fps.toInt()}|$family|$hasA"
                    val score = if (tbr > 0.0) tbr else size.toDouble()
                    val old = videoBest[key]
                    if (old == null || score > old.first) videoBest[key] = score to opt
                } else if (hasA) {
                    val kbps = if (abr > 0.0) abr else tbr
                    val label = if (kbps > 0.0) "${kbps.toInt()} kbps" else str(f, "format_note").ifBlank { "Audio" }
                    val opt = FormatOption(id, label, ext.ifBlank { "m4a" }, codecFamily(acodec), size, true)
                    val key = "${ext}|${kbps.toInt()}"
                    val old = audioBest[key]
                    if (old == null || kbps > old.first) audioBest[key] = kbps to opt
                }
            }
        }
        // every height / frame rate that only exists as separate video + audio streams becomes one selectable quality
        val m4aAudio = bestM4a
        val webmAudio = bestWebm
        val haveKeys = videoBest.values.map { heightOf(it.second.label) to fpsOf(it.second.label) }.toHashSet()
        val groups = videoOnly.groupBy { it.height to (if (it.fps > 30.0) it.fps.toInt() else 0) }
        for ((key, list) in groups) {
            if (key in haveKeys) continue
            val pick = list
                .mapNotNull { v ->
                    val isMp4 = v.ext == "mp4" || v.ext == "m4v"
                    val isWebm = v.ext == "webm"
                    when {
                        isMp4 && v.family == "AV1" && Build.VERSION.SDK_INT < 34 -> null
                        isMp4 && m4aAudio != null -> Triple(v, m4aAudio, "mp4")
                        isWebm && (v.family == "VP9" || v.family == "VP8") && webmAudio != null -> Triple(v, webmAudio, "webm")
                        else -> null
                    }
                }
                .minWithOrNull(compareBy<Triple<VideoOnly, AudioOnly, String>> { familyRank(it.first.family) }.thenByDescending { it.first.score })
                ?: continue
            val (v, a, container) = pick
            val fpsPart = if (v.fps > 30.0) v.fps.toInt().toString() else ""
            val label = "${v.height}p$fpsPart" + (if (v.width > 0) " (${v.width}x${v.height})" else "")
            val total = if (v.size > 0L && a.size > 0L) v.size + a.size else 0L
            videoBest["m|${key.first}|${key.second}"] = v.score to FormatOption(v.id + "+" + a.id, label, container, v.family, total, true, v.height, v.fps.toInt())
        }
        video.addAll(
            videoBest.values.map { it.second }.sortedWith(
                compareByDescending<FormatOption> { heightOf(it.label) }
                    .thenByDescending { fpsOf(it.label) }
                    .thenBy { it.codec }
            )
        )
        audio.addAll(audioBest.values.sortedByDescending { it.first }.map { it.second })
        return LinkInfo(
            title = str(root, "title").ifBlank { null },
            uploader = (str(root, "uploader").ifBlank { str(root, "channel") }).ifBlank { null },
            durationSec = duration.toLong(),
            videoFormats = video,
            audioFormats = audio,
            thumbnailUrl = str(root, "thumbnail").ifBlank { null }
        )
    }

    private class VideoOnly(
        val id: String, val ext: String, val height: Int, val width: Int, val fps: Double,
        val family: String, val size: Long, val score: Double
    )

    private class AudioOnly(val id: String, val ext: String, val kbps: Double, val size: Long)

    /** Smaller = preferred: H.264 plays everywhere, VP9 is the next best (1440p / 2160p exist only as VP9 / AV1). */
    private fun familyRank(family: String): Int = when (family) {
        "AVC" -> 0
        "VP9" -> 1
        "HEVC" -> 2
        else -> 3
    }

    private fun heightOf(label: String): Int = Regex("^(\\d+)p").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun fpsOf(label: String): Int = Regex("^\\d+p(\\d+)").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun codecFamily(codec: String): String {
        val c = codec.lowercase()
        return when {
            c.isEmpty() || c == "none" -> ""
            c.startsWith("avc") || c.startsWith("h264") -> "AVC"
            c.startsWith("vp09") || c.startsWith("vp9") -> "VP9"
            c.startsWith("av01") || c.startsWith("av1") -> "AV1"
            c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("h265") -> "HEVC"
            c.startsWith("mp4a") || c.startsWith("aac") -> "AAC"
            c.startsWith("opus") -> "Opus"
            else -> c.substringBefore('.').uppercase()
        }
    }
}
