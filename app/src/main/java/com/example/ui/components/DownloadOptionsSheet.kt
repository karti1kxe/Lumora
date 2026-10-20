package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.DownloadFormat
import com.example.util.DownloadProbe
import com.example.util.DownloadRequest
import com.example.util.FormatOption
import com.example.util.LinkInfo

/** Same translucent "liquid glass" colour as the app's other bottom sheets; the opacity follows the transparency setting. */
@Composable
internal fun downloadSheetColor(isDark: Boolean, glassBlurTransparency: Float): Color {
    return glassPanelColor(isDark, glassBlurTransparency)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadGlassSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = downloadSheetColor(isDark, glassBlurTransparency),
        scrimColor = Color.Black.copy(alpha = 0.35f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(secondaryText.copy(alpha = 0.4f))
            )
        }
    ) {
        PanelScaled {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 20.dp)
        ) {
            content()
        }
    }
}
}

/**
 * Opens from the download button of a link in Recent Streams: name, author, container and the quality of the video
 * (or of the audio only) can be changed before the download starts. Tapping the quality opens the list of qualities.
 */
@Composable
fun DownloadOptionsSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    url: String,
    initialTitle: String,
    initialAuthor: String?,
    startWithAudio: Boolean,
    onDismiss: () -> Unit,
    onDownload: (DownloadRequest) -> Unit,
    /** true = only audio can be downloaded (opened from the online audio player): no Video tab. */
    audioOnly: Boolean = false,
    /** Thumbnail of the link, embedded into a downloaded audio as its cover picture. */
    initialThumbnail: String? = null
) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val fieldBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    var tab by remember { mutableStateOf(if (startWithAudio || audioOnly) 0 else 1) }
    var title by remember { mutableStateOf(initialTitle) }
    var titleEdited by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf(initialAuthor.orEmpty()) }
    var info by remember { mutableStateOf<LinkInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoSel by remember { mutableStateOf<FormatOption?>(null) }
    var audioSel by remember { mutableStateOf<FormatOption?>(null) }
    var picker by remember { mutableStateOf(0) }

    LaunchedEffect(url) {
        val (loaded, err) = DownloadProbe.load(context, url)
        info = loaded
        error = err
        loading = false
        if (loaded != null) {
            val t = loaded.title
            if (!titleEdited && t != null && t.isNotBlank()) title = t
            val u = loaded.uploader
            if (author.isBlank() && u != null) author = u
            // the list is sorted best first: the top quality (with its real size) is the default choice
            if (videoSel == null) videoSel = loaded.videoFormats.firstOrNull()
            if (audioSel == null) audioSel = loaded.audioFormats.firstOrNull()
        }
    }

    val isAudio = tab == 0
    val options = if (isAudio) info?.audioFormats.orEmpty() else info?.videoFormats.orEmpty()
    val selected = if (isAudio) audioSel else videoSel

    fun buildRequest(): DownloadRequest {
        val cleanTitle = title.trim().ifBlank { initialTitle.ifBlank { url } }
        val cleanAuthor = author.trim().ifBlank { null }
        val sel = selected
        // every offered quality is one file (sound included), so the original format / container is kept
        val spec: String? = when {
            sel == null -> null
            sel.spec != null -> sel.spec
            isAudio -> sel.id + "/ba/b"
            else -> sel.id
        }
        val estimate = sel?.sizeBytes ?: 0L
        return DownloadRequest(
            source = url,
            title = cleanTitle,
            author = cleanAuthor,
            isAudio = isAudio,
            formatSpec = spec,
            container = null,
            estimatedBytes = estimate,
            thumbnailUrl = info?.thumbnailUrl ?: initialThumbnail
        )
    }

    DownloadGlassSheet(isDark, glassBlurTransparency, onDismiss) {
        // ---- header: title + Download button ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Download", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = primaryText)
                Text(text = "Adjust download", fontSize = 13.sp, color = secondaryText)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AccentGradient)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDownload(buildRequest()) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledIcon(
                    drawableRes = R.drawable.lumora_download_minimalistic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Download", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        // ---- Audio | Video with the app's gradient liquid underline ----
        if (audioOnly) {
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            PlaylistFilterTabs(
                tabs = listOf("Audio", "Video"),
                selectedIndex = tab,
                isDark = isDark,
                onSelect = { tab = it },
                modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)
            )
        }

        // ---- name ----
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                titleEdited = true
            },
            label = { Text("Title") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text("Author") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // ---- quality ----
        Text(
            text = if (isAudio) "Audio quality" else "Video quality",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        DownloadQualityRow(
            option = selected,
            badge = selected?.ext ?: (if (isAudio) "AUTO" else "AUTO"),
            title = selected?.label ?: (if (isAudio) "Best audio (auto)" else "Best video (auto)"),
            isDark = isDark,
            enabled = true,
            onClick = { if (!loading && options.isNotEmpty()) picker = if (isAudio) 2 else 1 }
        )
        Spacer(modifier = Modifier.height(6.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentSkyBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Loading qualities…", fontSize = 12.sp, color = secondaryText)
            }
            error != null -> Text(
                text = "Could not load the quality list ($error). The best quality will be downloaded.",
                fontSize = 12.sp,
                color = secondaryText
            )
            options.isEmpty() -> Text(text = "Only the default quality is available for this link.", fontSize = 12.sp, color = secondaryText)
            else -> Text(text = "Tap to choose a quality (${options.size} available, sizes are approximate)", fontSize = 12.sp, color = secondaryText)
        }

        // ---- where it is saved ----
        Column(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(fieldBg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(text = "Save to", fontSize = 11.sp, color = secondaryText)
            Text(
                text = if (isAudio) "Music/Lumora" else "Movies/Lumora",
                fontSize = 14.sp,
                color = primaryText
            )
        }

        // ---- the link ----
        Row(
            modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledIcon(
                drawableRes = R.drawable.lumora_link,
                contentDescription = null,
                tint = AccentSkyBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = url,
                fontSize = 13.sp,
                color = AccentSkyBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (picker != 0) {
        val forAudio = picker == 2
        val list = if (forAudio) info?.audioFormats.orEmpty() else info?.videoFormats.orEmpty()
        val current = if (forAudio) audioSel else videoSel
        DownloadGlassSheet(isDark, glassBlurTransparency, onDismiss = { picker = 0 }) {
            Text(
                text = if (forAudio) "Audio quality" else "Video quality",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            if (list.isEmpty()) {
                DownloadQualityRow(
                    option = null,
                    badge = "AUTO",
                    title = if (forAudio) "Best audio (auto)" else "Best video (auto)",
                    isDark = isDark,
                    enabled = current == null,
                    onClick = {
                        if (forAudio) audioSel = null else videoSel = null
                        picker = 0
                    }
                )
            }
            for ((index, opt) in list.withIndex()) {
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                DownloadQualityRow(
                    option = opt,
                    badge = opt.ext,
                    title = opt.label,
                    isDark = isDark,
                    enabled = current?.id == opt.id,
                    onClick = {
                        if (forAudio) audioSel = opt else videoSel = opt
                        picker = 0
                    }
                )
            }
        }
    }
}

/** One quality: format badge, "2160p60 (3840x2160)", codec / size chips. [enabled] marks the chosen one in the list. */
@Composable
private fun DownloadQualityRow(
    option: FormatOption?,
    badge: String,
    title: String,
    isDark: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val accent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val rowBg = if (enabled) {
        if (isDark) Color(0xFF0284C7).copy(alpha = 0.22f) else Color(0xFFE0F2FE)
    } else {
        if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = badge.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (option != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (option.codec.isNotBlank()) OptionChip(option.codec, secondaryText)
                    if (option.sizeBytes > 0L) OptionChip(DownloadFormat.bytes(option.sizeBytes), accent)
                    if (!option.hasAudio) OptionChip("video only", secondaryText)
                }
            }
        }
        if (enabled) {
            StyledIcon(
                drawableRes = R.drawable.lumora_check_circle,
                contentDescription = "Selected",
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun OptionChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}
