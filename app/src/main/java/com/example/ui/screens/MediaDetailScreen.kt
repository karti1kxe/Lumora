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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.components.LinkThumbnail
import com.example.ui.components.LumoraExpressiveLoadingIndicator
import com.example.ui.components.OnlineQuickActionBar
import com.example.ui.components.StyledIcon
import com.example.ui.components.VideoItemCard
import com.example.ui.components.VisibleFields
import com.example.ui.components.shareOnlineLink
import com.example.ui.components.swipeLeftForActions
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
import com.example.util.media.MediaPost
import com.example.util.media.MediaProviderRepository
import com.example.util.media.MediaSavedVideo
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
    /** "plugin|link" -> [season index, quality index] */
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
private data class SeasonGroup(val label: String, val options: List<QualityOption>)

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

private fun seasonNumber(title: String): Int? =
    (SEASON_RE.find(title) ?: SHORT_SEASON_RE.find(title))?.groupValues?.get(1)?.toIntOrNull()

private fun qualityLabel(link: MediaLink): String {
    val q = link.quality?.takeIf { it.isNotBlank() } ?: QUALITY_RE.find(link.title)?.value.orEmpty()
    val size = SIZE_RE.find(link.title)?.value?.trim()
    val label = listOfNotNull(q.takeIf { it.isNotBlank() }, size?.let { "[$it]" }).joinToString(" ")
    return label.ifBlank { link.title.trim().take(42).ifBlank { "Default" } }
}

/** Groups the quality list by season (series) so the page can show "season -> quality -> episodes". */
private fun buildGroups(info: MediaInfo): List<SeasonGroup> {
    val bySeason = LinkedHashMap<Int?, MutableList<QualityOption>>()
    for (link in info.linkList) {
        bySeason.getOrPut(seasonNumber(link.title)) { ArrayList() }.add(QualityOption(qualityLabel(link), link))
    }
    val hasSeasons = bySeason.keys.any { it != null }
    val keys = bySeason.keys.sortedWith(compareBy<Int?> { it == null }.thenBy { it ?: 0 })
    return keys.map { key ->
        val raw = bySeason.getValue(key)
        val seen = HashMap<String, Int>()
        val options = raw.map { opt ->
            val n = (seen[opt.label] ?: 0) + 1
            seen[opt.label] = n
            if (n > 1) opt.copy(label = "${opt.label} #$n") else opt
        }
        SeasonGroup(
            label = when {
                key != null -> "Season $key"
                hasSeasons -> "Other"
                else -> ""
            },
            options = options
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val pageBg = MaterialTheme.colorScheme.background

    // ---- title details (getMeta) ----
    var info by remember(plugin.value, post.link) { mutableStateOf<MediaInfo?>(null) }
    var infoError by remember(plugin.value, post.link) { mutableStateOf<String?>(null) }
    var reload by remember(plugin.value, post.link) { mutableIntStateOf(0) }
    LaunchedEffect(plugin.value, post.link, reload) {
        info = null
        infoError = null
        try {
            info = MediaProviderRepository.loadInfo(context, plugin.value, post.link, force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            infoError = t.message ?: "Could not load this title"
        }
    }

    val loaded = info
    val groups = remember(loaded) { if (loaded == null) emptyList() else buildGroups(loaded) }
    val isSeries = loaded != null &&
        (loaded.type.equals("series", true) || loaded.linkList.any { it.episodesLink != null })
    val title = loaded?.title?.takeIf { it.isNotBlank() } ?: post.title
    val backdrop = loaded?.image?.takeIf { it.isNotBlank() } ?: loaded?.poster?.takeIf { it.isNotBlank() } ?: post.image
    val poster = loaded?.poster?.takeIf { it.isNotBlank() } ?: post.image.ifBlank { loaded?.image.orEmpty() }

    // ---- season / quality selection (remembered for this title) ----
    val selKey = plugin.value + "|" + post.link
    var seasonIdx by remember(selKey) { mutableIntStateOf(MediaDetailState.selections[selKey]?.getOrNull(0) ?: 0) }
    var qualityIdx by remember(selKey) { mutableIntStateOf(MediaDetailState.selections[selKey]?.getOrNull(1) ?: 0) }
    val season = groups.getOrNull(seasonIdx.coerceIn(0, (groups.size - 1).coerceAtLeast(0)))
    val optionList = season?.options.orEmpty()
    val option = optionList.getOrNull(qualityIdx.coerceIn(0, (optionList.size - 1).coerceAtLeast(0)))

    // ---- episodes of the selected quality (a series may need getEpisodes first) ----
    val optionKey = (option?.link?.episodesLink ?: option?.link?.directLinks?.firstOrNull()?.link).orEmpty() + "|" + option?.label
    var episodes by remember(optionKey) { mutableStateOf<List<MediaDirectLink>?>(null) }
    var episodesError by remember(optionKey) { mutableStateOf<String?>(null) }
    var episodesReload by remember(optionKey) { mutableIntStateOf(0) }
    LaunchedEffect(optionKey, episodesReload) {
        val l = option?.link ?: return@LaunchedEffect
        val epLink = l.episodesLink
        if (epLink == null) {
            episodes = l.directLinks
            return@LaunchedEffect
        }
        episodes = null
        episodesError = null
        try {
            episodes = MediaProviderRepository.loadEpisodes(context, plugin.value, epLink)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            episodesError = t.message ?: "Could not load the episodes"
        }
    }

    val videos: List<DetailVideo> = remember(episodes, option, isSeries, title, poster) {
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
            actionTarget != null -> actionTarget = null
            else -> onBack()
        }
    }

    fun playerTitle(v: DetailVideo): String = if (isSeries) "$title · ${v.title}" else v.title

    fun runResolve(label: String, forDownload: Boolean, v: DetailVideo, onOk: (com.example.util.media.MediaStream) -> Unit) {
        if (busyLabel != null) return
        busyLabel = label
        busyJob = scope.launch {
            try {
                val result = MediaPlayback.resolve(context, plugin, v.link, v.type, forDownload)
                val stream = result.getOrNull()
                if (stream == null) {
                    Toast.makeText(
                        context,
                        "Could not get this video: " + (result.exceptionOrNull()?.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    onOk(stream)
                }
            } finally {
                busyLabel = null
                busyJob = null
            }
        }
    }

    fun play(v: DetailVideo) {
        actionTarget = null
        runResolve("Getting video link…", false, v) { stream ->
            val item = MediaPlayback.toVideoItem(context, plugin, playerTitle(v), v.poster, stream)
            onPlayVideo(item, listOf(item))
        }
    }

    Box(modifier = modifier.fillMaxSize().background(pageBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- backdrop with the title fading into the page ----
            item(key = "hero") {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    LinkThumbnail(
                        thumbnail = backdrop.ifBlank { null },
                        modifier = Modifier.fillMaxSize(),
                        fallback = {
                            Box(modifier = Modifier.fillMaxSize().background(surfaceColor))
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, pageBg.copy(alpha = 0.55f), pageBg)))
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, top = 8.dp)
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
            }

            // ---- title, rating, genres ----
            item(key = "title") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        lineHeight = 33.sp,
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
                }
            }

            when {
                infoError != null -> item(key = "error") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(text = infoError.orEmpty(), fontSize = 13.sp, color = secondaryText)
                        Spacer(modifier = Modifier.height(12.dp))
                        DetailPill("Retry", primaryText, surfaceColor, borderColor) { reload++ }
                    }
                }
                loaded == null -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        LumoraExpressiveLoadingIndicator(size = 44.dp, isDark = isDark)
                    }
                }
                else -> {
                    // ---- synopsis ----
                    if (loaded.synopsis.isNotBlank()) {
                        item(key = "synopsis") {
                            var expanded by remember(selKey) { mutableStateOf(false) }
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
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
                        }
                    }

                    // ---- season (series) + quality selectors ----
                    if (groups.isNotEmpty()) {
                        item(key = "selectors") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (groups.size > 1) {
                                    SelectorPill(
                                        options = groups.map { it.label },
                                        selected = seasonIdx.coerceIn(0, groups.lastIndex),
                                        primaryText = primaryText,
                                        surfaceColor = surfaceColor,
                                        borderColor = borderColor,
                                        onSelect = {
                                            seasonIdx = it
                                            qualityIdx = 0
                                            MediaDetailState.selections[selKey] = intArrayOf(it, 0)
                                        }
                                    )
                                }
                                val opts = season?.options.orEmpty()
                                if (opts.isNotEmpty()) {
                                    SelectorPill(
                                        options = opts.map { it.label },
                                        selected = qualityIdx.coerceIn(0, opts.lastIndex),
                                        primaryText = primaryText,
                                        surfaceColor = surfaceColor,
                                        borderColor = borderColor,
                                        onSelect = {
                                            qualityIdx = it
                                            MediaDetailState.selections[selKey] = intArrayOf(seasonIdx, it)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "no-links") {
                            Text(
                                text = "No download or stream links were found for this title.",
                                fontSize = 13.sp,
                                color = secondaryText,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                            )
                        }
                    }

                    // ---- the movie / episode cards ----
                    when {
                        episodesError != null -> item(key = "episodes-error") {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                                Text(text = episodesError.orEmpty(), fontSize = 13.sp, color = secondaryText)
                                Spacer(modifier = Modifier.height(10.dp))
                                DetailPill("Retry", primaryText, surfaceColor, borderColor) { episodesReload++ }
                            }
                        }
                        episodes == null && option != null -> item(key = "episodes-loading") {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark)
                            }
                        }
                        else -> items(videos, key = { "v|" + it.link + "|" + it.title }) { v ->
                            val card = remember(v) {
                                VideoItem(
                                    id = -(kotlin.math.abs(v.link.hashCode().toLong())) - 1L,
                                    uri = Uri.parse(v.link),
                                    displayName = v.title,
                                    path = v.link,
                                    sizeBytes = 0L,
                                    durationMs = 0L,
                                    dateModified = 0L,
                                    isOnline = true,
                                    thumbnailUrl = v.poster.ifBlank { null },
                                    channelName = listOf(v.qualityLabel, plugin.name).filter { it.isNotBlank() }.joinToString(" · ")
                                )
                            }
                            Box(modifier = Modifier.padding(horizontal = 12.dp).swipeLeftForActions { actionTarget = v }) {
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
                        actionTarget = null
                        runResolve("Preparing download…", true, t) { stream ->
                            val message = MediaPlayback.startDownload(context, plugin, playerTitle(t), t.poster, stream)
                            Toast.makeText(context, "$message (see Network › Download)", Toast.LENGTH_SHORT).show()
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

        // ---- "getting the link" overlay (back cancels) ----
        if (busyLabel != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(surfaceColor)
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

/** Dropdown box (season / quality) in the app's glass style. */
@Composable
private fun SelectorPill(
    options: List<String>,
    selected: Int,
    primaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    onSelect: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(surfaceColor)
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = options.getOrNull(selected).orEmpty(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StyledIcon(
                drawableRes = R.drawable.lumora_arrow_right_solid,
                contentDescription = null,
                tint = primaryText.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f }
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(text = label, fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}
