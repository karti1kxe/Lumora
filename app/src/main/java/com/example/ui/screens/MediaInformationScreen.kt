package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BottomMoveCopyProgressBar
import com.example.ui.components.DirectoryPickerAction
import com.example.ui.components.FloatingDirectoryPickerSheet
import com.example.ui.components.isRestrictedDirectory
import com.example.ui.components.HeaderBar
import com.example.ui.components.StyledIcon
import com.example.ui.components.bounceClick
import com.example.ui.components.loadVideoThumbnail
import com.example.ui.state.SettingsPreferencesManager
import com.example.ui.state.ThemeMode
import com.example.ui.state.ThumbnailQuality
import com.example.ui.state.ThumbnailStrategy
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkBackgroundEnd
import com.example.ui.theme.DarkBackgroundStart
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightBackgroundEnd
import com.example.ui.theme.LightBackgroundStart
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.MkvAttachmentInfo
import com.example.util.MkvChapterInfo
import com.example.util.MkvContainerMetadata
import com.example.util.MkvManagerEngine
import com.example.util.MkvTrackInfo
import com.example.util.MkvTrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class MediaInfoSection(val title: String) {
    OVERVIEW("Overview"),
    VIDEO("Videos"),
    AUDIO("Audios"),
    SUBTITLE("Subtitles"),
    CHAPTERS("Chapters"),
    ATTACHMENTS("Attachments")
}

data class MediaInfoData(
    val metadata: MkvContainerMetadata,
    val tracks: List<MkvTrackInfo>,
    val chapters: List<MkvChapterInfo>,
    val attachments: List<MkvAttachmentInfo>
)

@Composable
fun MediaInformationScreen(
    video: VideoItem,
    uiState: com.example.ui.state.UiState,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = uiState.themeMode == ThemeMode.DARK
    val background = if (isDark) Brush.verticalGradient(listOf(DarkBackgroundStart, DarkBackgroundEnd))
    else Brush.verticalGradient(listOf(LightBackgroundStart, LightBackgroundEnd))
    val primary = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val surface = if (isDark) DarkGlassSurface else LightGlassSurface
    val border = if (isDark) DarkGlassBorder else LightGlassBorder

    var data by remember(video.path) { mutableStateOf<MediaInfoData?>(null) }
    var error by remember(video.path) { mutableStateOf<String?>(null) }
    var active by rememberSaveable { mutableStateOf(MediaInfoSection.OVERVIEW) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingOutputName by remember { mutableStateOf("") }
    var pendingExtract: (suspend (Uri) -> Boolean)? by remember { mutableStateOf(null) }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressName by remember { mutableStateOf("") }
    var progressMessage by remember { mutableStateOf("") }
    var chapterFormat by rememberSaveable { mutableStateOf("xml") }
    var expandedAttachments by rememberSaveable { mutableStateOf(false) }
    var showFontDirectoryPicker by remember { mutableStateOf(false) }
    var mediaThumbnail by remember(video.path) { mutableStateOf<android.graphics.Bitmap?>(null) }

    var lastExtractDir by rememberSaveable {
        mutableStateOf<String?>(SettingsPreferencesManager.getLastExtractDirectory(context))
    }

    val preferredExtractDir = remember(lastExtractDir, video.path) {
        lastExtractDir ?: try {
            val f = java.io.File(video.path)
            val p = if (f.isDirectory) f else f.parentFile
            if (p != null && p.exists() && p.canRead() && !isRestrictedDirectory(p)) {
                p.absolutePath
            } else null
        } catch (_: Throwable) { null }
    }

    // Search state
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(video.path, video.uri) {
        error = null
        data = null
        try {
            val result = withContext(Dispatchers.IO) {
                val ext = video.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext == "mkv" || ext == "webm" || ext == "mka") {
                        val parsed = MkvManagerEngine.parseMkvFile(context, video.uri)
                        val enrichedTracks = parsed.second.map { track ->
                            if (track.trackType == MkvTrackType.VIDEO && track.frameRate <= 0f && video.framerate > 0.0) {
                                track.copy(frameRate = video.framerate.toFloat(), durationMs = parsed.first.durationMs)
                            } else if (track.durationMs <= 0L) {
                                track.copy(durationMs = parsed.first.durationMs)
                            } else track
                        }
                        MediaInfoData(parsed.first, enrichedTracks, parsed.third.first, parsed.third.second)
                    } else {
                        val meta = com.example.util.MediaMetadataExtractor.extractMetadata(
                            filePath = video.path,
                            context = context,
                            uri = video.uri,
                            displayName = video.displayName,
                            sizeBytes = video.sizeBytes,
                            dateModified = video.dateModified
                        )
                        val track = MkvTrackInfo(
                            id = 1, trackNumber = 1, trackType = MkvTrackType.VIDEO,
                            name = video.displayName.substringBeforeLast('.', video.displayName),
                            codecName = "Container video stream",
                            pixelWidth = meta.width, pixelHeight = meta.height,
                            frameRate = meta.framerate.toFloat(), durationMs = meta.durationMs,
                            bitrateBps = if (meta.durationMs > 0) video.sizeBytes * 8_000L / meta.durationMs else 0L
                        )
                        MediaInfoData(
                            MkvContainerMetadata(
                                fileName = video.displayName,
                                filePath = video.path,
                                fileSizeBytes = video.sizeBytes,
                                durationMs = meta.durationMs
                            ),
                            listOf(track), emptyList(), emptyList()
                        )
                    }
                }
                data = result
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Unable to read media information."
            }
    }

    LaunchedEffect(video.path, video.uri) {
        mediaThumbnail = loadVideoThumbnail(
            context, video.id, video.uri, video.path,
            ThumbnailStrategy.SMART, ThumbnailQuality.MEDIUM, false
        )
    }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val infoText = remember(data, active) { buildInformationText(data, active, video) }

    fun copyText(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Media Information", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${video.displayName} — ${active.title}")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share"))
    }

    fun queueExtract(name: String, action: suspend (Uri) -> Boolean) {
        pendingOutputName = name
        pendingExtract = action
        showPicker = true
    }

    val body = data

    if (showPicker) {
        FloatingDirectoryPickerSheet(
            action = DirectoryPickerAction.EXTRACT,
            itemCount = 1,
            itemDescription = pendingOutputName,
            initialPath = preferredExtractDir,
            isDark = isDark,
            glassBlurTransparency = uiState.glassBlurTransparency,
            onConfirmTargetDir = { targetDir ->
                showPicker = false
                val dirPath = targetDir.absolutePath
                lastExtractDir = dirPath
                SettingsPreferencesManager.setLastExtractDirectory(context, dirPath)
                val work = pendingExtract
                pendingExtract = null
                val targetFile = getUniqueDestinationFile(targetDir, pendingOutputName)
                val targetUri = Uri.fromFile(targetFile)
                if (work != null) {
                    scope.launch {
                        processing = true
                        progress = 0.05f
                        progressName = targetFile.name
                        progressMessage = "Extracting..."
                        val ok = try {
                            work(targetUri)
                        } catch (_: Throwable) { false }
                        processing = false
                        progress = if (ok) 1f else 0f
                        if (ok) {
                            try {
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(targetFile.absolutePath),
                                    null,
                                    null
                                )
                            } catch (_: Throwable) {}
                        }
                        Toast.makeText(
                            context,
                            if (ok) "Extracted successfully: ${targetFile.name}" else "Extraction failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = {
                showPicker = false
                pendingExtract = null
            }
        )
    }

    if (showFontDirectoryPicker && body != null) {
        FloatingDirectoryPickerSheet(
            action = DirectoryPickerAction.EXTRACT,
            itemCount = body.attachments.count { it.isFont },
            itemDescription = "font attachments",
            initialPath = preferredExtractDir,
            isDark = isDark,
            glassBlurTransparency = uiState.glassBlurTransparency,
            onConfirmTargetDir = { target ->
                showFontDirectoryPicker = false
                val dirPath = target.absolutePath
                lastExtractDir = dirPath
                SettingsPreferencesManager.setLastExtractDirectory(context, dirPath)
                val fonts = body.attachments.filter { it.isFont }
                scope.launch {
                    processing = true
                    progress = 0f
                    progressName = "Fonts"
                    var completed = 0
                    for (font in fonts) {
                        try {
                            val out = getUniqueDestinationFile(target, sanitizeFileComponent(font.fileName))
                            out.parentFile?.mkdirs()
                            out.outputStream().use { it.write(font.data) }
                            try {
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(out.absolutePath),
                                    null,
                                    null
                                )
                            } catch (_: Throwable) {}
                        } catch (_: Throwable) {}
                        completed++
                        progress = completed.toFloat() / fonts.size.coerceAtLeast(1)
                        progressName = font.fileName
                        progressMessage = "Saving font ${completed}/${fonts.size}"
                    }
                    processing = false
                    Toast.makeText(context, "${fonts.size} font(s) extracted to ${target.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showFontDirectoryPicker = false }
        )
    }

    // Exact top safe area calculation (identical to HomeScreen to prevent camera notch jump)
    val currentStatusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val currentCutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val currentTopInset = maxOf(currentStatusBarTop, currentCutoutTop)

    var stableTopInsetFloat by rememberSaveable { mutableStateOf(0f) }
    if (currentTopInset > 0.dp && currentTopInset.value != stableTopInsetFloat) {
        stableTopInsetFloat = currentTopInset.value
    }
    val safeTopPadding = when {
        currentTopInset > 0.dp -> currentTopInset
        stableTopInsetFloat > 0f -> stableTopInsetFloat.dp
        else -> 36.dp
    }

    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = safeTopPadding + 8.dp)
                .padding(horizontal = 16.dp)
        ) {
            HeaderBar(
                title = "Media Information & Extractor",
                subtitle = video.displayName,
                themeMode = uiState.themeMode,
                onSettingsClick = null, // Settings button removed as requested
                onBackClick = onBack,
                onSearchClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                onSortClick = null,
                onCopyClick = { copyText(infoText) },
                onShareClick = { shareText(infoText) }
            )

            // Sleek Search Bar for Media Information
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(surface)
                        .border(1.dp, border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search by language (hi, ja, en), codec, quality (1080p), chapter, font...",
                                    fontSize = 13.sp,
                                    color = secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = primary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(AccentSkyBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.08f))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = "Clear",
                                    tint = secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Navigation Tabs with Custom Icons & Gradient Active Indicators
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(MediaInfoSection.entries) { section ->
                    val selected = active == section
                    val icon = when (section) {
                        MediaInfoSection.OVERVIEW -> Icons.Outlined.Info
                        MediaInfoSection.VIDEO -> Icons.Outlined.VideoFile
                        MediaInfoSection.AUDIO -> Icons.Outlined.Audiotrack
                        MediaInfoSection.SUBTITLE -> Icons.Outlined.Subtitles
                        MediaInfoSection.CHAPTERS -> Icons.Outlined.Bookmarks
                        MediaInfoSection.ATTACHMENTS -> Icons.Outlined.AttachFile
                    }
                    val tabModifier = if (selected) {
                        Modifier.background(AccentGradient)
                    } else {
                        Modifier.background(if (isDark) Color(0xFF1E293B).copy(alpha = .72f) else Color(0xFFF1F5F9))
                    }
                    Box(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .then(tabModifier)
                            .border(1.dp, if (selected) Color.Transparent else border, RoundedCornerShape(19.dp))
                            .bounceClick { active = section }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StyledIcon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) Color.White else primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = section.title,
                                color = if (selected) Color.White else primary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (body == null && error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentSkyBlue)
                }
            } else if (error != null) {
                InfoCard(title = "Unable to scan media", isDark = isDark, border = border, surface = surface) {
                    Text(error!!, color = secondary, fontSize = 14.sp)
                }
            } else {
                MediaInfoContent(
                    section = active,
                    data = body!!,
                    video = video,
                    searchQuery = searchQuery,
                    mediaThumbnail = mediaThumbnail,
                    isDark = isDark,
                    primary = primary,
                    secondary = secondary,
                    surface = surface,
                    border = border,
                    chapterFormat = chapterFormat,
                    onChapterFormatChange = { chapterFormat = it },
                    expandedAttachments = expandedAttachments,
                    onExpandedAttachmentsChange = { expandedAttachments = it },
                    onExtractAllFonts = { showFontDirectoryPicker = true },
                    onExtract = ::queueExtract,
                    onCopy = ::copyText,
                    onShare = ::shareText,
                    onProgress = { f, msg, name ->
                        progress = f
                        progressMessage = msg
                        progressName = name
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = processing,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).navigationBarsPadding()
        ) {
            BottomMoveCopyProgressBar(
                isDark = isDark,
                alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(.15f, 1f),
                isMove = false,
                customActionText = "Extracting...",
                customIcon = Icons.Outlined.Download,
                progressFraction = progress,
                currentItemName = progressName,
                completedCount = if (progress >= 1f) 1 else 0,
                totalCount = 1
            )
        }
    }
}

@Composable
private fun MediaInfoContent(
    section: MediaInfoSection,
    data: MediaInfoData,
    video: VideoItem,
    searchQuery: String,
    mediaThumbnail: android.graphics.Bitmap?,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color,
    chapterFormat: String,
    onChapterFormatChange: (String) -> Unit,
    expandedAttachments: Boolean,
    onExpandedAttachmentsChange: (Boolean) -> Unit,
    onExtractAllFonts: () -> Unit,
    onExtract: (String, suspend (Uri) -> Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onProgress: (Float, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val q = searchQuery.trim().lowercase(Locale.ROOT)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (section) {
            MediaInfoSection.OVERVIEW -> {
                item {
                    var bitmap by remember(video.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(video.path) {
                        bitmap = loadVideoThumbnail(
                            context, video.id, video.uri, video.path,
                            ThumbnailStrategy.SMART, ThumbnailQuality.MEDIUM, false
                        )
                    }
                    OverviewCard(data, video, bitmap, q, isDark, primary, secondary, surface, border, onCopy)
                }
            }
            MediaInfoSection.VIDEO -> {
                val allTracks = data.tracks.filter { it.trackType == MkvTrackType.VIDEO }
                val tracks = if (q.isBlank()) allTracks else allTracks.filter { track ->
                    track.name.contains(q, true) ||
                    track.language.contains(q, true) ||
                    track.displayCodec.contains(q, true) ||
                    track.codecId.contains(q, true) ||
                    track.displayResolution.contains(q, true) ||
                    "${track.pixelWidth}".contains(q) ||
                    "${track.pixelHeight}".contains(q) ||
                    track.hdrFormat.contains(q, true) ||
                    (q.contains("1080") && (track.pixelHeight >= 1080 || track.displayResolution.contains("1080"))) ||
                    (q.contains("720") && (track.pixelHeight == 720 || track.displayResolution.contains("720"))) ||
                    (q.contains("4k") && (track.pixelWidth >= 3840 || track.pixelHeight >= 2160)) ||
                    (q.contains("2k") && track.pixelWidth >= 2000) ||
                    (track.isDefault && "default".contains(q)) ||
                    (track.isForced && "forced".contains(q))
                }

                if (tracks.isEmpty()) {
                    item {
                        EmptyInfo(
                            if (q.isBlank()) "No video tracks detected." else "No video tracks matching \"$searchQuery\".",
                            primary, secondary, surface, border
                        )
                    }
                }
                val allVideoTracks = data.tracks.filter { it.trackType == MkvTrackType.VIDEO }
                items(tracks, key = { "v-${it.id}-${it.trackNumber}" }) { track ->
                    TrackCard(
                        track = track, type = MkvTrackType.VIDEO, thumbnail = mediaThumbnail, isDark = isDark, primary = primary,
                        secondary = secondary, surface = surface, border = border,
                        fileSizeBytes = data.metadata.fileSizeBytes,
                        onExtract = {
                            val ext = videoExtension(track)
                            val baseVideoName = sanitizeFileComponent(video.displayName.substringBeforeLast('.', video.displayName))
                            val trackIndex = allVideoTracks.indexOfFirst { it.id == track.id && it.trackNumber == track.trackNumber }
                                .takeIf { it >= 0 } ?: allVideoTracks.indexOf(track)
                            val videoSuffix = if (trackIndex <= 0) "" else "${trackIndex + 1}"
                            val outFileName = "$baseVideoName-video$videoSuffix.$ext"
                            onExtract(outFileName) { uri ->
                                onProgress(.12f, "Scanning video track...", outFileName)
                                MkvManagerEngine.extractSingleTrack(
                                    context, video.uri, data.metadata, track, uri
                                ) { f, msg -> onProgress(f, msg, outFileName) }
                            }
                        }
                    )
                }
            }
            MediaInfoSection.AUDIO -> {
                val allTracks = data.tracks.filter { it.trackType == MkvTrackType.AUDIO }
                val tracks = if (q.isBlank()) allTracks else allTracks.filter { track ->
                    track.name.contains(q, true) ||
                    track.language.contains(q, true) ||
                    track.displayCodec.contains(q, true) ||
                    track.codecId.contains(q, true) ||
                    channelLabel(track.channels).contains(q, true) ||
                    "${track.channels}".contains(q) ||
                    "${track.sampleRate}".contains(q) ||
                    (q.contains("5.1") && track.channels == 6) ||
                    (q.contains("7.1") && track.channels == 8) ||
                    (q.contains("stereo") && track.channels == 2) ||
                    (q.contains("mono") && track.channels == 1) ||
                    (q.contains("hindi") && (track.language.equals("hi", true) || track.language.contains("hin", true))) ||
                    (q.contains("english") && (track.language.equals("en", true) || track.language.contains("eng", true))) ||
                    (q.contains("japanese") && (track.language.equals("ja", true) || track.language.contains("jpn", true))) ||
                    (track.isDefault && "default".contains(q)) ||
                    (track.isForced && "forced".contains(q))
                }

                if (tracks.isEmpty()) {
                    item {
                        EmptyInfo(
                            if (q.isBlank()) "No audio tracks detected." else "No audio tracks matching \"$searchQuery\".",
                            primary, secondary, surface, border
                        )
                    }
                }
                items(tracks, key = { "a-${it.id}-${it.trackNumber}" }) { track ->
                    TrackCard(
                        track = track, type = MkvTrackType.AUDIO, isDark = isDark, primary = primary,
                        secondary = secondary, surface = surface, border = border,
                        fileSizeBytes = data.metadata.fileSizeBytes,
                        onExtract = {
                            val ext = audioExtension(track)
                            val outFileName = buildTrackOutputFileName(
                                videoDisplayName = video.displayName,
                                track = track,
                                allTracksOfType = allTracks,
                                extension = ext,
                                defaultFallbackLang = "audio"
                            )
                            onExtract(outFileName) { uri ->
                                onProgress(.12f, "Scanning audio track...", outFileName)
                                MkvManagerEngine.extractSingleTrack(
                                    context, video.uri, data.metadata, track, uri
                                ) { f, msg -> onProgress(f, msg, outFileName) }
                            }
                        }
                    )
                }
            }
            MediaInfoSection.SUBTITLE -> {
                val allTracks = data.tracks.filter { it.trackType == MkvTrackType.SUBTITLE }
                val tracks = if (q.isBlank()) allTracks else allTracks.filter { track ->
                    track.name.contains(q, true) ||
                    track.language.contains(q, true) ||
                    subtitleFormatLabel(track).contains(q, true) ||
                    track.codecId.contains(q, true) ||
                    (q.contains("hindi") && (track.language.equals("hi", true) || track.language.contains("hin", true))) ||
                    (q.contains("english") && (track.language.equals("en", true) || track.language.contains("eng", true))) ||
                    (q.contains("japanese") && (track.language.equals("ja", true) || track.language.contains("jpn", true))) ||
                    (q.contains("ass") && (track.codecId.contains("ASS", true) || track.codecId.contains("SSA", true))) ||
                    (q.contains("srt") && (track.codecId.contains("UTF8", true) || track.codecId.contains("SRT", true))) ||
                    (q.contains("pgs") && track.codecId.contains("PGS", true)) ||
                    (q.contains("vtt") && track.codecId.contains("VTT", true)) ||
                    (track.isDefault && "default".contains(q)) ||
                    (track.isForced && "forced".contains(q)) ||
                    (track.isHearingImpaired && "hearing impaired".contains(q))
                }

                if (tracks.isEmpty()) {
                    item {
                        EmptyInfo(
                            if (q.isBlank()) "No subtitle tracks detected." else "No subtitle tracks matching \"$searchQuery\".",
                            primary, secondary, surface, border
                        )
                    }
                }
                items(tracks, key = { "s-${it.id}-${it.trackNumber}" }) { track ->
                    SubtitleTrackCard(
                        track = track, isDark = isDark, primary = primary, secondary = secondary,
                        surface = surface, border = border,
                        fileSizeBytes = data.metadata.fileSizeBytes,
                        onCopy = {
                            scope.launch {
                                val content = MkvManagerEngine.extractSubtitleTrackToString(context, video.uri, track)
                                onCopy(content)
                            }
                        },
                        onExtract = {
                            val ext = subtitleExtension(track)
                            val outFileName = buildTrackOutputFileName(
                                videoDisplayName = video.displayName,
                                track = track,
                                allTracksOfType = allTracks,
                                extension = ext,
                                defaultFallbackLang = "subtitle"
                            )
                            onExtract(outFileName) { uri ->
                                onProgress(.10f, "Reading subtitle cues...", outFileName)
                                MkvManagerEngine.extractSingleTrack(
                                    context, video.uri, data.metadata, track, uri
                                ) { f, msg -> onProgress(f, msg, outFileName) }
                            }
                        }
                    )
                }
            }
            MediaInfoSection.CHAPTERS -> {
                val allChapters = data.chapters
                val filteredChapters = if (q.isBlank()) allChapters else allChapters.filterIndexed { index, ch ->
                    ch.title.contains(q, true) ||
                    ch.formattedStartTime.contains(q, true) ||
                    ch.formattedEndTime.contains(q, true) ||
                    ch.language.contains(q, true) ||
                    "chapter ${index + 1}".contains(q, true)
                }

                item {
                    ChapterInfoCard(
                        chapters = filteredChapters,
                        totalChapterCount = allChapters.size,
                        searchQuery = searchQuery,
                        format = chapterFormat,
                        onFormatChange = onChapterFormatChange,
                        onCopy = {
                            onCopy(buildChapterText(filteredChapters, chapterFormat))
                        },
                        onExtract = {
                            val extension = if (chapterFormat == "xml") "xml" else "txt"
                            val engineFormat = if (chapterFormat == "xml") "xml" else "ogm"
                            val baseVideoName = sanitizeFileComponent(video.displayName.substringBeforeLast('.', video.displayName))
                            val safe = "$baseVideoName-chapters.$extension"
                            onExtract(safe) { uri ->
                                onProgress(.25f, "Writing chapter file...", safe)
                                MkvManagerEngine.exportChapters(context, filteredChapters, engineFormat, uri)
                            }
                        },
                        isDark = isDark, primary = primary, secondary = secondary,
                        surface = surface, border = border
                    )
                }
            }
            MediaInfoSection.ATTACHMENTS -> {
                val allAttachments = data.attachments
                val attachments = if (q.isBlank()) allAttachments else allAttachments.filter { att ->
                    att.fileName.contains(q, true) ||
                    att.mimeType.contains(q, true) ||
                    att.description.contains(q, true) ||
                    (att.isFont && (q.contains("font") || q.contains("ttf") || q.contains("otf")))
                }

                if (attachments.isEmpty()) {
                    item {
                        EmptyInfo(
                            if (q.isBlank()) "No attachments detected." else "No attachments matching \"$searchQuery\".",
                            primary, secondary, surface, border
                        )
                    }
                } else {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${attachments.size} attachments • ${attachments.count { it.isFont }} fonts",
                                color = primary, fontWeight = FontWeight.Bold, fontSize = 15.sp
                            )
                            Text(
                                "Extract all fonts",
                                color = AccentSkyBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    onExtractAllFonts()
                                }
                            )
                        }
                    }
                    items(attachments, key = { "att-${it.id}-${it.uid}" }) { att ->
                        AttachmentCard(
                            attachment = att, isDark = isDark, primary = primary, secondary = secondary,
                            surface = surface, border = border,
                            onExtract = {
                                onExtract(att.fileName.ifBlank { "attachment_${att.id}" }) { uri ->
                                    onProgress(.25f, "Saving attachment...", att.fileName)
                                    MkvManagerEngine.extractAttachment(context, att, uri)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    data: MediaInfoData,
    video: VideoItem,
    bitmap: android.graphics.Bitmap?,
    searchQuery: String,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color,
    onCopy: (String) -> Unit
) {
    val meta = data.metadata
    val all = data.tracks
    val videos = all.filter { it.trackType == MkvTrackType.VIDEO }
    val audios = all.filter { it.trackType == MkvTrackType.AUDIO }
    val subs = all.filter { it.trackType == MkvTrackType.SUBTITLE }
    val duration = meta.durationMs
    val overallBitrate = if (duration > 0) (meta.fileSizeBytes * 8_000L / duration) else 0L
    val videoCodecs = videos.map { it.displayCodec }.distinct().joinToString(", ").ifBlank { "—" }
    val audioCodecs = audios.map { it.displayCodec }.distinct().joinToString(", ").ifBlank { "—" }
    val subFormats = subs.map { subtitleFormatLabel(it) }.distinct().joinToString(", ").ifBlank { "—" }
    val languages = all.map { it.language }.filter { it.isNotBlank() && it != "und" }.distinct().joinToString(", ").ifBlank { "—" }
    val resolution = videos.firstOrNull()?.displayResolution ?: "—"
    val fps = videos.firstOrNull()?.frameRate ?: 0f
    val hdr = videos.firstOrNull()?.hdrFormat?.ifBlank { "SDR" } ?: "SDR"

    val generalRows = listOfNotNull(
        "File Name" to meta.fileName,
        "Format" to (meta.fileName.substringAfterLast('.', "unknown").uppercase(Locale.ROOT).let {
            if (it == "MKV" || it == "WEBM") "Matroska" else it
        }),
        meta.formattedFormatVersion.takeIf { it.isNotBlank() }?.let { "Format Version" to it },
        "File Size" to formatBytes(meta.fileSizeBytes),
        "Duration" to formatMs(meta.durationMs),
        "Overall Bitrate" to formatBitrate(overallBitrate),
        meta.dateUtc.takeIf { it.isNotBlank() }?.let { "Encoded Date" to it },
        meta.muxingApp.takeIf { it.isNotBlank() }?.let { "Muxing App" to it },
        meta.writingApp.takeIf { it.isNotBlank() }?.let { "Writing App" to it }
    )

    val summaryRows = listOf(
        "Video Tracks" to videos.size.toString(),
        "Audio Tracks" to audios.size.toString(),
        "Subtitle Tracks" to subs.size.toString(),
        "Chapters" to data.chapters.size.toString(),
        "Attachments" to data.attachments.size.toString(),
        "Languages" to languages
    )

    val streamRows = listOf(
        "Resolution" to resolution,
        "Frame Rate" to if (fps > 0) "${"%.3f".format(Locale.US, fps)} FPS" else "—",
        "HDR / SDR" to hdr,
        "Video Codec(s)" to videoCodecs,
        "Audio Codec(s)" to audioCodecs,
        "Subtitle Format(s)" to subFormats
    )

    val q = searchQuery.trim().lowercase(Locale.ROOT)
    val filterRows = { list: List<Pair<String, String>> ->
        if (q.isBlank()) list else list.filter {
            it.first.lowercase(Locale.ROOT).contains(q) || it.second.lowercase(Locale.ROOT).contains(q)
        }
    }

    val filteredGeneral = filterRows(generalRows)
    val filteredSummary = filterRows(summaryRows)
    val filteredStream = filterRows(streamRows)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (filteredGeneral.isNotEmpty() || (q.isBlank() && bitmap != null)) {
            InfoCard(
                title = "General Information",
                icon = Icons.Outlined.Description,
                trailingContent = {
                    ActionRow(
                        onCopy = { onCopy(buildInformationText(data, MediaInfoSection.OVERVIEW, video)) },
                        onShare = null,
                        isDark = isDark
                    )
                },
                isDark = isDark, border = border, surface = surface
            ) {
                if (bitmap != null && q.isBlank()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (filteredGeneral.isNotEmpty()) {
                    InfoGrid(filteredGeneral, primary, secondary)
                }
            }
        }

        if (filteredSummary.isNotEmpty()) {
            InfoCard(
                title = "Track Summary",
                icon = Icons.Outlined.DashboardCustomize,
                badgeText = "${all.size} Tracks",
                badgeColor = AccentSkyBlue,
                isDark = isDark, border = border, surface = surface
            ) {
                InfoGrid(filteredSummary, primary, secondary)
            }
        }

        if (filteredStream.isNotEmpty()) {
            InfoCard(
                title = "Display & Codec Details",
                icon = Icons.Outlined.VideoFile,
                badgeText = resolution.takeIf { it != "—" },
                badgeColor = AccentPink,
                isDark = isDark, border = border, surface = surface
            ) {
                InfoGrid(filteredStream, primary, secondary)
            }
        }

        if (filteredGeneral.isEmpty() && filteredSummary.isEmpty() && filteredStream.isEmpty()) {
            EmptyInfo("No overview properties matching \"$searchQuery\".", primary, secondary, surface, border)
        }
    }
}

@Composable
private fun TrackCard(
    track: MkvTrackInfo,
    type: MkvTrackType,
    thumbnail: android.graphics.Bitmap? = null,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color,
    fileSizeBytes: Long = 0L,
    onExtract: () -> Unit
) {
    val isVideo = type == MkvTrackType.VIDEO
    val icon = if (isVideo) Icons.Outlined.VideoFile else Icons.Outlined.Audiotrack
    val badgeLabel = if (isVideo) track.displayCodec.ifBlank { "VIDEO" } else track.language.ifBlank { track.displayCodec }
    val badgeColor = if (isVideo) AccentSkyBlue else AccentPink

    InfoCard(
        title = "${type.displayName} Track #${track.trackNumber}",
        icon = icon,
        badgeText = badgeLabel,
        badgeColor = badgeColor,
        isDark = isDark, border = border, surface = surface
    ) {
        if (isVideo && thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Video preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(10.dp))
        }

        InfoGrid(
            listOfNotNull(
                "Track Name" to track.name.ifBlank { "Track #${track.trackNumber}" },
                "Language" to track.language.ifBlank { "und" },
                "Codec" to track.displayCodec,
                "Format / ID" to track.codecId.ifBlank { "—" },
                track.formatInfo.takeIf { it.isNotBlank() }?.let { "Format/Info" to it },
                if (isVideo) "Resolution" to track.displayResolution else null,
                if (isVideo) "Frame Rate" to if (track.frameRate > 0) "%.3f FPS".format(Locale.US, track.frameRate) else "—" else null,
                if (isVideo) track.frameRateMode.takeIf { it.isNotBlank() }?.let { "Frame Rate Mode" to it } else null,
                if (isVideo) "Bitrate" to formatBitrate(track.bitrateBps) else null,
                if (isVideo) track.formatProfile.takeIf { it.isNotBlank() }?.let { "Format Profile" to it } else null,
                if (isVideo) track.formatTier.takeIf { it.isNotBlank() }?.let { "Format Tier" to it } else null,
                if (isVideo) track.formatLevel.takeIf { it.isNotBlank() }?.let { "Format Level" to it } else null,
                if (isVideo) "Bit Depth" to if (track.bitDepth > 0) "${track.bitDepth}-bit" else "—" else null,
                if (isVideo) track.chromaSubsampling.takeIf { it.isNotBlank() }?.let { "Chroma Subsampling" to it } else null,
                if (isVideo) "Color Space" to track.colorSpace.ifBlank { "—" } else null,
                if (isVideo) "HDR Format" to track.hdrFormat.ifBlank { "SDR" } else null,
                if (isVideo) "Aspect Ratio" to track.aspectRatio.ifBlank { "—" } else null,
                if (isVideo) track.bitsPerPixelFrame.takeIf { it.isNotBlank() }?.let { "Bits/(Pixel*Frame)" to it } else null,
                if (isVideo) "Duration" to formatMs(if (track.durationMs > 0) track.durationMs else 0) else null,
                if (!isVideo) "Channels" to channelLabel(track.channels) else null,
                if (!isVideo) "Sample Rate" to if (track.sampleRate > 0) "${track.sampleRate} Hz" else "—" else null,
                if (!isVideo) "Bit Depth" to if (track.bitDepth > 0) "${track.bitDepth}-bit" else "—" else null,
                if (!isVideo) "Bitrate" to formatBitrate(track.bitrateBps) else null,
                if (!isVideo) "Delay vs Video" to if (track.delayRelativeToVideoMs != 0L) "${track.delayRelativeToVideoMs} ms" else "—" else null,
                if (!isVideo) "Duration" to formatMs(if (track.durationMs > 0) track.durationMs else 0) else null,
                track.compressionMode.takeIf { it.isNotBlank() }?.let { "Compression Mode" to it },
                "Stream Size" to track.formattedStreamSize(fileSizeBytes),
                "Default / Forced" to "${if (track.isDefault) "Default" else "No"} / ${if (track.isForced) "Forced" else "No"}",
                "Hearing Impaired" to if (track.isHearingImpaired) "Yes" else "No",
                "Commentary" to if (track.isCommentary) "Yes" else "No"
            ),
            primary, secondary
        )
        Spacer(Modifier.height(10.dp))
        ExtractButton(
            text = if (isVideo) "Extract Video Stream" else "Extract Audio Stream",
            onClick = onExtract
        )
    }
}

@Composable
private fun SubtitleTrackCard(
    track: MkvTrackInfo,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color,
    fileSizeBytes: Long = 0L,
    onCopy: () -> Unit,
    onExtract: () -> Unit
) {
    InfoCard(
        title = "Subtitle Track #${track.trackNumber}",
        icon = Icons.Outlined.Subtitles,
        badgeText = track.language.ifBlank { "SUB" },
        badgeColor = AccentSkyBlue,
        trailingContent = {
            SmallAction("Copy", Icons.Outlined.ContentCopy, AccentSkyBlue, onCopy)
        },
        isDark = isDark, border = border, surface = surface
    ) {
        InfoGrid(
            listOfNotNull(
                "Track Name" to track.name.ifBlank { "Track #${track.trackNumber}" },
                "Language" to track.language.ifBlank { "und" },
                "Format / Codec" to subtitleFormatLabel(track),
                "Format / ID" to track.codecId.ifBlank { "—" },
                "Character Encoding" to if (track.codecId.contains("UTF8", true) || track.codecId.contains("ASS", true) || track.codecId.contains("SSA", true)) "UTF-8" else "Container native",
                "Duration" to formatMs(track.durationMs),
                "Bit Rate" to formatBitrate(track.bitrateBps),
                track.compressionMode.takeIf { it.isNotBlank() }?.let { "Compression Mode" to it },
                if (track.countOfElements > 0) "Count of Elements" to track.countOfElements.toString() else null,
                "Stream Size" to track.formattedStreamSize(fileSizeBytes),
                "Default" to if (track.isDefault) "Yes" else "No",
                "Forced" to if (track.isForced) "Yes" else "No",
                "Hearing Impaired" to if (track.isHearingImpaired) "Yes" else "No"
            ),
            primary, secondary
        )
        Spacer(Modifier.height(10.dp))
        ExtractButton(
            text = "Extract Subtitle File",
            onClick = onExtract
        )
    }
}

@Composable
private fun ChapterInfoCard(
    chapters: List<MkvChapterInfo>,
    totalChapterCount: Int,
    searchQuery: String,
    format: String,
    onFormatChange: (String) -> Unit,
    onCopy: () -> Unit,
    onExtract: () -> Unit,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color
) {
    val countLabel = if (searchQuery.isBlank()) "$totalChapterCount Chapters" else "${chapters.size} / $totalChapterCount Chapters"
    InfoCard(
        title = "Chapters",
        icon = Icons.Outlined.Bookmarks,
        badgeText = countLabel,
        badgeColor = AccentPink,
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction("XML", Icons.Outlined.Bookmarks, if (format == "xml") AccentPink else secondary) { onFormatChange("xml") }
                SmallAction("OGM", Icons.Outlined.Bookmarks, if (format == "ogm") AccentPink else secondary) { onFormatChange("ogm") }
                SmallAction("Copy", Icons.Outlined.ContentCopy, AccentSkyBlue, onCopy)
            }
        },
        isDark = isDark, border = border, surface = surface
    ) {
        if (chapters.isEmpty()) {
            Text(
                if (searchQuery.isBlank()) "No chapters detected in container." else "No chapters matching \"$searchQuery\".",
                color = secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chapters.forEachIndexed { index, ch ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .border(1.dp, border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentPink.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d", index + 1),
                                    color = AccentPink,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = ch.title.ifBlank { "Chapter ${index + 1}" },
                                    color = primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ch.formattedStartTime}${if (ch.endTimeMs > 0) " → ${ch.formattedEndTime}" else ""} • ${ch.language.ifBlank { "eng" }}",
                                    color = secondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ExtractButton(
            text = "Extract Chapters (.${if (format == "xml") "xml" else "txt"})",
            onClick = onExtract
        )
    }
}

@Composable
private fun AttachmentCard(
    attachment: MkvAttachmentInfo,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    surface: Color,
    border: Color,
    onExtract: () -> Unit
) {
    InfoCard(
        title = attachment.fileName.ifBlank { "Attachment #${attachment.id}" },
        icon = Icons.Outlined.AttachFile,
        badgeText = if (attachment.isFont) "Font (.${attachment.fileName.substringAfterLast('.', "ttf")})" else "File",
        badgeColor = if (attachment.isFont) AccentSkyBlue else secondary,
        isDark = isDark, border = border, surface = surface
    ) {
        InfoGrid(
            listOf(
                "File Name" to attachment.fileName,
                "MIME Type" to attachment.mimeType,
                "Size" to attachment.formattedSize,
                "Description" to attachment.description.ifBlank { "—" },
                "Type" to if (attachment.isFont) "Embedded Font" else "Attachment"
            ), primary, secondary
        )
        Spacer(Modifier.height(10.dp))
        ExtractButton(
            text = "Extract ${if (attachment.isFont) "Font File" else "Attachment"}",
            onClick = onExtract
        )
    }
}

@Composable
private fun ExtractButton(
    text: String = "Extract",
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentGradient)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyledIcon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun ActionRow(onCopy: () -> Unit, onShare: (() -> Unit)?, isDark: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallAction("Copy", Icons.Outlined.ContentCopy, AccentSkyBlue, onCopy)
        if (onShare != null) SmallAction("Share", Icons.Outlined.Share, AccentPink, onShare)
    }
}

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, tint.copy(alpha = .35f), RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        StyledIcon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoBadge(
    text: String,
    color: Color = AccentSkyBlue,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badgeText: String? = null,
    badgeColor: Color = AccentSkyBlue,
    trailingContent: (@Composable () -> Unit)? = null,
    isDark: Boolean,
    border: Color,
    surface: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentSkyBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = if (isDark) DarkTextPrimary else LightTextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeText != null) {
                Spacer(Modifier.width(8.dp))
                InfoBadge(text = badgeText, color = badgeColor)
            }
            if (trailingContent != null) {
                Spacer(Modifier.weight(1f))
                trailingContent()
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>, primary: Color, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(k, color = secondary, fontSize = 12.sp, modifier = Modifier.width(140.dp))
                Text(v.ifBlank { "—" }, color = primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyInfo(message: String, primary: Color, secondary: Color, surface: Color, border: Color) {
    InfoCard("No Data", isDark = true, border = border, surface = surface) {
        Text(message, color = secondary, fontSize = 13.sp)
    }
}

private fun subtitleFormatLabel(track: MkvTrackInfo): String {
    val c = track.codecId.uppercase(Locale.ROOT)
    return when {
        c.contains("ASS") || c.contains("SSA") -> "Advanced SubStation Alpha (.ass)"
        c.contains("UTF8") || c.contains("SRT") -> "SubRip Text (.srt)"
        c.contains("WEBVTT") || c.contains("VTT") -> "WebVTT (.vtt)"
        c.contains("PGS") || c.contains("HDMV") -> "HDMV PGS Bitmap (.sup)"
        c.contains("VOBSUB") -> "VobSub Bitmap (.sub)"
        else -> track.displayCodec
    }
}

private fun videoExtension(track: MkvTrackInfo): String {
    val c = track.codecId.uppercase(Locale.ROOT)
    val name = track.codecName.uppercase(Locale.ROOT)
    return when {
        c.contains("AVC") || c.contains("H264") || name.contains("AVC") || name.contains("H.264") -> "h264"
        c.contains("HEVC") || c.contains("H265") || name.contains("HEVC") || name.contains("H.265") -> "hevc"
        c.contains("AV1") -> "av1"
        c.contains("VP9") -> "vp9"
        c.contains("VP8") -> "vp8"
        else -> "mkv"
    }
}

internal fun audioExtension(track: MkvTrackInfo): String {
    val c = track.codecId.uppercase(Locale.ROOT)
    val name = track.codecName.uppercase(Locale.ROOT)
    val display = track.displayCodec.uppercase(Locale.ROOT)
    val combined = "$c $name $display"
    return when {
        combined.contains("OPUS") -> "opus"
        combined.contains("AAC") || combined.contains("MP4A") || combined.contains("M4A") -> "m4a"
        combined.contains("EAC3") || combined.contains("EC-3") || combined.contains("DOLBY DIGITAL PLUS") -> "ac3"
        combined.contains("AC3") || combined.contains("AC-3") || combined.contains("DOLBY DIGITAL") -> "ac3"
        combined.contains("TRUEHD") -> "thd"
        combined.contains("MPEG/L3") || combined.contains("MP3") || combined.contains("MPEGLAYER3") -> "mp3"
        combined.contains("FLAC") -> "flac"
        combined.contains("VORBIS") || combined.contains("OGG") -> "ogg"
        combined.contains("DTS-HD") || combined.contains("DTSHD") -> "dtshd"
        combined.contains("DTS") -> "dts"
        combined.contains("ALAC") -> "m4a"
        combined.contains("PCM") || combined.contains("WAV") || combined.contains("RIFF") -> "wav"
        combined.contains("WMA") -> "wma"
        else -> "m4a"
    }
}

internal fun subtitleExtension(track: MkvTrackInfo): String {
    val c = track.codecId.uppercase(Locale.ROOT)
    val name = track.codecName.uppercase(Locale.ROOT)
    val display = track.displayCodec.uppercase(Locale.ROOT)
    val combined = "$c $name $display"
    return when {
        combined.contains("ASS") || combined.contains("SSA") || combined.contains("SUBSTATION") -> "ass"
        combined.contains("WEBVTT") || combined.contains("VTT") -> "vtt"
        combined.contains("PGS") || combined.contains("HDMV") -> "sup"
        combined.contains("VOBSUB") || combined.contains("DVD_SUB") -> "sub"
        combined.contains("TTML") || combined.contains("DFXP") -> "ttml"
        combined.contains("UTF8") || combined.contains("SRT") || combined.contains("SUBRIP") -> "srt"
        else -> "srt"
    }
}

private fun channelLabel(channels: Int): String {
    return when (channels) {
        1 -> "1.0 (Mono)"
        2 -> "2.0 (Stereo)"
        6 -> "5.1 Surround (6 ch)"
        8 -> "7.1 Surround (8 ch)"
        else -> if (channels > 0) "$channels channels" else "—"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val s = totalSecs % 60
    val m = (totalSecs / 60) % 60
    val h = totalSecs / 3600
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatBitrate(bps: Long): String {
    if (bps <= 0) return "—"
    val kbps = bps / 1000.0
    val mbps = kbps / 1000.0
    return if (mbps >= 1.0) String.format(Locale.US, "%.2f Mbps", mbps)
    else String.format(Locale.US, "%.0f kbps", kbps)
}

private fun sanitizeFileComponent(name: String): String {
    val cleaned = name.replace(Regex("""[/\\:*?"<>|\u0000]"""), "_").trim()
    return cleaned.ifBlank { "media" }
}

private fun sanitizeName(name: String): String = sanitizeFileComponent(name)

private fun mapLanguageToSimpleName(langRaw: String): String? {
    val clean = langRaw.trim().lowercase(Locale.ROOT)
    if (clean.isBlank() || clean == "und" || clean == "unk" || clean == "unknown" || clean == "mis" || clean == "qaa") {
        return null
    }
    return when (clean) {
        "hi", "hin", "hindi" -> "hindi"
        "en", "eng", "english" -> "english"
        "ta", "tam", "tamil" -> "tamil"
        "te", "tel", "telugu" -> "telugu"
        "ml", "mal", "malayalam" -> "malayalam"
        "kn", "kan", "kannada" -> "kannada"
        "mr", "mar", "marathi" -> "marathi"
        "bn", "ben", "bengali", "bangla" -> "bengali"
        "gu", "guj", "gujarati" -> "gujarati"
        "pa", "pan", "punjabi" -> "punjabi"
        "ur", "urd", "urdu" -> "urdu"
        "bho", "bhojpuri" -> "bhojpuri"
        "or", "ori", "odia", "oriya" -> "odia"
        "as", "asm", "assamese" -> "assamese"
        "ne", "nep", "nepali" -> "nepali"
        "sa", "san", "sanskrit" -> "sanskrit"
        "si", "sin", "sinhala" -> "sinhala"
        "es", "spa", "spanish" -> "spanish"
        "fr", "fra", "fre", "french" -> "french"
        "de", "deu", "ger", "german" -> "german"
        "it", "ita", "italian" -> "italian"
        "pt", "por", "portuguese" -> "portuguese"
        "ru", "rus", "russian" -> "russian"
        "ja", "jpn", "japanese" -> "japanese"
        "ko", "kor", "korean" -> "korean"
        "zh", "zho", "chi", "chinese", "mandarin", "cantonese" -> "chinese"
        "ar", "ara", "arabic" -> "arabic"
        "tr", "tur", "turkish" -> "turkish"
        "th", "tha", "thai" -> "thai"
        "vi", "vie", "vietnamese" -> "vietnamese"
        "id", "ind", "indonesian" -> "indonesian"
        "ms", "msa", "may", "malay" -> "malay"
        "fa", "fas", "per", "persian", "farsi" -> "persian"
        "pl", "pol", "polish" -> "polish"
        "nl", "nld", "dut", "dutch" -> "dutch"
        "sv", "swe", "swedish" -> "swedish"
        "no", "nor", "norwegian" -> "norwegian"
        "da", "dan", "danish" -> "danish"
        "fi", "fin", "finnish" -> "finnish"
        "el", "ell", "gre", "greek" -> "greek"
        "he", "heb", "hebrew" -> "hebrew"
        "hu", "hun", "hungarian" -> "hungarian"
        "cs", "ces", "cze", "czech" -> "czech"
        "ro", "ron", "rum", "romanian" -> "romanian"
        "uk", "ukr", "ukrainian" -> "ukrainian"
        "tl", "tgl", "fil", "tagalog", "filipino" -> "filipino"
        else -> {
            try {
                val loc = Locale.forLanguageTag(clean)
                val display = loc.getDisplayLanguage(Locale.ENGLISH)
                if (!display.isNullOrBlank() && !display.equals(clean, ignoreCase = true)) {
                    display.lowercase(Locale.ROOT)
                } else {
                    val l2 = Locale(clean).getDisplayLanguage(Locale.ENGLISH)
                    if (!l2.isNullOrBlank() && !l2.equals(clean, ignoreCase = true)) {
                        l2.lowercase(Locale.ROOT)
                    } else if (clean.all { it.isLetter() }) {
                        clean
                    } else null
                }
            } catch (_: Throwable) {
                if (clean.all { it.isLetter() }) clean else null
            }
        }
    }
}

private fun detectLanguageFromTitle(title: String): String? {
    val lower = title.lowercase(Locale.ROOT)
    val keywords = listOf(
        "hindi", "english", "tamil", "telugu", "malayalam", "kannada",
        "marathi", "bengali", "gujarati", "punjabi", "urdu", "bhojpuri",
        "spanish", "french", "german", "italian", "portuguese", "russian",
        "japanese", "korean", "chinese", "arabic", "turkish", "thai",
        "vietnamese", "indonesian"
    )
    for (kw in keywords) {
        if (Regex("""\b$kw\b""").containsMatchIn(lower)) {
            return kw
        }
    }
    return null
}

internal fun resolveTrackLanguageName(track: MkvTrackInfo, defaultFallback: String): String {
    mapLanguageToSimpleName(track.language)?.let { return it }
    if (track.name.isNotBlank()) {
        mapLanguageToSimpleName(track.name)?.let { return it }
        detectLanguageFromTitle(track.name)?.let { return it }
    }
    return defaultFallback
}

internal fun buildTrackOutputFileName(
    videoDisplayName: String,
    track: MkvTrackInfo,
    allTracksOfType: List<MkvTrackInfo>,
    extension: String,
    defaultFallbackLang: String
): String {
    val baseVideoName = sanitizeFileComponent(videoDisplayName.substringBeforeLast('.', videoDisplayName))
    val lang = resolveTrackLanguageName(track, defaultFallbackLang)

    val trackIndex = allTracksOfType.indexOfFirst { it.id == track.id && it.trackNumber == track.trackNumber }
        .takeIf { it >= 0 } ?: allTracksOfType.indexOf(track)

    val precedingSameLangCount = if (trackIndex >= 0) {
        allTracksOfType.take(trackIndex).count {
            resolveTrackLanguageName(it, defaultFallbackLang) == lang
        }
    } else 0

    val langWithNumber = if (precedingSameLangCount == 0) lang else "$lang${precedingSameLangCount + 1}"
    return "$baseVideoName-$langWithNumber.$extension"
}

fun getUniqueDestinationFile(targetDir: File, proposedFileName: String): File {
    val initialFile = File(targetDir, proposedFileName)
    if (!initialFile.exists()) {
        return initialFile
    }

    val ext = proposedFileName.substringAfterLast('.', "")
    val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
    val nameWithoutExt = if (ext.isNotEmpty()) proposedFileName.substringBeforeLast('.') else proposedFileName

    val match = Regex("""^(.*)-([a-zA-Z]+)(\d*)$""").matchEntire(nameWithoutExt)
    if (match != null) {
        val prefix = match.groupValues[1]
        val lang = match.groupValues[2]
        val numStr = match.groupValues[3]
        val startNum = numStr.toIntOrNull() ?: 1
        var counter = if (startNum <= 1) 2 else startNum + 1
        while (true) {
            val candidateFile = File(targetDir, "$prefix-$lang$counter$dotExt")
            if (!candidateFile.exists()) {
                return candidateFile
            }
            counter++
        }
    } else {
        var counter = 2
        while (true) {
            val candidateFile = File(targetDir, "${nameWithoutExt}_$counter$dotExt")
            if (!candidateFile.exists()) {
                return candidateFile
            }
            counter++
        }
    }
}

private fun buildInformationText(data: MediaInfoData?, active: MediaInfoSection, video: VideoItem): String {
    if (data == null) return "Media: ${video.displayName}"
    val meta = data.metadata
    val builder = StringBuilder()
    when (active) {
        MediaInfoSection.OVERVIEW -> {
            builder.appendLine("=== MEDIA INFORMATION (OVERVIEW) ===")
            builder.appendLine("File Name: ${meta.fileName}")
            builder.appendLine("File Size: ${formatBytes(meta.fileSizeBytes)}")
            builder.appendLine("Duration: ${formatMs(meta.durationMs)}")
            builder.appendLine("Video Tracks: ${data.tracks.count { it.trackType == MkvTrackType.VIDEO }}")
            builder.appendLine("Audio Tracks: ${data.tracks.count { it.trackType == MkvTrackType.AUDIO }}")
            builder.appendLine("Subtitle Tracks: ${data.tracks.count { it.trackType == MkvTrackType.SUBTITLE }}")
            builder.appendLine("Chapters: ${data.chapters.size}")
            builder.appendLine("Attachments: ${data.attachments.size}")
        }
        MediaInfoSection.VIDEO -> {
            builder.appendLine("=== VIDEO TRACKS ===")
            data.tracks.filter { it.trackType == MkvTrackType.VIDEO }.forEach {
                builder.appendLine("Track #${it.trackNumber}: ${it.name} | Codec: ${it.displayCodec} | Resolution: ${it.displayResolution} | FPS: ${it.frameRate} | Lang: ${it.language}")
            }
        }
        MediaInfoSection.AUDIO -> {
            builder.appendLine("=== AUDIO TRACKS ===")
            data.tracks.filter { it.trackType == MkvTrackType.AUDIO }.forEach {
                builder.appendLine("Track #${it.trackNumber}: ${it.name} | Codec: ${it.displayCodec} | Channels: ${channelLabel(it.channels)} | Rate: ${it.sampleRate} Hz | Lang: ${it.language}")
            }
        }
        MediaInfoSection.SUBTITLE -> {
            builder.appendLine("=== SUBTITLE TRACKS ===")
            data.tracks.filter { it.trackType == MkvTrackType.SUBTITLE }.forEach {
                builder.appendLine("Track #${it.trackNumber}: ${it.name} | Format: ${subtitleFormatLabel(it)} | Lang: ${it.language} | Default: ${it.isDefault}")
            }
        }
        MediaInfoSection.CHAPTERS -> {
            builder.appendLine(buildChapterText(data.chapters, "ogm"))
        }
        MediaInfoSection.ATTACHMENTS -> {
            builder.appendLine("=== ATTACHMENTS ===")
            data.attachments.forEach {
                builder.appendLine("${it.fileName} (${it.formattedSize}) - ${it.mimeType}")
            }
        }
    }
    return builder.toString()
}

private fun buildChapterText(chapters: List<MkvChapterInfo>, format: String): String {
    val sb = StringBuilder()
    if (format == "xml") {
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<Chapters>")
        sb.appendLine("  <EditionEntry>")
        chapters.forEachIndexed { index, ch ->
            sb.appendLine("    <ChapterAtom>")
            sb.appendLine("      <ChapterTimeStart>${ch.formattedStartTime}.000</ChapterTimeStart>")
            if (ch.endTimeMs > 0) sb.appendLine("      <ChapterTimeEnd>${ch.formattedEndTime}.000</ChapterTimeEnd>")
            sb.appendLine("      <ChapterDisplay>")
            sb.appendLine("        <ChapterString>${ch.title}</ChapterString>")
            sb.appendLine("        <ChapterLanguage>${ch.language.ifBlank { "eng" }}</ChapterLanguage>")
            sb.appendLine("      </ChapterDisplay>")
            sb.appendLine("    </ChapterAtom>")
        }
        sb.appendLine("  </EditionEntry>")
        sb.appendLine("</Chapters>")
    } else {
        chapters.forEachIndexed { index, ch ->
            val num = String.format(Locale.US, "%02d", index + 1)
            sb.appendLine("CHAPTER${num}=${ch.formattedStartTime}.000")
            sb.appendLine("CHAPTER${num}NAME=${ch.title}")
        }
    }
    return sb.toString()
}
