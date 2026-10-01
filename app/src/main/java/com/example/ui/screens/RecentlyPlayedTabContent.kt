package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AudioPlaybackManager
import com.example.ui.components.AudioTrackThumbnail
import com.example.ui.screens.AudioTrackItem
import com.example.ui.components.FolderGlyphType
import com.example.ui.components.ModernFolderIcon
import com.example.ui.components.LayoutMode
import com.example.ui.components.SortDirection
import com.example.ui.components.SortField
import com.example.ui.components.StyledIcon
import com.example.ui.components.VideoThumbnailLoader
import com.example.ui.components.ViewMode
import com.example.ui.components.VisibleFields
import com.example.ui.state.UiState
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
import com.example.util.AudioPlaybackHistoryManager
import com.example.util.AudioPlaybackResume
import com.example.util.PlaybackHistoryManager
import com.example.util.VideoPlaybackRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RecentsFolder(
    val name: String,
    val path: String,
    val records: List<VideoPlaybackRecord>,
    val totalDurationMs: Long,
    val totalWatchedMs: Long,
    val latestWatchedTimestamp: Long
)

@Composable
fun RecentlyPlayedTabContent(
    uiState: UiState,
    isDark: Boolean,
    allLibraryVideos: List<VideoItem>,
    searchQuery: String = "",
    sortField: SortField = SortField.DATE,
    sortDirection: SortDirection = SortDirection.DESCENDING,
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
    selectedFolder: RecentsFolder? = null,
    onSelectFolder: (RecentsFolder?) -> Unit = {},
    onFolderStateChange: (Boolean) -> Unit = {},
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val historyRecords = remember { mutableStateListOf<VideoPlaybackRecord>() }
    val audioHistoryRecords = remember { mutableStateListOf<AudioPlaybackResume>() }
    var selectedMediaTab by rememberSaveable { mutableStateOf("VIDEO") }
    var localSelectedFolder by remember { mutableStateOf<RecentsFolder?>(null) }
    val effectiveSelectedFolder = selectedFolder ?: localSelectedFolder

    fun setSelectedF(f: RecentsFolder?) {
        localSelectedFolder = f
        onSelectFolder(f)
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showClearAudioConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(effectiveSelectedFolder) {
        onFolderStateChange(effectiveSelectedFolder != null)
    }

    BackHandler(enabled = effectiveSelectedFolder != null) {
        setSelectedF(null)
    }

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    fun refreshAudioHistory() {
        coroutineScope.launch(Dispatchers.IO) {
            val records = AudioPlaybackHistoryManager.getAllHistory(context)
            withContext(Dispatchers.Main) {
                audioHistoryRecords.clear()
                audioHistoryRecords.addAll(records)
            }
        }
    }

    fun refreshHistory() {
        coroutineScope.launch(Dispatchers.IO) {
            val records = PlaybackHistoryManager.getAllPlaybackRecords(context)
            withContext(Dispatchers.Main) {
                historyRecords.clear()
                historyRecords.addAll(records)
                if (effectiveSelectedFolder != null) {
                    val matching = historyRecords.filter {
                        val folderPath = File(it.path).parent ?: "Unknown"
                        folderPath == effectiveSelectedFolder.path
                    }
                    if (matching.isEmpty()) {
                        setSelectedF(null)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
        refreshAudioHistory()
    }

    LaunchedEffect(selectedMediaTab) {
        if (selectedMediaTab == "AUDIO") {
            refreshAudioHistory()
        } else {
            refreshHistory()
        }
    }

    val filteredRecords = remember(historyRecords.toList(), searchQuery) {
        if (searchQuery.isBlank()) {
            historyRecords.toList()
        } else {
            historyRecords.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.path.contains(searchQuery, ignoreCase = true) ||
                        (File(it.path).parent ?: "").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val sortedRecords: List<VideoPlaybackRecord> = remember(filteredRecords, contentSortField, contentSortDirection) {
        when (contentSortField) {
            SortField.TITLE -> if (contentSortDirection == SortDirection.ASCENDING) {
                filteredRecords.sortedBy { it.title.lowercase() }
            } else {
                filteredRecords.sortedByDescending { it.title.lowercase() }
            }
            SortField.DATE -> if (contentSortDirection == SortDirection.ASCENDING) {
                filteredRecords.sortedBy { it.lastWatchedTimestamp }
            } else {
                filteredRecords.sortedByDescending { it.lastWatchedTimestamp }
            }
            SortField.DURATION -> if (contentSortDirection == SortDirection.DESCENDING) filteredRecords.sortedByDescending { it.durationMs } else filteredRecords.sortedBy { it.durationMs }
            SortField.SIZE -> filteredRecords
            SortField.COUNT -> filteredRecords
        }
    }

    val filteredAudioRecords = remember(audioHistoryRecords.toList(), searchQuery) {
        if (searchQuery.isBlank()) {
            audioHistoryRecords.toList()
        } else {
            audioHistoryRecords.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true) ||
                        it.path.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val sortedAudioRecords: List<AudioPlaybackResume> = remember(filteredAudioRecords, contentSortField, contentSortDirection) {
        when (contentSortField) {
            SortField.TITLE -> if (contentSortDirection == SortDirection.ASCENDING) {
                filteredAudioRecords.sortedBy { it.title.lowercase() }
            } else {
                filteredAudioRecords.sortedByDescending { it.title.lowercase() }
            }
            SortField.DATE -> if (contentSortDirection == SortDirection.ASCENDING) {
                filteredAudioRecords.sortedBy { it.lastWatchedTimestamp }
            } else {
                filteredAudioRecords.sortedByDescending { it.lastWatchedTimestamp }
            }
            SortField.DURATION -> if (contentSortDirection == SortDirection.DESCENDING) {
                filteredAudioRecords.sortedByDescending { it.durationMs }
            } else {
                filteredAudioRecords.sortedBy { it.durationMs }
            }
            else -> filteredAudioRecords
        }
    }

    // Grouping by folder for FOLDER mode (contains ONLY folders represented in recent playback history)
    val recentsFolders: List<RecentsFolder> = remember(sortedRecords) {
        sortedRecords.groupBy { record ->
            val f = File(record.path)
            f.parent ?: "Internal Storage"
        }.map { (folderPath, recordsInFolder) ->
            val folderName = File(folderPath).name.ifBlank { "Internal Storage" }
            val totalDur = recordsInFolder.sumOf { it.durationMs }
            val totalWatched = recordsInFolder.sumOf { it.positionMs }
            val latestTs = recordsInFolder.maxOfOrNull { it.lastWatchedTimestamp } ?: 0L
            RecentsFolder(
                name = folderName,
                path = folderPath,
                records = recordsInFolder,
                totalDurationMs = totalDur,
                totalWatchedMs = totalWatched,
                latestWatchedTimestamp = latestTs
            )
        }
    }

    val sortedFolders: List<RecentsFolder> = remember(recentsFolders, folderSortField, folderSortDirection) {
        when (folderSortField) {
            SortField.TITLE -> if (folderSortDirection == SortDirection.ASCENDING) {
                recentsFolders.sortedBy { it.name.lowercase() }
            } else {
                recentsFolders.sortedByDescending { it.name.lowercase() }
            }
            SortField.DATE -> if (folderSortDirection == SortDirection.ASCENDING) {
                recentsFolders.sortedBy { it.latestWatchedTimestamp }
            } else {
                recentsFolders.sortedByDescending { it.latestWatchedTimestamp }
            }
            SortField.DURATION -> if (folderSortDirection == SortDirection.DESCENDING) recentsFolders.sortedByDescending { it.totalDurationMs } else recentsFolders.sortedBy { it.totalDurationMs }
            SortField.COUNT -> if (folderSortDirection == SortDirection.ASCENDING) {
                recentsFolders.sortedBy { it.records.size }
            } else {
                recentsFolders.sortedByDescending { it.records.size }
            }
            SortField.SIZE -> recentsFolders
        }
    }

    fun playRecord(record: VideoPlaybackRecord, startFromBeginning: Boolean = false) {
        if (startFromBeginning) {
            PlaybackHistoryManager.clearPosition(
                context = context,
                videoId = record.videoId,
                path = record.path,
                uriString = record.uriString,
                title = record.title
            )
        }
        val targetVideo = allLibraryVideos.find {
            (record.videoId > 0L && it.id == record.videoId) ||
                    (record.path.isNotBlank() && it.path == record.path) ||
                    (record.title.isNotBlank() && it.displayName == record.title)
        } ?: VideoItem(
            id = if (record.videoId > 0L) record.videoId else record.path.hashCode().toLong(),
            uri = if (record.uriString.isNotBlank()) Uri.parse(record.uriString) else Uri.fromFile(File(record.path)),
            displayName = record.title.ifBlank { "Video" },
            path = record.path,
            sizeBytes = if (record.path.isNotBlank()) File(record.path).length() else 0L,
            durationMs = record.durationMs,
            dateModified = System.currentTimeMillis() / 1000L,
            isNew = false
        )
        onPlayVideo(targetVideo, allLibraryVideos.ifEmpty { listOf(targetVideo) })
    }

    fun removeRecord(record: VideoPlaybackRecord) {
        PlaybackHistoryManager.clearPosition(
            context = context,
            videoId = record.videoId,
            path = record.path,
            uriString = record.uriString,
            title = record.title
        )
        historyRecords.remove(record)
        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (effectiveSelectedFolder != null) {
                // INSIDE RECENTLY PLAYED FOLDER
                val folder = effectiveSelectedFolder
                val folderRecords = remember(folder, sortedRecords) {
                    sortedRecords.filter {
                        val p = File(it.path).parent ?: "Unknown"
                        p == folder.path
                    }
                }

                if (folderRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items remaining in this folder",
                            fontSize = 14.sp,
                            color = secondaryText
                        )
                    }
                } else {
                    if (contentLayoutMode == LayoutMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = folderRecords,
                                key = { "history_f_${it.path}_${it.lastWatchedTimestamp}" }
                            ) { record ->
                                RecentVideoCard(
                                    record = record,
                                    isDark = isDark,
                                    visibleFields = contentVisibleFields,
                                    onResume = { playRecord(record, false) },
                                    onRestart = { playRecord(record, true) },
                                    onDelete = { removeRecord(record) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = folderRecords,
                                key = { "history_f_${it.path}_${it.lastWatchedTimestamp}" }
                            ) { record ->
                                RecentVideoCard(
                                    record = record,
                                    isDark = isDark,
                                    visibleFields = contentVisibleFields,
                                    onResume = { playRecord(record, false) },
                                    onRestart = { playRecord(record, true) },
                                    onDelete = { removeRecord(record) }
                                )
                            }
                        }
                    }
                }
            } else {
                // MAIN RECENTLY PLAYED VIEW (LIBRARY OR FOLDER)
                Spacer(modifier = Modifier.height(12.dp))

                // Media Type Tabs: "Video" (FIRST) | "Audio" (SECOND) (Pill style matching Playlists & Settings)
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

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedMediaTab == "AUDIO") {
                    // AUDIO RECENTLY PLAYED VIEW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val countLabel = "${sortedAudioRecords.size} audio track${if (sortedAudioRecords.size != 1) "s" else ""}"
                        Text(
                            text = countLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryText
                        )

                        if (audioHistoryRecords.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable { showClearAudioConfirmDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StyledIcon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Clear Audio History",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Clear All",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }

                    if (sortedAudioRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                StyledIcon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = secondaryText.copy(alpha = 0.5f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isBlank()) "No recently played audio" else "No audio matching \"$searchQuery\"",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Songs and online audio you play will appear here",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                            }
                        }
                    } else {
                        if (contentLayoutMode == LayoutMode.GRID) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = sortedAudioRecords,
                                    key = { "audio_history_${it.path}_${it.id}_${it.lastWatchedTimestamp}" }
                                ) { record ->
                                    RecentAudioCard(
                                        record = record,
                                        isDark = isDark,
                                        visibleFields = contentVisibleFields,
                                        onResume = {
                                            AudioPlaybackManager.playRecentTrack(context, record, startFromBeginning = false)
                                        },
                                        onRestart = {
                                            AudioPlaybackManager.playRecentTrack(context, record, startFromBeginning = true)
                                        },
                                        onDelete = {
                                            AudioPlaybackHistoryManager.deleteRecord(context, record)
                                            audioHistoryRecords.remove(record)
                                            Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 140.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = sortedAudioRecords,
                                    key = { "audio_history_${it.path}_${it.id}_${it.lastWatchedTimestamp}" }
                                ) { record ->
                                    RecentAudioCard(
                                        record = record,
                                        isDark = isDark,
                                        visibleFields = contentVisibleFields,
                                        onResume = {
                                            AudioPlaybackManager.playRecentTrack(context, record, startFromBeginning = false)
                                        },
                                        onRestart = {
                                            AudioPlaybackManager.playRecentTrack(context, record, startFromBeginning = true)
                                        },
                                        onDelete = {
                                            AudioPlaybackHistoryManager.deleteRecord(context, record)
                                            audioHistoryRecords.remove(record)
                                            Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // VIDEO RECENTLY PLAYED VIEW
                    // Clean Action Bar (Replaces duplicate secondary header)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val countLabel = if (viewMode == ViewMode.FOLDER) {
                            "${sortedFolders.size} folder${if (sortedFolders.size != 1) "s" else ""} with recent history"
                        } else {
                            "${sortedRecords.size} video${if (sortedRecords.size != 1) "s" else ""}"
                        }
                        Text(
                            text = countLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryText
                        )

                        if (historyRecords.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable { showClearConfirmDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StyledIcon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Clear History",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Clear All",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }

                    // Content (Library or Folder Mode)
                    if (viewMode == ViewMode.FOLDER) {
                        // FOLDER VIEW MODE
                        if (sortedFolders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    StyledIcon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = secondaryText.copy(alpha = 0.5f),
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) "No recently played folders" else "No folders matching \"$searchQuery\"",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Folders will appear here as you watch videos",
                                        fontSize = 12.sp,
                                        color = secondaryText
                                    )
                                }
                            }
                        } else {
                            if (folderLayoutMode == LayoutMode.GRID) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 140.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = sortedFolders,
                                        key = { "recents_folder_${it.path}" }
                                    ) { folder ->
                                        RecentsFolderCard(
                                            folder = folder,
                                            isDark = isDark,
                                            visibleFields = folderVisibleFields,
                                            onClick = { setSelectedF(folder) }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 140.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = sortedFolders,
                                        key = { "recents_folder_${it.path}" }
                                    ) { folder ->
                                        RecentsFolderCard(
                                            folder = folder,
                                            isDark = isDark,
                                            visibleFields = folderVisibleFields,
                                            onClick = { setSelectedF(folder) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // LIBRARY VIEW MODE (DEFAULT)
                        if (sortedRecords.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    StyledIcon(
                                        imageVector = Icons.Outlined.Movie,
                                        contentDescription = null,
                                        tint = secondaryText.copy(alpha = 0.5f),
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) "No recently played videos" else "No results for \"$searchQuery\"",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Videos you watch will appear here with instant resume timestamps",
                                        fontSize = 12.sp,
                                        color = secondaryText
                                    )
                                }
                            }
                        } else {
                            if (contentLayoutMode == LayoutMode.GRID) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 140.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = sortedRecords,
                                        key = { "history_${it.path}_${it.lastWatchedTimestamp}" }
                                    ) { record ->
                                        RecentVideoCard(
                                            record = record,
                                            isDark = isDark,
                                            visibleFields = contentVisibleFields,
                                            onResume = { playRecord(record, false) },
                                            onRestart = { playRecord(record, true) },
                                            onDelete = { removeRecord(record) }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 140.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = sortedRecords,
                                        key = { "history_${it.path}_${it.lastWatchedTimestamp}" }
                                    ) { record ->
                                        RecentVideoCard(
                                            record = record,
                                            isDark = isDark,
                                            visibleFields = contentVisibleFields,
                                            onResume = { playRecord(record, false) },
                                            onRestart = { playRecord(record, true) },
                                            onDelete = { removeRecord(record) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clear All Confirmation Dialog
        if (showClearConfirmDialog) {
            DeleteConfirmDialog(
                title = "Clear Video Playback History",
                message = "Are you sure you want to clear all video playback history and saved resume timestamps? (Your video files on disk will not be deleted)",
                isDark = isDark,
                alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.2f, 1f),
                onDismiss = { showClearConfirmDialog = false },
                onConfirm = {
                    showClearConfirmDialog = false
                    PlaybackHistoryManager.clearAllHistory(context)
                    historyRecords.clear()
                    setSelectedF(null)
                    Toast.makeText(context, "Video history cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Clear Audio Confirmation Dialog
        if (showClearAudioConfirmDialog) {
            DeleteConfirmDialog(
                title = "Clear Audio History",
                message = "Are you sure you want to clear all audio playback history and saved resume timestamps? (Your audio files on disk will not be deleted)",
                isDark = isDark,
                alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.2f, 1f),
                onDismiss = { showClearAudioConfirmDialog = false },
                onConfirm = {
                    showClearAudioConfirmDialog = false
                    AudioPlaybackHistoryManager.clearAllHistory(context)
                    audioHistoryRecords.clear()
                    Toast.makeText(context, "Audio history cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun RecentsFolderCard(
    folder: RecentsFolder,
    isDark: Boolean,
    visibleFields: VisibleFields,
    onClick: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val relativeTime = formatRecentsRelativeTime(folder.latestWatchedTimestamp)
    val watchedDurFormatted = formatRecentsDuration(folder.totalWatchedMs)
    val totalDurFormatted = formatRecentsDuration(folder.totalDurationMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("recents_folder_${folder.path.hashCode()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modern Folder Icon with Video glyph
            ModernFolderIcon(
                isCustom = false,
                newCount = folder.records.size,
                showNewBadge = false,
                isDark = isDark,
                size = 52.dp,
                glyphType = FolderGlyphType.VIDEO
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = folder.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                    maxLines = if (visibleFields.showFullName) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (visibleFields.showPath && folder.path.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = folder.path,
                        fontSize = 11.sp,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (visibleFields.showVideoCount || visibleFields.showDuration || visibleFields.showDate) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (visibleFields.showVideoCount) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                    .border(
                                        1.dp,
                                        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.lumora_video),
                                        contentDescription = null,
                                        tint = AccentSkyBlue,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${folder.records.size} Videos",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                }
                            }
                        }

                        if (visibleFields.showDuration) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                    .border(
                                        1.dp,
                                        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$watchedDurFormatted / $totalDurFormatted",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryText
                                )
                            }
                        }

                        if (visibleFields.showDate) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                    .border(
                                        1.dp,
                                        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = relativeTime,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentVideoCard(
    record: VideoPlaybackRecord,
    isDark: Boolean,
    visibleFields: VisibleFields,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val percent = ((record.progressFraction) * 100).toInt().coerceIn(0, 100)
    val relativeTime = formatRecentsRelativeTime(record.lastWatchedTimestamp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("recent_card_${record.title.hashCode()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            if (visibleFields.showThumbnails) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                        .border(1.dp, if (isDark) DarkGlassBorder else LightGlassBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (record.path.isNotBlank()) {
                        VideoThumbnailLoader(
                            videoPath = record.path,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        StyledIcon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Playback progress badge on top of thumbnail
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$percent%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val displayName = if (visibleFields.showFullName) {
                    record.title.ifBlank { File(record.path).name }
                } else {
                    record.title.ifBlank { File(record.path).nameWithoutExtension }
                }

                Text(
                    text = displayName,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))

                val subtitleDetails = mutableListOf<String>()
                if (visibleFields.showDuration) {
                    subtitleDetails.add("${record.formattedPosition} / ${record.formattedDuration}")
                }
                if (visibleFields.showDate) {
                    subtitleDetails.add(relativeTime)
                }
                if (visibleFields.showExtension && record.path.isNotBlank()) {
                    val ext = File(record.path).extension
                    if (ext.isNotBlank()) subtitleDetails.add(".$ext".uppercase())
                }
                if (visibleFields.showSize && record.path.isNotBlank()) {
                    val file = File(record.path)
                    if (file.exists()) {
                        subtitleDetails.add(formatRecentsFileSize(file.length()))
                    }
                }

                Text(
                    text = subtitleDetails.joinToString(" • ").ifEmpty { "${record.formattedPosition} • $relativeTime" },
                    fontSize = 11.5.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (visibleFields.showPath && record.path.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = File(record.path).parent ?: record.path,
                        fontSize = 10.sp,
                        color = secondaryText.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Remove button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove from history",
                    tint = secondaryText.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Real Progress Bar
        if (visibleFields.showProgressBar) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { record.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentSkyBlue,
                trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Resume & Restart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Resume Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient)
                    .clickable(onClick = onResume)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resume (${record.formattedPosition})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Restart Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    .clickable(onClick = onRestart)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = null,
                        tint = primaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Restart",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText
                    )
                }
            }
        }
    }
}

@Composable
fun RecentAudioCard(
    record: AudioPlaybackResume,
    isDark: Boolean,
    visibleFields: VisibleFields,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardBg = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val percent = ((record.progressFraction) * 100).toInt().coerceIn(0, 100)
    val relativeTime = formatRecentsRelativeTime(record.lastWatchedTimestamp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onResume)
            .padding(14.dp)
            .testTag("recent_audio_card_${record.title.hashCode()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork / Thumbnail
            if (visibleFields.showThumbnails) {
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 62.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                        .border(1.dp, if (isDark) DarkGlassBorder else LightGlassBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val trackItem = remember(record) { record.toAudioTrackItem() }
                    AudioTrackThumbnail(
                        track = trackItem,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 10.dp,
                        fallbackIcon = Icons.Filled.MusicNote,
                        fallbackIconSize = 24.dp
                    )

                    // Playback progress badge on top of artwork
                    if (percent > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$percent%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // Title and Online badge row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = record.title.ifBlank { "Recent Audio" },
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (record.isOnline) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Text(
                                    text = "ONLINE",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Artist
                Text(
                    text = record.artist.ifBlank { if (record.isOnline) "Online Audio" else "Audio Track" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryText.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val subtitleDetails = mutableListOf<String>()
                if (visibleFields.showDuration && record.durationMs > 0L) {
                    subtitleDetails.add("${record.formattedPosition} / ${record.formattedDuration}")
                }
                if (visibleFields.showDate) {
                    subtitleDetails.add(relativeTime)
                }
                if (visibleFields.showExtension && !record.isOnline && record.path.isNotBlank()) {
                    val ext = File(record.path).extension
                    if (ext.isNotBlank()) subtitleDetails.add(".$ext".uppercase())
                }

                Text(
                    text = subtitleDetails.joinToString(" • ").ifEmpty { "${record.formattedPosition} • $relativeTime" },
                    fontSize = 11.5.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (visibleFields.showPath && record.path.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (record.isOnline) "Online Stream" else (File(record.path).parent ?: record.path),
                        fontSize = 10.sp,
                        color = secondaryText.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Remove button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove from history",
                    tint = secondaryText.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Real Progress Bar
        if (visibleFields.showProgressBar && percent > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { record.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (record.isOnline) Color(0xFF10B981) else AccentSkyBlue,
                trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Resume & Restart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Resume Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient)
                    .clickable(onClick = onResume)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (record.positionMs > 1000L) "Resume (${record.formattedPosition})" else "Play",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Restart Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    .clickable(onClick = onRestart)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = null,
                        tint = primaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Restart",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText
                    )
                }
            }
        }
    }
}

fun AudioPlaybackResume.toAudioTrackItem(): AudioTrackItem {
    val uri = try { if (uriString.isNotBlank()) Uri.parse(uriString) else Uri.EMPTY } catch (_: Throwable) { Uri.EMPTY }
    return AudioTrackItem(
        id = id,
        uri = uri,
        title = title.ifBlank { path.substringAfterLast('/').ifBlank { "Recent Audio" } },
        artist = artist,
        album = album,
        durationMs = durationMs,
        path = path,
        sizeBytes = 0L,
        format = if (isOnline) "STREAM" else path.substringAfterLast('.', ""),
        dateModified = lastWatchedTimestamp / 1000L,
        thumbnailUrl = thumbnailUrl
    )
}

private fun formatRecentsRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Recently"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 30 -> "${days / 30}mo ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

private fun formatRecentsDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSec = durationMs / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hr = totalSec / 3600
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

private fun formatRecentsFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
