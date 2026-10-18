package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.DownloadGlassSheet
import com.example.ui.components.HighResImage
import com.example.ui.components.LumoraExpressiveLoadingIndicator
import com.example.ui.components.OnlineQuickActionBar
import com.example.ui.components.StyledIcon
import com.example.ui.components.VideoItemCard
import com.example.ui.components.VisibleFields
import com.example.ui.components.shareOnlineLink
import com.example.ui.components.swipeLeftForActions
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.media.MediaDirectLink
import com.example.util.media.MediaInfo
import com.example.util.media.MediaLibraryStore
import com.example.util.media.MediaLink
import com.example.util.media.MediaPlayback
import com.example.util.media.MediaPluginInfo
import com.example.util.media.PluginException
import com.example.util.media.PluginPlaybackSession
import com.example.util.media.PluginQueuedEpisode
import com.example.util.media.PluginQueuedEpisodes
import com.example.util.media.PluginQualityChoice
import com.example.util.media.MediaPost
import com.example.util.media.MediaProviderRepository
import com.example.util.media.MediaSavedVideo
import com.example.util.media.MediaHistoryStore
import com.example.util.media.MediaWatchedTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the Media section currently has open. It lives outside composition on purpose: after the player is closed the
 * same title (with the same season / quality) is shown again, and the Media tab that was open is selected again.
 */
object MediaDetailState {
    var plugin by androidx.compose.runtime.mutableStateOf<MediaPluginInfo?>(null)
    var post by androidx.compose.runtime.mutableStateOf<MediaPost?>(null)
    var lastSection by mutableIntStateOf(0)
    /** The provider page that is open inside Network > Media (null = the plugin list). Kept so Back from a title / the player returns to it. */
    var provider by androidx.compose.runtime.mutableStateOf<MediaPluginInfo?>(null)
    /** Pull-down on an open provider page: bumped by the Home screen, handled by the page (reloads categories + rows). */
    var refreshRequest by mutableIntStateOf(0)
    /** True from the pull-down until the provider page has loaded its categories again (drives the refresh indicator). */
    var providerRefreshing by androidx.compose.runtime.mutableStateOf(false)
    /** Pull-down on an open title page: bumped by the Home screen, handled by [MediaDetailScreen] (details, episodes and pictures load again). */
    var titleRefreshRequest by mutableIntStateOf(0)
    /** True from the pull-down until the title page has its details and episodes again (drives the refresh indicator). */
    var titleRefreshing by androidx.compose.runtime.mutableStateOf(false)
    /** "plugin|link" -> [season index, audio index, quality index] */
    val selections = HashMap<String, IntArray>()

    fun open(plugin: MediaPluginInfo, post: MediaPost) {
        this.plugin = plugin
        this.post = post
    }

    fun close() {
        plugin = null
        post = null
    }
}

private data class QualityOption(val label: String, val link: MediaLink)
private data class AudioGroup(val label: String, val options: List<QualityOption>)
private data class SeasonGroup(val label: String, val audios: List<AudioGroup>)

/** One movie / episode card of the detail page. */
private data class DetailVideo(
    val title: String,
    val link: String,
    val type: String,
    val poster: String,
    val qualityLabel: String
)

private val SEASON_RE = Regex("(?i)\\bseason\\s*:?\\s*0*(\\d{1,2})\\b")
private val SHORT_SEASON_RE = Regex("(?i)\\bs0*(\\d{1,2})(?=\\s*e\\d|\\b)")
private val QUALITY_RE = Regex("(?i)\\b(2160p|1440p|1080p|720p|576p|480p|360p|4k)\\b")
private val SIZE_RE = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s?(?:GB|MB)\\b")
private val AUDIO_NOISE_RE = Regex(
    "(?i)(season\\s*:?\\s*0*\\d{1,2}|\\bs0*\\d{1,2}\\b|\\b(?:2160p|1440p|1080p|720p|576p|480p|360p|4k)\\b|" +
        "\\b\\d+(?:\\.\\d+)?\\s?(?:GB|MB)\\b|\\b(?:web-?dl|web-?rip|blu-?ray|hd-?rip|hevc|x26[45]|10-?bit)\\b)"
)
private val EMPTY_BRACKETS_RE = Regex("\\[\\s*]|\\(\\s*\\)")
private val SEPARATORS_RE = Regex("[\\s|:/,+\u2013\u00B7-]+")

private fun seasonNumber(title: String): Int? =
    (SEASON_RE.find(title) ?: SHORT_SEASON_RE.find(title))?.groupValues?.get(1)?.toIntOrNull()

/** The audio (language) of a quality option: its title without the season, quality and size words. */
private fun audioLabel(link: MediaLink): String {
    val cleaned = link.title
        .replace(AUDIO_NOISE_RE, " ")
        .replace(EMPTY_BRACKETS_RE, " ")
        .replace(SEPARATORS_RE, " ")
        .trim()
    // a label with no letter or digit at all (only an emoji / symbol) is not an audio name
    return if (cleaned.none { it.isLetterOrDigit() }) "Default" else cleaned
}

/** The quality (and size) of a quality option, exactly as the plugin offers it. */
private fun qualityLabel(link: MediaLink): String {
    val q = link.quality?.takeIf { it.isNotBlank() } ?: QUALITY_RE.find(link.title)?.value.orEmpty()
    val size = SIZE_RE.find(link.title)?.value?.trim()
    val label = listOfNotNull(q.takeIf { it.isNotBlank() }, size?.let { "[$it]" }).joinToString(" ")
    return label.ifBlank { "Default" }
}

/** Groups the plugin's list as season -> audio -> quality, so the page can show "audio + quality" pickers. */
private fun buildGroups(info: MediaInfo): List<SeasonGroup> {
    val bySeason = LinkedHashMap<Int?, LinkedHashMap<String, MutableList<QualityOption>>>()
    for (link in info.linkList) {
        val audios = bySeason.getOrPut(seasonNumber(link.title)) { LinkedHashMap() }
        audios.getOrPut(audioLabel(link)) { ArrayList() }.add(QualityOption(qualityLabel(link), link))
    }
    val hasSeasons = bySeason.keys.any { it != null }
    val keys = bySeason.keys.sortedWith(compareBy<Int?> { it == null }.thenBy { it ?: 0 })
    return keys.map { key ->
        val audios = bySeason.getValue(key).map { (label, raw) ->
            val seen = HashMap<String, Int>()
            AudioGroup(
                label = label,
                options = raw.map { opt ->
                    val n = (seen[opt.label] ?: 0) + 1
                    seen[opt.label] = n
                    if (n > 1) opt.copy(label = "${opt.label} #$n") else opt
                }
            )
        }
        SeasonGroup(
            label = when {
                key != null -> "Season $key"
                hasSeasons -> "Other"
                else -> ""
            },
            audios = audios
        )
    }
}

@Composable
fun MediaDetailScreen(
    plugin: MediaPluginInfo,
    post: MediaPost,
    isDark: Boolean,
    glassBlurTransparency: Float,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val pageBg = Color.Transparent

    // ---- title details (getMeta) ----
    // A title opened before shows at once from the saved copy; the fresh details load quietly behind it.
    val infoPeek = remember(plugin.value, post.link) { MediaProviderRepository.peekInfo(context, plugin.value, post.link) }
    var info by remember(plugin.value, post.link) { mutableStateOf<MediaInfo?>(infoPeek?.value) }
    var infoError by remember(plugin.value, post.link) { mutableStateOf<String?>(null) }
    var reload by remember(plugin.value, post.link) { mutableIntStateOf(0) }
    LaunchedEffect(plugin.value, post.link, reload) {
        val shownAlready = info != null
        val forced = reload > 0
        val stale = infoPeek != null && infoPeek.ageMs >= MediaProviderRepository.FRESH_MS
        if (shownAlready && !forced && !stale) return@LaunchedEffect
        if (!shownAlready) infoError = null
        try {
            info = MediaProviderRepository.loadInfo(context, plugin.value, post.link, force = forced || shownAlready)
            infoError = null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // what is already on screen stays; only an empty page shows the error
            if (info == null) infoError = t.message ?: "Could not load this title"
        }
    }

    // pull-down on the title page (the Home screen bumps the request): details, episodes and every picture load again,
    // the page itself stays on screen the whole time
    val titleTick = MediaDetailState.titleRefreshRequest
    var handledTitleTick by remember(plugin.value, post.link) { mutableIntStateOf(titleTick) }
    var episodesReload by remember { mutableIntStateOf(0) }
    LaunchedEffect(titleTick) {
        if (titleTick == handledTitleTick) return@LaunchedEffect
        handledTitleTick = titleTick
        try {
            com.example.util.media.EpisodeFrames.retryFailed()
            com.example.ui.components.ImageReloadSignal.bump()
            info = MediaProviderRepository.loadInfo(context, plugin.value, post.link, force = true)
            infoError = null
            episodesReload++
            com.example.ui.components.ImageReloadSignal.bump()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            MediaDetailState.titleRefreshing = false
        }
    }
    // leaving the page must not leave the refresh flag on for the next screen
    DisposableEffect(plugin.value, post.link) {
        onDispose { MediaDetailState.titleRefreshing = false }
    }

    val loaded = info
    val groups = remember(loaded) { if (loaded == null) emptyList() else buildGroups(loaded) }
    val isSeries = loaded != null &&
        (loaded.type.equals("series", true) || loaded.linkList.any { it.episodesLink != null })
    val title = loaded?.title?.takeIf { it.isNotBlank() } ?: post.title
    val backdrop = loaded?.image?.takeIf { it.isNotBlank() } ?: loaded?.poster?.takeIf { it.isNotBlank() } ?: post.image
    val poster = loaded?.poster?.takeIf { it.isNotBlank() } ?: post.image.ifBlank { loaded?.image.orEmpty() }

    // ---- season / audio / quality selection (remembered for this title) ----
    val selKey = plugin.value + "|" + post.link
    var seasonIdx by remember(selKey) { mutableIntStateOf(MediaDetailState.selections[selKey]?.getOrNull(0) ?: 0) }
    var audioIdx by remember(selKey) { mutableIntStateOf(MediaDetailState.selections[selKey]?.getOrNull(1) ?: 0) }
    var qualityIdx by remember(selKey) { mutableIntStateOf(MediaDetailState.selections[selKey]?.getOrNull(2) ?: 0) }
    // Season taken from the episode titles (S01 E01 ...) when one list holds all the seasons of a series
    var epSeason by remember(selKey) { mutableIntStateOf(MediaDetailState.selections[selKey]?.getOrNull(3) ?: 0) }
    fun saveSelection() { MediaDetailState.selections[selKey] = intArrayOf(seasonIdx, audioIdx, qualityIdx, epSeason) }
    val season = groups.getOrNull(seasonIdx.coerceIn(0, (groups.size - 1).coerceAtLeast(0)))
    val audioList = season?.audios.orEmpty()
    val audio = audioList.getOrNull(audioIdx.coerceIn(0, (audioList.size - 1).coerceAtLeast(0)))
    val optionList = audio?.options.orEmpty()
    val option = optionList.getOrNull(qualityIdx.coerceIn(0, (optionList.size - 1).coerceAtLeast(0)))
    // which picker sheet is open: 0 none, 1 season, 2 audio, 3 quality
    var pickerSheet by remember { mutableIntStateOf(0) }
    var downloadTarget by remember { mutableStateOf<DetailVideo?>(null) }
    // the files / qualities the website offers for the movie / episode being downloaded (second step of a download)
    var downloadLinks by remember { mutableStateOf<DownloadLinksState?>(null) }

    // ---- episodes of the selected quality (a series may need getEpisodes first) ----
    val optionKey = (option?.link?.episodesLink ?: option?.link?.directLinks?.firstOrNull()?.link).orEmpty() + "|" + audio?.label + "|" + option?.label
    val epPeek = remember(optionKey) {
        option?.link?.episodesLink?.let { MediaProviderRepository.peekEpisodes(context, plugin.value, it) }
    }
    var episodes by remember(optionKey) { mutableStateOf<List<MediaDirectLink>?>(epPeek?.value) }
    var episodesError by remember(optionKey) { mutableStateOf<String?>(null) }
    var episodesRetry by remember(optionKey) { mutableIntStateOf(0) }
    // a pull-down refresh that happened before this option was opened must not force-reload it again
    val reloadAtOpen = remember(optionKey) { episodesReload }
    LaunchedEffect(optionKey, episodesRetry, episodesReload) {
        val l = option?.link ?: return@LaunchedEffect
        val epLink = l.episodesLink
        if (epLink == null) {
            episodes = l.directLinks
            return@LaunchedEffect
        }
        val shownAlready = episodes != null
        val forced = episodesRetry > 0 || episodesReload != reloadAtOpen
        val stale = epPeek != null && epPeek.ageMs >= MediaProviderRepository.FRESH_MS
        if (shownAlready && !forced && !stale) return@LaunchedEffect
        if (!shownAlready) {
            episodes = null
            episodesError = null
        }
        try {
            episodes = MediaProviderRepository.loadEpisodes(context, plugin.value, epLink, force = forced || shownAlready)
            episodesError = null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (episodes == null) episodesError = t.message ?: "Could not load the episodes"
        }
    }

    val allVideos: List<DetailVideo> = remember(episodes, option, isSeries, title, poster) {
        val q = option?.label.orEmpty()
        (episodes ?: emptyList()).map { d ->
            val shownTitle = if (!isSeries && d.title.equals("Movie", ignoreCase = true)) title else d.title
            DetailVideo(
                title = shownTitle,
                link = d.link,
                type = d.type ?: if (isSeries) "series" else "movie",
                poster = d.image ?: poster,
                qualityLabel = q
            )
        }
    }

    val epSeasonNumbers = remember(allVideos) { allVideos.mapNotNull { seasonNumber(it.title) }.distinct().sorted() }
    val showEpSeason = groups.size <= 1 && epSeasonNumbers.size > 1
    val epSeasonShown = if (epSeasonNumbers.contains(epSeason)) epSeason else epSeasonNumbers.firstOrNull() ?: 0
    val query = searchQuery.trim()
    val videos: List<DetailVideo> = if (query.isNotEmpty()) {
        // top search button: finds the episode / movie by name or number, across every season
        val numberRe = if (query.all { it.isDigit() }) Regex("(?i)(?:\\be|\\bep|episode)\\s*0*" + query.trimStart('0').ifEmpty { "0" } + "\\b") else null
        allVideos.filter { it.title.contains(query, ignoreCase = true) || (numberRe != null && numberRe.containsMatchIn(it.title)) }
    } else if (showEpSeason) {
        allVideos.filter {
            val n = seasonNumber(it.title)
            n == epSeasonShown || (n == null && epSeasonShown == epSeasonNumbers.first())
        }
    } else allVideos

    // ---- swipe toolbar + background work ----
    var actionTarget by remember { mutableStateOf<DetailVideo?>(null) }
    var lastActionTarget by remember { mutableStateOf<DetailVideo?>(null) }
    if (actionTarget != null) lastActionTarget = actionTarget
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var busyJob by remember { mutableStateOf<Job?>(null) }
    val savedTick by MediaLibraryStore.changes.collectAsState()

    BackHandler {
        when {
            busyLabel != null -> { busyJob?.cancel(); busyJob = null; busyLabel = null }
            pickerSheet != 0 -> pickerSheet = 0
            downloadLinks != null -> downloadLinks = null
            downloadTarget != null -> downloadTarget = null
            actionTarget != null -> actionTarget = null
            else -> onBack()
        }
    }

    fun playerTitle(v: DetailVideo): String = if (isSeries) "$title · ${v.title}" else v.title

    fun runBusy(label: String, block: suspend () -> Unit) {
        if (busyLabel != null) return
        busyLabel = label
        busyJob = scope.launch {
            try {
                block()
            } finally {
                busyLabel = null
                busyJob = null
            }
        }
    }

    fun showResolveError(error: Throwable?) {
        Toast.makeText(context, "Could not get this video: " + (error?.message ?: "unknown error"), Toast.LENGTH_LONG).show()
    }

    fun runResolve(label: String, forDownload: Boolean, v: DetailVideo, onOk: (com.example.util.media.MediaStream) -> Unit) {
        runBusy(label) {
            val result = MediaPlayback.resolve(
                context, plugin, v.link, v.type, forDownload,
                v.title, if (isSeries) allVideos.indexOfFirst { it.link == v.link }.coerceAtLeast(0) else -1,
                // a tapped video is only opened in the player when one of its servers really delivers video
                requireWorking = !forDownload
            )
            val stream = result.getOrNull()
            if (stream == null) showResolveError(result.exceptionOrNull()) else onOk(stream)
        }
    }

    fun play(v: DetailVideo) {
        actionTarget = null
        runResolve("Getting video link…", false, v) { stream ->
            // the player's quality menu lists exactly the qualities this plugin offers for this audio
            val session = PluginPlaybackSession(
                plugin = plugin,
                episodeIndex = allVideos.indexOfFirst { it.link == v.link }.coerceAtLeast(0),
                episodeTitle = v.title,
                isSeries = isSeries,
                choices = optionList.map { PluginQualityChoice(it.label, it.link.episodesLink, it.link.directLinks) },
                selectedQuality = option?.label.orEmpty(),
                subtitles = stream.subtitles
            )
            val item = MediaPlayback.toVideoItem(context, plugin, playerTitle(v), v.poster, stream, session)
            // "Continue watching" row of the provider page
            MediaHistoryStore.record(
                context,
                MediaWatchedTitle(plugin.value, title, post.link, poster.ifBlank { post.image })
            )
            // A series opens with the whole episode list queued: Next / auto-play continue with the following episodes
            // (each one gets its stream only when the player reaches it).
            val qualityChoices = optionList.map { PluginQualityChoice(it.label, it.link.episodesLink, it.link.directLinks) }
            val queue = if (isSeries && allVideos.size > 1) {
                allVideos.mapIndexed { idx, d ->
                    if (d.link == v.link) item
                    else PluginQueuedEpisodes.placeholder(
                        PluginQueuedEpisode(
                            plugin = plugin,
                            playerTitle = playerTitle(d),
                            poster = d.poster,
                            link = d.link,
                            type = d.type,
                            episodeIndex = idx,
                            episodeTitle = d.title,
                            isSeries = isSeries,
                            choices = qualityChoices,
                            selectedQuality = option?.label.orEmpty()
                        )
                    )
                }
            } else listOf(item)
            onPlayVideo(item, queue)
        }
    }

    suspend fun beginDownload(v: DetailVideo, suffix: String, opt: MediaPlayback.DownloadOption) {
        val tag = opt.quality?.takeIf { it.isNotBlank() && !suffix.contains(it, ignoreCase = true) }?.let { " [$it]" }.orEmpty()
        val name = playerTitle(v) + suffix + tag
        val message = if (MediaPlayback.needsTorrentEngine(opt.stream)) {
            MediaPlayback.startTorrentDownload(
                context, plugin, name, v.poster, opt.stream,
                v.title, if (isSeries) allVideos.indexOfFirst { it.link == v.link }.coerceAtLeast(0) else -1
            )
        } else {
            MediaPlayback.startDownload(context, plugin, name, v.poster, opt.stream)
        }
        Toast.makeText(context, "$message (see Network › Download)", Toast.LENGTH_SHORT).show()
    }

    fun startDownload(v: DetailVideo, chosenAudio: AudioGroup, chosen: QualityOption) {
        val suffix = chosen.label.takeIf { it != "Default" }?.let { " [$it]" }.orEmpty()
        runBusy("Searching available files…") {
            // like the reference app: ask the website for everything it offers (servers, qualities, files), then let the user pick
            val result = if (chosen === option) {
                MediaPlayback.resolveAll(context, plugin, v.link, v.type, true)
            } else {
                val choice = PluginQualityChoice(chosen.label, chosen.link.episodesLink, chosen.link.directLinks)
                MediaPlayback.resolveChoiceAll(
                    context, plugin, choice,
                    allVideos.indexOfFirst { it.link == v.link }.coerceAtLeast(0), v.title, isSeries, true
                )
            }
            val streams = result.getOrNull()
            if (streams == null) {
                showResolveError(result.exceptionOrNull())
            } else {
                // always show what is available first (even one file); the download starts only after a tap.
                // The list opens at once and every row fills in its real size as soon as it is measured,
                // so the sheet never waits for the slowest server.
                var opened = false
                val sizeJob = scope.launch {
                    MediaPlayback.downloadOptionsProgressive(context, streams) { list ->
                        if (!opened) {
                            opened = true
                            downloadLinks = DownloadLinksState(v, suffix, list)
                        } else if (downloadLinks?.video === v) {
                            downloadLinks = DownloadLinksState(v, suffix, list)
                        }
                    }
                }
                try {
                    var waited = 0L
                    while (!opened && waited < 3000L && sizeJob.isActive) {
                        kotlinx.coroutines.delay(30L)
                        waited += 30L
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    sizeJob.cancel()
                    throw e
                }
                if (!opened) {
                    sizeJob.cancel()
                    showResolveError(PluginException("no downloadable file was found"))
                }
            }
        }
    }

    // The first episodes get their picture (a frame of the video) and their working server prepared in the background,
    // so by the time one is tapped it already knows which server really plays.
    LaunchedEffect(videos) {
        for (pv in videos.take(12)) {
            launch {
                com.example.util.media.EpisodeFrames.obtain(
                    context, plugin, pv.link, pv.type, pv.title,
                    if (isSeries) allVideos.indexOfFirst { it.link == pv.link }.coerceAtLeast(0) else -1
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(pageBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- gradient card: backdrop, title, rating / genres, synopsis, audio + quality (like a channel card) ----
            item(key = "card") {
                val cardShape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(cardShape)
                        .background(surfaceColor)
                        .then(if (isDark) Modifier.border(1.5.dp, AccentGradient, cardShape) else Modifier.border(1.5.dp, borderColor, cardShape))
                        .padding(1.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(topStart = 14.5.dp, topEnd = 14.5.dp))
                    ) {
                        HighResImage(
                            url = backdrop.ifBlank { null },
                            modifier = Modifier.fillMaxSize(),
                            placeholder = {
                                Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)))
                            }
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 10.dp, top = 10.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(
                                drawableRes = R.drawable.lumora_arrow_left_solid,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            text = title,
                            fontSize = 26.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        val chips = buildList<String> {
                            loaded?.rating?.takeIf { it.isNotBlank() }?.let { add("★ $it") }
                            loaded?.tags?.let { addAll(it.take(8)) }
                        }
                        if (chips.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chips.forEach { chip ->
                                    Text(
                                        text = chip,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(surfaceColor)
                                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        when {
                            infoError != null -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = infoError.orEmpty(), fontSize = 13.sp, color = secondaryText)
                                Spacer(modifier = Modifier.height(12.dp))
                                DetailPill("Retry", primaryText, surfaceColor, borderColor) { reload++ }
                            }
                            loaded == null -> Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LumoraExpressiveLoadingIndicator(size = 44.dp, isDark = isDark)
                            }
                            else -> {
                                if (loaded.synopsis.isNotBlank()) {
                                    var expanded by remember(selKey) { mutableStateOf(false) }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(text = "Synopsis", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = plugin.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentSkyBlue,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = loaded.synopsis,
                                        fontSize = 14.5.sp,
                                        lineHeight = 21.sp,
                                        color = secondaryText,
                                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { expanded = !expanded }
                                    )
                                }
                                if (groups.isNotEmpty()) {
                                    // only what this provider really offers a choice of: season / audio / quality
                                    val showSeasonPicker = groups.size > 1 || showEpSeason
                                    val showAudioPicker = audioList.size > 1
                                    val showQualityPicker = optionList.size > 1
                                    if (showSeasonPicker || showAudioPicker || showQualityPicker) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                    }
                                    if (showSeasonPicker) {
                                        PickerPill(
                                            caption = "Season",
                                            value = if (groups.size > 1) season?.label.orEmpty() else "Season $epSeasonShown",
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            borderColor = borderColor,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { pickerSheet = if (groups.size > 1) 1 else 4 }
                                    }
                                    if (showAudioPicker || showQualityPicker) {
                                        if (showSeasonPicker) Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (showAudioPicker) {
                                                PickerPill(
                                                    caption = "Audio",
                                                    value = audio?.label.orEmpty(),
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderColor = borderColor,
                                                    modifier = Modifier.weight(1f)
                                                ) { pickerSheet = 2 }
                                            }
                                            if (showQualityPicker) {
                                                PickerPill(
                                                    caption = "Quality",
                                                    value = option?.label.orEmpty(),
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderColor = borderColor,
                                                    modifier = Modifier.weight(1f)
                                                ) { pickerSheet = 3 }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No download or stream links were found for this title.",
                                        fontSize = 13.sp,
                                        color = secondaryText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- the movie / episode cards ----
            if (loaded != null && groups.isNotEmpty()) {
                when {
                    episodesError != null -> item(key = "episodes-error") {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                            Text(text = episodesError.orEmpty(), fontSize = 13.sp, color = secondaryText)
                            Spacer(modifier = Modifier.height(10.dp))
                            DetailPill("Retry", primaryText, surfaceColor, borderColor) { episodesRetry++ }
                        }
                    }
                    episodes == null && option != null -> item(key = "episodes-loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark)
                        }
                    }
                    else -> items(videos, key = { "v|" + it.link + "|" + it.title }) { v ->
                        // the card's picture is a frame of the episode's own video (made when the card is shown, then kept)
                        var framePath by remember(v.link) { mutableStateOf(com.example.util.media.EpisodeFrames.cached(context, plugin, v.link)) }
                        LaunchedEffect(v.link) {
                            if (framePath == null) {
                                framePath = com.example.util.media.EpisodeFrames.obtain(
                                    context, plugin, v.link, v.type, v.title,
                                    if (isSeries) allVideos.indexOfFirst { it.link == v.link }.coerceAtLeast(0) else -1
                                )
                            }
                        }
                        val card = remember(v, framePath) {
                            VideoItem(
                                id = -(kotlin.math.abs(v.link.hashCode().toLong())) - 1L,
                                uri = Uri.parse(v.link),
                                displayName = v.title,
                                path = v.link,
                                sizeBytes = 0L,
                                durationMs = 0L,
                                dateModified = 0L,
                                isOnline = true,
                                // the plugin's own picture shows at once; it is replaced by a frame of the video as soon as that is ready
                                thumbnailUrl = framePath ?: v.poster.takeIf { it.startsWith("http") },
                                channelName = listOf(v.qualityLabel, plugin.name).filter { it.isNotBlank() }.joinToString(" · ")
                            )
                        }
                        Box(modifier = Modifier.swipeLeftForActions { actionTarget = v }) {
                            VideoItemCard(
                                video = card,
                                isDark = isDark,
                                visibleFields = VisibleFields(
                                    showExtension = false,
                                    showSize = false,
                                    showSubtitleIndicator = false,
                                    showResolution = false,
                                    showProgressBar = false
                                ),
                                onClick = { play(v) }
                            )
                        }
                    }
                }
            }
        }

        // ---- swipe (right -> left) toolbar: playlist, watch later, download, share ----
        AnimatedVisibility(
            visible = actionTarget != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(vertical = 18.dp, horizontal = 8.dp)
        ) {
            val t = actionTarget ?: lastActionTarget
            if (t != null) {
                val saved = remember(savedTick, t.link) { MediaLibraryStore.isSaved(context, plugin.value, t.link) }
                OnlineQuickActionBar(
                    isDark = isDark,
                    alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f),
                    isSavedForLater = saved,
                    onSaveToPlaylist = {
                        actionTarget = null
                        runResolve("Adding to playlist…", false, t) { stream ->
                            val item = MediaPlayback.toVideoItem(context, plugin, playerTitle(t), t.poster, stream)
                            MediaPlayback.addToPluginPlaylist(context, plugin, item)
                            Toast.makeText(context, "Added to the \"${plugin.name}\" playlist", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onWatchLater = {
                        val nowSaved = MediaLibraryStore.toggleWatchLater(
                            context,
                            MediaSavedVideo(
                                pluginId = plugin.value,
                                title = playerTitle(t),
                                pageTitle = title,
                                poster = t.poster,
                                link = t.link,
                                type = t.type
                            )
                        )
                        Toast.makeText(context, if (nowSaved) "Added to Watch later" else "Removed from Watch later", Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    },
                    onDownload = {
                        // the qualities the website offers open from the bottom, then the download starts
                        actionTarget = null
                        val onlyAudio = audioList.getOrNull(audioIdx.coerceIn(0, (audioList.size - 1).coerceAtLeast(0)))
                        val onlyQuality = optionList.getOrNull(qualityIdx.coerceIn(0, (optionList.size - 1).coerceAtLeast(0)))
                        if (audioList.size <= 1 && optionList.size <= 1 && onlyAudio != null && onlyQuality != null) {
                            // nothing to choose between: go straight to searching the files
                            startDownload(t, onlyAudio, onlyQuality)
                        } else {
                            downloadTarget = t
                        }
                    },
                    onShare = {
                        actionTarget = null
                        val page = loaded?.webUrl?.takeIf { it.isNotBlank() }
                            ?: post.link.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                        if (page != null) {
                            shareOnlineLink(context, playerTitle(t), page)
                        } else {
                            runResolve("Getting link…", false, t) { stream ->
                                shareOnlineLink(context, playerTitle(t), stream.link)
                            }
                        }
                    }
                )
            }
        }

        // ---- season / audio / quality pickers (bottom sheets, same glass as the download sheet) ----
        when (pickerSheet) {
            1 -> OptionSheet(
                title = "Season",
                options = groups.map { it.label },
                selected = seasonIdx.coerceIn(0, (groups.size - 1).coerceAtLeast(0)),
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { pickerSheet = 0 },
                onSelect = {
                    seasonIdx = it
                    audioIdx = 0
                    qualityIdx = 0
                    saveSelection()
                    pickerSheet = 0
                }
            )
            4 -> OptionSheet(
                title = "Season",
                options = epSeasonNumbers.map { "Season $it" },
                selected = epSeasonNumbers.indexOf(epSeasonShown).coerceAtLeast(0),
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { pickerSheet = 0 },
                onSelect = {
                    epSeason = epSeasonNumbers[it]
                    saveSelection()
                    pickerSheet = 0
                }
            )
            2 -> OptionSheet(
                title = "Audio",
                options = audioList.map { it.label },
                selected = audioIdx.coerceIn(0, (audioList.size - 1).coerceAtLeast(0)),
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { pickerSheet = 0 },
                onSelect = {
                    audioIdx = it
                    qualityIdx = 0
                    saveSelection()
                    pickerSheet = 0
                }
            )
            3 -> OptionSheet(
                title = "Quality",
                options = optionList.map { it.label },
                selected = qualityIdx.coerceIn(0, (optionList.size - 1).coerceAtLeast(0)),
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { pickerSheet = 0 },
                onSelect = {
                    qualityIdx = it
                    saveSelection()
                    pickerSheet = 0
                }
            )
        }

        // ---- download sheet: audio + quality of this movie / episode ----
        val dl = downloadTarget
        if (dl != null) {
            DownloadChoiceSheet(
                title = playerTitle(dl),
                audios = audioList,
                initialAudio = audioIdx.coerceIn(0, (audioList.size - 1).coerceAtLeast(0)),
                initialQuality = qualityIdx.coerceIn(0, (optionList.size - 1).coerceAtLeast(0)),
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { downloadTarget = null },
                onConfirm = { ai, qi ->
                    downloadTarget = null
                    val chosenAudio = audioList.getOrNull(ai)
                    val chosen = chosenAudio?.options?.getOrNull(qi)
                    if (chosenAudio != null && chosen != null) startDownload(dl, chosenAudio, chosen)
                }
            )
        }

        // ---- download list: every server / quality / file the website offers ----
        val links = downloadLinks
        if (links != null) {
            DownloadLinksSheet(
                title = playerTitle(links.video),
                options = links.options,
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { downloadLinks = null },
                onPick = { opt ->
                    downloadLinks = null
                    runBusy("Preparing download…") { beginDownload(links.video, links.suffix, opt) }
                }
            )
        }

        // ---- "getting the link" indicator: transparent glass, no dark scrim (back cancels) ----
        if (busyLabel != null) {
            val factor = (glassBlurTransparency / 100f).coerceIn(0.20f, 1.0f)
            val cardBg = (if (isDark) Color(0xFF0F172A) else Color.White).copy(alpha = 0.22f + 0.28f * factor)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LumoraExpressiveLoadingIndicator(size = 44.dp, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = busyLabel.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Press back to cancel", fontSize = 11.5.sp, color = secondaryText)
                }
            }
        }
    }
}

@Composable
private fun DetailPill(text: String, primaryText: Color, surfaceColor: Color, borderColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = primaryText)
    }
}

/** Audio / quality / season box: a small caption, the current value and an arrow; tapping opens the bottom sheet. */
@Composable
private fun PickerPill(
    caption: String,
    value: String,
    primaryText: Color,
    secondaryText: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(60.dp)
            .clip(shape)
            .background(primaryText.copy(alpha = 0.05f))
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = caption, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = secondaryText, maxLines = 1)
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        StyledIcon(
            drawableRes = R.drawable.lumora_arrow_right_solid,
            contentDescription = null,
            tint = primaryText.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 90f }
        )
    }
}

private class DownloadLinksState(val video: DetailVideo, val suffix: String, val options: List<MediaPlayback.DownloadOption>)

/** The files the website offers for a download (server, quality, format, size): tapping one starts the download. */
@Composable
private fun DownloadLinksSheet(
    title: String,
    options: List<MediaPlayback.DownloadOption>,
    isDark: Boolean,
    glassBlurTransparency: Float,
    onDismiss: () -> Unit,
    onPick: (MediaPlayback.DownloadOption) -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    DownloadGlassSheet(isDark = isDark, glassBlurTransparency = glassBlurTransparency, onDismiss = onDismiss) {
        Text(text = "Download", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, fontSize = 13.sp, color = secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${options.size} files available", fontSize = 12.sp, color = secondaryText)
        Spacer(modifier = Modifier.height(12.dp))
        options.forEach { opt ->
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(shape)
                    .background(primaryText.copy(alpha = 0.06f))
                    .border(1.dp, borderColor, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(opt) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(text = opt.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (opt.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = opt.detail, fontSize = 13.sp, color = AccentSkyBlue)
                }
            }
        }
    }
}

/** Bottom sheet list (season / audio / quality) in the same transparent glass as the download sheet. */
@Composable
private fun OptionSheet(
    title: String,
    options: List<String>,
    selected: Int,
    isDark: Boolean,
    glassBlurTransparency: Float,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    DownloadGlassSheet(isDark = isDark, glassBlurTransparency = glassBlurTransparency, onDismiss = onDismiss) {
        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText)
        Spacer(modifier = Modifier.height(8.dp))
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) primaryText.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) AccentSkyBlue else primaryText,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Text(text = "✓", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AccentSkyBlue)
                }
            }
        }
    }
}

/** Download button of an episode: pick the audio and the quality the website offers, then start the download. */
@Composable
private fun DownloadChoiceSheet(
    title: String,
    audios: List<AudioGroup>,
    initialAudio: Int,
    initialQuality: Int,
    isDark: Boolean,
    glassBlurTransparency: Float,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    var a by remember { mutableIntStateOf(initialAudio) }
    var q by remember { mutableIntStateOf(initialQuality) }
    val options = audios.getOrNull(a)?.options.orEmpty()

    @Composable
    fun ChipRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            if (isSelected) Modifier.background(AccentGradient)
                            else Modifier.background(primaryText.copy(alpha = 0.06f)).border(1.dp, borderColor, shape)
                        )
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(index) }
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else primaryText,
                        maxLines = 1
                    )
                }
            }
        }
    }

    DownloadGlassSheet(isDark = isDark, glassBlurTransparency = glassBlurTransparency, onDismiss = onDismiss) {
        Text(text = "Download", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, fontSize = 13.sp, color = secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Audio", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = secondaryText)
        Spacer(modifier = Modifier.height(8.dp))
        ChipRow(audios.map { it.label }, a) { a = it; q = 0 }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Quality", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = secondaryText)
        Spacer(modifier = Modifier.height(8.dp))
        ChipRow(options.map { it.label }, q.coerceIn(0, (options.size - 1).coerceAtLeast(0))) { q = it }
        Spacer(modifier = Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .background(AccentGradient)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onConfirm(a, q.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Download", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
