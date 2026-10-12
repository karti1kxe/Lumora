package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.VideoItem
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentPink
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.ui.state.ThumbnailQuality
import com.example.ui.state.ThumbnailStrategy

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mStr = if (minutes < 10) "0$minutes" else "$minutes"
    val sStr = if (seconds < 10) "0$seconds" else "$seconds"
    return if (hours > 0) "$hours:$mStr:$sStr" else "$mStr:$sStr"
}

/**
 * Resolves the subtitle indicator label according to strict user specifications:
 * - Single format: e.g. "1ASS" (or "2ASS", "3ASS" based on track count), "1SRT", "1VTT"
 * - Multiple formats: "MSuB"
 * - Fallback to filename indicator (e.g. {Jp-MultiSub FHD}) if metadata is pending
 */
fun resolveSubtitleBadgeText(
    subtitleFormats: List<String>,
    displayName: String = "",
    hasSubtitles: Boolean = false
): String? {
    if (subtitleFormats.isNotEmpty()) {
        if (subtitleFormats.size > 1) {
            return "MSuB"
        }
        val first = subtitleFormats.first().trim()
        if (first.isNotBlank()) {
            return if (first.first().isDigit()) first else "1$first"
        }
    }
    val nameLower = displayName.lowercase()
    if (nameLower.contains("multisub") || nameLower.contains("multi-sub") || nameLower.contains("multi sub")) {
        return "MSuB"
    }
    if (nameLower.contains(".ass") || nameLower.contains("[ass]") || nameLower.contains("{ass}") || nameLower.contains("-ass")) {
        return "1ASS"
    }
    if (nameLower.contains(".srt") || nameLower.contains("[srt]") || nameLower.contains("{srt}") || nameLower.contains("-srt")) {
        return "1SRT"
    }
    if (hasSubtitles) {
        return "SUB"
    }
    return null
}

/**
 * Single Video Row Item supporting all 10 VisibleFields options:
 * - Thumbnails toggle (16:9 preview vs compact icon)
 * - Extension toggle (with/without .mp4)
 * - Duration toggle
 * - Subtitle indicator toggle ([SUB])
 * - Full Name toggle (multiline vs 1-line)
 * - Size toggle
 * - Resolution toggle (1080p, 4K)
 * - Framerate toggle (60fps, 24fps)
 * - Date toggle
 * - Progress Bar toggle (Kitna aage tak dekha hai)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoItemCard(
    video: VideoItem,
    isDark: Boolean,
    visibleFields: VisibleFields = VisibleFields(),
    isHighlighted: Boolean = false,
    isSelected: Boolean = false,
    isLastPlayed: Boolean = false,
    isRenaming: Boolean = false,
    onConfirmRename: ((String) -> Unit)? = null,
    onCancelRename: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    thumbnailStrategy: ThumbnailStrategy = ThumbnailStrategy.SMART,
    thumbnailQuality: ThumbnailQuality = ThumbnailQuality.HIGH,
    thumbnailFallbackSecond: Int = 1,
    showNetworkThumbnails: Boolean = false,
    onThumbnailClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    reorderMode: Boolean = false
) {
    val surfaceColor = if (isDark) {
        if (isSelected) Color(0xFF1E1B4B).copy(alpha = 0.90f) else DarkGlassSurface
    } else {
        if (isSelected) Color(0xFFEEF2FF).copy(alpha = 0.95f) else LightGlassSurface
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val badgeBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f) else Color(0xFFE0F2FE)
    val badgeText = if (isDark) Color(0xFF94A3B8) else Color(0xFF0369A1)
    val subBadgeBg = if (isDark) Color(0xFF134E4A) else Color(0xFFCCFBF1)
    val subBadgeText = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    val resBadgeBg = if (isDark) Color(0xFF312E81) else Color(0xFFEEF2FF)
    val resBadgeText = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)

    val durationString = if (video.durationMs > 0) {
        formatDuration(video.durationMs)
    } else {
        "00:00"
    }

    val displayTitle = if (visibleFields.showExtension) {
        video.displayName
    } else {
        video.nameWithoutExtension
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // Playback history is the single source of truth (video.watchedProgress is a stale snapshot taken
    // at scan time) and it is observed, so the bar updates as soon as the user returns from the player.
    val historyVersion by com.example.util.PlaybackHistoryManager.version.collectAsState()
    val effectiveProgress = remember(video.id, video.path, video.durationMs, historyVersion) {
        com.example.util.PlaybackHistoryManager.getProgressFraction(context, video.id, video.path, video.durationMs)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = AccentPink.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, AccentGradient, RoundedCornerShape(16.dp))
                } else {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
                }
            )
            .then(
                if (!isRenaming && !reorderMode && !(visibleFields.showThumbnails && onThumbnailClick != null)) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Thumbnail or Compact Video Icon
            if (visibleFields.showThumbnails) {
                VideoThumbnailCard(
                    videoId = video.id,
                    videoUri = video.uri,
                    videoPath = video.path,
                    durationText = durationString,
                    isNew = video.isNew && visibleFields.showNewBadge,
                    isDark = isDark,
                    customGradientSeed = video.displayName.hashCode(),
                    showDuration = visibleFields.showDuration,
                    showProgressBar = visibleFields.showProgressBar,
                    watchedProgress = effectiveProgress,
                    isSelected = isSelected,
                    isLastPlayed = isLastPlayed,
                    thumbnailStrategy = thumbnailStrategy,
                    thumbnailQuality = thumbnailQuality,
                    thumbnailFallbackSecond = thumbnailFallbackSecond,
                    showNetworkThumbnails = showNetworkThumbnails,
                    remoteThumbnailUrl = video.thumbnailUrl,
                    onThumbnailClick = onThumbnailClick,
                    modifier = Modifier
                        .width(136.dp)
                        .height(80.dp)
                )
            } else {
                // Compact icon when thumbnails are hidden
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, AccentGradient, RoundedCornerShape(14.dp))
                            } else {
                                Modifier.border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = if (isSelected) AccentSkyBlue else if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Metadata details column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!isRenaming && !reorderMode && visibleFields.showThumbnails && onThumbnailClick != null) {
                            Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick
                            )
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.Center
            ) {
                if (isRenaming) {
                    val baseName = if (visibleFields.showExtension) video.displayName else video.nameWithoutExtension
                    InlineRenameField(
                        initialText = baseName,
                        onConfirm = { newName -> onConfirmRename?.invoke(newName) },
                        onCancel = { onCancelRename?.invoke() },
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                } else {
                    Text(
                        text = displayTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighlighted) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else primaryText,
                        maxLines = if (visibleFields.showFullName) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp
                    )
                }

                if (visibleFields.showPath && video.path.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = video.path,
                        fontSize = 11.sp,
                        color = if (isDark) DarkTextSecondary else LightTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Badges & details row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Size Badge
                    if (visibleFields.showSize) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = video.formattedSize,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }
                    }

                    // Resolution Badge
                    if (visibleFields.showResolution && video.resolution.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(resBadgeBg)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = video.resolution,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = resBadgeText
                            )
                        }
                    }

                    // Framerate Badge
                    if (visibleFields.showFramerate && video.framerate > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = video.formattedFramerate,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }
                    }

                    // Subtitle Badge (e.g. 1ASS, 2ASS, 1SRT, or MSuB if multiple formats exist)
                    if (visibleFields.showSubtitleIndicator) {
                        val subText = resolveSubtitleBadgeText(video.effectiveSubtitleFormats, video.displayName, video.hasSubtitles)
                        if (subText != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(subBadgeBg)
                                    .border(
                                        1.dp,
                                        if (isDark) Color(0xFF0D9488).copy(alpha = 0.4f) else Color(0xFF99F6E4),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = subBadgeText
                                )
                            }
                        }
                    }

                    // Date Badge with background chip matching folder date chip
                    if (visibleFields.showDate && video.formattedDate.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF334155).copy(alpha = 0.7f) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = video.formattedDate,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) DarkTextSecondary else LightTextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Mini watched progress bar if thumbnails are turned off but progress is enabled
                if (!visibleFields.showThumbnails && visibleFields.showProgressBar && effectiveProgress > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(effectiveProgress.coerceIn(0f, 1f))
                                .background(Color(0xFFEF4444))
                        )
                    }
                }
            }

            if (reorderMode) {
                ReorderTwoLineHandle(isDark = isDark)
            }
        }
    }
}

@Composable
private fun ReorderTwoLineHandle(isDark: Boolean) {
    val tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    Column(
        modifier = Modifier.padding(start = 8.dp).size(width = 24.dp, height = 30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(20.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(20.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(tint))
    }
}

/**
 * Grid View card for Video Items
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoItemGridCard(
    video: VideoItem,
    isDark: Boolean,
    visibleFields: VisibleFields = VisibleFields(),
    isSelected: Boolean = false,
    isLastPlayed: Boolean = false,
    isRenaming: Boolean = false,
    onConfirmRename: ((String) -> Unit)? = null,
    onCancelRename: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    thumbnailStrategy: ThumbnailStrategy = ThumbnailStrategy.SMART,
    thumbnailQuality: ThumbnailQuality = ThumbnailQuality.HIGH,
    thumbnailFallbackSecond: Int = 1,
    showNetworkThumbnails: Boolean = false,
    onThumbnailClick: (() -> Unit)? = null
) {
    val surfaceColor = if (isDark) {
        if (isSelected) Color(0xFF1E1B4B).copy(alpha = 0.90f) else DarkGlassSurface
    } else {
        if (isSelected) Color(0xFFEEF2FF).copy(alpha = 0.95f) else LightGlassSurface
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val badgeBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f) else Color(0xFFE0F2FE)
    val badgeText = if (isDark) Color(0xFF94A3B8) else Color(0xFF0369A1)
    val resBadgeBg = if (isDark) Color(0xFF312E81) else Color(0xFFEEF2FF)
    val resBadgeText = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
    val subBadgeBg = if (isDark) Color(0xFF134E4A) else Color(0xFFCCFBF1)
    val subBadgeText = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)

    val durationString = if (video.durationMs > 0) {
        formatDuration(video.durationMs)
    } else {
        "23:59"
    }

    val displayTitle = if (visibleFields.showExtension) {
        video.displayName
    } else {
        video.nameWithoutExtension
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // Playback history is the single source of truth (video.watchedProgress is a stale snapshot taken
    // at scan time) and it is observed, so the bar updates as soon as the user returns from the player.
    val historyVersion by com.example.util.PlaybackHistoryManager.version.collectAsState()
    val effectiveProgress = remember(video.id, video.path, video.durationMs, historyVersion) {
        com.example.util.PlaybackHistoryManager.getProgressFraction(context, video.id, video.path, video.durationMs)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = AccentPink.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, AccentGradient, RoundedCornerShape(16.dp))
                } else {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
                }
            )
            .then(
                if (!isRenaming) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (visibleFields.showThumbnails) {
                VideoThumbnailCard(
                    videoId = video.id,
                    videoUri = video.uri,
                    videoPath = video.path,
                    durationText = durationString,
                    isNew = video.isNew && visibleFields.showNewBadge,
                    isDark = isDark,
                    customGradientSeed = video.displayName.hashCode(),
                    showDuration = visibleFields.showDuration,
                    showProgressBar = visibleFields.showProgressBar,
                    watchedProgress = effectiveProgress,
                    isSelected = isSelected,
                    isLastPlayed = isLastPlayed,
                    thumbnailStrategy = thumbnailStrategy,
                    thumbnailQuality = thumbnailQuality,
                    thumbnailFallbackSecond = thumbnailFallbackSecond,
                    showNetworkThumbnails = showNetworkThumbnails,
                    remoteThumbnailUrl = video.thumbnailUrl,
                    onThumbnailClick = onThumbnailClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, AccentGradient, RoundedCornerShape(12.dp))
                            } else {
                                Modifier.border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isRenaming) {
                val baseName = if (visibleFields.showExtension) video.displayName else video.nameWithoutExtension
                InlineRenameField(
                    initialText = baseName,
                    onConfirm = { newName -> onConfirmRename?.invoke(newName) },
                    onCancel = { onCancelRename?.invoke() },
                    isDark = isDark,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            } else {
                Text(
                    text = displayTitle,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                    maxLines = if (visibleFields.showFullName) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (visibleFields.showSize) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = video.formattedSize,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeText
                        )
                    }
                }

                if (visibleFields.showResolution && video.resolution.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(resBadgeBg)
                            .padding(horizontal = 5.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = video.resolution,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = resBadgeText
                        )
                    }
                }

                if (visibleFields.showFramerate && video.framerate > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 5.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = video.formattedFramerate,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeText
                        )
                    }
                }

                if (visibleFields.showSubtitleIndicator) {
                    val subText = resolveSubtitleBadgeText(video.effectiveSubtitleFormats, video.displayName, video.hasSubtitles)
                    if (subText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(subBadgeBg)
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF0D9488).copy(alpha = 0.4f) else Color(0xFF99F6E4),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = subText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = subBadgeText
                            )
                        }
                    }
                }

                if (visibleFields.showDate && video.formattedDate.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF334155).copy(alpha = 0.7f) else Color(0xFFE2E8F0),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = video.formattedDate,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) DarkTextSecondary else LightTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
