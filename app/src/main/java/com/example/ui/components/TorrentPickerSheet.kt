/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Torrent file picker (download only, torrents are never played): metadata first, the file list is shown, the user picks
 * a file and it is added to the downloads. The screen itself is Lumora's own sheet.
 */

package com.example.ui.components

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
import com.example.torrent.TorrentPickerRequest
import com.example.torrent.TorrentPickerState
import com.example.torrent.TorrentCatalogs
import com.example.torrent.formatTorrentBytes
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.DownloadManager
import kotlinx.coroutines.CancellationException

private sealed interface PickerState {
    data object Loading : PickerState
    data class Ready(val catalog: TorrentCatalog) : PickerState
    data class Failed(val message: String) : PickerState
}

/**
 * Global torrent picker, hosted once in MainActivity. Opened through [TorrentPickerState] (Network tab, magnet / .torrent
 * intent). A torrent with ONE file is added to the downloads at once.
 */
@Composable
fun TorrentPickerHost(
    isDark: Boolean,
    glassBlurTransparency: Float
) {
    val request = TorrentPickerState.request ?: return
    TorrentPickerSheet(request, isDark, glassBlurTransparency)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TorrentPickerSheet(
    request: TorrentPickerRequest,
    isDark: Boolean,
    glassBlurTransparency: Float
) {
    val context = LocalContext.current
    val engine = remember { TorrentCatalogs.engine(context) }
    val enginePhase by engine.phase.collectAsState()
    var state by remember(request) { mutableStateOf<PickerState>(PickerState.Loading) }
    var attempt by remember(request) { mutableStateOf(0) }
    var preparationId by remember(request) { mutableStateOf<String?>(null) }

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val fieldBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    fun launchDownload(catalog: TorrentCatalog, file: TorrentFileItem) {
        engine.discardPreparation(catalog.preparationId)
        val message = try {
            DownloadManager.enqueueTorrent(context, catalog.source, file.index, file.name, file.size, catalog.torrentName)
        } catch (t: Throwable) {
            "Could not start the download"
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        TorrentPickerState.close()
    }

    LaunchedEffect(request, attempt) {
        state = PickerState.Loading
        try {
            val catalog = engine.prepareTorrent(request.source)
            preparationId = catalog.preparationId
            val single = catalog.files.singleOrNull()
            if (single != null) {
                launchDownload(catalog, single)
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
            preparationId?.let(engine::discardPreparation)
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
                val phase = enginePhase
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
                    text = "${s.catalog.files.size} files · choose one to download",
                    fontSize = 12.sp,
                    color = secondaryText,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                Box(modifier = Modifier.heightIn(max = 420.dp)) {
                    LazyColumn {
                        items(s.catalog.files, key = { it.index }) { file ->
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(fieldBg)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { launchDownload(s.catalog, file) }
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
                                TorrentRowButton(R.drawable.lumora_download_minimalistic, "Download", AccentSkyBlue) {
                                    launchDownload(s.catalog, file)
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
