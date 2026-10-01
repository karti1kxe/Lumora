package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.ChapterSkipMarker
import com.example.ui.screens.LocalGlassBlurTransparency
import com.example.ui.screens.getEffectiveGlassBg
import com.example.ui.screens.getEffectiveGlassBorder
import com.example.ui.screens.getEffectiveGlassIconTint

@Composable
fun PlayerChapterSkipButton(
    activeMarker: ChapterSkipMarker?,
    currentPositionMs: Long,
    onSkip: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = activeMarker != null &&
            currentPositionMs >= activeMarker.startTimeMs &&
            currentPositionMs < activeMarker.endTimeMs

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 2 },
        modifier = modifier
    ) {
        if (activeMarker != null) {
            val isEndingMarker = activeMarker.type.isEnding || 
                    activeMarker.type == com.example.player.SkipMarkerType.ENDING || 
                    activeMarker.type == com.example.player.SkipMarkerType.PREVIEW ||
                    activeMarker.type == com.example.player.SkipMarkerType.POST_CREDITS
            
            val isDark = com.example.ui.theme.isAppInDarkTheme()
            // Accent color for Skip marker type
            val accentColor = if (isDark) {
                if (isEndingMarker) Color(0xFF38BDF8) else Color(0xFFFBBF24)
            } else {
                if (isEndingMarker) Color(0xFF0284C7) else Color(0xFFD97706)
            }
            val effectiveGlassBg = getEffectiveGlassBg()
            val effectiveGlassBorder = getEffectiveGlassBorder()
            val effectiveTextTint = getEffectiveGlassIconTint()
            val isLowOpacity = LocalGlassBlurTransparency.current < 25f

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(effectiveGlassBg)
                    .border(
                        width = 1.dp,
                        color = effectiveGlassBorder,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .cardBounceClick(scaleDown = 0.93f) {
                        if (currentPositionMs >= activeMarker.startTimeMs && currentPositionMs < activeMarker.endTimeMs) {
                            onSkip(activeMarker.endTimeMs)
                        }
                    }
                    .height(42.dp)
                    .widthIn(min = 80.dp, max = 260.dp)
                    .padding(start = 7.dp, end = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = if (isLowOpacity) 0.35f else 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lumora_arrow_right_solid),
                            contentDescription = activeMarker.displayButtonTitle,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = activeMarker.displayButtonTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = effectiveTextTint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


