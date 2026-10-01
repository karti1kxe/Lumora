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
import androidx.compose.foundation.Image
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

    var selectedSectionId by remember(channel?.channelUrl) { mutableStateOf("videos") }

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

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            try { focusRequester.requestFocus() } catch (_: Throwable) {}
        }
    }

    var tracks by remember(channel?.channelUrl) { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
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
            selectedSectionId = result.availableSections.firstOrNull { it.id == "videos" }?.id
                ?: result.availableSections.firstOrNull()?.id
                ?: "videos"
        } else {
            loadError = "Unable to load this channel"
        }
        isLoading = false
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
        if (shouldLoad && selectedSectionId == "videos" && hasNextPage && !isLoadingMore) {
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
    val backgroundColor = if (isDark) Color(0xFF0B1120) else Color(0xFFF8FAFC)

    val currentDensity = LocalDensity.current
    val currentAppScale = (uiState?.appScale ?: 75f).coerceIn(1f, 100f)
    val scaleFactor = (0.85f + 0.15f * ((currentAppScale - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )
    }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter { it.title.contains(searchQuery, ignoreCase = true) }
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
                            // Header: avatar, name, metadata, description
                            ChannelHeader(
                                name = info?.name ?: channel?.name ?: "Channel",
                                avatarUrl = info?.avatarUrl ?: channel?.avatarUrl,
                                subscriberCountText = info?.subscriberCountText ?: channel?.subscriberCountText,
                                videoCountText = channel?.videoCountText,
                                description = info?.description,
                                isDark = isDark
                            )

                            // Section tabs: Tracks, Playlists
                            val hasPlaylistsTab = info?.playlists?.isNotEmpty() == true

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Tracks tab
                                val isVideosSelected = selectedSectionId == "videos"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isVideosSelected) AccentGradient else SolidColor(surfaceColor))
                                        .border(1.dp, if (isVideosSelected) Color.Transparent else borderColor, RoundedCornerShape(20.dp))
                                        .clickable { selectedSectionId = "videos" }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "Tracks",
                                        color = if (isVideosSelected) Color.White else primaryText,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isVideosSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }

                                // Playlists tab
                                if (hasPlaylistsTab) {
                                    val isPlaylistsSelected = selectedSectionId == "playlists"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isPlaylistsSelected) AccentGradient else SolidColor(surfaceColor))
                                            .border(1.dp, if (isPlaylistsSelected) Color.Transparent else borderColor, RoundedCornerShape(20.dp))
                                            .clickable { selectedSectionId = "playlists" }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = "Playlists",
                                            color = if (isPlaylistsSelected) Color.White else primaryText,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isPlaylistsSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            when (selectedSectionId) {
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
                                    // "videos" / "tracks" tab
                                    if (sortedTracks.isEmpty()) {
                                        EmptySectionMessage(
                                            if (searchQuery.isNotBlank()) "No tracks matching \"$searchQuery\" in ${channel?.name ?: "channel"}"
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
                                                val trackItem = remember(track) { track.toAudioTrackItem() }
                                                val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${track.videoUrl}"
                                                val progress = if (isCurrent && trackItem.durationMs > 0) {
                                                    (currentPositionMs.toFloat() / trackItem.durationMs).coerceIn(0f, 1f)
                                                } else 0f
                                                val displayItem = if (progress != trackItem.playbackProgress) trackItem.copy(playbackProgress = progress) else trackItem

                                                AudioTrackGridCard(
                                                    track = displayItem,
                                                    isCurrent = isCurrent,
                                                    isPlaying = isCurrent && isPlaying,
                                                    isDark = isDark,
                                                    visibleFields = visibleFields,
                                                    onClick = { playChannelAudio(track, sortedTracks) }
                                                )
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
                                                val trackItem = remember(track) { track.toAudioTrackItem() }
                                                val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${track.videoUrl}"
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
                                                    onClick = { playChannelAudio(track, sortedTracks) }
                                                )
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
                        isOnlineAudioFolder = true,
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
        }
    }
}

@Composable
private fun ChannelHeader(
    name: String,
    avatarUrl: String?,
    subscriberCountText: String?,
    videoCountText: String?,
    description: String?,
    isDark: Boolean
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    var avatarBitmap by remember(avatarUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        if (!avatarUrl.isNullOrBlank()) {
            avatarBitmap = loadRemoteAudioThumbnail(avatarUrl, targetSizePx = 240)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                val bmp = avatarBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = name,
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.padding(start = 12.dp))
            Column {
                Text(
                    text = name,
                    color = primaryText,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(subscriberCountText, videoCountText).joinToString("  •  ")
                if (meta.isNotBlank()) {
                    Text(text = meta, color = secondaryText, fontSize = 12.sp)
                }
            }
        }
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = secondaryText,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
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
