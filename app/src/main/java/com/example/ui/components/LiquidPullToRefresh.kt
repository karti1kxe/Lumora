package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentSkyBlue
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Android 16 / Material 3 Expressive Content-Push Pull-To-Refresh.
 *
 * - Translates the entire folder/video list content downward during drag via GPU graphicsLayer.
 * - Naturally reveals the top area where the M3 Expressive morphing refresh indicator emerges.
 * - Holds content translated during active refresh operation.
 * - Smooth, non-snapping spring return of content upon refresh completion with zero layout jumps or thumbnail reloads.
 * - Single refresh trigger guarantee with elastic resistance.
 */
@Composable
fun LiquidPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    refreshThreshold: Dp = 80.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { refreshThreshold.toPx() }
    val maxDragPx = thresholdPx * 1.9f

    val pullOffset = remember { Animatable(0f) }
    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            exitScale.snapTo(1f)
            exitAlpha.snapTo(1f)
            pullOffset.animateTo(
                targetValue = thresholdPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            // Smooth expressive exit: fade indicator & glide content back up with smooth easing
            coroutineScope.launch {
                exitScale.animateTo(
                    targetValue = 0.2f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                )
            }
            coroutineScope.launch {
                exitAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 180, easing = LinearEasing)
                )
            }
            pullOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            exitScale.snapTo(1f)
            exitAlpha.snapTo(1f)
        }
    }

    val nestedScrollConnection = remember(isRefreshing, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When content is pulled down and user scrolls up, consume scroll to push content back up
                if (source == NestedScrollSource.UserInput && available.y < 0 && pullOffset.value > 0f && !isRefreshing) {
                    val newOffset = (pullOffset.value + available.y).coerceAtLeast(0f)
                    coroutineScope.launch { pullOffset.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // When at top of list and user pulls down, move content down with natural resistance
                if (source == NestedScrollSource.UserInput && available.y > 0 && !isRefreshing) {
                    val current = pullOffset.value
                    val progressFraction = (current / maxDragPx).coerceIn(0f, 1f)
                    val resistanceRatio = (1f - progressFraction * 0.72f).coerceIn(0.18f, 0.62f)
                    val delta = available.y * resistanceRatio
                    val newOffset = (current + delta).coerceIn(0f, maxDragPx)
                    coroutineScope.launch { pullOffset.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pullOffset.value >= thresholdPx && !isRefreshing) {
                    onRefresh()
                } else if (!isRefreshing && pullOffset.value > 0f) {
                    pullOffset.animateTo(
                        0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                if (pullOffset.value >= thresholdPx && !isRefreshing) {
                    onRefresh()
                } else if (!isRefreshing && pullOffset.value > 0f) {
                    pullOffset.animateTo(
                        0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(isRefreshing, thresholdPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val allUp = event.changes.all { !it.pressed }
                        if (allUp && !isRefreshing) {
                            if (pullOffset.value >= thresholdPx) {
                                onRefresh()
                            } else if (pullOffset.value > 0f && !pullOffset.isRunning) {
                                coroutineScope.launch {
                                    pullOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val currentOffset = pullOffset.value

        // 1. Full Content Container translated downwards with GPU graphicsLayer (Zero recomposition/Zero remeasure)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentOffset
                }
        ) {
            content()
        }

        // 2. M3 Expressive Morphing Refresh Animation rendered naturally in the revealed top space
        if (currentOffset > 4f || isRefreshing) {
            val rawProgress = (currentOffset / thresholdPx)
            val progress = rawProgress.coerceIn(0f, 1f)
            val overDrag = (rawProgress - 1f).coerceAtLeast(0f)

            // Centered naturally within the revealed space above content
            val indicatorSizePx = with(density) { 54.dp.toPx() }
            val minTopMarginPx = with(density) { 8.dp.toPx() }
            val indicatorYOffset = (currentOffset * 0.5f - indicatorSizePx / 2f).coerceAtLeast(minTopMarginPx)

            // Infinite morph transitions for the active refreshing state
            val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_refresh")

            val morphPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "morphPhase"
            )

            val spinAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spinAngle"
            )

            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )

            val currentScale = if (isRefreshing) {
                (pulseScale * exitScale.value).coerceAtLeast(0f)
            } else {
                (0.65f + progress * 0.35f + overDrag * 0.08f).coerceIn(0.65f, 1.12f) * exitScale.value
            }

            val currentAlpha = if (isRefreshing) {
                exitAlpha.value
            } else {
                (progress * 1.2f).coerceIn(0.15f, 1f) * exitAlpha.value
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, indicatorYOffset.roundToInt()) }
                    .scale(currentScale),
                contentAlignment = Alignment.Center
            ) {
                M3ExpressiveMorphIndicator(
                    progress = progress,
                    isRefreshing = isRefreshing,
                    morphPhase = morphPhase,
                    spinAngle = spinAngle,
                    isDark = isDark,
                    alpha = currentAlpha,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}

/**
 * High-performance vector canvas rendering Android 16 / Material 3 Expressive shape morphing.
 */
@Composable
private fun M3ExpressiveMorphIndicator(
    progress: Float,
    isRefreshing: Boolean,
    morphPhase: Float,
    spinAngle: Float,
    isDark: Boolean,
    alpha: Float,
    transparentBg: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (transparentBg) Color.Transparent else if (isDark) Color(0xFF0F172A).copy(alpha = 0.96f * alpha) else Color(0xFFFFFFFF).copy(alpha = 0.98f * alpha)
    val shadowColor = if (transparentBg) Color.Transparent else if (isDark) Color(0xFF000000).copy(alpha = 0.35f * alpha) else Color(0xFF0284C7).copy(alpha = 0.18f * alpha)

    // Vibrant Material 3 Expressive gradient colors
    val primaryAccent = if (transparentBg) Color.White.copy(alpha = alpha) else AccentSkyBlue.copy(alpha = alpha)
    val secondaryAccent = if (transparentBg) Color(0xFFBAE6FD).copy(alpha = alpha) else Color(0xFF38BDF8).copy(alpha = alpha)
    val tertiaryAccent = if (transparentBg) Color(0xFFE0F2FE).copy(alpha = alpha) else Color(0xFF818CF8).copy(alpha = alpha)
    val quaternaryAccent = if (transparentBg) Color.White.copy(alpha = alpha * 0.9f) else Color(0xFFC084FC).copy(alpha = alpha)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val edgePadding = if (transparentBg) 1.dp.toPx() else (size.minDimension * 0.075f).coerceIn(2.dp.toPx(), 4.dp.toPx())
        val maxRadius = (size.minDimension / 2f - edgePadding).coerceAtLeast(1f)

        // 1. Draw outer soft expressive glow/shadow
        if (!transparentBg) {
            drawCircle(
                color = shadowColor,
                radius = maxRadius + 3.dp.toPx(),
                center = center + Offset(0f, 2.dp.toPx())
            )
        }

        // 2. Compute dynamic morphed path for outer container
        val outerPath = Path()
        val numPoints = 48
        val effectiveMorph = if (isRefreshing) morphPhase else (progress * 1.5f)
        val baseRotation = if (isRefreshing) spinAngle else (progress * 180f)

        for (i in 0 until numPoints) {
            val angleDeg = (i.toFloat() / numPoints) * 360f + baseRotation
            val angleRad = angleDeg * (PI / 180f).toFloat()

            // Morph between circle, 4-corner flower/squircle, 8-lobe scallop, and diamond bloom
            val r = computeExpressiveRadius(
                baseRadius = maxRadius,
                angleRad = angleRad,
                morph = effectiveMorph,
                isRefreshing = isRefreshing
            )

            val x = center.x + r * cos(angleRad)
            val y = center.y + r * sin(angleRad)

            if (i == 0) {
                outerPath.moveTo(x, y)
            } else {
                outerPath.lineTo(x, y)
            }
        }
        outerPath.close()

        // Draw outer morphed container background
        if (!transparentBg) {
            drawPath(
                path = outerPath,
                color = bgColor,
                style = Fill
            )
        }

        // Draw outer expressive glowing border
        val borderBrush = Brush.sweepGradient(
            colors = listOf(primaryAccent, secondaryAccent, tertiaryAccent, quaternaryAccent, primaryAccent),
            center = center
        )
        val strokeWidth = (maxRadius * 0.12f).coerceIn(1.2.dp.toPx(), 2.8.dp.toPx())
        drawPath(
            path = outerPath,
            brush = borderBrush,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 3. Draw Inner Expressive Core
        if (isRefreshing) {
            // Active state: Expressive blooming dynamic core with orbiting satellite beads
            val innerRadius = maxRadius * 0.46f
            val innerPath = Path()

            for (i in 0 until numPoints) {
                val angleDeg = (i.toFloat() / numPoints) * 360f - (spinAngle * 1.4f)
                val angleRad = angleDeg * (PI / 180f).toFloat()

                val r = computeInnerCoreRadius(
                    baseRadius = innerRadius,
                    angleRad = angleRad,
                    morph = morphPhase
                )

                val x = center.x + r * cos(angleRad)
                val y = center.y + r * sin(angleRad)

                if (i == 0) {
                    innerPath.moveTo(x, y)
                } else {
                    innerPath.lineTo(x, y)
                }
            }
            innerPath.close()

            val coreBrush = Brush.linearGradient(
                colors = listOf(primaryAccent, tertiaryAccent),
                start = Offset(center.x - innerRadius, center.y - innerRadius),
                end = Offset(center.x + innerRadius, center.y + innerRadius)
            )

            drawPath(
                path = innerPath,
                brush = coreBrush,
                style = Fill
            )

            // 4 Orbiting expressive micro-dots
            val orbitRadius = maxRadius * 0.70f
            val dotRadius = (maxRadius * 0.11f).coerceIn(1.0.dp.toPx(), 2.4.dp.toPx())
            for (k in 0 until 4) {
                val beadAngleRad = ((spinAngle * 2.2f + k * 90f) * (PI / 180f)).toFloat()
                val beadX = center.x + orbitRadius * cos(beadAngleRad)
                val beadY = center.y + orbitRadius * sin(beadAngleRad)
                val beadColor = if (k % 2 == 0) primaryAccent else quaternaryAccent
                drawCircle(
                    color = beadColor,
                    radius = dotRadius,
                    center = Offset(beadX, beadY)
                )
            }
        } else {
            // Pull phase: Expressive morphing arrow & closing ring
            val sweepAngle = (progress * 280f).coerceIn(10f, 300f)
            val ringRadius = maxRadius * 0.52f

            rotate(degrees = progress * 160f, pivot = center) {
                // Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryAccent.copy(alpha = 0.4f), primaryAccent),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Arrow head at end of arc
                if (progress > 0.25f) {
                    val endAngleRad = ((-90f + sweepAngle) * (PI / 180f)).toFloat()
                    val arrowTip = Offset(
                        center.x + ringRadius * cos(endAngleRad),
                        center.y + ringRadius * sin(endAngleRad)
                    )

                    val arrowSize = 4.2.dp.toPx()
                    val arrowPath = Path().apply {
                        val tangentAngle = endAngleRad + (PI / 2f).toFloat()
                        moveTo(arrowTip.x, arrowTip.y)
                        lineTo(
                            arrowTip.x - arrowSize * cos(tangentAngle - 0.7f),
                            arrowTip.y - arrowSize * sin(tangentAngle - 0.7f)
                        )
                        moveTo(arrowTip.x, arrowTip.y)
                        lineTo(
                            arrowTip.x - arrowSize * cos(tangentAngle + 0.7f),
                            arrowTip.y - arrowSize * sin(tangentAngle + 0.7f)
                        )
                    }

                    drawPath(
                        path = arrowPath,
                        color = primaryAccent,
                        style = Stroke(
                            width = 2.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Center pulsating dot as pull reaches threshold
                if (progress > 0.6f) {
                    val dotAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    drawCircle(
                        color = primaryAccent.copy(alpha = dotAlpha * alpha),
                        radius = (3.dp * progress).toPx(),
                        center = center
                    )
                }
            }
        }
    }
}

/**
 * Calculates continuous parametric radius for outer expressive shape morphing.
 * Morphs between:
 * 0.0 -> Circle
 * 1.0 -> 4-point Scallop / Squircle
 * 2.0 -> Blooming 6-Petal Flower
 * 3.0 -> 8-point Diamond Star
 * 4.0 -> Circle
 */
private fun computeExpressiveRadius(
    baseRadius: Float,
    angleRad: Float,
    morph: Float,
    isRefreshing: Boolean
): Float {
    val m = morph % 4f
    return when {
        m < 1f -> {
            val t = m
            val amp4 = 0.16f * t
            baseRadius * (1f + amp4 * cos(4f * angleRad))
        }
        m < 2f -> {
            val t = m - 1f
            val amp4 = 0.16f * (1f - t)
            val amp6 = 0.18f * t
            baseRadius * (1f + amp4 * cos(4f * angleRad) + amp6 * sin(6f * angleRad))
        }
        m < 3f -> {
            val t = m - 2f
            val amp6 = 0.18f * (1f - t)
            val amp8 = 0.15f * t
            baseRadius * (1f + amp6 * sin(6f * angleRad) + amp8 * cos(8f * angleRad))
        }
        else -> {
            val t = m - 3f
            val amp8 = 0.15f * (1f - t)
            baseRadius * (1f + amp8 * cos(8f * angleRad))
        }
    }
}

/**
 * Calculates inner core flower/clover morphing.
 */
private fun computeInnerCoreRadius(
    baseRadius: Float,
    angleRad: Float,
    morph: Float
): Float {
    val m = morph % 2f
    val amp = if (m < 1f) 0.25f * m else 0.25f * (2f - m)
    return baseRadius * (1f + amp * sin(4f * angleRad))
}

/**
 * Standalone Lumora Expressive Loading / Buffering Indicator.
 * Reuses the exact Android 16 / Material 3 Expressive morphing refresh animation
 * for all video buffering, stream extraction, and online network loading states.
 */
@Composable
fun LumoraExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    size: Dp = 54.dp,
    transparentBg: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lumora_expressive_loading")

    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphPhase"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        M3ExpressiveMorphIndicator(
            progress = 1f,
            isRefreshing = true,
            morphPhase = morphPhase,
            spinAngle = spinAngle,
            isDark = isDark,
            alpha = 1f,
            transparentBg = transparentBg,
            modifier = Modifier.size(size)
        )
    }
}
