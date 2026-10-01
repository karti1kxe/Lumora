package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.util.AppHaptics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Modern Interactive Audio Scrubber inspired by Android 13+ / YouTube Music Media Slider:
 * - Idle state: Sleek, minimalistic pill-shaped progress bar (6dp height) with left & right timestamps.
 * - Touch & Hold / Drag state: Smoothly expands in height (up to 16dp) with an animated spring effect,
 *   allowing real-time backward and forward scrubbing.
 * - Smoothly shrinks back down upon release and commits the seek position.
 */
@Composable
fun InteractiveAudioScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
    textColor: Color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
    loopPointA: Long? = null,
    loopPointB: Long? = null,
    isABLoopActive: Boolean = false
) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    var lastHapticSec by remember { mutableStateOf(-1L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val actualFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayFraction = if (isDragging) dragFraction else actualFraction

    val displayedPositionMs = (displayFraction * safeDuration).toLong().coerceIn(0L, safeDuration)

    // Animated track height on touch/hold/drag - Thick pill slider matching screenshot
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) 16.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrubber_track_height"
    )

    // Animated glow / alpha when expanded
    val animatedActiveAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.92f,
        animationSpec = tween(durationMillis = 200),
        label = "scrubber_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Scrubber Interactive Bar with Touch-and-Hold Expansion
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp) // Touch area remains comfortable
                .testTag("audio_player_progress_slider")
                .pointerInput(safeDuration) {
                    // Press-to-seek and drag-to-scrub are combined into a single
                    // pointerInput block (as sibling coroutines sharing the same
                    // pointer stream) instead of two separate stacked
                    // pointerInput modifiers. Two independent modifiers here
                    // used to race for the initial touch down, which is why a
                    // tap or the start of a drag on the seek bar sometimes
                    // needed to be repeated before it registered.
                    kotlinx.coroutines.coroutineScope {
                        launch {
                            detectTapGestures(
                                onPress = { offset ->
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    val targetFrac = (offset.x / width).coerceIn(0f, 1f)
                                    isDragging = true
                                    dragFraction = targetFrac
                                    AppHaptics.performGestureThreshold(view, haptic)

                                    val release = tryAwaitRelease()
                                    if (release) {
                                        val finalMs = (dragFraction * safeDuration).toLong()
                                        onSeekTo(finalMs)
                                    }
                                    isDragging = false
                                }
                            )
                        }
                        launch {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    isDragging = true
                                    dragFraction = (offset.x / width).coerceIn(0f, 1f)
                                    lastHapticSec = (dragFraction * safeDuration).toLong() / 1000L
                                    AppHaptics.performGestureStart(view, haptic)
                                },
                                onDragEnd = {
                                    val finalMs = (dragFraction * safeDuration).toLong()
                                    onSeekTo(finalMs)
                                    isDragging = false
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                                    val currentSec = (dragFraction * safeDuration).toLong() / 1000L
                                    if (kotlin.math.abs(currentSec - lastHapticSec) >= 1L) {
                                        lastHapticSec = currentSec
                                        AppHaptics.performTick(view, haptic)
                                    }
                                }
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedTrackHeight)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val cornerRadius = CornerRadius(canvasHeight / 2f, canvasHeight / 2f)

                // 1. Draw Inactive / Background Track
                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(canvasWidth, canvasHeight),
                    cornerRadius = cornerRadius
                )

                // 2. Draw A-B Loop Segment if active
                if (isABLoopActive && loopPointA != null && loopPointB != null) {
                    val loopStartFrac = (loopPointA.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val loopEndFrac = (loopPointB.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val loopStartX = loopStartFrac * canvasWidth
                    val loopWidth = (loopEndFrac - loopStartFrac) * canvasWidth

                    drawRoundRect(
                        color = Color(0x3338BDF8),
                        topLeft = Offset(loopStartX, 0f),
                        size = Size(loopWidth.coerceAtLeast(2f), canvasHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // 3. Draw Active / Progress Track
                val progressWidth = (canvasWidth * displayFraction).coerceIn(0f, canvasWidth)
                if (progressWidth > 0f) {
                    drawRoundRect(
                        color = activeTrackColor.copy(alpha = animatedActiveAlpha),
                        topLeft = Offset(0f, 0f),
                        size = Size(progressWidth, canvasHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // 4. Subtle Drag Handle Indicator when expanded
                if (isDragging) {
                    val knobX = progressWidth.coerceIn(canvasHeight / 2f, canvasWidth - canvasHeight / 2f)
                    drawCircle(
                        color = Color.White,
                        radius = canvasHeight * 0.42f,
                        center = Offset(knobX, canvasHeight / 2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Time Labels on Left and Right (e.g. 00:50 and 01:53)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatAudioScrubberTime(displayedPositionMs),
                fontSize = 12.5.sp,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                color = if (isDragging) activeTrackColor else textColor
            )

            if (isABLoopActive && loopPointA != null && loopPointB != null) {
                Text(
                    text = "A-B Loop (${formatAudioScrubberTime(loopPointA)} - ${formatAudioScrubberTime(loopPointB)})",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }

            Text(
                text = formatAudioScrubberTime(durationMs),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

private fun formatAudioScrubberTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
    } else {
        String.format(Locale.US, "%02d:%02d", remainingMinutes, remainingSeconds)
    }
}
