package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AudioPlaybackManager
import com.example.ui.components.toAudioTrackItem
import com.example.ui.components.DownloadOptionsSheet
import com.example.ui.components.LinkThumbnail
import com.example.ui.components.PlaylistFilterTabs
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
import com.example.util.DownloadManager
import com.example.util.NetworkStreamResolver
import com.example.util.RecentStreamEntry
import com.example.util.RecentStreamStore
import com.example.util.RecentThumbnails
import com.example.util.TorrentLibrary
import com.example.util.TorrentOpenResult
import com.example.util.TorrentSource
import com.example.util.TorrentStreamEngine
import com.example.util.media.MediaPluginStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Network" bottom tab. Sections: Local Network (paste a link and play it), Media (plugin repositories + downloads) and one
 * extra section per downloaded media plugin (category rows with thumbnails).
 * Playback itself is NOT done here: the link is resolved (yt-dlp) and handed to [onPlayVideo], so it opens in the
 * app's normal video player with its normal controls.
 */
@Composable
fun NetworkTabContent(
    isDark: Boolean,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDownloads: () -> Unit = {},
    glassBlurTransparency: Float = 85f,
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current

    // initial tab = the one that was open before the player / detail page was opened
    var section by rememberSaveable { mutableStateOf(MediaDetailState.lastSection) }
    val mediaTick by MediaPluginStore.changes.collectAsState()
    val mediaPlugins = remember(mediaTick) { MediaPluginStore.getInstalled(context) }
    // only two sections now (Local Network, Media); a downloaded provider opens as a page from the Media list
    val safeSection = section.coerceIn(0, 1)
    var optionsEntry by remember { mutableStateOf<RecentStreamEntry?>(null) }
    var url by rememberSaveable { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var pasteFlash by remember { mutableStateOf(false) }
    var resolvingLabel by remember { mutableStateOf("Loading link…") }
    val recentTick by RecentStreamStore.changes.collectAsState()
    val recents = remember(recentTick) { RecentStreamStore.getAll(context) }

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val outerBg = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFF0F172A).copy(alpha = 0.035f)

    LaunchedEffect(pasteFlash) {
        if (pasteFlash) {
            delay(1400L)
            pasteFlash = false
        }
    }

    val canPlay = url.isNotBlank() && !isResolving

    /** Magnet link or .torrent file: only the torrent info is loaded; a single video plays, several videos become a playlist. */
    fun openTorrent(source: TorrentSource, recentKey: String?) {
        focusManager.clearFocus()
        resolvingLabel = "Fetching torrent info… (can take up to a minute)"
        isResolving = true
        scope.launch {
            val result = try {
                TorrentStreamEngine.open(context, source)
            } catch (t: Throwable) {
                TorrentOpenResult.Failure(t.message ?: "Could not open the torrent")
            }
            isResolving = false
            when (result) {
                is TorrentOpenResult.Failure ->
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                is TorrentOpenResult.Single -> {
                    val item = TorrentLibrary.videoItem(context, TorrentLibrary.pathFor(result.summary.infoHash, result.file.index))
                    if (item != null) onPlayVideo(item, listOf(item))
                }
                is TorrentOpenResult.Multiple -> {
                    if (recentKey != null) {
                        RecentStreamStore.record(context, recentKey, result.summary.name, "Torrent playlist · ${result.videoCount} videos")
                    }
                    Toast.makeText(
                        context,
                        "Playlist \"${result.playlistName}\" created with ${result.videoCount} videos. Open it from the Playlists tab.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun playLink(raw: String) {
        if (isResolving) return
        val trimmed = raw.trim()
        // one video of an already opened torrent (Recent Streams)
        if (trimmed.startsWith(TorrentLibrary.PATH_PREFIX)) {
            val item = TorrentLibrary.videoItem(context, trimmed)
            if (item == null) {
                RecentStreamStore.remove(context, trimmed)
                Toast.makeText(context, "This torrent is no longer saved. Open its magnet / .torrent again.", Toast.LENGTH_LONG).show()
            } else {
                onPlayVideo(item, listOf(item))
            }
            return
        }
        val magnet = Regex("magnet:\\?\\S+", RegexOption.IGNORE_CASE).find(trimmed)?.value
        if (magnet != null) {
            openTorrent(TorrentSource.Magnet(magnet), magnet)
            return
        }
        val normalized = NetworkStreamResolver.normalizeUrl(raw)
        if (normalized == null) {
            Toast.makeText(context, "Enter a valid link", Toast.LENGTH_SHORT).show()
            return
        }
        if (normalized.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true)) {
            openTorrent(TorrentSource.RemoteFile(normalized), normalized)
            return
        }
        focusManager.clearFocus()
        resolvingLabel = "Loading link…"
        isResolving = true
        scope.launch {
            val stream = try {
                NetworkStreamResolver.resolve(context, normalized)
            } catch (_: Throwable) {
                null
            }
            isResolving = false
            if (stream == null) {
                val reason = NetworkStreamResolver.lastError
                Toast.makeText(
                    context,
                    if (reason.isNullOrBlank()) "Could not play this link." else "Could not play this link: $reason",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            if (stream.isAudio) {
                // Music / audio-only link: play it in the app's audio player (same player as the Music tab).
                if (stream.tracks.isNotEmpty()) {
                    // Spotify & playlist links: queue the tracks, each is matched to its audio when it starts.
                    val queue = stream.tracks.map { it.toAudioTrackItem() }
                    RecentStreamStore.record(context, normalized, stream.title, stream.uploader, stream.thumbnailUrl)
                    try {
                        AudioPlaybackManager.openFullScreen()
                        AudioPlaybackManager.playTrack(context = context, track = queue.first(), newQueue = queue)
                    } catch (_: Throwable) {
                        Toast.makeText(context, "Could not start the audio player", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val track = AudioTrackItem(
                    id = -(kotlin.math.abs(("netaudio:" + normalized).hashCode().toLong())) - 1L,
                    uri = android.net.Uri.parse(stream.videoUrl),
                    title = stream.title,
                    artist = stream.uploader ?: "Network",
                    album = "Network",
                    durationMs = stream.durationSec * 1000L,
                    path = normalized,
                    sizeBytes = 0L,
                    format = "STREAM",
                    thumbnailUrl = stream.thumbnailUrl
                )
                RecentStreamStore.record(context, normalized, stream.title, stream.uploader, stream.thumbnailUrl)
                try {
                    AudioPlaybackManager.openFullScreen()
                    AudioPlaybackManager.playTrack(context = context, track = track, newQueue = listOf(track))
                } catch (_: Throwable) {
                    Toast.makeText(context, "Could not start the audio player", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val item = VideoItem(
                id = -(kotlin.math.abs(("netlink:" + normalized).hashCode().toLong())) - 1L,
                uri = android.net.Uri.parse(normalized),
                displayName = stream.title,
                path = normalized,
                sizeBytes = 0L,
                durationMs = stream.durationSec * 1000L,
                dateModified = System.currentTimeMillis() / 1000L,
                isOnline = true,
                thumbnailUrl = stream.thumbnailUrl,
                channelName = stream.uploader,
                isNetworkStream = true
            )
            RecentStreamStore.record(context, normalized, stream.title, stream.uploader, stream.thumbnailUrl)
            // no thumbnail attached to this video: a frame from a random point of it is used instead
            if (stream.thumbnailUrl.isNullOrBlank() && stream.videoUrl.startsWith("http")) {
                RecentThumbnails.captureRandomFrame(context, normalized, stream.videoUrl, stream.durationSec)
            }
            onPlayVideo(item, listOf(item))
        }
    }

    fun play() = playLink(url)

    /**
     * Download button of a recent link. Torrents start right away; other links open the options sheet first (name,
     * container, video / audio quality). Either way the Download page is opened so the progress can be followed there.
     */
    fun downloadEntry(entry: RecentStreamEntry) {
        if (TorrentLibrary.parsePath(entry.url) != null || DownloadManager.isTorrentLink(entry.url)) {
            val message = try {
                DownloadManager.enqueueFromRecent(context, entry)
            } catch (t: Throwable) {
                "Could not start the download"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onOpenDownloads()
        } else {
            focusManager.clearFocus()
            optionsEntry = entry
        }
    }

    // A media title is open: its detail page replaces the whole Network tab until Back.
    val detailPlugin = MediaDetailState.plugin
    val detailPost = MediaDetailState.post
    if (detailPlugin != null && detailPost != null) {
        MediaDetailScreen(
            plugin = detailPlugin,
            post = detailPost,
            isDark = isDark,
            glassBlurTransparency = glassBlurTransparency,
            onBack = { MediaDetailState.close() },
            onPlayVideo = onPlayVideo,
            modifier = modifier,
            searchQuery = searchQuery
        )
        return
    }

    // A provider (downloaded media plugin) is open: its own page replaces the Network tab until Back.
    val openProvider = MediaDetailState.provider?.let { p -> mediaPlugins.firstOrNull { it.value == p.value } }
    if (MediaDetailState.provider != null && openProvider == null) MediaDetailState.provider = null
    BackHandler(enabled = openProvider != null) { MediaDetailState.provider = null }
    if (openProvider != null) {
        MediaPluginPage(
            plugin = openProvider,
            isDark = isDark,
            onOpenPost = { MediaDetailState.open(openProvider, it) },
            onPlayVideo = onPlayVideo,
            modifier = modifier,
            searchQuery = searchQuery
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Same gradient "liquid" underline + slide animation as the Saved / Favourite tabs of a playlist.
        PlaylistFilterTabs(
            tabs = listOf("Local Network", "Media"),
            selectedIndex = safeSection,
            isDark = isDark,
            onSelect = { section = it; MediaDetailState.lastSection = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 4.dp, bottom = 12.dp)
        )

        Crossfade(
            targetState = safeSection,
            animationSpec = tween(220),
            label = "NetworkSection",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { current ->
            if (current == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // outer rounded card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(outerBg)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // URL field: link icon + hint + paste button
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(surfaceColor)
                                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                                .padding(start = 16.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StyledIcon(
                                drawableRes = R.drawable.lumora_link,
                                contentDescription = null,
                                tint = secondaryText.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (url.isEmpty()) {
                                    Text(
                                        text = "Enter stream URL...",
                                        fontSize = 15.sp,
                                        color = secondaryText.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = primaryText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(AccentSkyBlue),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(onGo = { play() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        val text = clipboard.getText()?.text?.trim().orEmpty()
                                        if (text.isNotEmpty()) {
                                            url = text
                                            pasteFlash = true
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(
                                    drawableRes = if (pasteFlash) R.drawable.lumora_pasted else R.drawable.lumora_paste,
                                    contentDescription = "Paste link",
                                    tint = if (pasteFlash) AccentSkyBlue else primaryText.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Play button: grey while empty, gradient when a link is typed
                        val playAlpha by animateFloatAsState(
                            targetValue = if (canPlay || isResolving) 1f else 0f,
                            animationSpec = tween(220),
                            label = "NetworkPlayEnabled"
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFD5D9DD))
                                .then(
                                    if (canPlay || isResolving) Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { play() } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(AccentGradient, alpha = playAlpha)
                            )
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.6.dp
                                )
                            } else {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_play_solid,
                                    contentDescription = "Play",
                                    tint = if (canPlay) Color.White else secondaryText.copy(alpha = 0.75f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                    if (isResolving) {
                        Text(
                            text = resolvingLabel,
                            fontSize = 12.5.sp,
                            color = secondaryText,
                            modifier = Modifier.padding(start = 18.dp, top = 10.dp)
                        )
                    }

                    // ---- Recent Streams: everything played from here (links, torrent videos) so it can be reopened ----
                    if (recents.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Streams",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Clear all",
                                fontSize = 13.sp,
                                color = AccentSkyBlue,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { RecentStreamStore.clear(context) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recents, key = { it.url }) { entry ->
                                RecentStreamRow(
                                    entry = entry,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    surfaceColor = surfaceColor,
                                    borderColor = borderColor,
                                    onClick = { playLink(entry.url) },
                                    onDownload = { downloadEntry(entry) },
                                    onRemove = { RecentStreamStore.remove(context, entry.url) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
            } else {
                MediaPluginManager(
                    isDark = isDark,
                    onOpenPlugin = { MediaDetailState.provider = it }
                )
            }
        }
    }

    optionsEntry?.let { entry ->
        val isMusic = entry.url.contains("music.youtube.com", ignoreCase = true)
        DownloadOptionsSheet(
            isDark = isDark,
            glassBlurTransparency = glassBlurTransparency,
            url = entry.url,
            initialTitle = entry.title,
            initialAuthor = entry.subtitle,
            initialThumbnail = entry.thumbnail?.takeIf { it.startsWith("http") },
            startWithAudio = isMusic,
            onDismiss = { optionsEntry = null },
            onDownload = { request ->
                optionsEntry = null
                val message = try {
                    DownloadManager.enqueueCustom(context, request)
                } catch (t: Throwable) {
                    "Could not start the download"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onOpenDownloads()
            }
        )
    }
}

@Composable
private fun RecentStreamRow(
    entry: RecentStreamEntry,
    primaryText: Color,
    secondaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    val subtitle = entry.subtitle
        ?: if (entry.isTorrent) "Torrent" else entry.url.substringAfter("://").substringBefore('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinkThumbnail(
            thumbnail = entry.thumbnail,
            modifier = Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)),
            fallback = {
                StyledIcon(
                    drawableRes = R.drawable.lumora_link,
                    contentDescription = null,
                    tint = secondaryText.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RecentActionButton(R.drawable.lumora_download_minimalistic, "Download", primaryText.copy(alpha = 0.85f), onDownload)
        RecentActionButton(R.drawable.lumora_trash_bin_2, "Remove from recent", secondaryText.copy(alpha = 0.9f), onRemove)
        RecentActionButton(R.drawable.lumora_play_solid, "Play", AccentSkyBlue, onClick)
    }
}

@Composable
private fun RecentActionButton(
    drawableRes: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
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
