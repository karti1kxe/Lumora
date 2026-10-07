package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * Smooth "finger-like" glide to an item of a lazy list / grid.
 *
 * Animating through dozens of rows makes Compose measure every card on the way (janky), while a plain
 * scrollToItem() just jumps with no animation. Compromise: jump to a few rows before the target, then
 * glide the remaining distance with one long decelerating animation, and finally snap-correct.
 */
private const val GLIDE_LEAD_ITEMS = 12
private const val GLIDE_DURATION_MS = 800

suspend fun LazyListState.glideToItem(index: Int) {
    if (index < 0) return
    val lead = GLIDE_LEAD_ITEMS
    when {
        index - firstVisibleItemIndex > lead -> scrollToItem(index - lead)
        firstVisibleItemIndex - index > lead -> scrollToItem(index + lead)
    }
    withFrameNanos { }
    val info = layoutInfo.visibleItemsInfo
    if (info.size >= 2) {
        val pitch = (info[1].offset - info[0].offset).toFloat()
        if (pitch > 0f) {
            val distance = (index - firstVisibleItemIndex) * pitch - firstVisibleItemScrollOffset
            if (abs(distance) > 1f) {
                animateScrollBy(distance, tween(durationMillis = GLIDE_DURATION_MS, easing = FastOutSlowInEasing))
            }
        }
    }
    // Lands exactly on the target (tiny remaining distance only).
    animateScrollToItem(index)
}

suspend fun LazyGridState.glideToItem(index: Int) {
    if (index < 0) return
    val lead = GLIDE_LEAD_ITEMS
    when {
        index - firstVisibleItemIndex > lead -> scrollToItem(index - lead)
        firstVisibleItemIndex - index > lead -> scrollToItem(index + lead)
    }
    withFrameNanos { }
    val info = layoutInfo.visibleItemsInfo
    if (info.size >= 2) {
        val firstY = info[0].offset.y
        val perRow = info.count { it.offset.y == firstY }.coerceAtLeast(1)
        val nextRow = info.firstOrNull { it.offset.y > firstY }
        if (nextRow != null) {
            val rowPitch = (nextRow.offset.y - firstY).toFloat()
            val distance = ((index - firstVisibleItemIndex) / perRow.toFloat()) * rowPitch - firstVisibleItemScrollOffset
            if (abs(distance) > 1f) {
                animateScrollBy(distance, tween(durationMillis = GLIDE_DURATION_MS, easing = FastOutSlowInEasing))
            }
        }
    }
    animateScrollToItem(index)
}
