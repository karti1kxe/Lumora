package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.OnlineDownloadSheet
import com.example.ui.components.OnlineQuickActionBar
import com.example.ui.components.shareOnlineLink
import com.example.ui.components.swipeLeftForActions
import com.example.ui.theme.DarkBackgroundStart
import com.example.util.OnlineMediaLibrary
import com.example.util.OnlineVideoQueue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioPlaybackManager
import com.example.ui.components.HeaderBar
import com.example.ui.components.LayoutMode
import com.example.ui.components.ViewMode
import com.example.ui.components.LumoraExpressiveLoadingIndicator
import com.example.ui.components.ONLINE_TRACK_PATH_PREFIX
import com.example.ui.components.toOnlineVideoItem
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SortAndViewOptionsPopup
import com.example.ui.components.SortDirection
import com.example.ui.components.SortField
import com.example.ui.components.StyledIcon
import com.example.ui.components.VisibleFields
import com.example.ui.components.loadRemoteAudioThumbnail
import com.example.ui.components.toAudioTrackItem
import com.example.ui.state.ThemeMode
import com.example.ui.state.UiState
import com.example.ui.state.AppViewModel
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
import com.example.util.LyricsFetcher
import com.example.util.OnlineChannelDetail
import com.example.util.OnlineChannelPlaylist
import com.example.util.OnlineChannelResult
import com.example.util.OnlineChannelSection
import com.example.util.OnlineMusicService
import com.example.util.OnlineTrackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page

/**
 * Lumora-native Channel Detail screen shown after tapping an artist / channel result in online
 * music search. Supports Back, Channel Name, Search filtering, and List/Grid
 * layout switching. Plays media as audio through AudioPlaybackManager.
 */
@Composable
fun ChannelDetailScreen(
    channel: OnlineChannelResult?,
    uiState: UiState? = null,
    viewModel: AppViewModel? = null,
    isDark: Boolean = (uiState?.themeMode == ThemeMode.DARK) ||
        (uiState?.themeMode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme()),
    isAudioMode: Boolean = true,
    onBack: () -> Unit,
    onPlayVideo: ((VideoItem, List<VideoItem>) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember(channel?.channelUrl) { mutableStateOf(true) }
    var loadError by remember(channel?.channelUrl) { mutableStateOf<String?>(null) }
    var detail by remember(channel?.channelUrl) { mutableStateOf<OnlineChannelDetail?>(null) }

    var selectedSectionId by remember(channel?.channelUrl) { mutableStateOf("home") }

    // Top Bar UI states
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortAndViewDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var layoutMode by remember { mutableStateOf(LayoutMode.LIST) }
    var sortField by remember { mutableStateOf(SortField.DATE) }
    var sortDirection by remember { mutableStateOf(SortDirection.DESCENDING) }
    var visibleFields by remember { mutableStateOf(VisibleFields()) }
    val focusRequester = remember { FocusRequester() }

    // ---- swipe (right -> left) quick actions, same bottom toolbar as the Online folders: nothing is selected ----
    val actionKind = if (isAudioMode) OnlineMediaLibrary.KIND_AUDIO else OnlineMediaLibrary.KIND_VIDEO
    var actionTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    var lastActionTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    if (actionTarget != null) lastActionTarget = actionTarget
    var playlistTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    var downloadTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    var watchLaterList by remember(actionKind) { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
    var hiddenVersion by remember(actionKind) { mutableIntStateOf(0) }
    LaunchedEffect(actionKind) {
        OnlineMediaLibrary.attach(context)
        watchLaterList = withContext(Dispatchers.IO) { OnlineMediaLibrary.watchLater(context, actionKind) }
    }
    val hiddenUrls = remember(hiddenVersion, actionKind) { OnlineMediaLibrary.notInterestedSet(context, actionKind) }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            try { focusRequester.requestFocus() } catch (_: Throwable) {}
        }
    }

    var tracks by remember(channel?.channelUrl) { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
    var shortsTracks by remember(channel?.channelUrl) { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
    var shortsLoaded by remember(channel?.channelUrl) { mutableStateOf(false) }
    var isLoadingShorts by remember(channel?.channelUrl) { mutableStateOf(false) }
    var isChannelSubscribed by remember(channel?.channelUrl) {
        mutableStateOf(channel?.channelUrl?.let { com.example.util.SubscribedChannelsManager.isSubscribed(context, it) } == true)
    }
    // Total uploaded videos ("128 videos"), counted in the background once the channel is on screen.
    var videoCountText by remember(channel?.channelUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(channel?.channelUrl) {
        val url = channel?.channelUrl
        if (!url.isNullOrBlank()) {
            videoCountText = OnlineMusicService.fetchChannelVideoCountText(url)
        }
    }
    var nextPage by remember(channel?.channelUrl) { mutableStateOf<Page?>(null) }
    var hasNextPage by remember(channel?.channelUrl) { mutableStateOf(false) }
    var isLoadingMore by remember(channel?.channelUrl) { mutableStateOf(false) }

    var expandedPlaylist by remember(channel?.channelUrl) { mutableStateOf<OnlineChannelPlaylist?>(null) }
    var playlistTracks by remember(channel?.channelUrl) { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
    var isLoadingPlaylist by remember(channel?.channelUrl) { mutableStateOf(false) }

    val currentPlayingTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val currentPositionMs by AudioPlaybackManager.currentPositionMs.collectAsState()

    val isFullScreenAudioOpen by AudioPlaybackManager.isFullScreenOpen.collectAsState()

    // Back navigation: full screen audio -> collapse; search open -> close search; playlist open -> close playlist; else exit channel
    BackHandler {
        when {
            isFullScreenAudioOpen -> {
                AudioPlaybackManager.collapseToMiniPlayer()
            }
            actionTarget != null -> {
                actionTarget = null
            }
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
            }
            expandedPlaylist != null -> {
                expandedPlaylist = null
            }
            else -> {
                onBack()
            }
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(listState.isScrollInProgress, gridState.isScrollInProgress) {
        if (listState.isScrollInProgress || gridState.isScrollInProgress) actionTarget = null
    }

    LaunchedEffect(channel?.channelUrl) {
        val url = channel?.channelUrl
        if (url.isNullOrBlank()) {
            isLoading = false
            loadError = "Channel unavailable"
            return@LaunchedEffect
        }
        isLoading = true
        loadError = null
        val result = OnlineMusicService.fetchChannelDetail(url)
        if (result != null) {
            detail = result
            tracks = result.initialTracks.items
            nextPage = result.initialTracks.nextPage
            hasNextPage = result.initialTracks.hasNextPage
        } else {
            loadError = "Unable to load this channel"
        }
        isLoading = false
    }

    // Shorts tab: loaded lazily the first time it is opened.
    LaunchedEffect(selectedSectionId, detail) {
        val info = detail
        if (selectedSectionId == "shorts" && info != null && !shortsLoaded) {
            val section = info.availableSections.firstOrNull { it.id == "shorts" }
            if (section == null) {
                shortsLoaded = true
            } else {
                isLoadingShorts = true
                shortsTracks = OnlineMusicService.fetchChannelSectionTracks(section)
                isLoadingShorts = false
                shortsLoaded = true
            }
        }
    }

    fun loadMoreTracks() {
        val section = detail?.availableSections?.firstOrNull { it.id == "videos" } ?: return
        val next = nextPage ?: return
        if (!hasNextPage || isLoadingMore) return
        isLoadingMore = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val page = OnlineMusicService.fetchNextChannelTrackPage(section, next)
                withContext(Dispatchers.Main) {
                    if (page.items.isNotEmpty()) {
                        val existingUrls = tracks.map { it.videoUrl }.toSet()
                        tracks = tracks + page.items.filter { it.videoUrl !in existingUrls }
                    }
                    nextPage = page.nextPage
                    hasNextPage = page.hasNextPage
                    isLoadingMore = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { isLoadingMore = false }
            }
        }
    }

    val shouldLoadMoreList by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMoreList, shouldLoadMoreGrid, selectedSectionId) {
        val shouldLoad = if (layoutMode == LayoutMode.GRID) shouldLoadMoreGrid else shouldLoadMoreList
        if (shouldLoad && (selectedSectionId == "videos" || selectedSectionId == "home") && hasNextPage && !isLoadingMore) {
            loadMoreTracks()
        }
    }

    fun openPlaylist(playlist: OnlineChannelPlaylist) {
        expandedPlaylist = playlist
        isLoadingPlaylist = true
        playlistTracks = emptyList()
        coroutineScope.launch(Dispatchers.IO) {
            val items = OnlineMusicService.fetchPlaylistTracks(playlist.playlistUrl)
            withContext(Dispatchers.Main) {
                playlistTracks = items
                isLoadingPlaylist = false
            }
        }
    }

    fun playChannelAudio(trackResult: OnlineTrackResult, allTracks: List<OnlineTrackResult>) {
        val selectedTrack = AudioTrackItem(
            id = -(kotlin.math.abs(trackResult.videoUrl.hashCode().toLong())),
            uri = Uri.EMPTY,
            title = trackResult.title,
            artist = channel?.name ?: trackResult.artist.ifBlank { "YouTube Music" },
            album = channel?.name ?: "Online Channel",
            durationMs = trackResult.durationSec * 1000L,
            path = "$ONLINE_TRACK_PATH_PREFIX${trackResult.videoUrl}",
            sizeBytes = 0L,
            format = trackResult.detectedFormat ?: "STREAM",
            audioType = trackResult.detectedAudioType ?: "Stereo",
            hasLyrics = false,
            lyricsText = "",
            thumbnailUrl = trackResult.thumbnailUrl
        )
        val audioQueue = allTracks.map { v ->
            AudioTrackItem(
                id = -(kotlin.math.abs(v.videoUrl.hashCode().toLong())),
                uri = Uri.EMPTY,
                title = v.title,
                artist = channel?.name ?: v.artist.ifBlank { "YouTube Music" },
                album = channel?.name ?: "Online Channel",
                durationMs = v.durationSec * 1000L,
                path = "$ONLINE_TRACK_PATH_PREFIX${v.videoUrl}",
                sizeBytes = 0L,
                format = v.detectedFormat ?: "STREAM",
                audioType = v.detectedAudioType ?: "Stereo",
                thumbnailUrl = v.thumbnailUrl
            )
        }
        // 1. Immediately open Audio Player Screen
        AudioPlaybackManager.openFullScreen()
        // 2. Immediately stop previous audio and play new track
        AudioPlaybackManager.playTrack(context, selectedTrack, if (audioQueue.isNotEmpty()) audioQueue else listOf(selectedTrack))

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val lyrics = LyricsFetcher.fetch(trackResult.title, channel?.name ?: trackResult.artist, trackResult.durationSec)
                val fullLyrics = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
                if (!fullLyrics.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        AudioPlaybackManager.setLyricsForCurrentTrack(fullLyrics)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    // Same colour as the Home screen (AMOLED black / dark slate / white in System mode / ice in Light mode), never plain white
    val backgroundColor = when {
        isDark && uiState?.amoledBlackMode == true -> DarkBackgroundStart
        isDark -> Color(0xFF1B2630)
        uiState?.themeMode == ThemeMode.SYSTEM -> Color.White
        else -> Color(0xFFDAE3E9)
    }

    val currentDensity = LocalDensity.current
    val currentAppScale = (uiState?.appScale ?: 75f).coerceIn(1f, 100f)
    val scaleFactor = (0.85f + 0.15f * ((currentAppScale - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )
    }

    val rawActiveTracks = if (selectedSectionId == "shorts") shortsTracks else tracks
    // "Not interested" videos / songs disappear from the channel too
    val activeTracks = remember(rawActiveTracks, hiddenUrls) {
        if (hiddenUrls.isEmpty()) rawActiveTracks else rawActiveTracks.filter { it.videoUrl !in hiddenUrls }
    }
    val filteredTracks = remember(activeTracks, searchQuery) {
        if (searchQuery.isBlank()) activeTracks
        else activeTracks.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val sortedTracks = remember(filteredTracks, sortField, sortDirection) {
        when (sortField) {
            SortField.TITLE -> {
                if (sortDirection == SortDirection.ASCENDING) filteredTracks.sortedBy { it.title.lowercase() }
                else filteredTracks.sortedByDescending { it.title.lowercase() }
            }
            SortField.DATE -> {
                if (sortDirection == SortDirection.ASCENDING) filteredTracks.sortedBy { it.uploadDateEpochMs ?: 0L }
                else filteredTracks.sortedByDescending { it.uploadDateEpochMs ?: 0L }
            }
            SortField.DURATION -> {
                if (sortDirection == SortDirection.ASCENDING) filteredTracks.sortedBy { it.durationSec }
                else filteredTracks.sortedByDescending { it.durationSec }
            }
            SortField.COUNT -> {
                if (sortDirection == SortDirection.ASCENDING) filteredTracks.sortedBy { it.viewCount }
                else filteredTracks.sortedByDescending { it.viewCount }
            }
            SortField.SIZE -> filteredTracks
        }
    }

    val playlists = detail?.playlists.orEmpty()
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderBar(
                    title = channel?.name ?: "Channel",
                    themeMode = uiState?.themeMode ?: ThemeMode.DARK,
                    onBackClick = onBack,
                    onSearchClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) {
                            searchQuery = ""
                        }
                    },
                    onSortClick = { showSortAndViewDialog = true },
                    onSettingsClick = { showSettingsSheet = true }
                )

                // CHANNEL-SPECIFIC SEARCH BAR
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(surfaceColor)
                            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = AccentSkyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in ${channel?.name ?: "channel"}...",
                                        fontSize = 13.sp,
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = primaryText,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(AccentSkyBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(primaryText.copy(alpha = 0.08f))
                                        .clickable { searchQuery = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    StyledIcon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = primaryText.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LumoraExpressiveLoadingIndicator(size = 48.dp, isDark = isDark)
                        }
                    }
                    loadError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = loadError ?: "Unable to load this channel",
                                color = primaryText,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    else -> {
                        val info = detail
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Channel card (same gradient card as the search result) + Subscribe
                            ChannelProfileCard(
                                name = info?.name ?: channel?.name ?: "Channel",
                                avatarUrl = info?.avatarUrl ?: channel?.avatarUrl,
                                subscriberCountText = info?.subscriberCountText ?: channel?.subscriberCountText,
                                videoCountText = videoCountText,
                                bannerUrl = info?.bannerUrl,
                                description = info?.description ?: channel?.description,
                                isVerified = info?.isVerified == true,
                                isSubscribed = isChannelSubscribed,
                                onSubscribeToggle = {
                                    isChannelSubscribed = !isChannelSubscribed
                                    channel?.channelUrl?.let { url ->
                                        com.example.util.SubscribedChannelsManager.setSubscribed(
                                            context,
                                            url,
                                            info?.name ?: channel?.name ?: "Channel",
                                            info?.avatarUrl ?: channel?.avatarUrl,
                                            isChannelSubscribed
                                        )
                                    }
                                },
                                isDark = isDark
                            )

                            // Tabs: Home | Videos | Shorts | Playlists | Posts (sliding selected indicator)
                            ChannelTabsRow(
                                tabs = listOf(
                                    "home" to "Home",
                                    "videos" to "Videos",
                                    "shorts" to "Shorts",
                                    "playlists" to "Playlists",
                                    "posts" to "Posts"
                                ),
                                selectedId = selectedSectionId,
                                onSelect = { selectedSectionId = it },
                                isDark = isDark
                            )

                            when (selectedSectionId) {
                                "posts" -> {
                                    EmptySectionMessage("Posts are not available for this channel", primaryText)
                                }
                                "playlists" -> {
                                    val playlist = expandedPlaylist
                                    if (playlist != null) {
                                        PlaylistExpandedList(
                                            playlist = playlist,
                                            tracks = playlistTracks,
                                            isLoading = isLoadingPlaylist,
                                            isDark = isDark,
                                            visibleFields = visibleFields,
                                            currentPlayingTrack = currentPlayingTrack,
                                            isPlaying = isPlaying,
                                            currentPositionMs = currentPositionMs,
                                            onBackToPlaylists = { expandedPlaylist = null },
                                            onPlayAudio = { track, all -> playChannelAudio(track, all) }
                                        )
                                    } else {
                                        if (filteredPlaylists.isEmpty()) {
                                            EmptySectionMessage(
                                                if (searchQuery.isNotBlank()) "No playlists matching \"$searchQuery\""
                                                else "No playlists available for this channel",
                                                primaryText
                                            )
                                        } else {
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(2),
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(items = filteredPlaylists, key = { it.playlistUrl }) { pl ->
                                                    PlaylistCard(pl, isDark) { openPlaylist(pl) }
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // "home" / "videos" / "shorts" tabs
                                    if (selectedSectionId == "shorts" && isLoadingShorts) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            LumoraExpressiveLoadingIndicator(size = 40.dp, isDark = isDark)
                                        }
                                    } else if (sortedTracks.isEmpty()) {
                                        EmptySectionMessage(
                                            if (searchQuery.isNotBlank()) "No tracks matching \"$searchQuery\" in ${channel?.name ?: "channel"}"
                                            else if (selectedSectionId == "shorts") "No shorts found for this channel"
                                            else "No tracks found for this channel",
                                            primaryText
                                        )
                                    } else if (layoutMode == LayoutMode.GRID) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            state = gridState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(
                                                items = sortedTracks,
                                                key = { "channel_track_grid_${it.videoUrl}" }
                                            ) { track ->
                                                if (!isAudioMode && onPlayVideo != null) {
                                                    val vItem = remember(track) { track.toOnlineVideoItem() }
                                                    Box(Modifier.swipeLeftForActions { actionTarget = track }) {
                                                        com.example.ui.components.VideoItemGridCard(
                                                            video = vItem,
                                                            isDark = isDark,
                                                            visibleFields = visibleFields.copy(showExtension = false, showSize = false, showSubtitleIndicator = false),
                                                            onClick = {
                                                                actionTarget = null
                                                                OnlineMediaLibrary.rememberMeta(context, OnlineMediaLibrary.KIND_VIDEO, track)
                                                                com.example.util.OnlineVideoRecommender.recordOpened(context, track.videoUrl, track.artist, track.title)
                                                                onPlayVideo.invoke(vItem, sortedTracks.map { it.toOnlineVideoItem() })
                                                            }
                                                        )
                                                    }
                                                    return@items
                                                }
                                                val trackItem = remember(track) { track.toAudioTrackItem() }
                                                val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${track.videoUrl}"
                                                val progress = if (isCurrent && trackItem.durationMs > 0) {
                                                    (currentPositionMs.toFloat() / trackItem.durationMs).coerceIn(0f, 1f)
                                                } else 0f
                                                val displayItem = if (progress != trackItem.playbackProgress) trackItem.copy(playbackProgress = progress) else trackItem

                                                Box(Modifier.swipeLeftForActions { actionTarget = track }) {
                                                    AudioTrackGridCard(
                                                        track = displayItem,
                                                        isCurrent = isCurrent,
                                                        isPlaying = isCurrent && isPlaying,
                                                        isDark = isDark,
                                                        visibleFields = visibleFields,
                                                        onClick = { actionTarget = null; playChannelAudio(track, sortedTracks) }
                                                    )
                                                }
                                            }
                                            if (isLoadingMore) {
                                                item(span = { GridItemSpan(maxLineSpan) }) {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        LumoraExpressiveLoadingIndicator(size = 32.dp, isDark = isDark)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(
                                                items = sortedTracks,
                                                key = { "channel_track_${it.videoUrl}" }
                                            ) { track ->
                                                if (!isAudioMode && onPlayVideo != null) {
                                                    val vItem = remember(track) { track.toOnlineVideoItem() }
                                                    Box(Modifier.swipeLeftForActions { actionTarget = track }) {
                                                        com.example.ui.components.VideoItemCard(
                                                            video = vItem,
                                                            isDark = isDark,
                                                            visibleFields = visibleFields.copy(showExtension = false, showSize = false, showSubtitleIndicator = false),
                                                            onClick = {
                                                                actionTarget = null
                                                                OnlineMediaLibrary.rememberMeta(context, OnlineMediaLibrary.KIND_VIDEO, track)
                                                                com.example.util.OnlineVideoRecommender.recordOpened(context, track.videoUrl, track.artist, track.title)
                                                                onPlayVideo.invoke(vItem, sortedTracks.map { it.toOnlineVideoItem() })
                                                            }
                                                        )
                                                    }
                                                    return@items
                                                }
                                                val trackItem = remember(track) { track.toAudioTrackItem() }
                                                val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${track.videoUrl}"
                                                val progress = if (isCurrent && trackItem.durationMs > 0) {
                                                    (currentPositionMs.toFloat() / trackItem.durationMs).coerceIn(0f, 1f)
                                                } else 0f
                                                val displayItem = if (progress != trackItem.playbackProgress) trackItem.copy(playbackProgress = progress) else trackItem

                                                Box(Modifier.swipeLeftForActions { actionTarget = track }) {
                                                    AudioTrackListCard(
                                                        track = displayItem,
                                                        isCurrent = isCurrent,
                                                        isPlaying = isCurrent && isPlaying,
                                                        isDark = isDark,
                                                        visibleFields = visibleFields,
                                                        onClick = { actionTarget = null; playChannelAudio(track, sortedTracks) }
                                                    )
                                                }
                                            }
                                            if (isLoadingMore) {
                                                item {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        LumoraExpressiveLoadingIndicator(size = 32.dp, isDark = isDark)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sort & View dialog
                if (showSortAndViewDialog) {
                    SortAndViewOptionsPopup(
                        isDark = isDark,
                        sortField = sortField,
                        sortDirection = sortDirection,
                        viewMode = ViewMode.FOLDER,
                        layoutMode = layoutMode,
                        visibleFields = visibleFields,
                        isMusicMode = true,
                        isRecentsMode = false,
                        isPlaylistMode = false,
                        isOnlineMode = true,
                        isOnlinePlaylist = false,
                        isOnlineAudioFolder = isAudioMode,
                        isOnlineVideoFolder = !isAudioMode,
                        appScale = currentAppScale,
                        glassBlurTransparency = uiState?.glassBlurTransparency ?: 75f,
                        onSortFieldChange = { sortField = it },
                        onSortDirectionChange = { sortDirection = it },
                        onLayoutModeChange = { layoutMode = it },
                        onVisibleFieldsChange = { visibleFields = it },
                        onViewModeChange = {},
                        onDismiss = { showSortAndViewDialog = false }
                    )
                }

                // Settings Sheet
                if (showSettingsSheet && uiState != null) {
                    SettingsSheet(
                        uiState = uiState,
                        onDismiss = { showSettingsSheet = false },
                        onThemeChange = { viewModel?.setThemeMode(it) },
                        viewModel = viewModel
                    )
                }

                // Full Screen Audio Player Sheet with dynamic slide-up & collapse animations
                AnimatedVisibility(
                    visible = isFullScreenAudioOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(
                            durationMillis = 380,
                            easing = CubicBezierEasing(0.12f, 0.95f, 0.22f, 1.0f)
                        )
                    ) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(
                            durationMillis = 340,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 260,
                            easing = FastOutSlowInEasing
                        )
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                        )
                    ) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = 60,
                            easing = LinearEasing
                        )
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    AudioPlayerScreen(
                        isDark = isDark,
                        glassBlurTransparency = uiState?.glassBlurTransparency ?: 75f,
                        onCollapse = { AudioPlaybackManager.collapseToMiniPlayer() }
                    )
                }
            }

            // ---- swipe toolbar: Play next / Play last / Playlist / Watch later / Not interested / Download / Share ----
            val barTransparency = uiState?.glassBlurTransparency ?: 75f
            AnimatedVisibility(
                visible = actionTarget != null && !isFullScreenAudioOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(vertical = 18.dp)
            ) {
                val t = actionTarget ?: lastActionTarget
                if (t != null) {
                    OnlineQuickActionBar(
                        isDark = isDark,
                        alphaRatio = (barTransparency / 100f).coerceIn(0.15f, 1.0f),
                        isSavedForLater = watchLaterList.any { it.videoUrl == t.videoUrl },
                        onPlayNext = {
                            if (isAudioMode) {
                                val track = t.toAudioTrackItem()
                                val q = AudioPlaybackManager.queue.value.toMutableList()
                                q.removeAll { it.path == track.path }
                                val cur = q.indexOfFirst { it.path == AudioPlaybackManager.currentTrack.value?.path }
                                if (cur >= 0) q.add(cur + 1, track) else q.add(0, track)
                                AudioPlaybackManager.updateQueue(q)
                            } else {
                                OnlineMediaLibrary.rememberMeta(context, actionKind, t)
                                OnlineVideoQueue.addNext(t)
                            }
                            Toast.makeText(context, "Will play next: ${t.title}", Toast.LENGTH_SHORT).show()
                            actionTarget = null
                        },
                        onPlayLast = {
                            if (isAudioMode) {
                                val track = t.toAudioTrackItem()
                                val q = AudioPlaybackManager.queue.value.toMutableList()
                                q.removeAll { it.path == track.path }
                                q.add(track)
                                AudioPlaybackManager.updateQueue(q)
                                Toast.makeText(context, "Added to queue: ${t.title}", Toast.LENGTH_SHORT).show()
                            } else {
                                OnlineMediaLibrary.rememberMeta(context, actionKind, t)
                                OnlineVideoQueue.addLast(t)
                                Toast.makeText(context, "Added to the end of the queue", Toast.LENGTH_SHORT).show()
                            }
                            actionTarget = null
                        },
                        onSaveToPlaylist = {
                            OnlineMediaLibrary.rememberMeta(context, actionKind, t)
                            playlistTarget = t
                            actionTarget = null
                        },
                        onWatchLater = {
                            val saved = OnlineMediaLibrary.toggleWatchLater(context, actionKind, t)
                            watchLaterList = OnlineMediaLibrary.watchLater(context, actionKind)
                            Toast.makeText(context, if (saved) "Added to Watch later" else "Removed from Watch later", Toast.LENGTH_SHORT).show()
                            actionTarget = null
                        },
                        onNotInterested = {
                            OnlineMediaLibrary.markNotInterested(context, actionKind, t.videoUrl)
                            if (!isAudioMode) OnlineVideoQueue.remove(t.videoUrl)
                            watchLaterList = OnlineMediaLibrary.watchLater(context, actionKind)
                            hiddenVersion++
                            Toast.makeText(
                                context,
                                if (isAudioMode) "Okay, you will see fewer songs like this" else "Okay, you will see fewer videos like this",
                                Toast.LENGTH_SHORT
                            ).show()
                            actionTarget = null
                        },
                        onShare = {
                            shareOnlineLink(context, t.title, t.videoUrl)
                            actionTarget = null
                        },
                        onDownload = {
                            downloadTarget = t
                            actionTarget = null
                        }
                    )
                }
            }

            downloadTarget?.let { t ->
                OnlineDownloadSheet(
                    isDark = isDark,
                    glassBlurTransparency = barTransparency,
                    url = t.videoUrl,
                    title = t.title,
                    author = t.artist,
                    thumbnailUrl = t.thumbnailUrl,
                    audioOnly = isAudioMode,
                    onDismiss = { downloadTarget = null }
                )
            }

            playlistTarget?.let { t ->
                if (isAudioMode) {
                    AddToPlaylistDialog(
                        selectedAudioTracks = listOf(t.toAudioTrackItem()),
                        isDark = isDark,
                        glassBlurTransparency = barTransparency,
                        onDismiss = { playlistTarget = null },
                        onPlaylistUpdated = { playlistTarget = null }
                    )
                } else {
                    AddToPlaylistDialog(
                        selectedVideos = listOf(t.toOnlineVideoItem()),
                        isDark = isDark,
                        glassBlurTransparency = barTransparency,
                        onDismiss = { playlistTarget = null },
                        onPlaylistUpdated = { playlistTarget = null }
                    )
                }
            }
        }
    }
}

/**
 * Channel card used inside the channel screen. Same look as the card in the search results
 * (glass surface + gradient border + avatar + name/subscribers), with the gradient Subscribe button.
 * Subscribe is UI-only, exactly like in the search result card.
 */
@Composable
private fun ChannelProfileCard(
    name: String,
    avatarUrl: String?,
    subscriberCountText: String?,
    videoCountText: String?,
    bannerUrl: String?,
    description: String?,
    isVerified: Boolean,
    isSubscribed: Boolean,
    onSubscribeToggle: () -> Unit,
    isDark: Boolean
) {
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    var avatarBitmap by remember(avatarUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        if (!avatarUrl.isNullOrBlank()) {
            avatarBitmap = loadRemoteAudioThumbnail(avatarUrl, targetSizePx = 240)
        }
    }
    var descriptionExpanded by remember(name) { mutableStateOf(false) }

    var bannerBitmap by remember(bannerUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bannerUrl) {
        if (!bannerUrl.isNullOrBlank()) {
            bannerBitmap = loadRemoteAudioThumbnail(bannerUrl, targetSizePx = 1200)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.5.dp, AccentGradient, RoundedCornerShape(16.dp))
            .padding(1.5.dp)
    ) {
        // Channel banner: first thing inside the card (above avatar / name). Nothing is drawn if the channel has none.
        val banner = bannerBitmap
        if (banner != null) {
            Image(
                bitmap = banner.asImageBitmap(),
                contentDescription = "Channel banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 4.2f)
                    .clip(RoundedCornerShape(topStart = 14.5.dp, topEnd = 14.5.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(AccentGradient),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = avatarBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = name,
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            color = primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isVerified) {
                            Spacer(modifier = Modifier.width(5.dp))
                            StyledIcon(
                                drawableRes = com.example.R.drawable.lumora_check_circle,
                                contentDescription = "Verified",
                                tint = primaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val meta = listOfNotNull(subscriberCountText, videoCountText).joinToString(" \u2022 ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = secondaryText,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = secondaryText,
                    fontSize = 12.5.sp,
                    maxLines = if (descriptionExpanded) 12 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { descriptionExpanded = !descriptionExpanded }
                )
            }

            // Subscribe button (system gradient, UI only)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSubscribed) SolidColor(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                        else AccentGradient
                    )
                    .clickable { onSubscribeToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                    color = if (isSubscribed) primaryText else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Home | Videos | Shorts | Playlists | Posts tab row. The gradient underline slides
 * left/right (animated) to the selected tab.
 */
@Composable
private fun ChannelTabsRow(
    tabs: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    isDark: Boolean
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val density = LocalDensity.current

    val tabBounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    val selectedBounds = tabBounds[selectedId]
    val indicatorX by animateFloatAsState(
        targetValue = selectedBounds?.first ?: 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "ChannelTabIndicatorX"
    )
    val indicatorWidth by animateFloatAsState(
        targetValue = selectedBounds?.second ?: 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "ChannelTabIndicatorWidth"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row {
                tabs.forEach { (id, label) ->
                    val isSelected = id == selectedId
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                tabBounds[id] = coords.positionInParent().x to coords.size.width.toFloat()
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(id) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) primaryText else secondaryText,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            if (selectedBounds != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(indicatorX.roundToInt(), 0) }
                        .width(with(density) { indicatorWidth.toDp() })
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AccentGradient)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(borderColor))
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun EmptySectionMessage(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = color, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlaylistCard(playlist: OnlineChannelPlaylist, isDark: Boolean, onClick: () -> Unit) {
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    var thumb by remember(playlist.playlistUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(playlist.playlistUrl, playlist.thumbnailUrl) {
        val url = playlist.thumbnailUrl
        if (!url.isNullOrBlank()) {
            thumb = loadRemoteAudioThumbnail(url, targetSizePx = 400)
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor)
            .clickable { onClick() }
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                StyledIcon(
                    imageVector = Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = secondaryText
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = playlist.name,
                color = primaryText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!playlist.trackCountText.isNullOrBlank()) {
                Text(text = playlist.trackCountText, color = secondaryText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PlaylistExpandedList(
    playlist: OnlineChannelPlaylist,
    tracks: List<OnlineTrackResult>,
    isLoading: Boolean,
    isDark: Boolean,
    visibleFields: VisibleFields,
    currentPlayingTrack: AudioTrackItem?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    onBackToPlaylists: () -> Unit,
    onPlayAudio: (OnlineTrackResult, List<OnlineTrackResult>) -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToPlaylists) {
                StyledIcon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back to playlists", tint = primaryText)
            }
            Text(
                text = playlist.name,
                color = primaryText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LumoraExpressiveLoadingIndicator(size = 40.dp, isDark = isDark)
            }
        } else if (tracks.isEmpty()) {
            EmptySectionMessage("This playlist has no playable tracks", primaryText)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = tracks, key = { "playlist_track_${it.videoUrl}" }) { item ->
                    val trackItem = remember(item) { item.toAudioTrackItem() }
                    val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${item.videoUrl}"
                    val progress = if (isCurrent && trackItem.durationMs > 0) {
                        (currentPositionMs.toFloat() / trackItem.durationMs).coerceIn(0f, 1f)
                    } else 0f
                    val displayItem = if (progress != trackItem.playbackProgress) trackItem.copy(playbackProgress = progress) else trackItem

                    AudioTrackListCard(
                        track = displayItem,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isDark = isDark,
                        visibleFields = visibleFields,
                        onClick = { onPlayAudio(item, tracks) }
                    )
                }
            }
        }
    }
}
