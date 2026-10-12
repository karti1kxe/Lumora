package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

/**
 * One search result from Online Music search. Distinct from AudioTrackItem on purpose:
 * this is the raw search-result shape; OnlineMusicPanel converts it into an AudioTrackItem
 * only once the user taps it and a playable stream URL has been resolved.
 */
data class OnlineTrackResult(
    val videoUrl: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val uploadDateText: String? = null,
    val uploadDateEpochMs: Long? = null,
    val viewCount: Long = 0L,
    val detectedFormat: String? = null,
    val detectedAudioType: String? = null
)

/**
 * Direct audio stream URL and related recommendation tracks resolved from StreamInfo.
 */
data class StreamInfoWithRelated(
    val streamUrl: String?,
    val relatedTracks: List<OnlineTrackResult>
)

data class OnlineChannelTrackPage(
    val items: List<OnlineTrackResult>,
    val nextPage: Page?,
    val hasNextPage: Boolean
)

data class OnlineChannelPlaylist(
    val playlistUrl: String,
    val name: String,
    val thumbnailUrl: String?,
    val trackCountText: String?
)

data class OnlineChannelSection(
    val id: String,
    val label: String,
    val linkHandler: ListLinkHandler
)

data class OnlineChannelDetail(
    val channelUrl: String,
    val name: String,
    val handle: String?,
    val avatarUrl: String?,
    val subscriberCountText: String?,
    val description: String?,
    val availableSections: List<OnlineChannelSection>,
    val initialTracks: OnlineChannelTrackPage,
    val playlists: List<OnlineChannelPlaylist>
)

/**
 * Singleton — Online Music search + stream-URL resolution, backed by NewPipeExtractor against
 * YouTube. Follows this project's existing singleton-object-owns-its-concern pattern
 * (see brain.md Section 13) rather than introducing a Repository-interface layer.
 *
 * RISK NOTE FOR FUTURE MAINTAINERS / AI AGENTS:
 * NewPipeExtractor's exact method/class names have shifted slightly across versions in the
 * past (YouTube periodically changes its internal API, forcing NewPipeExtractor releases).
 * If this file fails to compile after a version bump in gradle/libs.versions.toml, the fix is
 * almost always a renamed method on StreamInfo / SearchExtractor / AudioStream — the overall
 * approach below (init one Downloader, search, resolve a StreamInfo, pick an AudioStream with
 * a direct URL) remains correct even if a method name moved. Do not reintroduce Media3/ExoPlayer
 * to "fix" this — playback of the resolved URL goes through the existing MediaPlayer-based
 * AudioPlaybackManager, unchanged.
 */
object OnlineMusicService {
    private const val TAG = "OnlineMusicService"
    private val httpClient = OkHttpClient.Builder().build()

    @Volatile
    private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpNewPipeDownloader(httpClient))
            initialized = true
        }
    }

    /** Minimal NewPipeExtractor Downloader implementation backed by OkHttp. */
    private class OkHttpNewPipeDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = OkRequest.Builder().url(request.url())
            request.headers().forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }
            val bodyBytes = request.dataToSend()
            when (request.httpMethod()) {
                "GET" -> builder.get()
                "HEAD" -> builder.head()
                else -> builder.method(
                    request.httpMethod(),
                    (bodyBytes ?: ByteArray(0)).toRequestBody(null)
                )
            }
            client.newCall(builder.build()).execute().use { resp ->
                val responseBody = resp.body?.string() ?: ""
                val headers: Map<String, List<String>> = resp.headers.toMultimap()
                return Response(resp.code, resp.message, headers, responseBody, resp.request.url.toString())
            }
        }
    }

    data class OnlineMusicPage(
        val items: List<OnlineTrackResult>,
        val nextPage: Page?,
        val hasNextPage: Boolean,
        val searchSuggestion: String? = null,
        val isCorrectedSearch: Boolean = false,
        val channelResults: List<OnlineChannelResult> = emptyList()
    )

    /** Search suggestion keywords for query autocompletion dropdown. */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            ensureInit()
            val extractor = ServiceList.YouTube.getSuggestionExtractor()
            extractor.suggestionList(query.trim()).take(8)
        } catch (e: Throwable) {
            Log.w(TAG, "getSearchSuggestions failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun parseUploadDate(dateWrapper: DateWrapper?, textualDate: String?): Long {
        if (dateWrapper != null) {
            val instant = try {
                dateWrapper.instant ?: dateWrapper.offsetDateTime()?.toInstant()
            } catch (_: Throwable) { null }
            if (instant != null) {
                return instant.toEpochMilli()
            }
        }
        if (!textualDate.isNullOrBlank()) {
            val text = textualDate.trim().lowercase(Locale.US)
            val now = System.currentTimeMillis()
            val num = Regex("\\d+").find(text)?.value?.toLongOrNull() ?: 1L
            return when {
                text.contains("second") -> now - num * 1000L
                text.contains("minute") -> now - num * 60 * 1000L
                text.contains("hour") -> now - num * 3600 * 1000L
                text.contains("day") -> now - num * 86400 * 1000L
                text.contains("week") -> now - num * 7 * 86400 * 1000L
                text.contains("month") -> now - num * 30 * 86400 * 1000L
                text.contains("year") -> now - num * 365 * 86400 * 1000L
                else -> 0L
            }
        }
        return 0L
    }

    /** Search YouTube for music with pagination support. */
    suspend fun searchMusicPage(query: String): Pair<SearchExtractor, OnlineMusicPage> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            val dummyExtractor = ServiceList.YouTube.getSearchExtractor(ServiceList.YouTube.searchQHFactory.fromQuery(""))
            return@withContext Pair(dummyExtractor, OnlineMusicPage(emptyList(), null, false))
        }
        ensureInit()
        val service = ServiceList.YouTube
        val queryHandler = service.searchQHFactory.fromQuery(query)
        val extractor: SearchExtractor = service.getSearchExtractor(queryHandler)
        extractor.fetchPage()
        val initialPage = extractor.initialPage
        val results = initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                val uploadEpoch = parseUploadDate(item.uploadDate, item.textualUploadDate)
                OnlineTrackResult(
                    videoUrl = item.url,
                    title = item.name?.takeIf { it.isNotBlank() } ?: query,
                    artist = item.uploaderName ?: "",
                    durationSec = item.duration.coerceAtLeast(0L),
                    thumbnailUrl = extractBestThumbnailUrl(item.thumbnails, item.url),
                    uploadDateText = item.textualUploadDate,
                    uploadDateEpochMs = uploadEpoch,
                    viewCount = item.viewCount.coerceAtLeast(0L)
                )
            }
        val channelResults = initialPage.items
            .filterIsInstance<ChannelInfoItem>()
            .map { it.toOnlineChannelResult() }
        val searchSuggestion = try { extractor.searchSuggestion } catch (_: Throwable) { null }
        val isCorrected = try { extractor.isCorrectedSearch } catch (_: Throwable) { false }
        val page = OnlineMusicPage(
            items = results,
            nextPage = if (initialPage.hasNextPage()) initialPage.nextPage else null,
            hasNextPage = initialPage.hasNextPage(),
            searchSuggestion = searchSuggestion,
            isCorrectedSearch = isCorrected,
            channelResults = channelResults
        )
        Pair(extractor, page)
    }

    private fun ChannelInfoItem.toOnlineChannelResult(): OnlineChannelResult {
        val subCount = try { this.subscriberCount } catch (_: Throwable) { -1L }
        val streamCount = try { this.streamCount } catch (_: Throwable) { -1L }
        return OnlineChannelResult(
            channelUrl = this.url,
            name = this.name?.takeIf { it.isNotBlank() } ?: "Channel",
            avatarUrl = try { extractBestThumbnailUrl(this.thumbnails, null) } catch (_: Throwable) { null },
            subscriberCountText = if (subCount >= 0) "${formatCount(subCount)} subscribers" else null,
            videoCountText = if (streamCount >= 0) "${formatCount(streamCount)} tracks" else null,
            description = try { this.description } catch (_: Throwable) { null }
        )
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    /** Fetch the next page of online music using an active SearchExtractor and Page token. */
    suspend fun fetchNextMusicPage(extractor: SearchExtractor, nextPage: Page): OnlineMusicPage = withContext(Dispatchers.IO) {
        try {
            val page = extractor.getPage(nextPage)
            val results = page.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val uploadEpoch = parseUploadDate(item.uploadDate, item.textualUploadDate)
                    OnlineTrackResult(
                        videoUrl = item.url,
                        title = item.name?.takeIf { it.isNotBlank() } ?: "",
                        artist = item.uploaderName ?: "",
                        durationSec = item.duration.coerceAtLeast(0L),
                        thumbnailUrl = extractBestThumbnailUrl(item.thumbnails, item.url),
                        uploadDateText = item.textualUploadDate,
                        uploadDateEpochMs = uploadEpoch,
                        viewCount = item.viewCount.coerceAtLeast(0L)
                    )
                }
            OnlineMusicPage(
                items = results,
                nextPage = if (page.hasNextPage()) page.nextPage else null,
                hasNextPage = page.hasNextPage()
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchNextMusicPage failed: ${e.message}", e)
            OnlineMusicPage(
                items = emptyList(),
                nextPage = null,
                hasNextPage = false
            )
        }
    }

    /** Search YouTube for a query and return lightweight track results. Never throws. */
    suspend fun search(query: String): List<OnlineTrackResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            ensureInit()
            val service = ServiceList.YouTube
            val queryHandler = service.searchQHFactory.fromQuery(query)
            val extractor: SearchExtractor = service.getSearchExtractor(queryHandler)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val uploadEpoch = parseUploadDate(item.uploadDate, item.textualUploadDate)
                    OnlineTrackResult(
                        videoUrl = item.url,
                        title = item.name?.takeIf { it.isNotBlank() } ?: query,
                        artist = item.uploaderName ?: "",
                        durationSec = item.duration.coerceAtLeast(0L),
                        thumbnailUrl = extractBestThumbnailUrl(item.thumbnails, item.url),
                        uploadDateText = item.textualUploadDate,
                        uploadDateEpochMs = uploadEpoch,
                        viewCount = item.viewCount.coerceAtLeast(0L)
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Resolve a direct, playable audio stream URL for a video page URL returned by [search].
     * Picks the highest-bitrate audio stream that exposes a direct URL (so it works with plain
     * android.media.MediaPlayer, matching how AudioPlaybackManager already plays every track).
     * If [videoUrl] is a Spotify or title-based track, resolves via music search match.
     */
    suspend fun resolveStreamUrl(
        videoUrl: String,
        title: String? = null,
        artist: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            // If it's a Spotify track or title-based query
            if (videoUrl.startsWith("spotify:") ||
                videoUrl.contains("spotify.com") ||
                (!videoUrl.contains("youtube.com") && !videoUrl.contains("youtu.be"))
            ) {
                val query = if (!title.isNullOrBlank()) {
                    "${title.trim()} ${(artist ?: "").trim()}".trim()
                } else {
                    videoUrl
                }
                val searchRes = search(query).firstOrNull()
                if (searchRes != null) {
                    return@withContext resolveStreamUrl(searchRes.videoUrl)
                }
            }

            val targetUrl = videoUrl.replace("music.youtube.com", "www.youtube.com")
            val info = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)
            val audioStreams: List<AudioStream> = info.audioStreams ?: emptyList()
            audioStreams
                .filter { it.isUrl }
                .maxByOrNull { it.averageBitrate }
                ?.content
        } catch (e: Exception) {
            Log.e(TAG, "resolveStreamUrl('$videoUrl') failed: ${e.message}", e)
            null
        }
    }

    /**
     * Helper to verify that a track is genuine audio/music content and strictly not
     * general video content like gameplays, vlogs, reactions, trailers, or movies.
     */
    fun isAudioOrMusicContent(title: String, uploaderName: String, durationSec: Long): Boolean {
        val lowerTitle = title.lowercase(Locale.ROOT)
        val lowerUploader = uploaderName.lowercase(Locale.ROOT)

        // Strict blacklist of non-audio / non-music indicators
        val nonMusicKeywords = listOf(
            "gameplay", "walkthrough", "playthrough", "trailer", "teaser", "full movie",
            "full episode", "season 1", "season 2", "season 3", "part 1", "part 2",
            "reaction", "reacting to", "unboxing", "vlog", "prank", "news", "review", "meme",
            "speedrun", "roast", "pubg", "free fire", "bgmi", "gta 5", "gta v", "minecraft",
            "roblox", "highlights", "stream replay", "stream highlights", "funny moments",
            "live stream replay", "behind the scenes", "tutorial", "how to", "interview",
            "podcast episode", "documentary", "short film", "anime episode", "web series"
        )
        if (nonMusicKeywords.any { lowerTitle.contains(it) || lowerUploader.contains(it) }) {
            return false
        }

        // Standard music tracks are between 30s and 12 minutes (720s)
        if (durationSec > 720) {
            return false
        }

        return true
    }

    /**
     * Resolve a direct audio stream URL and extract related/recommended music tracks
     * from the single NewPipeExtractor StreamInfo call.
     */
    suspend fun resolveStreamWithRelated(videoUrl: String): StreamInfoWithRelated = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val audioStreams: List<AudioStream> = info.audioStreams ?: emptyList()
            val streamUrl = audioStreams
                .filter { it.isUrl }
                .maxByOrNull { it.averageBitrate }
                ?.content

            val rawRelated = (info.relatedItems ?: emptyList())
            val relatedItems = rawRelated
                .filterIsInstance<StreamInfoItem>()
                .filter { item ->
                    item.url.isNotBlank() &&
                    item.url != videoUrl &&
                    item.streamType != org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM &&
                    item.streamType != org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM &&
                    (item.duration <= 0L || item.duration in 30..720) &&
                    isAudioOrMusicContent(item.name ?: "", item.uploaderName ?: info.uploaderName ?: "", item.duration)
                }
                .distinctBy { it.url }
                .map { item ->
                    OnlineTrackResult(
                        videoUrl = item.url,
                        title = item.name?.takeIf { it.isNotBlank() } ?: "Recommended Track",
                        artist = item.uploaderName ?: info.uploaderName ?: "",
                        durationSec = item.duration.coerceAtLeast(0L),
                        thumbnailUrl = extractBestThumbnailUrl(item.thumbnails, item.url)
                    )
                }

            val bestAudioForMeta = audioStreams.filter { it.isUrl }.maxByOrNull { it.averageBitrate } ?: audioStreams.firstOrNull()
            if (bestAudioForMeta != null) {
                val suffix = try { bestAudioForMeta.format?.suffix?.uppercase(Locale.ROOT) } catch (_: Throwable) { null }
                val name = try { bestAudioForMeta.format?.name?.uppercase(Locale.ROOT) } catch (_: Throwable) { null }
                val codec = try { bestAudioForMeta.codec?.uppercase(Locale.ROOT) } catch (_: Throwable) { null }
                val fmt = when {
                    suffix?.isNotBlank() == true -> if (suffix == "WEBMA") "OPUS" else suffix
                    name?.isNotBlank() == true -> if (name.contains("OPUS")) "OPUS" else name
                    codec?.contains("MP4A") == true -> "M4A"
                    codec?.contains("OPUS") == true -> "OPUS"
                    else -> "M4A"
                }
                val itag = try { bestAudioForMeta.itagItem } catch (_: Throwable) { null }
                val channels = itag?.audioChannels ?: 2
                val aType = when (channels) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1 Surround"
                    8 -> "7.1 Surround"
                    else -> if (channels > 0) "Stereo" else ""
                }
                setCachedAudioMeta(videoUrl, fmt, aType)
            }

            StreamInfoWithRelated(streamUrl = streamUrl, relatedTracks = relatedItems)
        } catch (e: Exception) {
            Log.e(TAG, "resolveStreamWithRelated('$videoUrl') failed: ${e.message}", e)
            StreamInfoWithRelated(streamUrl = null, relatedTracks = emptyList())
        }
    }

    private val detectedAudioMetaCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    fun getCachedAudioMeta(url: String): Pair<String, String>? {
        val clean = url.removePrefix("online://")
        return detectedAudioMetaCache[url] ?: detectedAudioMetaCache[clean]
    }

    fun setCachedAudioMeta(url: String, format: String, audioType: String) {
        val clean = url.removePrefix("online://")
        val pair = Pair(format, audioType)
        detectedAudioMetaCache[url] = pair
        detectedAudioMetaCache[clean] = pair
    }

    fun detectFormatFromTitle(title: String): String? {
        val upper = title.uppercase(Locale.ROOT)
        val regex = Regex("\\b(FLAC|WAV|MP3|M4A|AAC|OGG|OPUS|ALAC|AIFF|DSD|LOSSLESS)\\b")
        val match = regex.find(upper)?.value
        return when (match) {
            "LOSSLESS" -> "FLAC"
            else -> match
        }
    }

    fun detectAudioTypeFromTitle(title: String): String? {
        val upper = title.uppercase(Locale.ROOT)
        return when {
            upper.contains("8D") || upper.contains("8-D") -> "8D"
            upper.contains("16D") || upper.contains("16-D") -> "16D"
            upper.contains("9D") || upper.contains("9-D") -> "9D"
            upper.contains("4D") || upper.contains("4-D") -> "4D"
            upper.contains("3D") || upper.contains("3-D") -> "3D"
            upper.contains("BINAURAL") -> "Binaural"
            upper.contains("DOLBY") || upper.contains("ATMOS") -> "Dolby Atmos"
            upper.contains("SPATIAL") -> "Spatial"
            upper.contains("5.1") -> "5.1 Surround"
            upper.contains("7.1") -> "7.1 Surround"
            upper.contains("MONO") -> "Mono"
            upper.contains("STEREO") -> "Stereo"
            else -> null
        }
    }

    suspend fun probeAudioMetadata(videoUrl: String, title: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val cached = getCachedAudioMeta(videoUrl)
        if (cached != null) return@withContext cached

        val titleFormat = detectFormatFromTitle(title)
        val titleAudioType = detectAudioTypeFromTitle(title)

        var streamFormat: String? = null
        var streamAudioType: String? = null

        try {
            ensureInit()
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val audioStreams: List<AudioStream> = info.audioStreams ?: emptyList()
            val bestAudio = audioStreams.filter { it.isUrl }.maxByOrNull { it.averageBitrate } ?: audioStreams.firstOrNull()

            if (bestAudio != null) {
                val suffix = try { bestAudio.format?.suffix?.uppercase(Locale.ROOT) } catch (_: Throwable) { null }
                val name = try { bestAudio.format?.name?.uppercase(Locale.ROOT) } catch (_: Throwable) { null }
                val codec = try { bestAudio.codec?.uppercase(Locale.ROOT) } catch (_: Throwable) { null }

                streamFormat = when {
                    suffix?.isNotBlank() == true -> if (suffix == "WEBMA") "OPUS" else suffix
                    name?.isNotBlank() == true -> if (name.contains("OPUS")) "OPUS" else name
                    codec?.contains("MP4A") == true -> "M4A"
                    codec?.contains("OPUS") == true -> "OPUS"
                    else -> "M4A"
                }

                val itag = try { bestAudio.itagItem } catch (_: Throwable) { null }
                val channels = itag?.audioChannels ?: 2
                streamAudioType = when (channels) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1 Surround"
                    8 -> "7.1 Surround"
                    else -> if (channels > 0) "Stereo" else null
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "probeAudioMetadata failed for '$videoUrl': ${e.message}")
        }

        val finalFormat = titleFormat ?: streamFormat ?: "M4A"
        val finalAudioType = titleAudioType ?: streamAudioType ?: "Stereo"

        setCachedAudioMeta(videoUrl, finalFormat, finalAudioType)
        Pair(finalFormat, finalAudioType)
    }

    private fun StreamInfoItem.toOnlineTrackResult(): OnlineTrackResult {
        val uploadEpoch = try {
            val d = this.uploadDate
            if (d != null) {
                try { d.offsetDateTime().toInstant().toEpochMilli() } catch (_: Throwable) { 0L }
            } else 0L
        } catch (_: Throwable) { 0L }

        return OnlineTrackResult(
            videoUrl = this.url,
            title = this.name?.takeIf { it.isNotBlank() } ?: "Audio Track",
            artist = this.uploaderName ?: "",
            durationSec = this.duration.coerceAtLeast(0L),
            thumbnailUrl = extractBestThumbnailUrl(this.thumbnails, this.url),
            uploadDateText = this.textualUploadDate,
            uploadDateEpochMs = uploadEpoch,
            viewCount = this.viewCount.coerceAtLeast(0L)
        )
    }

    suspend fun fetchChannelDetail(channelUrl: String): OnlineChannelDetail? = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val service = ServiceList.YouTube
            val info = service.getChannelExtractor(channelUrl)
            info.fetchPage()

            val tabs = try { info.tabs ?: emptyList() } catch (_: Throwable) { emptyList() }
            val sections = mutableListOf<OnlineChannelSection>()
            var videosTab: ListLinkHandler? = null
            var playlistsTab: ListLinkHandler? = null

            tabs.forEach { tab ->
                val filters = try { tab.contentFilters } catch (_: Throwable) { emptyList<String>() }
                when {
                    filters.contains(ChannelTabs.VIDEOS) -> {
                        videosTab = tab
                        sections.add(OnlineChannelSection("videos", "Tracks", tab))
                    }
                    filters.contains(ChannelTabs.PLAYLISTS) -> {
                        playlistsTab = tab
                        sections.add(OnlineChannelSection("playlists", "Playlists", tab))
                    }
                }
            }

            val initialTracks = videosTab?.let { handler ->
                try {
                    val tabExtractor = service.getChannelTabExtractor(handler)
                    tabExtractor.fetchPage()
                    val page = tabExtractor.initialPage
                    val items = page.items.filterIsInstance<StreamInfoItem>()
                        .filter { it.duration > 0L }
                        .map { it.toOnlineTrackResult() }
                    OnlineChannelTrackPage(
                        items = items,
                        nextPage = if (page.hasNextPage()) page.nextPage else null,
                        hasNextPage = page.hasNextPage()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Channel tracks tab failed: ${e.message}", e)
                    null
                }
            } ?: OnlineChannelTrackPage(emptyList(), null, false)

            val playlists = playlistsTab?.let { handler ->
                try {
                    val tabExtractor = service.getChannelTabExtractor(handler)
                    tabExtractor.fetchPage()
                    tabExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().map { pl ->
                        val streamCount = try { pl.streamCount } catch (_: Throwable) { -1L }
                        OnlineChannelPlaylist(
                            playlistUrl = pl.url,
                            name = pl.name?.takeIf { it.isNotBlank() } ?: "Playlist",
                            thumbnailUrl = try { extractBestThumbnailUrl(pl.thumbnails, null) } catch (_: Throwable) { null },
                            trackCountText = if (streamCount >= 0) "$streamCount tracks" else null
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Channel playlists tab failed: ${e.message}", e)
                    emptyList()
                }
            } ?: emptyList()

            val subCount = try { info.subscriberCount } catch (_: Throwable) { -1L }
            val avatar = try { extractBestThumbnailUrl(info.avatars, null) } catch (_: Throwable) { null }
            val desc = try { info.description?.takeIf { it.isNotBlank() } } catch (_: Throwable) { null }

            OnlineChannelDetail(
                channelUrl = channelUrl,
                name = info.name?.takeIf { it.isNotBlank() } ?: "Channel",
                handle = null,
                avatarUrl = avatar,
                subscriberCountText = if (subCount >= 0) "${formatCount(subCount)} subscribers" else null,
                description = desc,
                availableSections = sections,
                initialTracks = initialTracks,
                playlists = playlists
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchChannelDetail failed for $channelUrl: ${e.message}", e)
            null
        }
    }

    suspend fun fetchNextChannelTrackPage(section: OnlineChannelSection, nextPage: Page): OnlineChannelTrackPage = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val service = ServiceList.YouTube
            val tabExtractor = service.getChannelTabExtractor(section.linkHandler)
            val page = tabExtractor.getPage(nextPage)
            val items = page.items.filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0L }
                .map { it.toOnlineTrackResult() }
            OnlineChannelTrackPage(
                items = items,
                nextPage = if (page.hasNextPage()) page.nextPage else null,
                hasNextPage = page.hasNextPage()
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchNextChannelTrackPage failed: ${e.message}", e)
            OnlineChannelTrackPage(emptyList(), null, false)
        }
    }

    suspend fun fetchPlaylistTracks(playlistUrl: String): List<OnlineTrackResult> = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val targetUrl = playlistUrl.replace("music.youtube.com", "www.youtube.com")
            val extractor = ServiceList.YouTube.getPlaylistExtractor(targetUrl)
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0L }
                .map { it.toOnlineTrackResult() }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPlaylistTracks failed for $playlistUrl: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Resolves an ultra high-quality thumbnail URL for YouTube or Spotify tracks.
     */
    fun getHighQualityThumbnailUrl(originalThumb: String?, videoUrl: String?): String {
        val videoIdFromUrl = if (!videoUrl.isNullOrBlank()) {
            when {
                videoUrl.contains("v=") -> videoUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
                videoUrl.contains("youtu.be/") -> videoUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                videoUrl.contains("/shorts/") -> videoUrl.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
                videoUrl.contains("/live/") -> videoUrl.substringAfter("/live/").substringBefore("?").substringBefore("&")
                else -> null
            }
        } else null

        val videoIdFromThumb = if (!originalThumb.isNullOrBlank()) {
            when {
                originalThumb.contains("/vi/") -> originalThumb.substringAfter("/vi/").substringBefore("/")
                originalThumb.contains("/vi_webp/") -> originalThumb.substringAfter("/vi_webp/").substringBefore("/")
                originalThumb.contains("img.youtube.com/vi/") -> originalThumb.substringAfter("img.youtube.com/vi/").substringBefore("/")
                else -> null
            }
        } else null

        val finalVideoId = videoIdFromUrl?.takeIf { it.isNotBlank() && it.length in 8..16 }
            ?: videoIdFromThumb?.takeIf { it.isNotBlank() && it.length in 8..16 }

        if (!finalVideoId.isNullOrBlank()) {
            return "https://i.ytimg.com/vi/$finalVideoId/maxresdefault.jpg"
        }

        if (!originalThumb.isNullOrBlank()) {
            if (originalThumb.contains("scdn.co/image/") || originalThumb.contains("spotifycdn.com/image/")) {
                return originalThumb
                    .replace("00001e02", "0000b273")
                    .replace("00004851", "0000b273")
                    .replace("00000002", "0000b273")
            }
            if (originalThumb.contains("googleusercontent.com") || originalThumb.contains("yt3.ggpht.com")) {
                return originalThumb
                    .replace(Regex("=s\\d+.*"), "=s800")
                    .replace(Regex("=w\\d+-h\\d+.*"), "=w800-h800")
            }
            if (originalThumb.contains("/hqdefault.jpg")) {
                return originalThumb.replace("/hqdefault.jpg", "/maxresdefault.jpg")
            }
            if (originalThumb.contains("/mqdefault.jpg")) {
                return originalThumb.replace("/mqdefault.jpg", "/maxresdefault.jpg")
            }
            if (originalThumb.contains("/default.jpg")) {
                return originalThumb.replace("/default.jpg", "/maxresdefault.jpg")
            }
            return originalThumb
        }
        return ""
    }

    fun extractBestThumbnailUrl(images: List<org.schabi.newpipe.extractor.Image>?, videoUrl: String? = null): String? {
        if (images.isNullOrEmpty()) {
            return videoUrl?.let { getHighQualityThumbnailUrl(null, it).takeIf { u -> u.isNotBlank() } }
        }
        val best = images.maxByOrNull { (it.width.takeIf { w -> w > 0 } ?: 0) * (it.height.takeIf { h -> h > 0 } ?: 0) }
            ?: images.lastOrNull()
            ?: images.firstOrNull()
        val rawUrl = best?.url ?: return null
        return getHighQualityThumbnailUrl(rawUrl, videoUrl).takeIf { it.isNotBlank() } ?: rawUrl
    }

    /**
     * Checks if a URL or text contains a YouTube Music or Spotify link.
     */
    fun isMusicPlaylistOrTrackUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("music.youtube.com") ||
                lower.contains("youtube.com/playlist") ||
                lower.contains("youtube.com/watch") ||
                lower.contains("youtu.be/") ||
                lower.contains("open.spotify.com") ||
                lower.contains("spotify.link") ||
                lower.startsWith("spotify:")
    }

    /**
     * Extracts an HTTP/HTTPS URL from raw shared text if present.
     */
    fun extractUrlFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = "(https?://[^\\s]+)".toRegex()
        return regex.find(text)?.value?.trim() ?: if (text.startsWith("http://") || text.startsWith("https://")) text.trim() else null
    }

    /**
     * Fetches complete playlist metadata, track list, and 4 high-res preview thumbnails
     * for YouTube Music, YouTube, and Spotify shared links.
     */
    suspend fun fetchPlaylistDetails(rawUrl: String): OnlinePlaylistDetail? = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val url = rawUrl.trim()
            val isSpotify = url.contains("spotify.com") || url.startsWith("spotify:") || url.contains("spotify.link")
            if (isSpotify) {
                return@withContext fetchSpotifyPlaylistDetails(url)
            }

            // YouTube / YouTube Music playlist
            val listId = if (url.contains("list=")) {
                url.substringAfter("list=").substringBefore("&").substringBefore("#")
            } else null

            val targetUrl = if (listId != null) {
                "https://www.youtube.com/playlist?list=$listId"
            } else {
                url.replace("music.youtube.com", "www.youtube.com")
            }

            val extractor = ServiceList.YouTube.getPlaylistExtractor(targetUrl)
            extractor.fetchPage()

            val plTitle = extractor.name?.takeIf { it.isNotBlank() } ?: "Shared Playlist"
            val plAuthor = extractor.uploaderName?.takeIf { it.isNotBlank() } ?: "YouTube Music"

            val allItems = mutableListOf<StreamInfoItem>()
            val firstPageItems = extractor.initialPage.items.filterIsInstance<StreamInfoItem>().filter { it.duration > 0L }
            allItems.addAll(firstPageItems)

            var currentPage = extractor.initialPage
            var pageCount = 1
            while (currentPage.hasNextPage() && pageCount < 3) {
                try {
                    currentPage = extractor.getPage(currentPage.nextPage)
                    val more = currentPage.items.filterIsInstance<StreamInfoItem>().filter { it.duration > 0L }
                    if (more.isEmpty()) break
                    allItems.addAll(more)
                    pageCount++
                } catch (_: Throwable) {
                    break
                }
            }

            val trackResults = allItems.map { it.toOnlineTrackResult() }

            // High quality thumbnails: pick 4 random distinct tracks that have thumbnails
            val shuffled = trackResults.shuffled()
            val randomFour = shuffled.take(4).map { track ->
                getHighQualityThumbnailUrl(track.thumbnailUrl, track.videoUrl)
            }.filter { it.isNotBlank() }

            val cover = extractBestThumbnailUrl(extractor.thumbnails, null)

            OnlinePlaylistDetail(
                playlistUrl = url,
                title = plTitle,
                author = plAuthor,
                trackCountText = "${trackResults.size} tracks",
                coverUrl = cover,
                tracks = trackResults,
                previewThumbnails = randomFour,
                sourcePlatform = if (url.contains("music.youtube.com")) "YouTube Music" else "YouTube"
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchPlaylistDetails failed for $rawUrl: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchSpotifyPlaylistDetails(url: String): OnlinePlaylistDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.substringBefore("?").trim()
            val id = when {
                cleanUrl.contains("/playlist/") -> cleanUrl.substringAfter("/playlist/").substringBefore("/")
                cleanUrl.contains("/album/") -> cleanUrl.substringAfter("/album/").substringBefore("/")
                cleanUrl.contains("/track/") -> cleanUrl.substringAfter("/track/").substringBefore("/")
                else -> cleanUrl.substringAfterLast("/")
            }
            val type = if (cleanUrl.contains("/album/")) "album" else "playlist"
            val embedUrl = "https://open.spotify.com/embed/$type/$id"

            val okReq = OkRequest.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()

            val html = httpClient.newCall(okReq).execute().use { resp ->
                resp.body?.string() ?: ""
            }

            val scriptMatch = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>").find(html)
            if (scriptMatch != null) {
                val jsonStr = scriptMatch.groupValues[1]
                val root = JSONObject(jsonStr)
                val entity = root.getJSONObject("props")
                    .getJSONObject("pageProps")
                    .getJSONObject("state")
                    .getJSONObject("data")
                    .getJSONObject("entity")

                val title = entity.optString("name").ifBlank { entity.optString("title", "Spotify Playlist") }
                val author = entity.optString("subtitle", "Spotify")
                val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                val baseCover = coverSources?.optJSONObject(0)?.optString("url")
                val highResCover = baseCover?.replace("00001e02", "0000b273")?.replace("00004851", "0000b273")

                val rawTrackList = entity.optJSONArray("trackList") ?: JSONArray()
                val trackResults = mutableListOf<OnlineTrackResult>()

                for (i in 0 until rawTrackList.length()) {
                    val t = rawTrackList.getJSONObject(i)
                    val tTitle = t.optString("title", "Track $i")
                    val tArtist = t.optString("subtitle", author)
                    val durMs = t.optLong("duration", 0L)
                    val uriStr = t.optString("uri", "")

                    trackResults.add(
                        OnlineTrackResult(
                            videoUrl = if (uriStr.isNotBlank()) uriStr else "spotify:track:$i",
                            title = tTitle,
                            artist = tArtist,
                            durationSec = (durMs / 1000L).coerceAtLeast(0L),
                            thumbnailUrl = highResCover ?: baseCover
                        )
                    )
                }

                // Random 4 track thumbnails:
                // Fetch up to 4 track oembed thumbnails asynchronously for maximum album fidelity
                val randomTracks = if (trackResults.size >= 4) trackResults.shuffled().take(4) else trackResults
                val randomFourThumbs = mutableListOf<String>()
                for (track in randomTracks) {
                    val trackId = track.videoUrl.removePrefix("spotify:track:").substringAfterLast(":")
                    if (trackId.isNotBlank() && !trackId.startsWith("http")) {
                        try {
                            val oembedReq = OkRequest.Builder()
                                .url("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$trackId")
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            val oembedResp = httpClient.newCall(oembedReq).execute().use { it.body?.string() ?: "" }
                            val oembedJson = JSONObject(oembedResp)
                            val thumb = oembedJson.optString("thumbnail_url")
                            if (thumb.isNotBlank()) {
                                randomFourThumbs.add(thumb.replace("00001e02", "0000b273").replace("00004851", "0000b273"))
                            }
                        } catch (_: Throwable) {}
                    }
                }
                while (randomFourThumbs.size < 4 && highResCover != null) {
                    randomFourThumbs.add(highResCover)
                }

                return@withContext OnlinePlaylistDetail(
                    playlistUrl = url,
                    title = title,
                    author = author,
                    trackCountText = "${trackResults.size} tracks",
                    coverUrl = highResCover ?: baseCover,
                    tracks = trackResults,
                    previewThumbnails = randomFourThumbs.take(4),
                    sourcePlatform = "Spotify"
                )
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "fetchSpotifyPlaylistDetails failed: ${e.message}", e)
            null
        }
    }
}

/**
 * Representation of an external shared playlist (YouTube Music, Spotify, YouTube).
 */
data class OnlinePlaylistDetail(
    val playlistUrl: String,
    val title: String,
    val author: String = "",
    val trackCountText: String? = null,
    val coverUrl: String? = null,
    val tracks: List<OnlineTrackResult> = emptyList(),
    val previewThumbnails: List<String> = emptyList(),
    val sourcePlatform: String = "YouTube Music"
)
