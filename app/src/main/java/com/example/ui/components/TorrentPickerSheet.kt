/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Behaviour ported from mpvRx (ui/torrent/TorrentSelectionViewModel + TorrentSelectionActivity): metadata first, the
 * file list is shown, the user picks a file, then it plays (or downloads). The screen itself is Lumora's own sheet.
 */

package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.torrent.TorrentCatalog
import com.example.torrent.TorrentFileItem
import com.example.torrent.TorrentPickerMode
import com.example.torrent.TorrentPickerRequest
import com.example.torrent.TorrentPickerState
import com.example.torrent.TorrentStreaming
import com.example.torrent.TorrentStreamingState
import com.example.torrent.formatTorrentBytes
import com.example.torrent.formatTorrentSpeed
import com.example.ui.screens.VideoItem
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.DownloadManager
import com.example.util.RecentStreamStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PickerState {
    data object Loading : PickerState
    data class Ready(val catalog: TorrentCatalog) : PickerState
    data class Failed(val message: String) : PickerState
}

/** The VideoItem of one torrent file. [VideoItem.path] is the durable magnet / .torrent source, never the loopback URL. */
private fun torrentVideoItem(catalog: TorrentCatalog, file: TorrentFileItem): VideoItem =
    VideoItem(
        id = -(kotlin.math.abs(("torrent:${catalog.infoHash}:${file.index}").hashCode().toLong())) - 1L,
        uri = Uri.parse(catalog.source),
        displayName = file.name,
        path = catalog.source,
        sizeBytes = file.size,
        durationMs = 0L,
        dateModified = System.currentTimeMillis() / 1000L,
        isOnline = true,
        channelName = catalog.torrentName,
        isNetworkStream = true,
        torrentFileIndex = file.index
    )

/**
 * Global torrent picker, hosted once in MainActivity. Opened through [TorrentPickerState] (Network tab, magnet / .torrent
 * intent, a torrent item that was opened without a chosen file). A torrent with ONE playable file starts at once, like in mpvRx.
 */
@Composable
fun TorrentPickerHost(
    isDark: Boolean,
    glassBlurTransparency: Float,
    onPlay: (VideoItem, List<VideoItem>) -> Unit
) {
    val request = TorrentPickerState.request ?: return
    TorrentPickerSheet(request, isDark, glassBlurTransparency, onPlay)
}

@Composable
private fun TorrentPickerSheet(
    request: TorrentPickerRequest,
    isDark: Boolean,
    glassBlurTransparency: Float,
    onPlay: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val engine = remember { TorrentStreaming.engine(context) }
    val engineState by engine.state.collectAsState()
    var state by remember(request) { mutableStateOf<PickerState>(PickerState.Loading) }
    var attempt by remember(request) { mutableStateOf(0) }
    // true once a file was handed to the player / downloader: the prepared session is then NOT discarded on dismiss
    var handedOver by remember(request) { mutableStateOf(false) }
    var preparationId by remember(request) { mutableStateOf<String?>(null) }

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val fieldBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    fun launchPlay(catalog: TorrentCatalog, file: TorrentFileItem) {
        handedOver = true
        TorrentStreaming.handOffPreparation(catalog.source, file.index, catalog.preparationId)
        // a multi-file torrent is a series: every episode is a queue entry, the chosen one starts first
        val items = catalog.playableFiles.map { torrentVideoItem(catalog, it) }
        val chosen = items.first { it.torrentFileIndex == file.index }
        RecentStreamStore.record(context, catalog.source, catalog.torrentName, "Torrent", null)
        TorrentPickerState.close()
        onPlay(chosen, items)
    }

    fun launchDownload(catalog: TorrentCatalog, file: TorrentFileItem) {
        handedOver = false
        RecentStreamStore.record(context, catalog.source, catalog.torrentName, "Torrent", null)
        val app = context.applicationContext
        val preparation = catalog.preparationId
        // The picker's own libtorrent session is closed COMPLETELY before the download opens its session; the
        // composable is gone by then, so the work runs in the app scope.
        TorrentStreaming.appScope.launch {
            val message = try {
                TorrentStreaming.engine(app).discardPreparationNow(preparation)
                DownloadManager.enqueueTorrent(app, catalog.source, file.index, file.name, file.size, catalog.torrentName)
            } catch (t: Throwable) {
                "Could not start the download"
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        TorrentPickerState.close()
    }

    LaunchedEffect(request, attempt) {
        state = PickerState.Loading
        try {
            val catalog = engine.prepareTorrent(request.source)
            preparationId = catalog.preparationId
            val single = catalog.playableFiles.singleOrNull()
            if (single != null) {
                if (request.mode == TorrentPickerMode.PLAY) launchPlay(catalog, single) else launchDownload(catalog, single)
            } else {
                state = PickerState.Ready(catalog)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            state = PickerState.Failed(t.message?.take(240) ?: "Couldn't open this torrent.")
        }
    }

    DisposableEffect(request) {
        onDispose {
            if (!handedOver) preparationId?.let(engine::discardPreparation)
        }
    }

    DownloadGlassSheet(isDark, glassBlurTransparency, onDismiss = { TorrentPickerState.close() }) {
        Text(
            text = "Torrent",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            modifier = Modifier.padding(top = 4.dp)
        )
        when (val s = state) {
            PickerState.Loading -> {
                val phase = when (val e = engineState) {
                    is TorrentStreamingState.Connecting -> {
                        val extras = if (e.peers > 0) " (${e.peers} peers, ${formatTorrentSpeed(e.downloadSpeed)})" else ""
                        e.phase + extras
                    }
                    else -> "Starting Torrent Engine..."
                }
                Row(
                    modifier = Modifier.padding(top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentSkyBlue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = phase, fontSize = 13.sp, color = secondaryText)
                }
            }
            is PickerState.Failed -> {
                Text(
                    text = s.message,
                    fontSize = 13.sp,
                    color = secondaryText,
                    modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { attempt++ }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(text = "Retry", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(modifier = Modifier.size(10.dp))
            }
            is PickerState.Ready -> {
                Text(
                    text = s.catalog.torrentName,
                    fontSize = 13.sp,
                    color = secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${s.catalog.playableFiles.size} files · choose one to " +
                        (if (request.mode == TorrentPickerMode.PLAY) "play" else "download"),
                    fontSize = 12.sp,
                    color = secondaryText,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                Box(modifier = Modifier.heightIn(max = 420.dp)) {
                    LazyColumn {
                        items(s.catalog.playableFiles, key = { it.index }) { file ->
                            val primaryAction = {
                                if (request.mode == TorrentPickerMode.PLAY) launchPlay(s.catalog, file)
                                else launchDownload(s.catalog, file)
                            }
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(fieldBg)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = primaryAction
                                    )
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(text = formatTorrentBytes(file.size), fontSize = 12.sp, color = secondaryText)
                                }
                                TorrentRowButton(R.drawable.lumora_download_minimalistic, "Download", primaryText.copy(alpha = 0.85f)) {
                                    launchDownload(s.catalog, file)
                                }
                                TorrentRowButton(R.drawable.lumora_play_solid, "Play", AccentSkyBlue) {
                                    launchPlay(s.catalog, file)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentRowButton(drawableRes: Int, description: String, tint: Color, onClick: () -> Unit) {
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
        StyledIcon(
            drawableRes = drawableRes,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
    }
}
