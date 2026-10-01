package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AudioPlaybackManager
import com.example.ui.components.*
import com.example.ui.screens.AudioPlayerScreen
import com.example.ui.state.AppViewModel
import com.example.ui.state.UiState
import com.example.ui.theme.*
import com.example.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlaylistScreen(
    playlist: OnlinePlaylistDetail,
    uiState: UiState,
    viewModel: AppViewModel? = null,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSortAndViewDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<AudioTrackItem?>(null) }

    // Search and Sort State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    var currentSortField by remember { mutableStateOf(SortField.TITLE) }
    var currentSortDirection by remember { mutableStateOf(SortDirection.ASCENDING) }
    var currentLayoutMode by remember { mutableStateOf(LayoutMode.LIST) }
    var currentVisibleFields by remember { mutableStateOf(VisibleFields(showThumbnails = true, showDuration = true, showExtension = false, showSize = false, showAudioType = false)) }

    val currentPlayingTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val currentPositionMs by AudioPlaybackManager.currentPositionMs.collectAsState()

    // 4 random high-quality track thumbnails selected for the collage
    val randomFourThumbnails = remember(playlist.playlistUrl, playlist.tracks) {
        if (playlist.previewThumbnails.size >= 4) {
            playlist.previewThumbnails.take(4)
        } else {
            val fromTracks = playlist.tracks
                .filter { !it.thumbnailUrl.isNullOrBlank() }
                .shuffled()
                .map { OnlineMusicService.getHighQualityThumbnailUrl(it.thumbnailUrl, it.videoUrl) }
                .distinct()
            if (fromTracks.size >= 4) {
                fromTracks.take(4)
            } else if (fromTracks.isNotEmpty()) {
                val padded = fromTracks.toMutableList()
                while (padded.size < 4) {
                    padded.add(fromTracks[padded.size % fromTracks.size])
                }
                padded
            } else if (!playlist.coverUrl.isNullOrBlank()) {
                List(4) { playlist.coverUrl }
            } else {
                emptyList()
            }
        }
    }

    // Convert all online tracks to AudioTrackItems for playback and UI display
    val allTrackItems = remember(playlist.tracks) {
        playlist.tracks.map { it.toAudioTrackItem() }
    }

    // Filtered and Sorted tracks according to search and sort/view options
    val filteredAndSortedTracks = remember(allTrackItems, searchQuery, currentSortField, currentSortDirection) {
        val filtered = if (searchQuery.isBlank()) {
            allTrackItems
        } else {
            allTrackItems.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
        when (currentSortField) {
            SortField.TITLE -> if (currentSortDirection == SortDirection.ASCENDING) {
                filtered.sortedBy { it.title.lowercase() }
            } else {
                filtered.sortedByDescending { it.title.lowercase() }
            }
            SortField.DURATION -> if (currentSortDirection == SortDirection.ASCENDING) {
                filtered.sortedBy { it.durationMs }
            } else {
                filtered.sortedByDescending { it.durationMs }
            }
            SortField.SIZE -> if (currentSortDirection == SortDirection.ASCENDING) {
                filtered.sortedBy { it.sizeBytes }
            } else {
                filtered.sortedByDescending { it.sizeBytes }
            }
            SortField.DATE -> if (currentSortDirection == SortDirection.ASCENDING) {
                filtered.sortedBy { it.dateModified }
            } else {
                filtered.sortedByDescending { it.dateModified }
            }
            else -> filtered
        }
    }

    // Play all starting from track 0 or from a random track
    fun playPlaylist(startIndex: Int = 0, isShuffle: Boolean = false) {
        if (filteredAndSortedTracks.isEmpty()) return
        val queue = if (isShuffle) {
            val startTrack = filteredAndSortedTracks.getOrElse(startIndex) { filteredAndSortedTracks.random() }
            val rest = filteredAndSortedTracks.filter { it.id != startTrack.id }.shuffled()
            listOf(startTrack) + rest
        } else {
            val head = filteredAndSortedTracks.getOrElse(startIndex) { filteredAndSortedTracks.first() }
            val reordered = filteredAndSortedTracks.drop(startIndex) + filteredAndSortedTracks.take(startIndex)
            reordered
        }
        AudioPlaybackManager.playTrack(context, queue.first(), queue)
        AudioPlaybackManager.openFullScreen()
    }

    val isFullScreenAudioOpen by AudioPlaybackManager.isFullScreenOpen.collectAsState()

    BackHandler {
        if (isFullScreenAudioOpen) {
            AudioPlaybackManager.collapseToMiniPlayer()
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val bgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF8FAFC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top App Bar: Exact same UI as offline player (Back Button, "Music" title, Search, Sort & View, Settings)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                HeaderBar(
                    title = "Music",
                    subtitle = "${allTrackItems.size} Tracks",
                    onBackClick = onBack,
                    themeMode = uiState.themeMode,
                    onSearchClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    onSortClick = { showSortAndViewDialog = true },
                    onSettingsClick = { showSettingsSheet = true }
                )
            }

            // Expandable Search Bar (when user clicks Search icon in top bar)
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(
                    animationSpec = tween(180, easing = FastOutLinearInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
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
                                    text = "Search tracks or artists...",
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
                                    .focusRequester(searchFocusRequester)
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
                                Text(
                                    text = "✕",
                                    fontSize = 11.sp,
                                    color = secondaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Content: Dynamically switches between List and Grid layout when Sort & View is changed!
            val contentBottomPadding = if (currentPlayingTrack != null) 100.dp else 80.dp

            if (currentLayoutMode == LayoutMode.GRID) {
                // GRID LAYOUT
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 155.dp),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = contentBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Item in Grid
                    item(span = { GridItemSpan(maxLineSpan) }, key = "playlist_header_grid") {
                        PlaylistHeaderSection(
                            playlist = playlist,
                            randomFourThumbnails = randomFourThumbnails,
                            allTrackItems = allTrackItems,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            isDark = isDark,
                            onPlay = { playPlaylist(startIndex = 0, isShuffle = false) },
                            onShuffle = {
                                val randomIdx = if (allTrackItems.isNotEmpty()) allTrackItems.indices.random() else 0
                                playPlaylist(startIndex = randomIdx, isShuffle = true)
                            },
                            onSaveClick = { showSaveDialog = true }
                        )
                    }

                    // Section title: ♫ AUDIOS (count) exactly like offline player
                    item(span = { GridItemSpan(maxLineSpan) }, key = "audios_section_header_grid") {
                        AudiosSectionHeader(
                            count = filteredAndSortedTracks.size,
                            secondaryText = secondaryText
                        )
                    }

                    // Track Grid Cards (NO M4A, 0.0 MB, Stereo tags - only Title, Artist, 3-dot menu!)
                    itemsIndexed(
                        items = filteredAndSortedTracks,
                        key = { index, track -> "grid_${track.id}_${track.path}_$index" }
                    ) { index, trackItem ->
                        val isCurrent = currentPlayingTrack?.path == trackItem.path
                        val progress = if (isCurrent && trackItem.durationMs > 0) {
                            (currentPositionMs.toFloat() / trackItem.durationMs).coerceIn(0f, 1f)
                        } else 0f
                        val displayItem = if (progress != trackItem.playbackProgress) {
                            trackItem.copy(playbackProgress = progress)
                        } else trackItem

                        SharedTrackGridCard(
                            track = displayItem,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isDark = isDark,
                            onClick = { playPlaylist(startIndex = index, isShuffle = false) },
                            onMenuClick = { selectedTrackForOptions = trackItem }
                        )
                    }
                }
            } else {
                // LIST LAYOUT
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 6.dp, bottom = contentBottomPadding)
                ) {
                    // Header: 2x2 Thumbnail Grid below top bar, Playlist Name, Artist Name, and Play/Shuffle buttons
                    item(key = "playlist_header_list") {
                        PlaylistHeaderSection(
                            playlist = playlist,
                            randomFourThumbnails = randomFourThumbnails,
                            allTrackItems = allTrackItems,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            isDark = isDark,
                            onPlay = { playPlaylist(startIndex = 0, isShuffle = false) },
                            onShuffle = {
                                val randomIdx = if (allTrackItems.isNotEmpty()) allTrackItems.indices.random() else 0
                                playPlaylist(startIndex = randomIdx, isShuffle = true)
                            },
                            onSaveClick = { showSaveDialog = true }
                        )
                    }

                    // Section title: ♫ AUDIOS (count) exactly like offline player
                    item(key = "audios_section_header_list") {
                        AudiosSectionHeader(
                            count = filteredAndSortedTracks.size,
                            secondaryText = secondaryText
                        )
                    }

                    // Track List Cards (NO M4A, 0.0 MB, Stereo tags - only Title, Artist, 3-dot menu!)
                    itemsIndexed(
                        items = filteredAndSortedTracks,
                        key = { index, track -> "list_${track.id}_${track.path}_$index" }
                    ) { index, trackItem ->
                        val isCurrent = currentPlayingTrack?.path == trackItem.path
                        val progress = if (isCurrent && trackItem.durationMs > 0) {
                            (currentPositionMs.toFloat() / trackItem.durationMs).coerceIn(0f, 1f)
                        } else 0f
                        val displayItem = if (progress != trackItem.playbackProgress) {
                            trackItem.copy(playbackProgress = progress)
                        } else trackItem

                        SharedTrackListCard(
                            track = displayItem,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isDark = isDark,
                            onClick = { playPlaylist(startIndex = index, isShuffle = false) },
                            onMenuClick = { selectedTrackForOptions = trackItem },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // 4. Floating Action Button (FAB): Bottom right corner, purple play button matching the offline player
        val fabShape = RoundedCornerShape(20.dp)
        val fabBottomPadding = if (currentPlayingTrack != null) 92.dp else 24.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = fabBottomPadding)
                .size(60.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = fabShape,
                    ambientColor = AccentSkyBlue.copy(alpha = 0.50f),
                    spotColor = AccentPink.copy(alpha = 0.60f)
                )
                .clip(fabShape)
                .background(
                    if (isDark) {
                        Brush.linearGradient(listOf(Color(0xFF1E293B).copy(alpha = 0.95f), Color(0xFF0F172A).copy(alpha = 0.95f)))
                    } else {
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFF1F5F9).copy(alpha = 0.95f)))
                    }
                )
                .border(1.5.dp, AccentGradient, fabShape)
                .clickable {
                    // Automatic playback of either the top song or a random song from the playlist
                    playPlaylist(startIndex = 0, isShuffle = false)
                }
                .testTag("shared_playlist_fab"),
            contentAlignment = Alignment.Center
        ) {
            PlayGradientLogoIcon(size = 32.dp)
        }

        // Minimized Audio Player Pill (if audio is currently playing)
        MinimizedAudioPlayer(
            isDark = isDark,
            glassBlurTransparency = uiState.glassBlurTransparency,
            onExpand = { AudioPlaybackManager.openFullScreen() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )

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
                glassBlurTransparency = uiState.glassBlurTransparency,
                onCollapse = { AudioPlaybackManager.collapseToMiniPlayer() }
            )
        }
    }

    // Sort & View Options Popup Dialog (fully functional List/Grid layout toggle and Sort fields)
    if (showSortAndViewDialog) {
        SortAndViewOptionsPopup(
            isDark = isDark,
            sortField = currentSortField,
            sortDirection = currentSortDirection,
            viewMode = ViewMode.LIBRARY,
            layoutMode = currentLayoutMode,
            visibleFields = currentVisibleFields,
            isInsideFolder = true,
            isMusicMode = true,
            isOnlinePlaylist = true,
            appScale = uiState.appScale,
            glassBlurTransparency = uiState.glassBlurTransparency,
            onSortFieldChange = { currentSortField = it },
            onSortDirectionChange = { currentSortDirection = it },
            onViewModeChange = { },
            onLayoutModeChange = { currentLayoutMode = it },
            onVisibleFieldsChange = { currentVisibleFields = it },
            onDismiss = { showSortAndViewDialog = false }
        )
    }

    // Settings Sheet Modal
    if (showSettingsSheet) {
        SettingsSheet(
            uiState = uiState,
            onDismiss = { showSettingsSheet = false },
            onThemeChange = { viewModel?.setThemeMode(it) },
            onScaleChange = { viewModel?.setAppScale(it) },
            onGlassBlurTransparencyChange = { viewModel?.setGlassBlurTransparency(it) },
            onLanguageChange = { viewModel?.setLanguage(it) }
        )
    }

    // Track 3-Dot Context Options Dialog
    if (selectedTrackForOptions != null) {
        val track = selectedTrackForOptions!!
        TrackOptionsDialog(
            track = track,
            isDark = isDark,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNext = {
                val currentQ = AudioPlaybackManager.queue.value.toMutableList()
                val curIdx = currentQ.indexOfFirst { it.path == AudioPlaybackManager.currentTrack.value?.path }
                if (curIdx >= 0) {
                    currentQ.add(curIdx + 1, track)
                } else {
                    currentQ.add(0, track)
                }
                AudioPlaybackManager.updateQueue(currentQ)
                Toast.makeText(context, "Will play next: ${track.title}", Toast.LENGTH_SHORT).show()
                selectedTrackForOptions = null
            },
            onAddToQueue = {
                val currentQ = AudioPlaybackManager.queue.value.toMutableList()
                currentQ.add(track)
                AudioPlaybackManager.updateQueue(currentQ)
                Toast.makeText(context, "Added to queue: ${track.title}", Toast.LENGTH_SHORT).show()
                selectedTrackForOptions = null
            },
            onSaveToPlaylist = {
                selectedTrackForOptions = null
                showSaveDialog = true
            },
            onShare = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                    putExtra(Intent.EXTRA_TEXT, "Listen to '${track.title}' by ${track.artist}: ${track.path}")
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                selectedTrackForOptions = null
            }
        )
    }

    // Save Playlist Dialog
    if (showSaveDialog) {
        SavePlaylistDialog(
            playlist = playlist,
            allTrackItems = allTrackItems,
            isDark = isDark,
            onDismiss = { showSaveDialog = false },
            onSaved = { savedName, count ->
                showSaveDialog = false
                Toast.makeText(context, "Saved $count songs to '$savedName'!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * Section Header: ♫ AUDIOS (count) matching the offline player UI
 */
@Composable
private fun AudiosSectionHeader(
    count: Int,
    secondaryText: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.lumora_audio_track),
            contentDescription = null,
            tint = AccentSkyBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "AUDIOS ($count)",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = secondaryText
        )
    }
}

/**
 * Playlist Header Section:
 * - 2x2 Thumbnail Grid below top bar
 * - Playlist Name + Artist Name
 * - Play & Shuffle Buttons using lumora_* local resource icons (NOT default Material icons)
 */
@Composable
private fun PlaylistHeaderSection(
    playlist: OnlinePlaylistDetail,
    randomFourThumbnails: List<String>,
    allTrackItems: List<AudioTrackItem>,
    surfaceColor: Color,
    borderColor: Color,
    primaryText: Color,
    secondaryText: Color,
    isDark: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = 0.4f)))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Horizontal row with 2x2 collage on the left, and Title, 3-dots, Platform badge, song count, and Artist on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2x2 Thumbnail Grid Collage on the left
                PlaylistCollageHeader(
                    thumbnails = randomFourThumbnails,
                    isDark = isDark,
                    modifier = Modifier.size(112.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Playlist Info Column on the right
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Playlist Name + 3-Dot Options Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 22.sp
                            ),
                            color = primaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        IconButton(
                            onClick = onSaveClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lumora_menu_dots),
                                contentDescription = "Playlist Options",
                                tint = secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Platform Badge & Song count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isSpotify = playlist.sourcePlatform.contains("Spotify", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSpotify) {
                                Color(0xFF1DB954).copy(alpha = 0.18f)
                            } else {
                                Color(0xFFFF0000).copy(alpha = 0.16f)
                            },
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        if (isSpotify) Color(0xFF1DB954).copy(alpha = 0.5f) else Color(0xFFFF0000).copy(alpha = 0.5f),
                                        borderColor
                                    )
                                )
                            )
                        ) {
                            Text(
                                text = playlist.sourcePlatform,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSpotify) Color(0xFF1DB954) else Color(0xFFFF4E4E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = "${allTrackItems.size} ${if (allTrackItems.size == 1) "song" else "songs"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = secondaryText
                        )
                    }

                    // Artist Name below platform and song count
                    if (playlist.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playlist.author,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Play & Shuffle using local resource icons (NOT Material icons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play Button using local lumora_play_solid resource
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lumora_play_solid),
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Shuffle Button using local lumora_shuffle resource
                FilledTonalButton(
                    onClick = onShuffle,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        contentColor = primaryText
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lumora_shuffle),
                        contentDescription = "Shuffle",
                        tint = primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Track Item Card - List Mode
 * STRICT REQUIREMENTS:
 * - NO "M4A", "0.0 MB", or "Stereo" tags!
 * - ONLY Song Title, Artist Name, and Three-Dot Menu!
 */
@Composable
fun SharedTrackListCard(
    track: AudioTrackItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceColor = if (isDark) {
        if (isCurrent) Color(0xFF1E1B4B).copy(alpha = 0.90f) else DarkGlassSurface
    } else {
        if (isCurrent) Color(0xFFEEF2FF).copy(alpha = 0.95f) else LightGlassSurface
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    val context = LocalContext.current
    var thumbnailBitmap by remember(track.id, track.path, track.thumbnailUrl) {
        val cacheKey = if (!track.thumbnailUrl.isNullOrBlank()) {
            "remote_audio_${track.thumbnailUrl}_600"
        } else {
            "audio_${track.id}_${track.path}_600"
        }
        mutableStateOf(ThumbnailCache.memoryCache.get(cacheKey))
    }

    LaunchedEffect(track.id, track.path, track.thumbnailUrl) {
        if (thumbnailBitmap == null) {
            val loaded = if (!track.thumbnailUrl.isNullOrBlank()) {
                loadRemoteAudioThumbnail(url = track.thumbnailUrl, targetSizePx = 600)
            } else {
                loadAudioThumbnail(context = context, audioId = track.id, uri = track.uri, path = track.path, targetSizePx = 600)
            }
            thumbnailBitmap = loaded
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isCurrent) 8.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = AccentPink.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                brush = if (isCurrent) AccentGradient else Brush.linearGradient(listOf(borderColor, borderColor)),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Thumbnail (136.dp x 80dp)
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrent) AccentGradient else Brush.linearGradient(
                            if (isDark) listOf(Color(0xFF1E293B), Color(0xFF0F172A)) else listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                        )
                    )
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                val bmp = thumbnailBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }

                // Equalizer or fallback headphones
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && isPlaying) {
                        AnimatedAudioEqualizer(isDark = isDark)
                    } else if (bmp == null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lumora_audio_track),
                                contentDescription = "Audio",
                                tint = if (isCurrent) Color.White else AccentSkyBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Duration Badge
                if (track.formattedDuration.isNotBlank() && track.formattedDuration != "00:00") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.formattedDuration,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Playback progress bar
                if (track.playbackProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.40f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(track.playbackProgress.coerceIn(0f, 1f))
                                .background(AccentSkyBlue)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle Column: ONLY Song Title & Artist Name
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 12.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Three-Dot Menu Button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lumora_menu_dots),
                    contentDescription = "Options",
                    tint = secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Track Item Card - Grid Mode
 * STRICT REQUIREMENTS:
 * - NO "M4A", "0.0 MB", or "Stereo" tags!
 * - ONLY Song Title, Artist Name, and Three-Dot Menu!
 */
@Composable
fun SharedTrackGridCard(
    track: AudioTrackItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceColor = if (isDark) {
        if (isCurrent) Color(0xFF1E1B4B).copy(alpha = 0.90f) else DarkGlassSurface
    } else {
        if (isCurrent) Color(0xFFEEF2FF).copy(alpha = 0.95f) else LightGlassSurface
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    val context = LocalContext.current
    var thumbnailBitmap by remember(track.id, track.path, track.thumbnailUrl) {
        val cacheKey = if (!track.thumbnailUrl.isNullOrBlank()) {
            "remote_audio_${track.thumbnailUrl}_600"
        } else {
            "audio_${track.id}_${track.path}_600"
        }
        mutableStateOf(ThumbnailCache.memoryCache.get(cacheKey))
    }

    LaunchedEffect(track.id, track.path, track.thumbnailUrl) {
        if (thumbnailBitmap == null) {
            val loaded = if (!track.thumbnailUrl.isNullOrBlank()) {
                loadRemoteAudioThumbnail(url = track.thumbnailUrl, targetSizePx = 600)
            } else {
                loadAudioThumbnail(context = context, audioId = track.id, uri = track.uri, path = track.path, targetSizePx = 600)
            }
            thumbnailBitmap = loaded
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isCurrent) 8.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = AccentPink.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                brush = if (isCurrent) AccentGradient else Brush.linearGradient(listOf(borderColor, borderColor)),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCurrent) AccentGradient else Brush.linearGradient(
                            if (isDark) listOf(Color(0xFF1E293B), Color(0xFF0F172A)) else listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                        )
                    )
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                val bmp = thumbnailBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }

                // Equalizer or fallback
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && isPlaying) {
                        AnimatedAudioEqualizer(isDark = isDark)
                    } else if (bmp == null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lumora_audio_track),
                                contentDescription = "Audio",
                                tint = if (isCurrent) Color.White else AccentSkyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Duration Badge
                if (track.formattedDuration.isNotBlank() && track.formattedDuration != "00:00") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.formattedDuration,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row: Title + 3-Dot Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lumora_menu_dots),
                        contentDescription = "Options",
                        tint = secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Artist Name ONLY (NO M4A, 0.0 MB, Stereo tags!)
            Text(
                text = track.artist.ifBlank { "Unknown Artist" },
                fontSize = 11.5.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dialog for Track 3-Dot Options (Play Next, Add to Queue, Save, Share)
 */
@Composable
private fun TrackOptionsDialog(
    track: AudioTrackItem,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onShare: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Play Next
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayNext() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lumora_play_solid),
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Play Next",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText
                        )
                    }
                }

                // Add to Queue
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddToQueue() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = AccentPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Add to Queue",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText
                        )
                    }
                }

                // Save to My Playlists
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSaveToPlaylist() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lumora_playlist_audio),
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Save to My Playlists",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText
                        )
                    }
                }

                // Share
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShare() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Share Track",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = surfaceColor
    )
}

/**
 * 2x2 Artwork Collage displaying 4 randomly selected tracks' high-resolution thumbnails.
 */
@Composable
private fun PlaylistCollageHeader(
    thumbnails: List<String>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val surfaceColor = if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0)

    val displayThumbs = remember(thumbnails) {
        if (thumbnails.isEmpty()) {
            emptyList()
        } else if (thumbnails.size >= 4) {
            thumbnails.take(4)
        } else {
            val list = thumbnails.toMutableList()
            while (list.size < 4) {
                list.add(thumbnails[list.size % thumbnails.size])
            }
            list
        }
    }

    Box(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
    ) {
        if (displayThumbs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageImageItem(
                        url = displayThumbs[0],
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                    CollageImageItem(
                        url = displayThumbs[1],
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(borderColor))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageImageItem(
                        url = displayThumbs[2],
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                    CollageImageItem(
                        url = displayThumbs[3],
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CollageImageItem(
    url: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(url) {
        mutableStateOf(ThumbnailCache.memoryCache.get("remote_audio_${url}_800"))
    }

    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) {
            bitmap = loadRemoteAudioThumbnail(url, targetSizePx = 800)
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF1E293B)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Dialog allowing the user to save the entire shared playlist into the application's
 * internal custom playlists system (creating a new playlist or adding to an existing one).
 */
@Composable
private fun SavePlaylistDialog(
    playlist: OnlinePlaylistDetail,
    allTrackItems: List<AudioTrackItem>,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSaved: (name: String, count: Int) -> Unit
) {
    val context = LocalContext.current
    var playlistTitleInput by remember { mutableStateOf(playlist.title.trim()) }
    val existingPlaylists = remember {
        PlaylistManager.getPlaylists(context).filter { it.isAudio }
    }
    var selectedExistingPlaylist by remember { mutableStateOf<CustomPlaylist?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Save to My Playlists",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Save all ${playlist.tracks.size} tracks into your personal music collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                OutlinedTextField(
                    value = playlistTitleInput,
                    onValueChange = { playlistTitleInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (existingPlaylists.isNotEmpty()) {
                    Text(
                        text = "Or add to existing playlist:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDark) DarkTextPrimary else LightTextPrimary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        existingPlaylists.forEach { pl ->
                            val isSelected = selectedExistingPlaylist?.id == pl.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedExistingPlaylist = if (isSelected) null else pl
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDark) DarkTextPrimary else LightTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pl.isOnlineFolder) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• Online",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trackPaths = playlist.tracks.map { "$ONLINE_TRACK_PATH_PREFIX${it.videoUrl}" }
                    // Update cache and persistent storage with track metadata so they appear complete with titles, artists, and thumbnails across app restarts
                    com.example.util.OnlineTrackMetadataCache.saveTracks(context, allTrackItems)
                    AudioLibraryCache.updateCachedTracks(allTrackItems, context)

                    val chosenTarget = selectedExistingPlaylist
                    if (chosenTarget != null) {
                        PlaylistManager.addVideosToPlaylist(context, chosenTarget.id, trackPaths)
                        onSaved(chosenTarget.name, trackPaths.size)
                    } else {
                        val name = playlistTitleInput.trim().ifBlank { playlist.title.ifBlank { "Saved Playlist" } }
                        val created = PlaylistManager.createPlaylist(context, name, "AUDIO", isOnline = true)
                        PlaylistManager.addVideosToPlaylist(context, created.id, trackPaths)
                        onSaved(created.name, trackPaths.size)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save All Tracks")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isDark) DarkGlassSurface else LightGlassSurface
    )
}
