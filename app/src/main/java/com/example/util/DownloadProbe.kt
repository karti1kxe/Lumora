package com.example.util

import android.content.Context

/**
 * One selectable quality of a link (video or audio). [id] is a single format id, or "videoId+audioId" when the
 * video and the sound are separate streams.
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
    val fps: Int = 0,
    /**
     * Full yt-dlp `--format` string of this quality, with fallbacks (a comma separates the video and the audio stream
     * that are fetched one after another and joined afterwards). null = [id] alone is used.
     */
    val spec: String? = null
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
    /** Format id; null = the app's default quality. */
    val formatSpec: String?,
    /** Output container (mp4 / mkv / webm for video, mp3 / m4a / opus / wav for audio); null = default. */
    val container: String?,
    val estimatedBytes: Long,
    /** Cover / thumbnail of the link (embedded into downloaded audio, shown on the Download page). */
    val thumbnailUrl: String? = null,
    /** HTTP headers (Referer / User-Agent / ...) the link needs; used by media-plugin streams. */
    val headers: Map<String, String> = emptyMap(),
    /** Subtitle tracks the provider lists for this stream; attached to the downloaded video when it is finished. */
    val subtitles: List<com.example.util.media.MediaSubtitle> = emptyList()
)

/**
 * Loads the quality list of a link for the download options sheet. Page links (YouTube, extractor sites, ...) are
 * read with the bundled yt-dlp; links it does not handle keep the sheet's "not available" state (default quality).
 */
object DownloadProbe {

    suspend fun load(context: Context, url: String): Pair<LinkInfo?, String?> =
        com.example.ytdlp.YtdlpProbe.load(context.applicationContext, url)
}
