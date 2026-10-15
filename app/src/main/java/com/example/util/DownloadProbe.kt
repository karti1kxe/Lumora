package com.example.util

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One selectable quality of a link (video or audio) as the yt-dlp format list reports it. */
data class FormatOption(
    val id: String,
    val label: String,
    val ext: String,
    val codec: String,
    val sizeBytes: Long,
    /** true = the format already contains sound (no audio has to be merged). */
    val hasAudio: Boolean
)

data class LinkInfo(
    val title: String?,
    val uploader: String?,
    val durationSec: Long,
    val videoFormats: List<FormatOption>,
    val audioFormats: List<FormatOption>
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
    val estimatedBytes: Long
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

    private fun str(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "")

    private fun parse(root: JSONObject): LinkInfo {
        val duration = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
        val arr = root.optJSONArray("formats")
        val video = ArrayList<FormatOption>()
        val audio = ArrayList<FormatOption>()
        // best candidate per quality step (height / fps / codec family)
        val videoBest = LinkedHashMap<String, Pair<Double, FormatOption>>()
        val audioBest = LinkedHashMap<String, Pair<Double, FormatOption>>()

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

                if (hasV) {
                    val family = codecFamily(vcodec)
                    val fpsPart = if (fps > 30.0) fps.toInt().toString() else ""
                    val label = if (h > 0) "${h}p$fpsPart" + (if (w > 0) " (${w}x$h)" else "")
                    else str(f, "format_note").ifBlank { "Best available" }
                    val opt = FormatOption(id, label, ext.ifBlank { "mp4" }, family, size, hasA)
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
            audioFormats = audio
        )
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
