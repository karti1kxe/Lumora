package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import com.example.R
import com.example.ui.theme.AccentGradient
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val ActionSlot = 64.dp

/**
 * Left swipe on a playlist card: the card slides left, stops exactly over the width of the revealed icons and the
 * icons sit behind it. Heart = Favourite, Bookmark (only [showBookmark], e.g. M3U folders) = Saved.
 * [revealed] is controlled by the caller so only one card is open at a time.
 */
@Composable
fun PlaylistRevealSwipe(
    enabled: Boolean,
    showBookmark: Boolean,
    isFavourite: Boolean,
    isSaved: Boolean,
    isDark: Boolean,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val slots = if (showBookmark) 2 else 1
    val revealPx = with(density) { (ActionSlot * slots).toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestRevealedChange by rememberUpdatedState(onRevealedChange)

    LaunchedEffect(revealed, revealPx, enabled) {
        offset.animateTo(if (revealed && enabled) -revealPx else 0f, tween(220))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Icons behind the card (they only become visible while the card is slid away).
        val progress = (abs(offset.value) / revealPx).coerceIn(0f, 1f)
        if (enabled && progress > 0.01f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(progress),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBookmark) {
                    RevealActionButton(
                        iconRes = R.drawable.lumora_saved_bookmark,
                        description = "Save",
                        active = isSaved,
                        activeColor = Color(0xFF38BDF8),
                        isDark = isDark,
                        onClick = { onToggleSaved(); latestRevealedChange(false) }
                    )
                }
                RevealActionButton(
                    iconRes = R.drawable.lumora_favourite_heart,
                    description = "Favourite",
                    active = isFavourite,
                    activeColor = Color(0xFFFF3366),
                    isDark = isDark,
                    onClick = { onToggleFavourite(); latestRevealedChange(false) }
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .then(
                    if (enabled) Modifier.pointerInput(revealPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val open = offset.value < -revealPx * 0.4f
                                latestRevealedChange(open)
                                scope.launch { offset.animateTo(if (open) -revealPx else 0f, tween(200)) }
                            },
                            onDragCancel = {
                                scope.launch { offset.animateTo(if (revealed) -revealPx else 0f, tween(200)) }
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                scope.launch { offset.snapTo((offset.value + dx).coerceIn(-revealPx, 0f)) }
                            }
                        )
                    } else Modifier
                )
        ) {
            content()
            // While the card is slid open a tap on it only closes it (no accidental playback).
            if (enabled && revealed) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { latestRevealedChange(false) }
                )
            }
        }
    }
}

@Composable
private fun RevealActionButton(
    iconRes: Int,
    description: String,
    active: Boolean,
    activeColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.width(ActionSlot).height(ActionSlot),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (active) activeColor.copy(alpha = 0.22f)
                    else if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = if (active) activeColor else if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * YouTube-channel style text tabs (selected = bold dark text + gradient underline, others muted).
 */
@Composable
fun PlaylistFilterTabs(
    tabs: List<String>,
    selectedIndex: Int,
    isDark: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = if (isDark) Color.White else Color(0xFF0F172A)
    val mutedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val lineColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(lineColor)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            tabs.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(i) },
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (selected) selectedColor else mutedColor,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (selected) AccentGradient
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                    )
                }
            }
        }
    }
}
