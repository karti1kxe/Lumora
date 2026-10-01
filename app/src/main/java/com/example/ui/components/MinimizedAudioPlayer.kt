package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioPlaybackManager
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import kotlin.math.roundToInt

/**
 * Minimized Floating Audio Player.
 * Renders cleanly above the bottom navigation bar / selection bar across all tabs.
 * Features:
 * - Real-time smooth playback progress fill background (0% -> 100%)
 * - Album artwork / track icon
 * - Track Title and Playing status
 * - Play/Pause button, Next Track button, Close/Stop button
 * - Seamless tap to expand into Full-Screen Audio Player
 * - Touch & hold (1 sec) anywhere in the center to drag and position vertically
 */
@Composable
fun MinimizedAudioPlayer(
    isDark: Boolean,
    glassBlurTransparency: Float = 85f,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val isBuffering by AudioPlaybackManager.isBuffering.collectAsState()
    val positionMs by AudioPlaybackManager.currentPositionMs.collectAsState()
    val durationMs by AudioPlaybackManager.durationMs.collectAsState()
    val isMiniPlayerVisible by AudioPlaybackManager.isMiniPlayerVisible.collectAsState()
    val isFullScreenOpen by AudioPlaybackManager.isFullScreenOpen.collectAsState()
    val isABLoopActive by AudioPlaybackManager.isABLoopActive.collectAsState()

    val track = currentTrack
    val isVisible = isMiniPlayerVisible && !isFullScreenOpen && track != null

    // Touch & hold drag offset (allows user to drag up or down after holding for 1 sec)
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "mini_player_drag"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 340,
                delayMillis = 80,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + androidx.compose.animation.scaleIn(
            initialScale = 0.94f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 320,
                delayMillis = 60,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 280,
                delayMillis = 80,
                easing = androidx.compose.animation.core.LinearEasing
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 4 },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + androidx.compose.animation.scaleOut(
            targetScale = 0.95f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 180,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        ),
        modifier = modifier
    ) {
        if (track == null) return@AnimatedVisibility

        val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
        val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
        val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

        val effectiveDuration = durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(1L)
        val progressRatio = (positionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)

        val alphaFactor = (glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f)
        val containerBg = if (isDark) {
            if (alphaFactor >= 0.99f) Color(0xFF0F172A)
            else Color(0xFF0F172A).copy(alpha = (0.75f + 0.25f * alphaFactor).coerceIn(0.60f, 0.98f))
        } else {
            if (alphaFactor >= 0.99f) Color(0xFFFFFFFF)
            else Color(0xFFFFFFFF).copy(alpha = (0.80f + 0.20f * alphaFactor).coerceIn(0.65f, 0.98f))
        }

        val progressFillColor = if (isDark) {
            AccentSkyBlue.copy(alpha = 0.22f)
        } else {
            Color(0xFFBAE6FD).copy(alpha = 0.50f)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(0, animatedDragOffsetY.roundToInt()) }
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .shadow(
                    elevation = if (isDragging) 22.dp else 14.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = if (isDark) AccentSkyBlue.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.12f),
                    spotColor = if (isDark) AccentSkyBlue.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(containerBg)
                .border(
                    width = if (isDragging) 1.5.dp else 1.dp,
                    color = if (isDragging) AccentSkyBlue else borderColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-700f, 200f)
                        }
                    )
                }
                .clickable(onClick = onExpand)
        ) {
            // PROGRESS EFFECT BACKGROUND FILL
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressRatio)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    progressFillColor,
                                    progressFillColor.copy(alpha = progressFillColor.alpha * 1.25f)
                                )
                            )
                        )
                )
            }

            // PLAYER CONTENT ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork / Icon Thumbnail
                AudioTrackThumbnail(
                    track = track,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 12.dp,
                    fallbackIcon = Icons.Filled.Headphones,
                    fallbackIconSize = 22.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track Title + Playing Status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isABLoopActive) {
                            Text(
                                text = "A-B",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(end = 5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentSkyBlue)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "${if (isPlaying) "Playing" else "Paused"} • ${track.artist}",
                            fontSize = 11.sp,
                            color = if (isPlaying) AccentSkyBlue else secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // CONTROLS: Play/Pause + Next + Close
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Button
                    Box(
                        modifier = Modifier
                            .testTag("mini_player_play_pause_button")
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentGradient)
                            .clickable(enabled = !isBuffering) { AudioPlaybackManager.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            LumoraExpressiveLoadingIndicator(
                                size = 22.dp,
                                isDark = true,
                                transparentBg = true
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.lumora_pause_solid else R.drawable.lumora_play_solid
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Next Track Button (Matches Video Player Next Icon)
                    Box(
                        modifier = Modifier
                            .testTag("mini_player_next_button")
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { AudioPlaybackManager.skipNext(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lumora_arrow_right_solid),
                            contentDescription = "Next Track",
                            tint = primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Close / Dismiss Button (stops playback)
                    Box(
                        modifier = Modifier
                            .testTag("mini_player_close_button")
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { AudioPlaybackManager.closeAndStop() },
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Player",
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
