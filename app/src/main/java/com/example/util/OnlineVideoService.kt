package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale

/** One selectable video quality of an online video (e.g. "1080p60"). [url] is a direct stream URL. */
data class OnlineVideoQuality(
    val label: String,
    val height: Int,
    val fps: Int,
    val url: String,
    /** true = adaptive video-only stream (needs a separate audio track), false = muxed (audio inside). */
    val isVideoOnly: Boolean
)

data class OnlineAudioStreamInfo(
    val url: String,
    val title: String,
    val language: String,
    val bitrate: Int,
    val isOriginal: Boolean
)

data class OnlineChapterInfo(val title: String, val startMs: Long)

/** Everything the player needs to play one online video. */
data class OnlineVideoPlayback(
    val pageUrl: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val qualities: List<OnlineVideoQuality>,
    val audios: List<OnlineAudioStreamInfo>,
    val chapters: List<OnlineChapterInfo>
)

/**
 * Online Video — resolves YouTube watch URLs to direct streams through NewPipeExtractor, exactly like
 * [OnlineMusicService] does for audio (same Downloader / same single NewPipe.init()). Search, channel search
 * and paging are shared with OnlineMusicService (it returns OnlineTrackResult for any video).
 */
object OnlineVideoService {
    private const val TAG = "OnlineVideoService"

    /** Path prefix that marks a VideoItem as an online video (the rest is the YouTube watch URL). */
    const val ONLINE_VIDEO_PATH_PREFIX = "online-video://"

    fun toWatchUrl(path: String): String = path.removePrefix(ONLINE_VIDEO_PATH_PREFIX)

    private val playbackCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, OnlineVideoPlayback>>()
    // YouTube stream URLs expire after a few hours; keep a short-lived cache only for fast re-opening.
    private const val CACHE_TTL_MS = 20 * 60 * 1000L

    suspend fun resolvePlayback(watchUrl: String, forceRefresh: Boolean = false): OnlineVideoPlayback? =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                playbackCache[watchUrl]?.let { (t, v) ->
                    if (System.currentTimeMillis() - t < CACHE_TTL_MS) return@withContext v
                }
            }
            try {
                OnlineMusicService.ensureInit()
                val target = watchUrl.replace("music.youtube.com", "www.youtube.com")
                val info = StreamInfo.getInfo(ServiceList.YouTube, target)

                // ---- video qualities: best stream per (height, fps) ----
                val qualities = LinkedHashMap<String, OnlineVideoQuality>()
                fun add(v: org.schabi.newpipe.extractor.stream.VideoStream, videoOnly: Boolean) {
                    if (!v.isUrl) return
                    val h = try { v.height } catch (_: Throwable) { 0 }
                    if (h <= 0) return
                    val fps = try { v.fps } catch (_: Throwable) { 30 }
                    val label = if (fps > 30) "${h}p$fps" else "${h}p"
                    val prev = qualities[label]
                    // prefer mp4/avc (best hw-decoder support) over webm when both exist for the same label
                    val isMp4 = try { v.format?.suffix.equals("mp4", true) } catch (_: Throwable) { false }
                    val prevIsMp4 = prev?.url?.contains("mime=video%2Fmp4") == true || prev?.url?.contains("mime=video/mp4") == true
                    if (prev == null || (isMp4 && !prevIsMp4) || (prev.isVideoOnly && !videoOnly)) {
                        qualities[label] = OnlineVideoQuality(label, h, fps, v.content, videoOnly)
                    }
                }
                (info.videoOnlyStreams ?: emptyList()).forEach { add(it, true) }
                // muxed streams are usually capped at 360p/720p; only used when no better adaptive label exists
                (info.videoStreams ?: emptyList()).forEach { s ->
                    val h = try { s.height } catch (_: Throwable) { 0 }
                    val fps = try { s.fps } catch (_: Throwable) { 30 }
                    val label = if (fps > 30) "${h}p$fps" else "${h}p"
                    if (!qualities.containsKey(label)) add(s, false)
                }
                val sortedQualities = qualities.values.sortedWith(
                    compareByDescending<OnlineVideoQuality> { it.height }.thenByDescending { it.fps }
                )

                // ---- audio tracks: every available language (original, dubbed, YouTube auto-dubbed) ----
                val byTrack = LinkedHashMap<String, org.schabi.newpipe.extractor.stream.AudioStream>()
                (info.audioStreams ?: emptyList()).filter { it.isUrl }.forEach { a ->
                    val trackId = try { a.audioTrackId } catch (_: Throwable) { null }
                    val loc = try { a.audioLocale?.toLanguageTag() } catch (_: Throwable) { null }
                    val key = trackId ?: loc ?: "default"
                    val prev = byTrack[key]
                    if (prev == null || a.averageBitrate > prev.averageBitrate) byTrack[key] = a
                }
                val audios = byTrack.values.map { a ->
                    val name = try { a.audioTrackName } catch (_: Throwable) { null }
                    val loc = try { a.audioLocale } catch (_: Throwable) { null }
                    val typeName = try { a.audioTrackType?.name } catch (_: Throwable) { null }
                    val lang = loc?.language ?: ""
                    val langName = loc?.getDisplayLanguage(Locale.getDefault())?.takeIf { it.isNotBlank() }
                    val title = when {
                        !name.isNullOrBlank() -> name
                        !langName.isNullOrBlank() -> langName
                        else -> "Original"
                    }
                    val suffix = when (typeName) {
                        "ORIGINAL" -> " (original)"
                        "DUBBED" -> " (dubbed)"
                        "DESCRIPTIVE" -> " (descriptive)"
                        else -> ""
                    }
                    OnlineAudioStreamInfo(
                        url = a.content,
                        title = if (name != null && (name.contains("original", true) || name.contains("dub", true) || name.contains("auto", true))) title else title + suffix,
                        language = lang,
                        bitrate = a.averageBitrate,
                        isOriginal = typeName == "ORIGINAL" || typeName == null && byTrack.size == 1
                    )
                }.sortedWith(compareByDescending<OnlineAudioStreamInfo> { it.isOriginal }.thenByDescending { it.bitrate })

                // ---- chapters ----
                val chapters = try {
                    (info.streamSegments ?: emptyList()).map {
                        OnlineChapterInfo(it.title ?: "", it.startTimeSeconds.toLong() * 1000L)
                    }
                } catch (_: Throwable) { emptyList() }

                if (sortedQualities.isEmpty()) return@withContext null
                val result = OnlineVideoPlayback(
                    pageUrl = watchUrl,
                    title = info.name ?: "",
                    uploader = info.uploaderName ?: "",
                    durationSec = info.duration.coerceAtLeast(0L),
                    qualities = sortedQualities,
                    audios = audios,
                    chapters = chapters
                )
                playbackCache[watchUrl] = System.currentTimeMillis() to result
                result
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "resolvePlayback('$watchUrl') failed: ${e.message}", e)
                null
            }
        }

    fun cachedResolutionLabel(watchUrl: String): String? = playbackCache[watchUrl]?.second?.qualities?.firstOrNull()?.label

    /** Picks the default quality: the user's last choice if available, else the best one up to 1080p. */
    fun pickDefaultQuality(qualities: List<OnlineVideoQuality>, preferredLabel: String?): OnlineVideoQuality? {
        if (qualities.isEmpty()) return null
        preferredLabel?.let { l -> qualities.firstOrNull { it.label == l }?.let { return it } }
        return qualities.firstOrNull { it.height <= 1080 } ?: qualities.last()
    }

    /** Highest quality not above [maxHeight] for a "like 720p" request — used when preferred label is missing. */
    fun isLiveOrShort(item: StreamInfoItem): Boolean =
        item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM ||
            (item.duration in 1..60)
}

/** Best quality label (e.g. "1080p") if this video was already resolved in this session — shown as the Resolution field. */
fun OnlineVideoService.cachedBestLabel(watchUrl: String): String? = cachedResolutionLabel(watchUrl)
