package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimum time between two accepted clicks on the same debounced control.
 * Prevents rapid double/triple taps from firing a click handler more than once,
 * which was a common cause of crashes when a click opened a dialog / mutated
 * state twice in quick succession before the first recomposition landed.
 */
private const val CLICK_DEBOUNCE_MS = 350L

/**
 * Wraps [onClick] so that repeated invocations within [CLICK_DEBOUNCE_MS] are ignored.
 * Safe to use across recompositions since the last-click timestamp is remembered.
 */
@Composable
private fun rememberDebouncedClick(onClick: () -> Unit): () -> Unit {
    val lastClickTime = remember { mutableStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (now - lastClickTime.value >= CLICK_DEBOUNCE_MS) {
            lastClickTime.value = now
            try {
                onClick()
            } catch (_: Throwable) {
                // A control's click handler must never crash the whole player screen.
            }
        }
    }
}

/**
 * Super tactile, fluid spring-based micro-interaction modifier for buttons.
 * When touched/pressed, smoothly compresses down and springs back with satisfying bounce physics.
 * Reads scale strictly inside graphicsLayer block so animation executes entirely on the RenderThread.
 */
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.88f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ButtonBounceScale"
    )
    val debouncedOnClick = rememberDebouncedClick(onClick)

    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = if (enabled) ripple(bounded = false, radius = 24.dp) else null,
            onClick = debouncedOnClick
        )
}

/**
 * Spring-based bounce modifier tailored for larger cards (video cards, dialog action buttons).
 * Reads scale strictly inside graphicsLayer block to eliminate UI stutter and avoid recomposition.
 */
@Composable
fun Modifier.cardBounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "CardBounceScale"
    )
    val debouncedOnClick = rememberDebouncedClick(onClick)

    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = if (enabled) ripple(bounded = true) else null,
            onClick = debouncedOnClick
        )
}

/**
 * Combined click and long click bounce interaction modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.cardBounceCombinedClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.88f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale = animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "CardBounceCombinedScale"
    )
    val debouncedOnClick = rememberDebouncedClick(onClick)

    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = if (enabled) ripple(bounded = true) else null,
            onLongClick = onLongClick,
            onClick = debouncedOnClick
        )
}

