package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.util.NetworkStreamResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Network" bottom tab. Sections: Local Network (paste a link and play it) and Media (reserved for the next step).
 * Playback itself is NOT done here: the link is resolved (yt-dlp) and handed to [onPlayVideo], so it opens in the
 * app's normal video player with its normal controls.
 */
@Composable
fun NetworkTabContent(
    isDark: Boolean,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current

    var section by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var pasteFlash by remember { mutableStateOf(false) }

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

    fun play() {
        if (isResolving) return
        val normalized = NetworkStreamResolver.normalizeUrl(url)
        if (normalized == null) {
            Toast.makeText(context, "Enter a valid link", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
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
            onPlayVideo(item, listOf(item))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Same gradient "liquid" underline + slide animation as the Saved / Favourite tabs of a playlist.
        PlaylistFilterTabs(
            tabs = listOf("Local Network", "Media"),
            selectedIndex = section,
            isDark = isDark,
            onSelect = { section = it },
            modifier = Modifier.padding(horizontal = 4.dp).padding(top = 4.dp, bottom = 12.dp)
        )

        Crossfade(targetState = section, animationSpec = tween(220), label = "NetworkSection") { current ->
            if (current == 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            text = "Loading link…",
                            fontSize = 12.5.sp,
                            color = secondaryText,
                            modifier = Modifier.padding(start = 18.dp, top = 10.dp)
                        )
                    }
                }
            } else {
                // "Media" section is added in the next step.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "Coming soon",
                        fontSize = 13.sp,
                        color = secondaryText,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
}
