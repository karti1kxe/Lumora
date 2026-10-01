package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.OnlineChannelResult

/**
 * YouTube channel result card shown when a search query matches a creator/channel.
 * Features the channel avatar, title, subscriber info, a system gradient Subscribe button (UI only),
 * and a functional View Channel button.
 */
@Composable
fun OnlineChannelResultCard(
    channel: OnlineChannelResult,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    var avatarBitmap by remember(channel.channelUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(channel.channelUrl, channel.avatarUrl) {
        val url = channel.avatarUrl
        if (!url.isNullOrBlank()) {
            avatarBitmap = loadRemoteAudioThumbnail(url, targetSizePx = 200)
        }
    }

    var isSubscribed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top section: Avatar + Channel Info (tapping opens channel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                val bmp = avatarBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = primaryText,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(channel.subscriberCountText, channel.videoCountText)
                    .joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = secondaryText,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bottom section: Subscribe Button (System Gradient, UI only) + View Channel Button (Functional)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subscribe Button
            val subBg = if (isSubscribed) {
                SolidColor(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
            } else {
                AccentGradient
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(subBg)
                    .clickable {
                        // UI-only toggle
                        isSubscribed = !isSubscribed
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                    color = if (isSubscribed) primaryText else Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // View Channel Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                    .border(
                        1.dp,
                        if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                        RoundedCornerShape(50)
                    )
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "View Channel",
                    color = primaryText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
