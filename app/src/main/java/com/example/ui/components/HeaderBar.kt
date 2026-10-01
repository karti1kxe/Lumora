package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.ThemeMode
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary

@Composable
fun HeaderBar(
    title: String,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier,
    onSettingsClick: (() -> Unit)? = null,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onSortClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    showBorder: Boolean = true,
    elevation: Dp = 6.dp
) {
    val isDark = themeMode == ThemeMode.DARK
    val surfaceColor = backgroundColor ?: (if (isDark) DarkGlassSurface else LightGlassSurface)
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val textColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val subtextColor = if (isDark) DarkTextSecondary else LightTextSecondary
    val iconButtonBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.16f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .then(
                if (showBorder) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
                } else Modifier
            )
            .padding(horizontal = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            // Left Side: Back button OR App Icon
            if (onBackClick != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("header_back_button")
                        .clip(CircleShape)
                        .bounceClick(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_abouticon),
                    contentDescription = "Lumora Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("header_app_icon")
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = if (!subtitle.isNullOrBlank()) 16.5.sp else 18.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = subtextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side icons:
            // 1. Search icon (leftmost)
            if (onSearchClick != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("search_button")
                        .clip(CircleShape)
                        .bounceClick(onClick = onSearchClick),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            // 2. Context action: either normal Sort, or Copy + Share on information pages.
            if (onCopyClick != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("copy_button")
                        .clip(CircleShape)
                        .bounceClick(onClick = onCopyClick),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = textColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            } else if (onSortClick != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("sort_button")
                        .clip(CircleShape)
                        .bounceClick(onClick = onSortClick),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.SortByAlpha,
                        contentDescription = "Sort & View Options",
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            if (onShareClick != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("share_button")
                        .clip(CircleShape)
                        .bounceClick(onClick = onShareClick),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = textColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            // 3. Settings gear icon (rightmost)
            if (onSettingsClick != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("settings_button")
                        .clip(CircleShape)
                        .bounceClick(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
