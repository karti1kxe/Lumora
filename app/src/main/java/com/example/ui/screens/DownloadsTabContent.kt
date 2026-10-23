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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AudioPlaybackManager
import com.example.ui.components.LayoutMode
import com.example.ui.components.LinkThumbnail
import com.example.ui.components.SortDirection
import com.example.ui.components.SortField
import com.example.ui.components.StyledIcon
import com.example.ui.components.VisibleFields
import com.example.ui.state.ThumbnailQuality
import com.example.ui.state.ThumbnailStrategy
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
import com.example.util.DownloadFormat
import com.example.util.DownloadItem
import com.example.util.DownloadManager
import com.example.util.DownloadSourceType
import com.example.util.DownloadStatus
import com.example.util.DownloadStore
import java.io.File

/**
 * Universal Downloads Management Screen:
 * Supports Online Videos, Online Audio, Network Downloads, Media Plugins, and Torrents.
 * Features:
 * - Live speed (MB/s), exact sizes, and remaining ETA
 * - Multi-select mode with batch actions (Delete Selected, Pause All, Resume All)
 * - Individual controls: Play, Pause, Resume, Stop, and Delete
 * - Category filter tabs
 */
@Composable
fun DownloadsTabContent(
    isDark: Boolean,
    searchQuery: String,
    sortField: SortField,
    sortDirection: SortDirection,
    layoutMode: LayoutMode,
    visibleFields: VisibleFields,
    thumbnailStrategy: ThumbnailStrategy,
    thumbnailQuality: ThumbnailQuality,
    thumbnailFallbackSecond: Int,
    showNetworkThumbnails: Boolean,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        DownloadStore.init(context)
        DownloadManager.init(context)
    }

    val all by DownloadStore.items.collectAsState()
    val query = searchQuery.trim()

    var selectedFilter by remember { mutableStateOf("All") }
    val categories = listOf("All", "Videos", "Music", "Media", "Torrents")

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds.clear()
    }

    val entries = remember(all, query, selectedFilter) {
        all.filter { item ->
            when (selectedFilter) {
                "Videos" -> item.sourceType == DownloadSourceType.VIDEO || (!item.isAudio && item.sourceType == DownloadSourceType.NETWORK)
                "Music" -> item.isAudio || item.sourceType == DownloadSourceType.AUDIO
                "Media" -> item.sourceType == DownloadSourceType.MEDIA_PLUGIN
                "Torrents" -> item.sourceType == DownloadSourceType.TORRENT
                else -> true
            }
        }.filter {
            query.isEmpty() || it.title.contains(query, true) || it.subtitle.orEmpty().contains(query, true)
        }.sortedByDescending { it.createdAt }
    }

    val primary = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val surface = if (isDark) DarkGlassSurface else LightGlassSurface
    val border = if (isDark) DarkGlassBorder else LightGlassBorder

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header filter bar and Selection Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Filter Pills
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat == selectedFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (isSelected) Modifier.background(AccentGradient)
                                    else Modifier.background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                                )
                                .clickable { selectedFilter = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Select / Done Action Pill
                if (entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelectionMode) AccentSkyBlue.copy(alpha = 0.2f) else if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
                            .clickable {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedIds.clear()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isSelectionMode) "Done" else "Select",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelectionMode) AccentSkyBlue else primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        StyledIcon(
                            drawableRes = R.drawable.lumora_download_minimalistic,
                            contentDescription = null,
                            tint = secondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "No downloads in $selectedFilter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Downloaded videos, audio, streams, and files will appear here.",
                            fontSize = 13.sp,
                            color = secondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        UniversalDownloadCard(
                            item = item,
                            isDark = isDark,
                            primary = primary,
                            secondary = secondary,
                            surface = surface,
                            border = border,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedIds.remove(item.id)
                                else selectedIds.add(item.id)
                            },
                            onPlay = {
                                playDownloadedItem(context, item, onPlayVideo)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // Multi-Select Floating Action Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f))
                    .border(1.5.dp, AccentSkyBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedIds.size} selected",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (selectedIds.size == entries.size) "Deselect" else "Select All",
                        fontSize = 13.sp,
                        color = AccentSkyBlue,
                        modifier = Modifier
                            .clickable {
                                if (selectedIds.size == entries.size) selectedIds.clear()
                                else {
                                    selectedIds.clear()
                                    selectedIds.addAll(entries.map { it.id })
                                }
                            }
                            .padding(6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedIds.isNotEmpty()) Color(0xFFEF4444) else Color.Gray.copy(alpha = 0.3f))
                            .clickable(enabled = selectedIds.isNotEmpty()) { confirmBatchDelete = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("Delete selected downloads?") },
            text = { Text("This will permanently remove ${selectedIds.size} download tasks and files.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBatchDelete = false
                    DownloadManager.deleteMultiple(context, selectedIds.toSet())
                    selectedIds.clear()
                    isSelectionMode = false
                    Toast.makeText(context, "Downloads removed", Toast.LENGTH_SHORT).show()
                }) { Text("Delete All", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun UniversalDownloadCard(
    item: DownloadItem,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    var confirmRemove by remember(item.id) { mutableStateOf(false) }

    val completed = item.status == DownloadStatus.COMPLETED
    val failed = item.status == DownloadStatus.FAILED
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentSkyBlue else border,
                shape = shape
            )
            .clickable {
                if (isSelectionMode) onToggleSelect()
                else if (completed) onPlay()
            }
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AccentSkyBlue else Color.Transparent)
                            .border(1.5.dp, if (isSelected) AccentSkyBlue else secondary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }

                LinkThumbnail(
                    thumbnail = item.thumbnailUrl,
                    modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)),
                    fallback = {
                        StyledIcon(
                            drawableRes = if (item.isAudio) R.drawable.lumora_song else R.drawable.lumora_video,
                            contentDescription = null,
                            tint = secondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.selectedQuality?.takeIf { it.isNotBlank() }?.let { q ->
                            Text(
                                text = q,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentSkyBlue,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentSkyBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = statusText(item),
                            fontSize = 11.5.sp,
                            color = if (failed) Color(0xFFEF4444) else if (completed) AccentSkyBlue else secondary,
                            maxLines = 1
                        )
                    }
                }

                if (!isSelectionMode) {
                    when (item.status) {
                        DownloadStatus.COMPLETED -> SmallAction(R.drawable.lumora_play_solid, "Play", AccentSkyBlue) { onPlay() }
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> SmallAction(R.drawable.lumora_pause_solid, "Pause", primary) { DownloadManager.pause(context, item.id) }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> SmallAction(R.drawable.lumora_play_solid, "Resume", AccentSkyBlue) { DownloadManager.resume(context, item.id) }
                        DownloadStatus.PROCESSING -> Unit
                    }
                    SmallAction(R.drawable.lumora_trash_bin_2, if (completed) "Delete" else "Cancel", secondary) { confirmRemove = true }
                }
            }

            // Progress bar and live speed metrics
            if (!completed && !failed) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.fraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = AccentSkyBlue,
                    trackColor = secondary.copy(alpha = 0.18f)
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DownloadFormat.sizeLine(item) + if (item.totalBytes > 0) " (${(item.fraction * 100).toInt()}%)" else "",
                        modifier = Modifier.weight(1f),
                        fontSize = 11.5.sp,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(DownloadFormat.speedLine(item), fontSize = 11.5.sp, color = AccentSkyBlue, maxLines = 1)
                }
            } else if (completed) {
                Text(
                    text = DownloadFormat.bytes(item.totalBytes) + (if (item.filePath != null) " • Saved" else ""),
                    fontSize = 11.5.sp,
                    color = secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (completed) "Delete download?" else "Cancel download?") },
            text = { Text("This removes '${item.title}' from Lumora.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    DownloadManager.remove(context, item.id)
                }) { Text(if (completed) "Delete" else "Cancel download", color = Color(0xFFEF4444)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun SmallAction(drawable: Int, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        StyledIcon(drawableRes = drawable, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
    }
}

private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Queued…"
    DownloadStatus.DOWNLOADING -> item.note ?: if (item.speedBps > 0L) "Downloading" else "Connecting…"
    DownloadStatus.PROCESSING -> item.note ?: "Processing container…"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.FAILED -> item.note ?: "Download failed"
    DownloadStatus.COMPLETED -> "Ready to play"
}

private fun playDownloadedItem(
    context: android.content.Context,
    item: DownloadItem,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit
) {
    val fileUri = item.fileUri ?: item.filePath ?: return
    val parsedUri = if (fileUri.startsWith("content://") || fileUri.startsWith("file://")) Uri.parse(fileUri) else Uri.fromFile(File(fileUri))

    if (item.isAudio) {
        val track = AudioTrackItem(
            id = -(kotlin.math.abs(item.id.hashCode().toLong())) - 1L,
            uri = parsedUri,
            title = item.title,
            artist = item.subtitle ?: "Lumora Downloads",
            album = "Downloads",
            durationMs = 0L,
            path = item.filePath ?: fileUri,
            sizeBytes = item.totalBytes,
            format = item.formatContainer ?: "MP3",
            thumbnailUrl = item.thumbnailUrl
        )
        try {
            AudioPlaybackManager.openFullScreen()
            AudioPlaybackManager.playTrack(context = context, track = track, newQueue = listOf(track))
        } catch (_: Throwable) {
            Toast.makeText(context, "Could not open audio player", Toast.LENGTH_SHORT).show()
        }
    } else {
        val video = VideoItem(
            id = -(kotlin.math.abs(item.id.hashCode().toLong())) - 1L,
            uri = parsedUri,
            displayName = item.title,
            path = item.filePath ?: fileUri,
            sizeBytes = item.totalBytes,
            durationMs = 0L,
            dateModified = System.currentTimeMillis() / 1000L,
            thumbnailUrl = item.thumbnailUrl,
            channelName = item.subtitle
        )
        onPlayVideo(video, listOf(video))
    }
}
