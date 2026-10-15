package com.example.ui.screens

import android.content.ContentUris
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.player.AudioPlaybackManager
import com.example.ui.components.SortDirection
import com.example.ui.components.SortField
import com.example.ui.components.StyledIcon
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
import com.example.util.DownloadFileSaver
import com.example.util.DownloadFormat
import com.example.util.DownloadItem
import com.example.util.DownloadManager
import com.example.util.DownloadStatus
import com.example.util.DownloadStore
import java.io.File

/**
 * Download page (opened from the download button of the Network tab): every download with its progress bar, the
 * size (done / total), the speed and the time left; finished videos play in the normal player.
 */
@Composable
fun DownloadsTabContent(
    isDark: Boolean,
    searchQuery: String,
    sortField: SortField,
    sortDirection: SortDirection,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DownloadStore.init(context) }
    val all by DownloadStore.items.collectAsState()

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val query = searchQuery.trim()
    val filtered = remember(all, query) {
        if (query.isEmpty()) all
        else all.filter { it.title.contains(query, ignoreCase = true) || (it.subtitle?.contains(query, ignoreCase = true) == true) }
    }
    val running = remember(filtered) { filtered.filter { it.status != DownloadStatus.COMPLETED }.sortedByDescending { it.createdAt } }
    val finished = remember(filtered, sortField, sortDirection) {
        val base = filtered.filter { it.status == DownloadStatus.COMPLETED }
        val sorted = when (sortField) {
            SortField.TITLE -> base.sortedBy { it.title.lowercase() }
            SortField.SIZE -> base.sortedBy { it.totalBytes }
            else -> base.sortedBy { it.createdAt }
        }
        if (sortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted
    }

    if (all.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 56.dp, start = 32.dp, end = 32.dp)) {
                StyledIcon(
                    drawableRes = R.drawable.lumora_download_minimalistic,
                    contentDescription = null,
                    tint = secondaryText.copy(alpha = 0.7f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "No downloads yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the download icon next to a link in Recent Streams (Network tab) to save the video.",
                    fontSize = 12.5.sp,
                    color = secondaryText
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (running.isNotEmpty()) {
            item(key = "hdr_running") { SectionTitle("In progress", primaryText) }
            items(running, key = { it.id }) { d ->
                DownloadCard(d, primaryText, secondaryText, surfaceColor, borderColor) { playDownload(context, d, onPlayVideo) }
            }
        }
        if (finished.isNotEmpty()) {
            item(key = "hdr_done") { SectionTitle("Downloaded", primaryText) }
            items(finished, key = { it.id }) { d ->
                DownloadCard(d, primaryText, secondaryText, surfaceColor, borderColor) { playDownload(context, d, onPlayVideo) }
            }
        }
        item(key = "bottom_space") { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun DownloadCard(
    d: DownloadItem,
    primaryText: Color,
    secondaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val done = d.status == DownloadStatus.COMPLETED
    val failed = d.status == DownloadStatus.FAILED
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (done) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlay
                ) else Modifier
            )
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledIcon(
                drawableRes = if (done) R.drawable.lumora_play_circle else R.drawable.lumora_download_minimalistic,
                contentDescription = null,
                tint = if (failed) Color(0xFFEF4444) else AccentSkyBlue,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = d.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText(d),
                    fontSize = 12.sp,
                    color = if (failed) Color(0xFFEF4444) else secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (d.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED ->
                    CardButton(R.drawable.lumora_pause_solid, "Pause", primaryText.copy(alpha = 0.85f)) { DownloadManager.pause(context, d.id) }
                DownloadStatus.PAUSED ->
                    CardButton(R.drawable.lumora_play_solid, "Resume", AccentSkyBlue) { DownloadManager.resume(context, d.id) }
                DownloadStatus.FAILED ->
                    CardButton(R.drawable.lumora_refresh, "Retry", AccentSkyBlue) { DownloadManager.resume(context, d.id) }
                else -> {}
            }
            CardButton(R.drawable.lumora_trash_bin_2, if (done) "Delete" else "Cancel", secondaryText.copy(alpha = 0.9f)) {
                DownloadManager.remove(context, d.id)
            }
        }

        if (!done && !failed) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.padding(end = 8.dp)) {
                if (d.totalBytes > 0L && d.note == null && d.status != DownloadStatus.QUEUED) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(secondaryText.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(d.fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(AccentGradient)
                        )
                    }
                } else if (d.status == DownloadStatus.PAUSED) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(secondaryText.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(d.fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(secondaryText.copy(alpha = 0.55f))
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentSkyBlue,
                        trackColor = secondaryText.copy(alpha = 0.18f)
                    )
                }
            }
            if (d.status == DownloadStatus.DOWNLOADING && d.note == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DownloadFormat.sizeLine(d) + if (d.totalBytes > 0L) "  (${(d.fraction * 100).toInt()}%)" else "",
                        fontSize = 11.5.sp,
                        color = secondaryText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DownloadFormat.speedLine(d),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentSkyBlue,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CardButton(drawableRes: Int, description: String, tint: Color, onClick: () -> Unit) {
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
        StyledIcon(drawableRes = drawableRes, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

private fun statusText(d: DownloadItem): String = when (d.status) {
    DownloadStatus.QUEUED -> "Waiting in queue…"
    DownloadStatus.DOWNLOADING -> d.note ?: (if (d.speedBps > 0L) "Downloading" else "Starting…")
    DownloadStatus.PROCESSING -> d.note ?: "Saving to your library…"
    DownloadStatus.PAUSED -> "Paused • " + DownloadFormat.sizeLine(d)
    DownloadStatus.FAILED -> d.note ?: "Download failed"
    DownloadStatus.COMPLETED -> "Downloaded • " + DownloadFormat.bytes(d.totalBytes) + (d.subtitle?.let { " • $it" } ?: "")
}

private fun playDownload(context: android.content.Context, d: DownloadItem, onPlayVideo: (VideoItem, List<VideoItem>) -> Unit) {
    if (!DownloadFileSaver.exists(context, d)) {
        Toast.makeText(context, "This file was deleted from the phone", Toast.LENGTH_SHORT).show()
        return
    }
    val uri: Uri = d.fileUri?.let { Uri.parse(it) } ?: Uri.fromFile(File(d.filePath ?: return))
    val path = d.filePath ?: uri.toString()
    val mediaId = try { ContentUris.parseId(uri) } catch (_: Throwable) { -(kotlin.math.abs(("download:" + d.id).hashCode().toLong())) - 1L }
    if (d.isAudio) {
        val track = AudioTrackItem(
            id = mediaId,
            uri = uri,
            title = d.title,
            artist = d.subtitle ?: "Download",
            album = "Download",
            durationMs = 0L,
            path = path,
            sizeBytes = d.totalBytes,
            format = path.substringAfterLast('.', "AUDIO").uppercase(),
            thumbnailUrl = null
        )
        try {
            AudioPlaybackManager.openFullScreen()
            AudioPlaybackManager.playTrack(context = context, track = track, newQueue = listOf(track))
        } catch (_: Throwable) {
            Toast.makeText(context, "Could not start the audio player", Toast.LENGTH_SHORT).show()
        }
        return
    }
    val video = VideoItem(
        id = mediaId,
        uri = uri,
        displayName = d.title,
        path = path,
        sizeBytes = d.totalBytes,
        durationMs = 0L,
        dateModified = d.createdAt / 1000L
    )
    onPlayVideo(video, listOf(video))
}

/** Sort options of the Download page (opened from the Sort & View button of its header). */
@Composable
fun DownloadSortDialog(
    isDark: Boolean,
    sortField: SortField,
    sortDirection: SortDirection,
    onSortFieldChange: (SortField) -> Unit,
    onSortDirectionChange: (SortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surface = if (isDark) Color(0xFF0B1120) else Color.White
    val accent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val activeBg = if (isDark) Color(0xFF0284C7).copy(alpha = 0.28f) else Color(0xFFE0F2FE)

    @Composable
    fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) activeBg else Color.Transparent)
                .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else secondaryText.copy(alpha = 0.3f), RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontSize = 13.sp, color = if (selected) accent else primaryText, fontWeight = FontWeight.Medium)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(surface)
                .border(1.dp, secondaryText.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text(text = "Sort & View", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryText)
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "Sort downloaded videos by", fontSize = 12.5.sp, color = secondaryText)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Date", sortField == SortField.DATE) { onSortFieldChange(SortField.DATE) }
                Chip("Name", sortField == SortField.TITLE) { onSortFieldChange(SortField.TITLE) }
                Chip("Size", sortField == SortField.SIZE) { onSortFieldChange(SortField.SIZE) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Order", fontSize = 12.5.sp, color = secondaryText)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Ascending", sortDirection == SortDirection.ASCENDING) { onSortDirectionChange(SortDirection.ASCENDING) }
                Chip("Descending", sortDirection == SortDirection.DESCENDING) { onSortDirectionChange(SortDirection.DESCENDING) }
            }
        }
    }
}
