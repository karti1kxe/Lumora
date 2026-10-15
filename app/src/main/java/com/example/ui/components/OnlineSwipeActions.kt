package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.util.DownloadManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.LightTextPrimary
import kotlinx.coroutines.launch

/**
 * Right-to-left swipe on an online card. The card only follows the finger a little (it is NOT selected / highlighted),
 * and [onTriggered] fires once when the swipe is long enough so the screen can show [OnlineQuickActionBar].
 * Vertical scrolling and taps keep working because only horizontal drags are claimed.
 */
fun Modifier.swipeLeftForActions(enabled: Boolean = true, onTriggered: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    val maxPx = with(density) { 48.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val trigger by rememberUpdatedState(onTriggered)

    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    val fire = total <= -thresholdPx
                    total = 0f
                    scope.launch { offset.animateTo(0f) }
                    if (fire) trigger()
                },
                onDragCancel = {
                    total = 0f
                    scope.launch { offset.animateTo(0f) }
                },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    total += dx
                    // only right -> left is used; the card never moves to the right
                    scope.launch { offset.snapTo(total.coerceIn(-maxPx, 0f)) }
                }
            )
        }
        .graphicsLayer { translationX = offset.value }
}

/**
 * Bottom floating glass toolbar (same look as the selection toolbar of the offline lists):
 * Play next · Play last · Playlist · Watch later · Not interested · Download · Share.
 */
@Composable
fun OnlineQuickActionBar(
    isDark: Boolean,
    alphaRatio: Float,
    isSavedForLater: Boolean,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onWatchLater: () -> Unit,
    onNotInterested: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null
) {
    val barBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0F172A)
        else Color(0xFF0F172A).copy(alpha = (0.70f + 0.30f * alphaRatio).coerceIn(0.40f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.75f + 0.25f * alphaRatio).coerceIn(0.45f, 1.0f))
    }
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val barShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .shadow(
                elevation = 18.dp,
                shape = barShape,
                ambientColor = AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = AccentPink.copy(alpha = 0.40f)
            )
            .clip(barShape)
            .background(barBg)
            .border(width = 1.5.dp, brush = AccentGradient, shape = barShape)
            .padding(horizontal = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        // Items keep a comfortable fixed width and the row scrolls sideways, so more actions can be added later.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionItem(R.drawable.lumora_next_in_queue, "Play next", Color(0xFF38BDF8), Modifier.width(78.dp), onPlayNext)
            QuickActionItem(R.drawable.lumora_last_in_queue, "Play last", Color(0xFF818CF8), Modifier.width(78.dp), onPlayLast)
            QuickActionItem(R.drawable.lumora_playlist_video, "Playlist", Color(0xFFF472B6), Modifier.width(78.dp), onSaveToPlaylist)
            QuickActionItem(
                R.drawable.lumora_watch_later,
                if (isSavedForLater) "Remove" else "Watch later",
                Color(0xFFA78BFA),
                Modifier.width(78.dp),
                onWatchLater
            )
            QuickActionItem(R.drawable.lumora_block, "Not interested", Color(0xFFEF4444), Modifier.width(78.dp), onNotInterested)
            if (onDownload != null) {
                QuickActionItem(R.drawable.lumora_download_minimalistic, "Download", Color(0xFF34D399), Modifier.width(78.dp), onDownload)
            }
            QuickActionItem(R.drawable.lumora_share, "Share", primaryText, Modifier.width(78.dp), onShare)
        }
    }
}

@Composable
private fun QuickActionItem(
    drawableRes: Int,
    label: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        StyledIcon(
            drawableRes = drawableRes,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

fun shareOnlineLink(context: Context, title: String, url: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {}
}

/**
 * Download button of the swipe toolbar: opens the same quality sheet as the Network tab (qualities with their sizes)
 * and starts the download. [audioOnly] (online audio player) shows only the audio qualities; the track's thumbnail
 * is embedded into the downloaded audio file.
 */
@Composable
fun OnlineDownloadSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    url: String,
    title: String,
    author: String?,
    thumbnailUrl: String?,
    audioOnly: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    DownloadOptionsSheet(
        isDark = isDark,
        glassBlurTransparency = glassBlurTransparency,
        url = url,
        initialTitle = title,
        initialAuthor = author,
        startWithAudio = audioOnly,
        audioOnly = audioOnly,
        initialThumbnail = thumbnailUrl,
        onDismiss = onDismiss,
        onDownload = { request ->
            onDismiss()
            val message = try {
                DownloadManager.enqueueCustom(context, request)
            } catch (_: Throwable) {
                "Could not start the download"
            }
            Toast.makeText(context, "$message (see Network › Download)", Toast.LENGTH_SHORT).show()
        }
    )
}
