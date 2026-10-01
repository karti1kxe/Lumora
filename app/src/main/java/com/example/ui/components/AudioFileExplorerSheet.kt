package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Complete set of supported audio extensions for external audio track selection:
 * .mp3, .m4a, .aac, .flac, .wav, .opus, .ogg, .mka, .wma, .alac, .amr, .ac3, .eac3, .dts, .dtshd, .aiff, .mid, .midi
 */
val STRICT_AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "flac", "wav", "opus", "ogg", "mka",
    "wma", "alac", "amr", "ac3", "eac3", "dts", "dtshd", "aiff", "mid", "midi"
)

private val RESTRICTED_AUDIO_FOLDERS = setOf("data", "obb", ".android_secure")

private fun isRestrictedAudioPath(file: File): Boolean {
    val path = file.absolutePath
    if (path.contains("/Android/data/") || path.endsWith("/Android/data") ||
        path.contains("/Android/obb/") || path.endsWith("/Android/obb")) return true
    if (file.isDirectory && RESTRICTED_AUDIO_FOLDERS.contains(file.name.lowercase(Locale.ROOT)) &&
        file.parentFile?.name?.equals("Android", ignoreCase = true) == true) {
        return true
    }
    return false
}

data class AudioFileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val extension: String,
    val sizeText: String,
    val dateText: String,
    val audioCount: Int = 0
)

/**
 * Dedicated Audio File Explorer Modal Sheet for adding external audio tracks.
 * Features:
 * - Interactive breadcrumb path bar (Internal Storage > Folder > Subfolder) where tapping any ancestor jumps directly to it.
 * - No redundant back button in the file list.
 * - Formatted badges for all standard audio codecs (FLAC, MP3, AAC, M4A, OPUS, WAV, etc.).
 * - Search bar with instant filter.
 * - Seamless glass modal sheet UI matching Lumora design system.
 */
@Composable
fun AudioFileTreeDialog(
    initialVideoPath: String? = null,
    isLandscape: Boolean = false,
    glassBlurTransparency: Float = 85f,
    onAudioSelected: (File) -> Unit,
    onOpenSystemPicker: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Determine initial directory: prefer parent of current video if accessible, else Music/Downloads or Storage root
    val initialDir = remember(initialVideoPath) {
        if (!initialVideoPath.isNullOrBlank()) {
            val vFile = File(initialVideoPath)
            val parent = vFile.parentFile
            if (parent != null && parent.exists() && parent.canRead() && !isRestrictedAudioPath(parent)) {
                parent
            } else {
                getDefaultAudioStorageDir(context)
            }
        } else {
            getDefaultAudioStorageDir(context)
        }
    }

    var currentDir by remember { mutableStateOf(initialDir) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            kotlinx.coroutines.delay(120)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Breadcrumb path segments (from root down to current directory)
    val pathSegments = remember(currentDir) {
        val segments = mutableListOf<Pair<String, File>>()
        var curr: File? = currentDir
        while (curr != null && curr.absolutePath != "/storage/emulated" && curr.absolutePath != "/storage" && curr.parentFile != null) {
            val label = if (curr.absolutePath == Environment.getExternalStorageDirectory()?.absolutePath) {
                "Internal Storage"
            } else {
                curr.name.ifBlank { curr.absolutePath }
            }
            segments.add(0, label to curr)
            curr = curr.parentFile
        }
        if (segments.isEmpty() && curr != null) {
            segments.add(curr.name.ifBlank { "Storage" } to curr)
        }
        segments
    }

    // Color Palette matching Lumora Player Glass aesthetic
    val isDark = com.example.ui.theme.isAppInDarkTheme()
    val accentBlue = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFFCBD5E1)
    val badgeBg = if (isDark) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFFE0F2FE)
    val inputBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.60f) else Color(0xFFF1F5F9)

    // Load file list filtered to STRICT_AUDIO_EXTENSIONS and navigatable directories
    var fileItems by remember { mutableStateOf<List<AudioFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentDir) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val items = try {
                val allFiles = currentDir.listFiles() ?: emptyArray()
                val filtered = allFiles.filter { file ->
                    if (isRestrictedAudioPath(file)) return@filter false
                    if (file.name.startsWith(".")) return@filter false

                    if (file.isDirectory) {
                        true
                    } else {
                        val ext = file.extension.lowercase(Locale.ROOT)
                        ext in STRICT_AUDIO_EXTENSIONS
                    }
                }.sortedWith { f1, f2 ->
                    if (f1.isDirectory && !f2.isDirectory) -1
                    else if (!f1.isDirectory && f2.isDirectory) 1
                    else f1.name.compareTo(f2.name, ignoreCase = true)
                }

                val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                filtered.map { file ->
                    val ext = file.extension.lowercase(Locale.ROOT)
                    val audioCount = if (file.isDirectory) {
                        file.listFiles()?.count {
                            !it.isDirectory && it.extension.lowercase(Locale.ROOT) in STRICT_AUDIO_EXTENSIONS
                        } ?: 0
                    } else 0

                    val sizeStr = if (file.isDirectory) {
                        if (audioCount > 0) "$audioCount audio files" else "Folder"
                    } else {
                        formatAudioFileSize(file.length())
                    }

                    val dateStr = try {
                        df.format(Date(file.lastModified()))
                    } catch (_: Exception) { "" }

                    AudioFileItem(
                        file = file,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        extension = ext,
                        sizeText = sizeStr,
                        dateText = dateStr,
                        audioCount = audioCount
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
            fileItems = items
            isLoading = false
        }
    }

    val displayedItems = remember(fileItems, searchQuery) {
        if (searchQuery.isBlank()) fileItems
        else fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    PlayerGlassModalSheet(
        panelKey = "audio_file_tree",
        isLandscape = isLandscape,
        heightFraction = 0.72f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismiss,
        headerStartContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Select Audio Track",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        headerEndContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Search toggle button styled identically in height, border and corner radius to System Picker
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSearchActive) accentBlue.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSearchActive) accentBlue.copy(alpha = 0.8f) else accentBlue.copy(alpha = 0.45f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = if (isSearchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = if (isSearchActive) "Close search" else "Search audio files",
                        tint = accentBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSystemPicker()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentBlue.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("System Picker", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        // 1. Interactive Breadcrumb Path Bar (Subtly rounded corners, not overly round)
        val breadcrumbScrollState = rememberScrollState()
        LaunchedEffect(pathSegments.size, currentDir.absolutePath) {
            breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(inputBg)
                .border(1.dp, borderCol.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .horizontalScroll(breadcrumbScrollState)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vibrant Amber/Orange Folder icon matching screenshot
            StyledIcon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            pathSegments.forEachIndexed { index, (label, folder) ->
                val isLast = index == pathSegments.lastIndex
                val isCurrent = folder.absolutePath == currentDir.absolutePath

                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = if (isLast || isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast || isCurrent) accentBlue else textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isCurrent) {
                            currentDir = folder
                            searchQuery = ""
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                if (!isLast) {
                    StyledIcon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 2. Expandable Sleek Search Bar (HomeScreen-styled smooth pill rounded search bar)
        AnimatedVisibility(
            visible = isSearchActive,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(150, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(inputBg)
                    .border(1.dp, borderCol.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search audio files in ${currentDir.name.ifBlank { "folder" }}...",
                                fontSize = 12.5.sp,
                                color = textSecondary.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(accentBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(textPrimary.copy(alpha = 0.08f))
                                .clickable { searchQuery = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. File & Folder List Area
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentBlue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                if (displayedItems.isEmpty()) {
                    item(key = "empty_audio_notice") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.MusicOff,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No audio files found in this folder",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Supported: .mp3, .m4a, .aac, .flac, .wav, .opus, .ogg, .mka, etc.",
                                    fontSize = 10.5.sp,
                                    color = textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(displayedItems, key = { it.file.absolutePath }) { item ->
                        AudioSheetItemRow(
                            item = item,
                            accentBlue = accentBlue,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            borderCol = borderCol,
                            onClick = {
                                if (item.isDirectory) {
                                    currentDir = item.file
                                    searchQuery = ""
                                } else {
                                    onAudioSelected(item.file)
                                    onDismiss()
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
private fun AudioSheetItemRow(
    item: AudioFileItem,
    accentBlue: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderCol: Color,
    onClick: () -> Unit
) {
    val badgeColor = when (item.extension) {
        "flac", "alac" -> Color(0xFF06B6D4) // Cyan for Lossless
        "mp3" -> Color(0xFF0284C7)          // Blue for MP3
        "aac", "m4a" -> Color(0xFF8B5CF6)   // Purple for AAC/M4A
        "opus", "ogg" -> Color(0xFF10B981)  // Emerald for Opus/Ogg
        "wav", "aiff" -> Color(0xFF6366F1)  // Indigo for WAV
        "ac3", "eac3", "dts" -> Color(0xFFF59E0B) // Amber for Surround Audio
        else -> accentBlue
    }

    val isDark = com.example.ui.theme.isAppInDarkTheme()
    val badgeBg = if (isDark) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFFE0F2FE)
    val itemBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.50f) else Color(0xFFF8FAFC)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(itemBg)
            .border(1.dp, borderCol.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Icon
            if (item.isDirectory) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Folder",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(17.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(badgeColor.copy(alpha = if (isDark) 0.25f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = "Audio",
                        tint = badgeColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // File Name & Size / Date Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Medium,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.sizeText,
                        fontSize = 10.5.sp,
                        color = textSecondary
                    )
                    if (item.dateText.isNotBlank()) {
                        Text(
                            text = " • ${item.dateText}",
                            fontSize = 10.5.sp,
                            color = textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing indicator or extension tag badge
            if (item.isDirectory) {
                if (item.audioCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${item.audioCount} tracks",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                    }
                } else {
                    StyledIcon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(0.5.dp, badgeColor.copy(alpha = 0.40f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.extension.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}

private fun getDefaultAudioStorageDir(context: Context): File {
    val extStorage = Environment.getExternalStorageDirectory()
    return if (extStorage != null && extStorage.exists() && extStorage.canRead()) {
        extStorage
    } else {
        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (music.exists() && music.canRead()) music
        else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists() && downloads.canRead()) downloads
            else context.getExternalFilesDir(null) ?: context.filesDir
        }
    }
}

private fun formatAudioFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
