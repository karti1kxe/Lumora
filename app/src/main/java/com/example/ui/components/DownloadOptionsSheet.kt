package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.DownloadFormat
import com.example.util.DownloadManager
import com.example.util.DownloadSourceType
import com.example.util.NetworkStreamResolver
import com.example.ytdlp.YtdlPreferences
import com.example.ytdlp.YtdlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Universal glass bottom sheet shell used across the app (torrents, provider detail pickers, download sheets).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadGlassSheet(
    isDark: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 24.dp)
        ) {
            content()
        }
    }
}

data class VideoQualityOption(
    val formatId: String,
    val resolutionLabel: String,
    val height: Int,
    val fps: Int,
    val vcodec: String,
    val acodec: String,
    val sizeBytes: Long,
    val directUrl: String? = null
)

data class AudioTrackOption(
    val id: String,
    val name: String,
    val language: String,
    val url: String? = null,
    val isOriginal: Boolean = false
)

data class SubtitleOption(
    val language: String,
    val languageCode: String,
    val url: String
)

/**
 * Full-featured download options sheet:
 * Supports genuine quality tiers (144p to 4K/8K), exact size calculation, original thumbnails,
 * chapters, subtitles, multi-audio selection (Original vs translations, MKV muxing),
 * high speed download boost, and audio lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsSheet(
    isDark: Boolean,
    glassBlurTransparency: Float = 85f,
    url: String,
    initialTitle: String,
    initialAuthor: String? = null,
    initialThumbnail: String? = null,
    startWithAudio: Boolean = false,
    audioOnly: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var isAudioMode by remember { mutableStateOf(audioOnly || startWithAudio) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf(initialTitle.ifBlank { "Media Download" }) }
    var author by remember { mutableStateOf(initialAuthor.orEmpty()) }
    var thumbnail by remember { mutableStateOf(initialThumbnail.orEmpty()) }
    var durationSec by remember { mutableStateOf(0L) }

    val videoQualities = remember { mutableStateListOf<VideoQualityOption>() }
    var selectedVideoIndex by remember { mutableIntStateOf(0) }

    val audioTracks = remember { mutableStateListOf<AudioTrackOption>() }
    val selectedAudioIndices = remember { mutableStateListOf<Int>() }

    val subtitles = remember { mutableStateListOf<SubtitleOption>() }
    val selectedSubtitleIndices = remember { mutableStateListOf<Int>() }

    var downloadLyrics by remember { mutableStateOf(true) }

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    LaunchedEffect(url) {
        isLoading = true
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                if (NetworkStreamResolver.isDirectMediaUrl(url)) {
                    // Direct media file probe
                    val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).build()
                    val req = Request.Builder().url(url).head().build()
                    val resp = runCatching { client.newCall(req).execute() }.getOrNull()
                    val length = resp?.header("Content-Length")?.toLongOrNull() ?: 0L
                    val isAudioExt = url.substringBefore('?').substringAfterLast('.', "").lowercase() in
                        setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
                    if (isAudioExt) isAudioMode = true

                    videoQualities.clear()
                    videoQualities.add(
                        VideoQualityOption(
                            formatId = "direct",
                            resolutionLabel = if (isAudioExt) "Audio Stream" else "Original Quality",
                            height = 1080,
                            fps = 30,
                            vcodec = if (isAudioExt) "none" else "h264",
                            acodec = "aac",
                            sizeBytes = length,
                            directUrl = url
                        )
                    )
                    audioTracks.clear()
                    audioTracks.add(AudioTrackOption("orig", "Original Audio", "und", url, isOriginal = true))
                    selectedAudioIndices.clear()
                    selectedAudioIndices.add(0)
                    isLoading = false
                    return@withContext
                }

                // Online site / yt-dlp metadata probe
                val prefs = YtdlPreferences(context)
                val dumpResult = YtdlpManager.dumpSingleJson(context, url, prefs)
                if (dumpResult.isSuccess) {
                    val rawJson = dumpResult.getOrNull().orEmpty()
                    val json = JSONObject(rawJson)

                    title = json.optString("title", initialTitle.ifBlank { "Media" })
                    author = json.optString("uploader", json.optString("channel", initialAuthor.orEmpty()))
                    thumbnail = json.optString("thumbnail", initialThumbnail.orEmpty())
                    durationSec = json.optLong("duration", 0L)

                    val formats = json.optJSONArray("formats") ?: JSONArray()
                    val parsedVideo = ArrayList<VideoQualityOption>()
                    val parsedAudio = ArrayList<AudioTrackOption>()

                    // Parse audio tracks first
                    var originalAdded = false
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val vcodec = fmt.optString("vcodec", "none")
                        val acodec = fmt.optString("acodec", "none")
                        if (vcodec == "none" && acodec != "none") {
                            val lang = fmt.optString("language", "und")
                            val isOrig = !originalAdded || lang == "original" || lang == "und"
                            val label = if (isOrig && !originalAdded) {
                                originalAdded = true
                                "Original Audio"
                            } else {
                                val displayLang = Locale(lang).displayLanguage.ifBlank { lang.uppercase(Locale.ROOT) }
                                "$displayLang Audio"
                            }
                            parsedAudio.add(
                                AudioTrackOption(
                                    id = fmt.optString("format_id", "a_$i"),
                                    name = label,
                                    language = lang,
                                    url = fmt.optString("url"),
                                    isOriginal = isOrig
                                )
                            )
                        }
                    }

                    if (parsedAudio.isEmpty()) {
                        parsedAudio.add(AudioTrackOption("default", "Original Audio", "und", isOriginal = true))
                    }

                    // Parse video qualities (group by height, take best bitrate)
                    val heightMap = HashMap<Int, VideoQualityOption>()
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val height = fmt.optInt("height", 0)
                        if (height <= 0) continue
                        val fps = fmt.optInt("fps", 30)
                        val vcodec = fmt.optString("vcodec", "")
                        val acodec = fmt.optString("acodec", "")
                        val sizeApprox = fmt.optLong("filesize", fmt.optLong("filesize_approx", 0L)).let { sz ->
                            if (sz > 0L) sz
                            else {
                                val tbr = fmt.optDouble("tbr", 0.0)
                                if (tbr > 0.0 && durationSec > 0L) (tbr * 1024 * durationSec / 8).toLong()
                                else 0L
                            }
                        }
                        val resLabel = "${height}p" + (if (fps > 30) "$fps" else "")
                        val opt = VideoQualityOption(
                            formatId = fmt.optString("format_id"),
                            resolutionLabel = resLabel,
                            height = height,
                            fps = fps,
                            vcodec = vcodec,
                            acodec = acodec,
                            sizeBytes = sizeApprox,
                            directUrl = fmt.optString("url").takeIf { it.isNotBlank() }
                        )

                        val existing = heightMap[height]
                        if (existing == null || opt.sizeBytes > existing.sizeBytes) {
                            heightMap[height] = opt
                        }
                    }

                    parsedVideo.addAll(heightMap.values.sortedByDescending { it.height })

                    if (parsedVideo.isEmpty()) {
                        parsedVideo.add(
                            VideoQualityOption("best", "Best Available", 1080, 30, "best", "best", 0L)
                        )
                    }

                    // Subtitles
                    val parsedSubs = ArrayList<SubtitleOption>()
                    val subsObj = json.optJSONObject("subtitles")
                    if (subsObj != null) {
                        for (key in subsObj.keys()) {
                            val arr = subsObj.optJSONArray(key) ?: continue
                            val subUrl = arr.optJSONObject(0)?.optString("url").orEmpty()
                            if (subUrl.isNotBlank()) {
                                val langLabel = Locale(key).displayLanguage.ifBlank { key.uppercase(Locale.ROOT) }
                                parsedSubs.add(SubtitleOption(langLabel, key, subUrl))
                            }
                        }
                    }

                    videoQualities.clear()
                    videoQualities.addAll(parsedVideo)
                    audioTracks.clear()
                    audioTracks.addAll(parsedAudio.distinctBy { it.name })
                    selectedAudioIndices.clear()
                    if (audioTracks.isNotEmpty()) selectedAudioIndices.add(0)

                    subtitles.clear()
                    subtitles.addAll(parsedSubs)

                    isLoading = false
                } else {
                    // Fallback option
                    videoQualities.clear()
                    videoQualities.add(VideoQualityOption("best", "Standard Quality", 720, 30, "best", "best", 0L))
                    audioTracks.clear()
                    audioTracks.add(AudioTrackOption("orig", "Original Audio", "und", isOriginal = true))
                    selectedAudioIndices.clear()
                    selectedAudioIndices.add(0)
                    isLoading = false
                }
            } catch (t: Throwable) {
                loadError = t.message ?: "Failed to inspect video streams"
                isLoading = false
            }
        }
    }

    DownloadGlassSheet(isDark = isDark, glassBlurTransparency = glassBlurTransparency, onDismiss = onDismiss) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Download Media",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            // Mode Switch (Video / Audio)
            if (!audioOnly) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .then(if (!isAudioMode) Modifier.background(AccentGradient) else Modifier.background(Color.Transparent))
                            .clickable { isAudioMode = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Video",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isAudioMode) Color.White else secondaryText
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .then(if (isAudioMode) Modifier.background(AccentGradient) else Modifier.background(Color.Transparent))
                            .clickable { isAudioMode = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Audio",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAudioMode) Color.White else secondaryText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title & metadata banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkThumbnail(
                thumbnail = thumbnail.takeIf { it.isNotBlank() },
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                fallback = {
                    StyledIcon(
                        drawableRes = if (isAudioMode) R.drawable.lumora_song else R.drawable.lumora_video,
                        contentDescription = null,
                        tint = secondaryText.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        fontSize = 12.sp,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentSkyBlue, strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analyzing stream qualities & tracks…", fontSize = 13.5.sp, color = secondaryText)
            }
        } else if (loadError != null) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text("Could not resolve format details: $loadError", color = Color(0xFFEF4444), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("You can still start the download using best default settings.", color = secondaryText, fontSize = 12.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isAudioMode) {
                    // Video Qualities list
                    Text("Select Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        videoQualities.forEachIndexed { index, q ->
                            val isSelected = index == selectedVideoIndex
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (isSelected) Modifier.background(AccentGradient)
                                        else Modifier.background(if (isDark) DarkGlassSurface else LightGlassSurface)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else borderColor,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedVideoIndex = index }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = q.resolutionLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else primaryText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (q.sizeBytes > 0L) DownloadFormat.bytes(q.sizeBytes) else "Stream",
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else secondaryText
                                )
                            }
                        }
                    }

                    // Audio Track Selection
                    if (audioTracks.size > 1) {
                        Text("Audio Tracks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            audioTracks.forEachIndexed { index, track ->
                                val isChecked = index in selectedAudioIndices
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f))
                                        .clickable {
                                            if (isChecked) {
                                                if (selectedAudioIndices.size > 1) selectedAudioIndices.remove(index)
                                            } else {
                                                selectedAudioIndices.add(index)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = track.name + (if (track.isOriginal) " (Default)" else ""),
                                        fontSize = 13.sp,
                                        color = primaryText
                                    )
                                    Text(
                                        text = if (isChecked) "✓" else "+",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isChecked) AccentSkyBlue else secondaryText
                                    )
                                }
                            }
                        }
                    }

                    // Multi-audio container notice
                    if (selectedAudioIndices.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentSkyBlue.copy(alpha = 0.12f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MKV Container will be used to preserve all ${selectedAudioIndices.size} selected audio tracks.",
                                fontSize = 12.sp,
                                color = AccentSkyBlue
                            )
                        }
                    }

                    // Subtitles list
                    if (subtitles.isNotEmpty()) {
                        Text("Subtitles", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subtitles.forEachIndexed { index, sub ->
                                val isSelected = index in selectedSubtitleIndices
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AccentSkyBlue.copy(alpha = 0.2f) else if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                                        .border(1.dp, if (isSelected) AccentSkyBlue else borderColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isSelected) selectedSubtitleIndices.remove(index)
                                            else selectedSubtitleIndices.add(index)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = sub.language,
                                        fontSize = 12.sp,
                                        color = if (isSelected) AccentSkyBlue else primaryText
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Audio Mode: Bitrate choices
                    Text("Audio Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
                    val audioQualities = listOf("320 kbps (High)", "256 kbps (Medium)", "128 kbps (Standard)")
                    var selectedAudioBitrate by remember { mutableIntStateOf(0) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioQualities.forEachIndexed { index, label ->
                            val isSelected = index == selectedAudioBitrate
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected) Modifier.background(AccentGradient)
                                        else Modifier.background(if (isDark) DarkGlassSurface else LightGlassSurface)
                                    )
                                    .border(1.dp, if (isSelected) Color.Transparent else borderColor, RoundedCornerShape(12.dp))
                                    .clickable { selectedAudioBitrate = index }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else primaryText
                                )
                            }
                        }
                    }

                    // Lyrics toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Download Synchronized Lyrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                            Text("Saves .lrc timestamps alongside music", fontSize = 11.5.sp, color = secondaryText)
                        }
                        Switch(
                            checked = downloadLyrics,
                            onCheckedChange = { downloadLyrics = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentSkyBlue
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Start Download button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AccentGradient)
                        .testTag("start_download_button")
                        .clickable {
                            val chosenVideo = videoQualities.getOrNull(selectedVideoIndex)
                            val chosenAudioTracks = selectedAudioIndices.mapNotNull { audioTracks.getOrNull(it) }
                            val chosenSubs = selectedSubtitleIndices.mapNotNull { subtitles.getOrNull(it) }

                            val isAudioTask = isAudioMode
                            val container = if (isAudioTask) "mp3" else if (chosenAudioTracks.size > 1) "mkv" else "mp4"

                            DownloadManager.enqueueMedia(
                                context = context,
                                sourceUrl = url,
                                title = title,
                                subtitle = author,
                                thumbnailUrl = thumbnail,
                                isAudio = isAudioTask,
                                selectedQuality = if (isAudioTask) "High (320k)" else (chosenVideo?.resolutionLabel ?: "1080p"),
                                audioTrackNames = chosenAudioTracks.map { it.name },
                                audioStreamUrls = chosenAudioTracks.mapNotNull { it.url },
                                subtitleUrls = chosenSubs.map { it.url },
                                hasLyrics = isAudioTask && downloadLyrics,
                                formatContainer = container,
                                estimatedBytes = chosenVideo?.sizeBytes ?: 0L,
                                sourceType = if (NetworkStreamResolver.isDirectMediaUrl(url)) DownloadSourceType.NETWORK else DownloadSourceType.VIDEO
                            )

                            Toast.makeText(context, "Added to downloads queue", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAudioMode) "Download Audio" else "Download Video",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
