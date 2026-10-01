package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.util.NaturalOrderComparator
import com.example.util.MediaMetadataExtractor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.R
import com.example.ui.components.BottomMoveCopyProgressBar
import com.example.ui.components.FolderGlyphType
import com.example.ui.components.InlineRenameField
import com.example.ui.components.LayoutMode
import com.example.ui.components.ModernFolderIcon
import com.example.ui.components.SortDirection
import com.example.ui.components.SortField
import com.example.ui.components.StyledIcon
import com.example.ui.components.VideoItemCard
import com.example.ui.components.ViewMode
import com.example.ui.components.VisibleFields
import com.example.ui.state.UiState
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.AudioFileManager
import com.example.util.AudioLibraryCache
import com.example.util.CustomPlaylist
import com.example.util.PlaylistManager
import com.example.util.VideoFileManager
import com.example.player.AudioPlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomPlaylistsTabContent(
    uiState: UiState,
    isDark: Boolean,
    allLibraryVideos: List<VideoItem>,
    searchQuery: String = "",
    sortField: SortField = SortField.TITLE,
    sortDirection: SortDirection = SortDirection.ASCENDING,
    viewMode: ViewMode = ViewMode.LIBRARY,
    layoutMode: LayoutMode = LayoutMode.LIST,
    visibleFields: VisibleFields,
    folderSortField: SortField = sortField,
    folderSortDirection: SortDirection = sortDirection,
    folderLayoutMode: LayoutMode = layoutMode,
    folderVisibleFields: VisibleFields = visibleFields,
    contentSortField: SortField = sortField,
    contentSortDirection: SortDirection = sortDirection,
    contentLayoutMode: LayoutMode = layoutMode,
    contentVisibleFields: VisibleFields = visibleFields,
    selectedPlaylist: CustomPlaylist? = null,
    onSelectPlaylist: (CustomPlaylist?) -> Unit = {},
    onPlaylistSelected: (Boolean) -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    onSelectionDetailsChange: (
        count: Int,
        isFolder: Boolean,
        onClear: () -> Unit,
        onSelectAll: () -> Unit,
        onShare: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playlists = remember { mutableStateListOf<CustomPlaylist>() }
    val allAudioTracks = remember { mutableStateListOf<AudioTrackItem>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tracks = AudioLibraryCache.getOrScanAudio(context, force = false)
            withContext(Dispatchers.Main) {
                allAudioTracks.clear()
                allAudioTracks.addAll(tracks)
            }
        }
    }

    // Direct sync with hoisted state
    val effectiveSelectedPlaylist = selectedPlaylist

    fun setSelectedPl(pl: CustomPlaylist?) {
        onSelectPlaylist(pl)
    }

    // Selection States for Playlists (Root) and Videos (Inside Playlist)
    val selectedPlaylistIds = remember { mutableStateListOf<String>() }
    val selectedVideoPaths = remember { mutableStateListOf<String>() }
    var isReorderMode by remember { mutableStateOf(false) }

    val isPlaylistSelection = effectiveSelectedPlaylist == null && selectedPlaylistIds.isNotEmpty()
    val isVideoSelection = effectiveSelectedPlaylist != null && selectedVideoPaths.isNotEmpty()
    var isDeleteInProgress by remember { mutableStateOf(false) }
    var deleteProgressFraction by remember { mutableStateOf(0f) }
    var deleteItemName by remember { mutableStateOf("") }
    var deleteCompletedCount by remember { mutableStateOf(0) }
    var deleteTotalCount by remember { mutableStateOf(0) }

    val isSelectionMode = isPlaylistSelection || isVideoSelection || isDeleteInProgress

    // Reorder Mode for playlist videos

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var renamePlaylistTargetId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Inline Rename States
    var renamingPlaylistId by remember { mutableStateOf<String?>(null) }
    var renamingVideoPath by remember { mutableStateOf<String?>(null) }

    // Video Rename Dialog State
    var showRenameVideoDialog by remember { mutableStateOf(false) }
    var videoToRename by remember { mutableStateOf<VideoItem?>(null) }
    var renameVideoNameText by remember { mutableStateOf("") }

    // Media Playlist Filter Tab: "VIDEO" | "AUDIO" (Video is primary/default)
    var selectedMediaTab by remember { mutableStateOf("VIDEO") }

    LaunchedEffect(effectiveSelectedPlaylist) {
        onPlaylistSelected(effectiveSelectedPlaylist != null)
        selectedVideoPaths.clear()
        isReorderMode = false
    }

    fun refreshPlaylists() {
        coroutineScope.launch(Dispatchers.IO) {
            val list = PlaylistManager.getPlaylists(context)
            withContext(Dispatchers.Main) {
                playlists.clear()
                playlists.addAll(list)
                if (effectiveSelectedPlaylist != null) {
                    val updated = list.find { it.id == effectiveSelectedPlaylist.id }
                    setSelectedPl(updated)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPlaylists()
    }

    val mediaFilteredPlaylists = remember(playlists.toList(), selectedMediaTab) {
        playlists.filter {
            if (selectedMediaTab == "AUDIO") it.isAudio else !it.isAudio
        }
    }

    val filteredPlaylists = remember(mediaFilteredPlaylists, searchQuery) {
        if (searchQuery.isBlank()) mediaFilteredPlaylists else {
            mediaFilteredPlaylists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val sortedPlaylists = remember(filteredPlaylists, allLibraryVideos, allAudioTracks.toList(), folderSortField, folderSortDirection) {
        when (folderSortField) {
            SortField.TITLE -> if (folderSortDirection == SortDirection.ASCENDING) {
                filteredPlaylists.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
            } else {
                filteredPlaylists.sortedWith { a, b -> NaturalOrderComparator.compare(b.name, a.name) }
            }
            SortField.DATE -> if (folderSortDirection == SortDirection.ASCENDING) {
                filteredPlaylists.sortedBy { it.createdAt }
            } else {
                filteredPlaylists.sortedByDescending { it.createdAt }
            }
            SortField.SIZE -> {
                val sizes = filteredPlaylists.associateWith { pl ->
                    if (pl.isAudio) pl.videoPaths.sumOf { path -> allAudioTracks.firstOrNull { it.path == path }?.sizeBytes ?: File(path).takeIf { it.exists() }?.length() ?: 0L }
                    else pl.videoPaths.sumOf { path -> allLibraryVideos.firstOrNull { it.path == path }?.sizeBytes ?: File(path).takeIf { it.exists() }?.length() ?: 0L }
                }
                if (folderSortDirection == SortDirection.ASCENDING) filteredPlaylists.sortedBy { sizes[it] ?: 0L }
                else filteredPlaylists.sortedByDescending { sizes[it] ?: 0L }
            }
            SortField.DURATION -> {
                val durations = filteredPlaylists.associateWith { pl ->
                    if (pl.isAudio) pl.videoPaths.sumOf { path -> allAudioTracks.firstOrNull { it.path == path }?.durationMs ?: 0L }
                    else pl.videoPaths.sumOf { path -> allLibraryVideos.firstOrNull { it.path == path }?.durationMs ?: 0L }
                }
                if (folderSortDirection == SortDirection.DESCENDING) filteredPlaylists.sortedByDescending { durations[it] ?: 0L }
                else filteredPlaylists.sortedBy { durations[it] ?: 0L }
            }
            SortField.COUNT -> filteredPlaylists
        }
    }

    val rawPlVideos = remember(effectiveSelectedPlaylist, allLibraryVideos) {
        if (effectiveSelectedPlaylist != null && !effectiveSelectedPlaylist.isAudio) {
            PlaylistManager.getVideosForPlaylist(effectiveSelectedPlaylist, allLibraryVideos)
        } else emptyList()
    }

    var enrichedPlVideosMap by remember(effectiveSelectedPlaylist?.id) { mutableStateOf<Map<String, VideoItem>>(emptyMap()) }

    val plVideos = remember(rawPlVideos, enrichedPlVideosMap) {
        if (enrichedPlVideosMap.isEmpty()) rawPlVideos
        else rawPlVideos.map { enrichedPlVideosMap[it.path] ?: it }
    }

    LaunchedEffect(effectiveSelectedPlaylist?.id, rawPlVideos.size) {
        val needsEnrichment = rawPlVideos.any { it.framerate <= 0.0 }
        if (needsEnrichment) {
            withContext(Dispatchers.IO) {
                val newMap = mutableMapOf<String, VideoItem>()
                rawPlVideos.forEach { video ->
                    if (video.framerate <= 0.0) {
                        val meta = try {
                            MediaMetadataExtractor.extractMetadata(
                                filePath = video.path,
                                context = context,
                                uri = video.uri,
                                displayName = video.displayName,
                                sizeBytes = video.sizeBytes,
                                dateModified = video.dateModified
                            )
                        } catch (_: Throwable) { null }
                        if (meta != null && (meta.framerate > 0.0 || meta.effectiveSubtitleFormats.isNotEmpty())) {
                            newMap[video.path] = video.copy(
                                framerate = if (meta.framerate > 0.0) meta.framerate else video.framerate,
                                resolution = if (meta.resolution.isNotBlank()) meta.resolution else video.resolution,
                                durationMs = if (meta.durationMs > 0L) meta.durationMs else video.durationMs,
                                embeddedSubtitleFormats = meta.embeddedSubtitleFormats,
                                externalSubtitleFormats = meta.externalSubtitleFormats,
                                subtitleFormats = meta.effectiveSubtitleFormats,
                                hasSubtitles = meta.effectiveSubtitleFormats.isNotEmpty()
                            )
                        }
                    }
                }
                if (newMap.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        enrichedPlVideosMap = enrichedPlVideosMap + newMap
                    }
                }
            }
        }
    }

    val plAudioTracks = remember(effectiveSelectedPlaylist, allAudioTracks.toList()) {
        if (effectiveSelectedPlaylist != null && effectiveSelectedPlaylist.isAudio) {
            val trackMap = allAudioTracks.associateBy { it.path }
            val cachedMap = com.example.util.AudioLibraryCache.getCachedTracks()?.associateBy { it.path } ?: emptyMap()
            effectiveSelectedPlaylist.videoPaths.mapNotNull { path ->
                trackMap[path] ?: cachedMap[path] ?: com.example.util.OnlineTrackMetadataCache.getTrack(context, path) ?: run {
                    if (path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)) {
                        val videoUrl = path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                        val cachedOnline = com.example.util.OnlineTrackMetadataCache.getTrack(context, videoUrl)
                        if (cachedOnline != null) cachedOnline else {
                            val meta = com.example.util.OnlineMusicService.getCachedAudioMeta(videoUrl)
                            AudioTrackItem(
                                id = -(kotlin.math.abs(videoUrl.hashCode().toLong())),
                                uri = android.net.Uri.EMPTY,
                                title = meta?.first ?: "Online Track",
                                artist = meta?.second ?: "Online Music",
                                album = "Online Playlist",
                                durationMs = 0L,
                                path = path,
                                sizeBytes = 0L,
                                format = "STREAM",
                                audioType = "Stereo",
                                dateModified = System.currentTimeMillis()
                            )
                        }
                    } else {
                        val f = File(path)
                        if (!f.exists()) null else AudioTrackItem(
                            id = f.absolutePath.hashCode().toLong(),
                            uri = android.net.Uri.fromFile(f),
                            title = f.nameWithoutExtension,
                            artist = "Unknown Artist",
                            album = "Unknown Album",
                            durationMs = 0L,
                            path = f.absolutePath,
                            sizeBytes = f.length(),
                            format = f.extension.uppercase(),
                            dateModified = f.lastModified() / 1000L
                        )
                    }
                }
            }
        } else emptyList()
    }

    val sortedPlVideos = remember(plVideos, contentSortField, contentSortDirection) {
        when (contentSortField) {
            SortField.TITLE -> if (contentSortDirection == SortDirection.ASCENDING) {
                plVideos.sortedWith { a, b -> NaturalOrderComparator.compare(a.displayName, b.displayName) }
            } else {
                plVideos.sortedWith { a, b -> NaturalOrderComparator.compare(b.displayName, a.displayName) }
            }
            SortField.DATE -> if (contentSortDirection == SortDirection.ASCENDING) {
                plVideos.sortedBy { it.dateModified }
            } else {
                plVideos.sortedByDescending { it.dateModified }
            }
            SortField.SIZE -> if (contentSortDirection == SortDirection.ASCENDING) plVideos.sortedBy { it.sizeBytes } else plVideos.sortedByDescending { it.sizeBytes }
            SortField.DURATION -> if (contentSortDirection == SortDirection.DESCENDING) plVideos.sortedByDescending { it.durationMs } else plVideos.sortedBy { it.durationMs }
            SortField.COUNT -> plVideos
        }
    }

    val sortedPlAudioTracks = remember(plAudioTracks, contentSortField, contentSortDirection) {
        when (contentSortField) {
            SortField.TITLE -> if (contentSortDirection == SortDirection.ASCENDING) {
                plAudioTracks.sortedWith { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
            } else {
                plAudioTracks.sortedWith { a, b -> NaturalOrderComparator.compare(b.title, a.title) }
            }
            SortField.DATE -> if (contentSortDirection == SortDirection.ASCENDING) {
                plAudioTracks.sortedBy { it.dateModified }
            } else {
                plAudioTracks.sortedByDescending { it.dateModified }
            }
            SortField.SIZE -> if (contentSortDirection == SortDirection.ASCENDING) plAudioTracks.sortedBy { it.sizeBytes } else plAudioTracks.sortedByDescending { it.sizeBytes }
            SortField.DURATION -> if (contentSortDirection == SortDirection.DESCENDING) plAudioTracks.sortedByDescending { it.durationMs } else plAudioTracks.sortedBy { it.durationMs }
            SortField.COUNT -> plAudioTracks
        }
    }

    var reorderedPaths by remember(effectiveSelectedPlaylist?.id, effectiveSelectedPlaylist?.videoPaths, contentSortField, contentSortDirection) {
        val basePaths = if (effectiveSelectedPlaylist?.isAudio == true) {
            sortedPlAudioTracks.map { it.path }
        } else {
            sortedPlVideos.map { it.path }
        }
        mutableStateOf(basePaths.ifEmpty { effectiveSelectedPlaylist?.videoPaths ?: emptyList() })
    }
    val displayPlVideos = remember(reorderedPaths, plVideos) {
        val map = plVideos.associateBy { it.path }
        reorderedPaths.mapNotNull { map[it] }
    }
    val displayPlAudioTracks = remember(reorderedPaths, plAudioTracks) {
        val map = plAudioTracks.associateBy { it.path }
        reorderedPaths.mapNotNull { map[it] }
    }

    val playlistMediaCount = if (effectiveSelectedPlaylist?.isAudio == true) displayPlAudioTracks.size else displayPlVideos.size

    // Sync selection details to top SelectionTopBar in HomeScreen
    LaunchedEffect(
        isPlaylistSelection,
        isVideoSelection,
        selectedPlaylistIds.size,
        selectedVideoPaths.size,
        sortedPlaylists,
        plVideos
    ) {
        if (isPlaylistSelection) {
            val count = selectedPlaylistIds.size
            val allIds = sortedPlaylists.map { it.id }
            val allSelected = count == allIds.size && allIds.isNotEmpty()
            onSelectionDetailsChange(
                count,
                true, // isFolder
                { selectedPlaylistIds.clear() },
                {
                    if (allSelected) {
                        selectedPlaylistIds.clear()
                    } else {
                        selectedPlaylistIds.clear()
                        selectedPlaylistIds.addAll(allIds)
                    }
                },
                {
                    val selectedPls = playlists.filter { selectedPlaylistIds.contains(it.id) }
                    val selectedAudio = selectedPls.filter { it.isAudio }
                        .flatMap { pl ->
                            pl.videoPaths.mapNotNull { path ->
                                allAudioTracks.find { it.path == path } ?: if (path.startsWith(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)) {
                                    val videoUrl = path.removePrefix(com.example.ui.components.ONLINE_TRACK_PATH_PREFIX)
                                    AudioTrackItem(
                                        id = -(kotlin.math.abs(videoUrl.hashCode().toLong())),
                                        uri = android.net.Uri.EMPTY,
                                        title = "Online Track",
                                        artist = "Online Music",
                                        album = pl.name,
                                        durationMs = 0L,
                                        path = path,
                                        sizeBytes = 0L,
                                        format = "STREAM"
                                    )
                                } else null
                            }
                        }
                        .distinctBy { it.path }
                    val selectedVideos = selectedPls.filter { !it.isAudio }
                        .flatMap { PlaylistManager.getVideosForPlaylist(it, allLibraryVideos) }
                        .distinctBy { it.path }
                    if (selectedAudio.isNotEmpty()) {
                        AudioFileManager.shareAudio(context, selectedAudio)
                        selectedPlaylistIds.clear()
                    } else if (selectedVideos.isNotEmpty()) {
                        VideoFileManager.shareVideos(context, selectedVideos)
                        selectedPlaylistIds.clear()
                    } else {
                        Toast.makeText(context, "Selected playlists have no available media", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else if (isVideoSelection) {
            val count = selectedVideoPaths.size
            val allPaths = if (effectiveSelectedPlaylist?.isAudio == true) plAudioTracks.map { it.path } else plVideos.map { it.path }
            val allSelected = count == allPaths.size && allPaths.isNotEmpty()
            onSelectionDetailsChange(
                count,
                false,
                {
                    selectedVideoPaths.clear()
                    isReorderMode = false
                },
                {
                    if (allSelected) {
                        selectedVideoPaths.clear()
                        isReorderMode = false
                    } else {
                        selectedVideoPaths.clear()
                        selectedVideoPaths.addAll(allPaths)
                    }
                },
                {
                    if (effectiveSelectedPlaylist?.isAudio == true) {
                        val selectedAudio = plAudioTracks.filter { selectedVideoPaths.contains(it.path) }
                        if (selectedAudio.isNotEmpty()) {
                            AudioFileManager.shareAudio(context, selectedAudio)
                            selectedVideoPaths.clear()
                            isReorderMode = false
                        } else Toast.makeText(context, "No audio to share", Toast.LENGTH_SHORT).show()
                    } else {
                        val selectedVideosList = plVideos.filter { selectedVideoPaths.contains(it.path) }
                        if (selectedVideosList.isNotEmpty()) {
                            VideoFileManager.shareVideos(context, selectedVideosList)
                            selectedVideoPaths.clear()
                            isReorderMode = false
                        } else Toast.makeText(context, "No videos to share", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            onSelectionDetailsChange(0, false, {}, {}, {})
        }
    }

    // Android back button handling
    BackHandler(enabled = showDeleteConfirmDialog || isSelectionMode || isReorderMode || effectiveSelectedPlaylist != null) {
        when {
            showDeleteConfirmDialog -> showDeleteConfirmDialog = false
            isVideoSelection -> { selectedVideoPaths.clear(); isReorderMode = false }
            isPlaylistSelection -> selectedPlaylistIds.clear()
            isReorderMode -> isReorderMode = false
            effectiveSelectedPlaylist != null -> setSelectedPl(null)
        }
    }

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    Box(modifier = modifier.fillMaxSize()) {
        if (effectiveSelectedPlaylist != null) {
            // ==========================================
            // PLAYLIST DETAIL VIEW (Inside Playlist)
            // ==========================================
            val pl = effectiveSelectedPlaylist

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // Online Playlist indicator banner
                if (pl.isOnlineFolder) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFD1FAE5).copy(alpha = 0.65f))
                            .border(1.dp, if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF059669).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StyledIcon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Online Playlist • Audio streams over the internet",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Media Items in Playlist — reuse the exact Home/Audio list cards so every
                // configured field (thumbnail, extension/format, size, resolution, fps,
                // subtitles, audio type, date, path, duration and progress) stays consistent.
                val isAudioPlaylist = pl.isAudio
                val playlistItems = if (isAudioPlaylist) displayPlAudioTracks else displayPlVideos
                if (playlistItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StyledIcon(
                                imageVector = if (isAudioPlaylist) Icons.Filled.MusicNote else Icons.Outlined.PlaylistAdd,
                                contentDescription = null,
                                tint = secondaryText.copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "This playlist is empty",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isAudioPlaylist) "Add audio tracks from Music & Audio" else "Add videos from your library",
                                fontSize = 12.sp,
                                color = secondaryText
                            )
                        }
                    }
                } else {
                    val detailListState = rememberLazyListState()
                    var draggingPath by remember { mutableStateOf<String?>(null) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }
                    var measuredItemHeightPx by remember { mutableFloatStateOf(0f) }
                    var isDraggingActive by remember { mutableStateOf(false) }
                    var dragPointerViewportY by remember { mutableFloatStateOf(0f) }
                    var listViewportHeightPx by remember { mutableFloatStateOf(0f) }
                    val density = LocalDensity.current
                    val haptic = LocalHapticFeedback.current

                    LaunchedEffect(isDraggingActive, dragPointerViewportY, listViewportHeightPx) {
                        if (!isDraggingActive || listViewportHeightPx <= 0f) return@LaunchedEffect
                        val edgeZone = 140f
                        while (isDraggingActive) {
                            val scrollDelta = if (dragPointerViewportY < edgeZone && dragPointerViewportY >= 0f) {
                                val factor = ((edgeZone - dragPointerViewportY) / edgeZone).coerceIn(0.2f, 1.5f)
                                -18f * factor
                            } else if (dragPointerViewportY > (listViewportHeightPx - edgeZone) && dragPointerViewportY <= listViewportHeightPx + 40f) {
                                val factor = ((dragPointerViewportY - (listViewportHeightPx - edgeZone)) / edgeZone).coerceIn(0.2f, 1.5f)
                                18f * factor
                            } else {
                                0f
                            }
                            if (scrollDelta != 0f) {
                                val consumed = detailListState.scrollBy(scrollDelta)
                                if (consumed != 0f) {
                                    dragOffsetY += consumed
                                }
                            }
                            kotlinx.coroutines.delay(16L)
                        }
                    }

                    val effectiveItemHeight = if (measuredItemHeightPx > 0f) {
                        measuredItemHeightPx
                    } else {
                        with(density) { 88.dp.toPx() }
                    }
                    val fromIndex = draggingPath?.let { p -> reorderedPaths.indexOf(p) } ?: -1
                    val hoverIndex = if (fromIndex != -1 && effectiveItemHeight > 0f) {
                        val steps = (dragOffsetY / effectiveItemHeight).roundToInt()
                        (fromIndex + steps).coerceIn(0, (reorderedPaths.size - 1).coerceAtLeast(0))
                    } else {
                        -1
                    }

                    LazyColumn(
                        state = detailListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                listViewportHeightPx = coordinates.size.height.toFloat()
                            },
                        contentPadding = PaddingValues(bottom = if (isVideoSelection) 120.dp else 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isAudioPlaylist) {
                            itemsIndexed(
                                items = displayPlAudioTracks,
                                key = { _, track -> "pl_audio_${track.path}" }
                            ) { index, track ->
                                val isSelected = selectedVideoPaths.contains(track.path)
                                val isRenaming = renamingVideoPath == track.path
                                val isBeingDragged = (draggingPath == track.path)

                                val targetTranslationY = when {
                                    isBeingDragged -> 0f
                                    draggingPath != null && fromIndex != -1 && hoverIndex != -1 -> {
                                        when {
                                            fromIndex < hoverIndex && index in (fromIndex + 1)..hoverIndex -> -effectiveItemHeight
                                            fromIndex > hoverIndex && index in hoverIndex until fromIndex -> effectiveItemHeight
                                            else -> 0f
                                        }
                                    }
                                    else -> 0f
                                }

                                val animatedTranslationY by animateFloatAsState(
                                    targetValue = targetTranslationY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "PlaylistAudioTranslationY_${track.path}"
                                )

                                val visualTranslationY = if (isBeingDragged) dragOffsetY else animatedTranslationY

                                AudioTrackListCard(
                                    track = track,
                                    isCurrent = AudioPlaybackManager.currentTrack.value?.path == track.path,
                                    isPlaying = AudioPlaybackManager.isPlaying.value && AudioPlaybackManager.currentTrack.value?.path == track.path,
                                    isDark = isDark,
                                    visibleFields = contentVisibleFields,
                                    isSelected = isSelected,
                                    isRenaming = isRenaming,
                                    onConfirmRename = { newTitle ->
                                        val src = File(track.path)
                                        val ext = src.extension
                                        val finalName = if (ext.isNotBlank()) "$newTitle.$ext" else newTitle
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val success = AudioFileManager.renameAudio(context, track, finalName)
                                            if (success) {
                                                val currentPaths = pl.videoPaths.toMutableList()
                                                val idx = currentPaths.indexOf(track.path)
                                                if (idx >= 0) currentPaths[idx] = File(src.parentFile, finalName).absolutePath
                                                PlaylistManager.updatePlaylistVideosOrder(context, pl.id, currentPaths)
                                            }
                                            withContext(Dispatchers.Main) {
                                                renamingVideoPath = null
                                                if (success) {
                                                    Toast.makeText(context, "Audio renamed to $finalName", Toast.LENGTH_SHORT).show()
                                                    val tracks = AudioLibraryCache.getOrScanAudio(context, force = true)
                                                    allAudioTracks.clear(); allAudioTracks.addAll(tracks)
                                                    refreshPlaylists()
                                                } else Toast.makeText(context, "Failed to rename audio file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onCancelRename = { renamingVideoPath = null },
                                    onClick = {
                                        if (isVideoSelection) {
                                            if (isSelected) selectedVideoPaths.remove(track.path) else selectedVideoPaths.add(track.path)
                                        } else {
                                            AudioPlaybackManager.playTrack(context, track, plAudioTracks)
                                            AudioPlaybackManager.openFullScreen()
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelected) selectedVideoPaths.remove(track.path) else selectedVideoPaths.add(track.path)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isBeingDragged) 99f else 1f)
                                        .graphicsLayer {
                                            translationY = visualTranslationY
                                            if (isBeingDragged) {
                                                scaleX = 1.028f
                                                scaleY = 1.028f
                                                shadowElevation = 18.dp.toPx()
                                                shape = RoundedCornerShape(16.dp)
                                                clip = false
                                            }
                                        }
                                        .then(if (isBeingDragged) Modifier.shadow(18.dp, RoundedCornerShape(16.dp)) else Modifier)
                                        .onGloballyPositioned { coordinates ->
                                            if (draggingPath == null && coordinates.size.height > 0) {
                                                measuredItemHeightPx = coordinates.size.height.toFloat() + with(density) { 10.dp.toPx() }
                                            }
                                        }
                                        .pointerInput(track.path, isReorderMode) {
                                            if (isReorderMode) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        try {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        } catch (_: Throwable) {}
                                                        draggingPath = track.path
                                                        dragOffsetY = 0f
                                                        isDraggingActive = true
                                                        val fIdx = reorderedPaths.indexOf(track.path)
                                                        val effH = if (measuredItemHeightPx > 0f) measuredItemHeightPx else with(density) { 88.dp.toPx() }
                                                        dragPointerViewportY = (fIdx * effH) + offset.y
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (draggingPath == track.path) {
                                                            dragOffsetY += dragAmount.y
                                                            dragPointerViewportY += dragAmount.y
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        isDraggingActive = false
                                                        val currentPaths = reorderedPaths
                                                        val fIdx = currentPaths.indexOf(track.path)
                                                        val effH = if (measuredItemHeightPx > 0f) measuredItemHeightPx else with(density) { 88.dp.toPx() }
                                                        if (fIdx != -1 && effH > 0f) {
                                                            val steps = (dragOffsetY / effH).roundToInt()
                                                            val tIdx = (fIdx + steps).coerceIn(0, currentPaths.size - 1)
                                                            if (tIdx != fIdx) {
                                                                val mutable = currentPaths.toMutableList()
                                                                val item = mutable.removeAt(fIdx)
                                                                mutable.add(tIdx, item)
                                                                reorderedPaths = mutable
                                                                PlaylistManager.updatePlaylistVideosOrder(context, pl.id, mutable)
                                                            }
                                                        }
                                                        draggingPath = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        isDraggingActive = false
                                                        draggingPath = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                        },
                                    reorderMode = isReorderMode
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = displayPlVideos,
                                key = { _, video -> "pl_video_${video.path}" }
                            ) { index, video ->
                                val isSelected = selectedVideoPaths.contains(video.path)
                                val isRenaming = renamingVideoPath == video.path
                                val isBeingDragged = (draggingPath == video.path)

                                val targetTranslationY = when {
                                    isBeingDragged -> 0f
                                    draggingPath != null && fromIndex != -1 && hoverIndex != -1 -> {
                                        when {
                                            fromIndex < hoverIndex && index in (fromIndex + 1)..hoverIndex -> -effectiveItemHeight
                                            fromIndex > hoverIndex && index in hoverIndex until fromIndex -> effectiveItemHeight
                                            else -> 0f
                                        }
                                    }
                                    else -> 0f
                                }

                                val animatedTranslationY by animateFloatAsState(
                                    targetValue = targetTranslationY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "PlaylistVideoTranslationY_${video.path}"
                                )

                                val visualTranslationY = if (isBeingDragged) dragOffsetY else animatedTranslationY

                                VideoItemCard(
                                    video = video,
                                    isDark = isDark,
                                    visibleFields = contentVisibleFields,
                                    isSelected = isSelected,
                                    isRenaming = isRenaming,
                                    onConfirmRename = { newName ->
                                        val src = File(video.path)
                                        val ext = src.extension
                                        val finalName = if (ext.isNotBlank() && !newName.endsWith(".$ext", ignoreCase = true)) "$newName.$ext" else newName
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val success = VideoFileManager.renameVideo(context, video, finalName)
                                            if (success) {
                                                val currentPaths = pl.videoPaths.toMutableList()
                                                val idx = currentPaths.indexOf(video.path)
                                                if (idx >= 0) currentPaths[idx] = File(src.parentFile, finalName).absolutePath
                                                PlaylistManager.updatePlaylistVideosOrder(context, pl.id, currentPaths)
                                            }
                                            withContext(Dispatchers.Main) {
                                                renamingVideoPath = null
                                                if (success) {
                                                    Toast.makeText(context, "Video renamed to $finalName", Toast.LENGTH_SHORT).show()
                                                    refreshPlaylists()
                                                } else Toast.makeText(context, "Failed to rename video file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onCancelRename = { renamingVideoPath = null },
                                    onClick = {
                                        if (isVideoSelection) {
                                            if (isSelected) selectedVideoPaths.remove(video.path) else selectedVideoPaths.add(video.path)
                                        } else onPlayVideo(video, plVideos)
                                    },
                                    onLongClick = {
                                        if (isSelected) selectedVideoPaths.remove(video.path) else selectedVideoPaths.add(video.path)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isBeingDragged) 99f else 1f)
                                        .graphicsLayer {
                                            translationY = visualTranslationY
                                            if (isBeingDragged) {
                                                scaleX = 1.028f
                                                scaleY = 1.028f
                                                shadowElevation = 18.dp.toPx()
                                                shape = RoundedCornerShape(16.dp)
                                                clip = false
                                            }
                                        }
                                        .then(if (isBeingDragged) Modifier.shadow(18.dp, RoundedCornerShape(16.dp)) else Modifier)
                                        .onGloballyPositioned { coordinates ->
                                            if (draggingPath == null && coordinates.size.height > 0) {
                                                measuredItemHeightPx = coordinates.size.height.toFloat() + with(density) { 10.dp.toPx() }
                                            }
                                        }
                                        .pointerInput(video.path, isReorderMode) {
                                            if (isReorderMode) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        try {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        } catch (_: Throwable) {}
                                                        draggingPath = video.path
                                                        dragOffsetY = 0f
                                                        isDraggingActive = true
                                                        val fIdx = reorderedPaths.indexOf(video.path)
                                                        val effH = if (measuredItemHeightPx > 0f) measuredItemHeightPx else with(density) { 88.dp.toPx() }
                                                        dragPointerViewportY = (fIdx * effH) + offset.y
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (draggingPath == video.path) {
                                                            dragOffsetY += dragAmount.y
                                                            dragPointerViewportY += dragAmount.y
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        isDraggingActive = false
                                                        val currentPaths = reorderedPaths
                                                        val fIdx = currentPaths.indexOf(video.path)
                                                        val effH = if (measuredItemHeightPx > 0f) measuredItemHeightPx else with(density) { 88.dp.toPx() }
                                                        if (fIdx != -1 && effH > 0f) {
                                                            val steps = (dragOffsetY / effH).roundToInt()
                                                            val tIdx = (fIdx + steps).coerceIn(0, currentPaths.size - 1)
                                                            if (tIdx != fIdx) {
                                                                val mutable = currentPaths.toMutableList()
                                                                val item = mutable.removeAt(fIdx)
                                                                mutable.add(tIdx, item)
                                                                reorderedPaths = mutable
                                                                PlaylistManager.updatePlaylistVideosOrder(context, pl.id, mutable)
                                                            }
                                                        }
                                                        draggingPath = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        isDraggingActive = false
                                                        draggingPath = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                        },
                                    reorderMode = isReorderMode,
                                    thumbnailStrategy = uiState.thumbnailStrategy,
                                    thumbnailQuality = uiState.thumbnailQuality,
                                    thumbnailFallbackSecond = uiState.thumbnailFallbackSecond,
                                    showNetworkThumbnails = uiState.showNetworkThumbnails,
                                    onThumbnailClick = null
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // PLAYLISTS ROOT LIST
            // ==========================================
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Media Type Tabs: "Video" (FIRST) | "Audio" (SECOND) (Pill style like Settings theme mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFFE2E8F0).copy(alpha = 0.8f))
                        .border(1.dp, borderColor, CircleShape)
                        .padding(4.dp)
                ) {
                    // 1. Video Tab (FIRST)
                    val isVideoTab = selectedMediaTab == "VIDEO"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(if (isVideoTab) AccentGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable { selectedMediaTab = "VIDEO" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = null,
                                tint = if (isVideoTab) Color.White else secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Video",
                                fontSize = 13.5.sp,
                                fontWeight = if (isVideoTab) FontWeight.Bold else FontWeight.Medium,
                                color = if (isVideoTab) Color.White else secondaryText
                            )
                        }
                    }

                    // 2. Audio Tab (SECOND)
                    val isAudioTab = selectedMediaTab == "AUDIO"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(if (isAudioTab) AccentGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable { selectedMediaTab = "AUDIO" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            StyledIcon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = if (isAudioTab) Color.White else secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Audio",
                                fontSize = 13.5.sp,
                                fontWeight = if (isAudioTab) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAudioTab) Color.White else secondaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isPlaylistSelection) {
                    // Normal Action Bar (Count and + New Playlist)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredPlaylists.size} ${if (selectedMediaTab == "AUDIO") "audio" else "video"} playlist${if (filteredPlaylists.size != 1) "s" else ""}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryText
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AccentGradient)
                                .clickable { showCreatePlaylistDialog = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Create",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Playlist",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Playlists List
                if (sortedPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StyledIcon(
                                imageVector = Icons.Outlined.PlaylistPlay,
                                contentDescription = null,
                                tint = secondaryText.copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No playlists created yet" else "No playlists matching \"$searchQuery\"",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ New Playlist' to organize videos into custom series or collections",
                                fontSize = 12.sp,
                                color = secondaryText
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AccentGradient)
                                        .clickable { showCreatePlaylistDialog = true }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StyledIcon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "New",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Create Playlist",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (folderLayoutMode == LayoutMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = if (isPlaylistSelection) 120.dp else 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = sortedPlaylists,
                                key = { "pl_grid_${it.id}" }
                            ) { pl ->
                                val isSelected = selectedPlaylistIds.contains(pl.id)
                                val isRenamingThisPl = renamingPlaylistId == pl.id
                                PlaylistCard(
                                    playlist = pl,
                                    isDark = isDark,
                                    visibleFields = folderVisibleFields,
                                    videoItems = allLibraryVideos,
                                    audioItems = allAudioTracks,
                                    isSelected = isSelected,
                                    isRenaming = isRenamingThisPl,
                                    onConfirmRename = { newName ->
                                        if (newName.isNotBlank()) {
                                            PlaylistManager.renamePlaylist(context, pl.id, newName)
                                            refreshPlaylists()
                                            Toast.makeText(context, "Playlist renamed to $newName", Toast.LENGTH_SHORT).show()
                                        }
                                        renamingPlaylistId = null
                                        selectedPlaylistIds.clear()
                                    },
                                    onCancelRename = { renamingPlaylistId = null },
                                    onClick = {
                                        if (isPlaylistSelection) {
                                            if (selectedPlaylistIds.contains(pl.id)) {
                                                selectedPlaylistIds.remove(pl.id)
                                            } else {
                                                selectedPlaylistIds.add(pl.id)
                                            }
                                        } else {
                                            setSelectedPl(pl)
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedPlaylistIds.contains(pl.id)) {
                                            selectedPlaylistIds.remove(pl.id)
                                        } else {
                                            selectedPlaylistIds.add(pl.id)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = if (isPlaylistSelection) 120.dp else 140.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = sortedPlaylists,
                                key = { "pl_list_${it.id}" }
                            ) { pl ->
                                val isSelected = selectedPlaylistIds.contains(pl.id)
                                val isRenamingThisPl = renamingPlaylistId == pl.id
                                PlaylistCard(
                                    playlist = pl,
                                    isDark = isDark,
                                    visibleFields = folderVisibleFields,
                                    videoItems = allLibraryVideos,
                                    audioItems = allAudioTracks,
                                    isSelected = isSelected,
                                    isRenaming = isRenamingThisPl,
                                    onConfirmRename = { newName ->
                                        if (newName.isNotBlank()) {
                                            PlaylistManager.renamePlaylist(context, pl.id, newName)
                                            refreshPlaylists()
                                            Toast.makeText(context, "Playlist renamed to $newName", Toast.LENGTH_SHORT).show()
                                        }
                                        renamingPlaylistId = null
                                        selectedPlaylistIds.clear()
                                    },
                                    onCancelRename = { renamingPlaylistId = null },
                                    onClick = {
                                        if (isPlaylistSelection) {
                                            if (selectedPlaylistIds.contains(pl.id)) {
                                                selectedPlaylistIds.remove(pl.id)
                                            } else {
                                                selectedPlaylistIds.add(pl.id)
                                            }
                                        } else {
                                            setSelectedPl(pl)
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedPlaylistIds.contains(pl.id)) {
                                            selectedPlaylistIds.remove(pl.id)
                                        } else {
                                            selectedPlaylistIds.add(pl.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // FLOATING BOTTOM TOOL ACTION BAR
        // ==========================================
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            val alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f)
            val barBg = if (isDark) {
                if (alphaRatio >= 0.99f) Color(0xFF0F172A)
                else Color(0xFF0F172A).copy(alpha = (0.70f + 0.30f * alphaRatio).coerceIn(0.40f, 1.0f))
            } else {
                if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
                else Color(0xFFFFFFFF).copy(alpha = (0.75f + 0.25f * alphaRatio).coerceIn(0.45f, 1.0f))
            }
            val barShape = RoundedCornerShape(26.dp)

            AnimatedContent(
                targetState = showDeleteConfirmDialog,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)) { it } + fadeIn(animationSpec = androidx.compose.animation.core.tween(220)))
                            .togetherWith(slideOutVertically(animationSpec = androidx.compose.animation.core.tween(160)) { it / 2 } + fadeOut(animationSpec = androidx.compose.animation.core.tween(140)))
                    } else {
                        (slideInVertically(animationSpec = androidx.compose.animation.core.tween(200)) { it / 2 } + fadeIn(animationSpec = androidx.compose.animation.core.tween(200)))
                            .togetherWith(slideOutVertically(animationSpec = androidx.compose.animation.core.tween(160)) { it } + fadeOut(animationSpec = androidx.compose.animation.core.tween(140)))
                    }
                },
                label = "PlaylistSelectionBarTransition"
            ) { isDeleting ->
                if (isDeleteInProgress) {
                    BottomMoveCopyProgressBar(
                        isDark = isDark,
                        alphaRatio = alphaRatio,
                        isMove = false,
                        isDelete = true,
                        customActionText = if (isVideoSelection || effectiveSelectedPlaylist != null) "Removing..." else "Deleting...",
                        progressFraction = deleteProgressFraction,
                        currentItemName = deleteItemName,
                        completedCount = deleteCompletedCount,
                        totalCount = deleteTotalCount
                    )
                } else if (isDeleting && isPlaylistSelection && selectedPlaylistIds.isNotEmpty()) {
                    val count = selectedPlaylistIds.size
                    val firstName = playlists.find { it.id == selectedPlaylistIds.first() }?.name ?: "Playlist"
                    val title = if (count == 1) "Delete \"$firstName\"?" else "Delete $count playlists?"
                    BottomDeleteConfirmationBar(
                        title = title,
                        message = "Media files on disk will not be deleted.",
                        count = count,
                        isDark = isDark,
                        alphaRatio = alphaRatio,
                        onCancel = { showDeleteConfirmDialog = false },
                        onConfirm = {
                            showDeleteConfirmDialog = false
                            val idsToDelete = selectedPlaylistIds.toList()
                            selectedPlaylistIds.clear()

                            isDeleteInProgress = true
                            deleteProgressFraction = 0f
                            deleteTotalCount = idsToDelete.size
                            deleteCompletedCount = 0
                            deleteItemName = ""

                            coroutineScope.launch(Dispatchers.IO) {
                                idsToDelete.forEachIndexed { index, id ->
                                    val plName = playlists.find { it.id == id }?.name ?: "Playlist"
                                    deleteItemName = plName
                                    PlaylistManager.deletePlaylist(context, id)
                                    deleteCompletedCount = index + 1
                                    deleteProgressFraction = (index + 1).toFloat() / idsToDelete.size.coerceAtLeast(1)
                                }
                                withContext(Dispatchers.Main) {
                                    deleteProgressFraction = 1f
                                    kotlinx.coroutines.delay(200L)
                                    isDeleteInProgress = false
                                    if (effectiveSelectedPlaylist != null && idsToDelete.contains(effectiveSelectedPlaylist.id)) {
                                        setSelectedPl(null)
                                    }
                                    refreshPlaylists()
                                    Toast.makeText(context, "Deleted $count playlist(s)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                } else if (isDeleting && isVideoSelection && selectedVideoPaths.isNotEmpty()) {
                    val count = selectedVideoPaths.size
                    val isAudioPl = effectiveSelectedPlaylist?.isAudio == true
                    val title = if (count == 1) "Remove from playlist?" else "Remove $count ${if (isAudioPl) "audio tracks" else "videos"}?"
                    BottomDeleteConfirmationBar(
                        title = title,
                        message = "Media files on disk will not be deleted.",
                        count = count,
                        isDark = isDark,
                        alphaRatio = alphaRatio,
                        onCancel = { showDeleteConfirmDialog = false },
                        onConfirm = {
                            showDeleteConfirmDialog = false
                            val currentPl = effectiveSelectedPlaylist
                            val pathsToRemove = selectedVideoPaths.toList()
                            selectedVideoPaths.clear()
                            if (currentPl != null) {
                                isDeleteInProgress = true
                                deleteProgressFraction = 0f
                                deleteTotalCount = pathsToRemove.size
                                deleteCompletedCount = 0
                                deleteItemName = ""

                                coroutineScope.launch(Dispatchers.IO) {
                                    pathsToRemove.forEachIndexed { index, vPath ->
                                        val itemName = vPath.substringAfterLast('/')
                                        deleteItemName = itemName
                                        PlaylistManager.removeVideoFromPlaylist(context, currentPl.id, vPath)
                                        deleteCompletedCount = index + 1
                                        deleteProgressFraction = (index + 1).toFloat() / pathsToRemove.size.coerceAtLeast(1)
                                    }
                                    withContext(Dispatchers.Main) {
                                        deleteProgressFraction = 1f
                                        kotlinx.coroutines.delay(200L)
                                        isDeleteInProgress = false
                                        refreshPlaylists()
                                        Toast.makeText(context, "Removed $count ${if (isAudioPl) "audio track(s)" else "video(s)"} from playlist", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .shadow(
                                elevation = 18.dp,
                                shape = barShape,
                                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                                spotColor = AccentPink.copy(alpha = 0.40f)
                            )
                            .clip(barShape)
                            .background(barBg)
                            .border(
                                width = 1.5.dp,
                                brush = AccentGradient,
                                shape = barShape
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaylistSelection) {
                            // Actions for Selected Playlist(s) in Root List:
                            // Rename (Inline), Delete (Bottom Confirmation), Copy, Play (NO Redundant Share)
                            val canRename = selectedPlaylistIds.size == 1
                            val canDelete = selectedPlaylistIds.isNotEmpty()
                            val canDuplicate = selectedPlaylistIds.isNotEmpty()
                            val canPlay = selectedPlaylistIds.isNotEmpty()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomSelectionBarItem(
                                    icon = Icons.Outlined.DriveFileRenameOutline,
                                    label = "Rename",
                                    tint = if (canRename) primaryText else primaryText.copy(alpha = 0.35f),
                                    enabled = canRename,
                                    onClick = {
                                        val singleId = selectedPlaylistIds.firstOrNull()
                                        if (singleId != null) {
                                            renamingPlaylistId = singleId
                                        }
                                    }
                                )
                                BottomSelectionBarItem(
                                    icon = Icons.Outlined.DeleteOutline,
                                    label = "Delete",
                                    tint = if (canDelete) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.35f),
                                    enabled = canDelete,
                                    onClick = {
                                        showDeleteConfirmDialog = true
                                    }
                                )
                                BottomSelectionBarItem(
                                    icon = Icons.Outlined.ContentCopy,
                                    label = "Copy",
                                    tint = if (canDuplicate) Color(0xFF818CF8) else Color(0xFF818CF8).copy(alpha = 0.35f),
                                    enabled = canDuplicate,
                                    onClick = {
                                        selectedPlaylistIds.forEach { id ->
                                            PlaylistManager.duplicatePlaylist(context, id)
                                        }
                                        selectedPlaylistIds.clear()
                                        refreshPlaylists()
                                        Toast.makeText(context, "Playlist(s) duplicated", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                BottomSelectionBarItem(
                                    icon = Icons.Filled.PlayArrow,
                                    label = "Play",
                                    tint = if (canPlay) AccentSkyBlue else AccentSkyBlue.copy(alpha = 0.35f),
                                    enabled = canPlay,
                                    onClick = {
                                        val selectedPls = playlists.filter { selectedPlaylistIds.contains(it.id) }
                                        val firstAudioPl = selectedPls.firstOrNull { it.isAudio }
                                        if (firstAudioPl != null) {
                                            val tracks = firstAudioPl.videoPaths.mapNotNull { path -> allAudioTracks.find { it.path == path } }
                                            if (tracks.isNotEmpty()) {
                                                selectedPlaylistIds.clear()
                                                AudioPlaybackManager.playTrack(context, tracks.first(), tracks)
                                                AudioPlaybackManager.openFullScreen()
                                            } else Toast.makeText(context, "Selected audio playlist has no available tracks", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val allPlVideos = selectedPls.flatMap { PlaylistManager.getVideosForPlaylist(it, allLibraryVideos) }.distinctBy { it.path }
                                            if (allPlVideos.isNotEmpty()) {
                                                selectedPlaylistIds.clear()
                                                onPlayVideo(allPlVideos.first(), allPlVideos)
                                            } else Toast.makeText(context, "Selected playlists have no videos", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        } else if (isVideoSelection && effectiveSelectedPlaylist != null) {
                            val currentPl = effectiveSelectedPlaylist
                            val canRenameItem = selectedVideoPaths.size == 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomSelectionBarItem(
                                    icon = Icons.Outlined.DriveFileRenameOutline,
                                    label = "Rename",
                                    tint = if (canRenameItem) primaryText else primaryText.copy(alpha = 0.35f),
                                    enabled = canRenameItem,
                                    onClick = { renamingVideoPath = selectedVideoPaths.firstOrNull() }
                                )
                                BottomSelectionBarItem(
                                    icon = Icons.Outlined.DeleteOutline,
                                    label = "Remove",
                                    tint = Color(0xFFEF4444),
                                    enabled = selectedVideoPaths.isNotEmpty(),
                                    onClick = { showDeleteConfirmDialog = true }
                                )
                                BottomSelectionBarItem(
                                    icon = Icons.Outlined.SwapVert,
                                    label = "Arrange",
                                    tint = AccentSkyBlue,
                                    enabled = selectedVideoPaths.isNotEmpty(),
                                    onClick = {
                                        selectedVideoPaths.clear()
                                        isReorderMode = true
                                        Toast.makeText(
                                            context,
                                            "Arrange mode on — touch and hold, then drag to reorder",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // FLOATING ARRANGE MODE DONE BAR
        // ==========================================
        AnimatedVisibility(
            visible = isReorderMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, CircleShape, spotColor = AccentSkyBlue.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(AccentGradient)
                    .clickable {
                        isReorderMode = false
                        Toast.makeText(context, "Playlist order saved", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Done Arranging",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ==========================================
        // DIALOGS
        // ==========================================

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            RenameDialog(
                title = if (selectedMediaTab == "AUDIO") "Create Audio Playlist" else "Create Video Playlist",
                initialText = "",
                isDark = isDark,
                alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.2f, 1f),
                onDismiss = { showCreatePlaylistDialog = false },
                onConfirm = { name ->
                    showCreatePlaylistDialog = false
                    if (name.isNotBlank()) {
                        PlaylistManager.createPlaylist(context, name, mediaType = selectedMediaTab)
                        refreshPlaylists()
                        Toast.makeText(context, "Playlist created: $name", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Rename Playlist Dialog
        if (showRenameDialog && renamePlaylistTargetId != null) {
            RenameDialog(
                title = "Rename Playlist",
                initialText = renamePlaylistName,
                isDark = isDark,
                alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.2f, 1f),
                onDismiss = {
                    showRenameDialog = false
                    renamePlaylistTargetId = null
                },
                onConfirm = { newName ->
                    val targetId = renamePlaylistTargetId
                    showRenameDialog = false
                    renamePlaylistTargetId = null
                    if (targetId != null && newName.isNotBlank()) {
                        PlaylistManager.renamePlaylist(context, targetId, newName)
                        selectedPlaylistIds.clear()
                        refreshPlaylists()
                        Toast.makeText(context, "Playlist renamed to $newName", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Rename Video in Playlist Dialog
        if (showRenameVideoDialog && videoToRename != null) {
            val currentTargetVideo = videoToRename!!
            RenameDialog(
                title = "Rename Video",
                initialText = renameVideoNameText,
                isDark = isDark,
                alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.2f, 1f),
                onDismiss = {
                    showRenameVideoDialog = false
                    videoToRename = null
                },
                onConfirm = { newBaseName ->
                    showRenameVideoDialog = false
                    val srcFile = File(currentTargetVideo.path)
                    val ext = srcFile.extension
                    val finalFileName = if (ext.isNotBlank()) "$newBaseName.$ext" else newBaseName
                    videoToRename = null

                    coroutineScope.launch(Dispatchers.IO) {
                        val success = VideoFileManager.renameVideo(context, currentTargetVideo, finalFileName)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "Video renamed to $finalFileName", Toast.LENGTH_SHORT).show()
                                selectedVideoPaths.clear()
                                refreshPlaylists()
                            } else {
                                Toast.makeText(context, "Failed to rename video file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Clean Playlist Card matching Home Screen folder cards:
 * - ModernFolderIcon on the left
 * - Name and badges in the middle
 * - Selected state with gradient border and elevated shadow
 * - Clean layout without any play button at the end
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: CustomPlaylist,
    isDark: Boolean,
    visibleFields: VisibleFields = VisibleFields(),
    videoItems: List<VideoItem> = emptyList(),
    audioItems: List<AudioTrackItem> = emptyList(),
    isSelected: Boolean = false,
    isRenaming: Boolean = false,
    onConfirmRename: ((String) -> Unit)? = null,
    onCancelRename: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) {
        if (isSelected) Color(0xFF1E1B4B).copy(alpha = 0.90f) else DarkGlassSurface
    } else {
        if (isSelected) Color(0xFFEEF2FF).copy(alpha = 0.95f) else LightGlassSurface
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    // Intentionally mirrors VideoFolderCard list dimensions: 16dp radius, 14dp padding,
    // 52dp folder glyph and 14dp gap. Only the metadata labels differ for playlists.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = AccentPink.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) AccentGradient else SolidColor(borderColor),
                RoundedCornerShape(16.dp)
            )
            .then(if (!isRenaming) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .padding(14.dp)
            .testTag("playlist_card_${playlist.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ModernFolderIcon(
                isCustom = true,
                newCount = playlist.videoPaths.size,
                showNewBadge = false,
                isDark = isDark,
                isSelected = isSelected,
                size = 52.dp,
                glyphType = if (playlist.isAudio) FolderGlyphType.AUDIO else FolderGlyphType.PLAYLIST
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                if (isRenaming) {
                    InlineRenameField(
                        initialText = playlist.name,
                        onConfirm = { onConfirmRename?.invoke(it) },
                        onCancel = { onCancelRename?.invoke() },
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playlist.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText,
                            maxLines = if (visibleFields.showFullName) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (playlist.isOnlineFolder) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.85f) else Color(0xFFD1FAE5))
                                    .border(1.dp, if (isDark) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF059669).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "ONLINE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                                )
                            }
                        }
                    }
                }
                if (visibleFields.showPath) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.videoPaths.firstOrNull()?.let { File(it).parent ?: it } ?: "",
                        fontSize = 11.sp, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isRenaming) {
                    val playlistVideos = videoItems.filter { playlist.videoPaths.contains(it.path) }
                    val playlistAudio = audioItems.filter { playlist.videoPaths.contains(it.path) }
                    val totalDurationMs = if (playlist.isAudio) playlistAudio.sumOf { it.durationMs } else playlistVideos.sumOf { it.durationMs }
                    val totalSizeBytes = if (playlist.isAudio) playlistAudio.sumOf { it.sizeBytes } else playlistVideos.sumOf { it.sizeBytes }
                    val resolution = playlistVideos.map { it.resolution }.firstOrNull { it.isNotBlank() } ?: ""
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (visibleFields.showVideoCount) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StyledIcon(
                                        imageVector = if (playlist.isAudio) Icons.Filled.MusicNote else Icons.Outlined.Movie,
                                        contentDescription = null,
                                        tint = AccentSkyBlue,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (playlist.isAudio) "${playlist.videoPaths.size} Audio" else "${playlist.videoPaths.size} Videos",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                }
                            }
                        }
                        // Permanent Online Field for Online Playlist folders:
                        if (playlist.isOnlineFolder) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.70f) else Color(0xFFD1FAE5))
                                    .border(1.dp, if (isDark) Color(0xFF10B981) else Color(0xFF059669), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StyledIcon(
                                        imageVector = Icons.Filled.Cloud,
                                        contentDescription = "Online",
                                        tint = if (isDark) Color(0xFF34D399) else Color(0xFF065F46),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Online",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                                    )
                                }
                            }
                        }
                        if (visibleFields.showDuration && totalDurationMs > 0L) {
                            MetaPill(text = formatDuration(totalDurationMs), primaryText = primaryText, secondaryText = secondaryText, isDark = isDark)
                        }
                        if (visibleFields.showResolution && resolution.isNotBlank()) {
                            MetaPill(text = resolution, primaryText = primaryText, secondaryText = secondaryText, isDark = isDark)
                        }
                        if (visibleFields.showDate) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                    .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(text = playlist.formattedDate, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = secondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun MetaPill(text: String, primaryText: Color, secondaryText: Color, isDark: Boolean) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
            .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = secondaryText)
    }
}
