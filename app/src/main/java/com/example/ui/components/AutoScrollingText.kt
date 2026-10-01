package com.example.ui.components

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-performance smooth marquee for video titles in player top bar.
 * Uses Compose basicMarquee so long filenames smoothly slide across and reveal the entire name.
 * Uses internal gradient edge fading inside the pill boundary so text smoothly fades to invisible
 * without any hard edge cut or screen boundary clipping.
 */
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    startDelayMillis: Int = 1000,
    pauseMillis: Int = 1000,
    speedMillis: Int = 3000,
    endGap: Dp = 32.dp
) {
    val resolvedColor = if (color != Color.Unspecified) color else style.color
    Box(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val w = size.width
                if (w > 32.dp.toPx()) {
                    val fadePx = 14.dp.toPx()
                    val startRatio = (fadePx / w).coerceIn(0f, 0.2f)
                    val endRatio = (1f - (fadePx / w)).coerceIn(0.8f, 1f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                startRatio to Color.Black,
                                endRatio to Color.Black,
                                1f to Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = style.copy(color = resolvedColor),
            color = resolvedColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    repeatDelayMillis = pauseMillis,
                    initialDelayMillis = startDelayMillis,
                    spacing = MarqueeSpacing(endGap),
                    velocity = 36.dp
                )
        )
    }
}
