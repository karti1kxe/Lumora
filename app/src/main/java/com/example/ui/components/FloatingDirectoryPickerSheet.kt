package com.example.ui.components

import android.content.Context
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.state.SettingsPreferencesManager
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Date
import java.util.Locale

enum class DirectoryPickerAction {
    MOVE,
    COPY,
    EXTRACT,
    EXPORT_SETTINGS,
    IMPORT_SETTINGS
}

internal val RESTRICTED_DIR_NAMES = setOf("data", "obb", ".android_secure")

internal fun isRestrictedDirectory(file: File): Boolean {
    val path = file.absolutePath
    if (path.contains("/Android/data/") || path.endsWith("/Android/data") ||
        path.contains("/Android/obb/") || path.endsWith("/Android/obb")) return true
    if (file.isDirectory && RESTRICTED_DIR_NAMES.contains(file.name.lowercase(Locale.ROOT)) &&
        file.parentFile?.name?.equals("Android", ignoreCase = true) == true) {
        return true
    }
    return false
}

data class DirEntry(
    val file: File,
    val name: String,
    val subFolderCount: Int,
    val dateText: String,
    val isDirectory: Boolean = true,
    val sizeText: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingDirectoryPickerSheet(
    action: DirectoryPickerAction,
    itemCount: Int,
    itemDescription: String,
    initialPath: String? = null,
    isDark: Boolean = true,
    glassBlurTransparency: Float = 85f,
    customTitle: String? = null,
    customActionLabel: String? = null,
    customActionIcon: ImageVector? = null,
    allowedFileExtensions: Set<String>? = null,
    onFileSelected: ((File) -> Unit)? = null,
    onConfirmTargetDir: (File) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val defaultRoot = remember(initialPath, action) {
        val pathCandidate = if (!initialPath.isNullOrBlank()) {
            initialPath
        } else if (action == DirectoryPickerAction.EXTRACT) {
            SettingsPreferencesManager.getLastExtractDirectory(context)
        } else null

        if (!pathCandidate.isNullOrBlank()) {
            val f = File(pathCandidate)
            val parent = if (f.isDirectory) f else f.parentFile
            if (parent != null && parent.exists() && parent.canRead() && !isRestrictedDirectory(parent)) {
                parent
            } else {
                Environment.getExternalStorageDirectory() ?: context.filesDir
            }
        } else {
            Environment.getExternalStorageDirectory() ?: context.filesDir
        }
    }

    var currentDir by remember { mutableStateOf(defaultRoot) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    var dirItems by remember { mutableStateOf<List<DirEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showCreateDirDialog by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }

    val alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
    val sheetBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0F172A)
        else Color(0xFF0F172A).copy(alpha = (0.12f + 0.85f * alphaRatio).coerceIn(0.12f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.12f + 0.85f * alphaRatio).coerceIn(0.12f, 1.0f))
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    LaunchedEffect(currentDir, allowedFileExtensions) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val list = mutableListOf<DirEntry>()
            val files = currentDir.listFiles()
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            files?.filter { !it.isHidden && !isRestrictedDirectory(it) }?.forEach { f ->
                if (f.isDirectory) {
                    val subCount = try {
                        f.listFiles { child ->
                            child.isDirectory || (allowedFileExtensions != null && child.extension.lowercase(Locale.ROOT) in allowedFileExtensions)
                        }?.size ?: 0
                    } catch (_: Exception) { 0 }
                    val dText = dateFormat.format(Date(f.lastModified()))
                    list.add(DirEntry(f, f.name, subCount, dText, isDirectory = true))
                } else if (allowedFileExtensions != null && f.extension.lowercase(Locale.ROOT) in allowedFileExtensions) {
                    val dText = dateFormat.format(Date(f.lastModified()))
                    val sizeStr = com.example.util.FolderDateUtils.formatFileSize(f.length())
                    list.add(DirEntry(f, f.name, 0, dText, isDirectory = false, sizeText = sizeStr))
                }
            }
            list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            dirItems = list
            isLoading = false
        }
    }

    val filteredItems = remember(dirItems, searchQuery) {
        if (searchQuery.isBlank()) dirItems
        else dirItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val pathSegments = remember(currentDir) {
        val segments = mutableListOf<Pair<String, File>>()
        var curr: File? = currentDir
        while (curr != null && curr.absolutePath != "/storage/emulated" && curr.absolutePath != "/storage") {
            val label = if (curr.absolutePath == Environment.getExternalStorageDirectory()?.absolutePath) {
                "Internal Storage"
            } else {
                curr.name.ifBlank { curr.absolutePath }
            }
            segments.add(0, label to curr)
            curr = curr.parentFile
        }
        segments
    }

    val parentFile = currentDir.parentFile
    val canGoUp = parentFile != null &&
            parentFile.canRead() &&
            !isRestrictedDirectory(parentFile) &&
            currentDir.absolutePath != Environment.getExternalStorageDirectory()?.absolutePath &&
            currentDir.absolutePath != "/storage/emulated/0"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            configureEdgeToEdgeDialogWindow(
                view = view,
                isLightStatusBars = !isDark,
                isLightNavBars = !isDark
            )
            onDispose {}
        }

        BackHandler {
            if (canGoUp && parentFile != null) {
                currentDir = parentFile
            } else {
                onDismiss()
            }
        }

        // Clean transparent backdrop without any black tint overlay (matching Sort & View Dialog)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            // Floating Island Sheet Card (floats between top selection bar and bottom bar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(top = 68.dp)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 76.dp)
                    .then(
                        if (alphaRatio > 0.35f) {
                            Modifier.shadow(
                                elevation = 20.dp * alphaRatio,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = Color.Black.copy(alpha = 0.20f * alphaRatio),
                                spotColor = Color.Black.copy(alpha = 0.30f * alphaRatio)
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(sheetBg)
                    .border(1.2.dp, borderColor, RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(16.dp)
            ) {
                // Header Bar inside Sheet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back / Close Button
                    IconButton(
                        onClick = {
                            if (canGoUp && parentFile != null) {
                                currentDir = parentFile
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        StyledIcon(
                            imageVector = if (canGoUp) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close,
                            contentDescription = "Back",
                            tint = primaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customTitle ?: when (action) {
                                DirectoryPickerAction.MOVE -> "Move to Folder"
                                DirectoryPickerAction.COPY -> "Copy to Folder"
                                DirectoryPickerAction.EXTRACT -> "Extract to Folder"
                                DirectoryPickerAction.EXPORT_SETTINGS -> "Export Settings"
                                DirectoryPickerAction.IMPORT_SETTINGS -> "Import Settings"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Text(
                            text = "$itemCount selected • $itemDescription",
                            fontSize = 11.5.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Search Button
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) AccentSkyBlue else primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // New Folder Button
                    IconButton(
                        onClick = {
                            newDirName = ""
                            showCreateDirDialog = true
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Search Input Field
                AnimatedVisibility(visible = isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0xFF1E293B).copy(alpha = (0.15f + 0.50f * alphaRatio)) else Color(0xFFFFFFFF).copy(alpha = (0.12f + 0.55f * alphaRatio)))
                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = AccentSkyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = primaryText, fontSize = 13.sp),
                                cursorBrush = SolidColor(AccentSkyBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    StyledIcon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Breadcrumbs Path Row
                val breadcrumbScrollState = rememberScrollState()
                LaunchedEffect(pathSegments.size) {
                    breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = (0.10f + 0.40f * alphaRatio)) else Color(0xFFFFFFFF).copy(alpha = (0.10f + 0.40f * alphaRatio)))
                        .horizontalScroll(breadcrumbScrollState)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = AccentSkyBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    pathSegments.forEachIndexed { idx, (label, dir) ->
                        val isLast = idx == pathSegments.lastIndex
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) AccentSkyBlue else secondaryText,
                            modifier = Modifier
                                .clickable { currentDir = dir }
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        )
                        if (!isLast) {
                            StyledIcon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = secondaryText.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(11.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Directory Listing (Clean Folder Cards matching standard system look)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = AccentSkyBlue,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = secondaryText.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No subfolders match search" else "No subfolders here",
                                    fontSize = 13.sp,
                                    color = secondaryText
                                )
                                Text(
                                    text = "You can paste directly here or create a new folder",
                                    fontSize = 11.5.sp,
                                    color = secondaryText.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredItems, key = { it.file.absolutePath }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = (0.12f + 0.45f * alphaRatio)) else Color(0xFFFFFFFF).copy(alpha = (0.10f + 0.45f * alphaRatio)))
                                        .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (item.isDirectory) {
                                                currentDir = item.file
                                            } else {
                                                onFileSelected?.invoke(item.file)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.isDirectory) {
                                        // Standard clean folder icon without video play glyph
                                        ModernFolderIcon(
                                            isDark = isDark,
                                            size = 42.dp,
                                            showBadge = false,
                                            showVideoGlyph = false
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isDark) AccentSkyBlue.copy(alpha = 0.18f) else AccentSkyBlue.copy(alpha = 0.12f))
                                                .border(1.dp, AccentSkyBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            StyledIcon(
                                                imageVector = Icons.Outlined.Description,
                                                contentDescription = null,
                                                tint = AccentSkyBlue,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (item.isDirectory) {
                                                "${item.subFolderCount} items • ${item.dateText}"
                                            } else {
                                                "${item.sizeText} • ${item.dateText}"
                                            },
                                            fontSize = 11.sp,
                                            color = secondaryText
                                        )
                                    }
                                    if (!item.isDirectory) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AccentSkyBlue.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = item.file.extension.uppercase(Locale.ROOT),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentSkyBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (action == DirectoryPickerAction.IMPORT_SETTINGS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0x1A38BDF8) else Color(0x150284C7))
                            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap any XML settings file to import",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Cancel", color = secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Bottom Action Bar: Current Folder Info & Action Button (Move / Copy / Export Here)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF1E293B).copy(alpha = (0.15f + 0.50f * alphaRatio)) else Color(0xFFFFFFFF).copy(alpha = (0.12f + 0.55f * alphaRatio)))
                            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Target:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryText
                            )
                            Text(
                                text = currentDir.name.ifBlank { "Storage" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentSkyBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                if (action == DirectoryPickerAction.EXTRACT) {
                                    SettingsPreferencesManager.setLastExtractDirectory(context, currentDir.absolutePath)
                                }
                                onConfirmTargetDir(currentDir)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentGradient)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                StyledIcon(
                                    imageVector = customActionIcon ?: when (action) {
                                        DirectoryPickerAction.MOVE -> Icons.Outlined.DriveFileMove
                                        DirectoryPickerAction.COPY -> Icons.Outlined.ContentCopy
                                        DirectoryPickerAction.EXTRACT -> Icons.Outlined.Download
                                        DirectoryPickerAction.EXPORT_SETTINGS -> Icons.Outlined.Download
                                        DirectoryPickerAction.IMPORT_SETTINGS -> Icons.Outlined.Upload
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = customActionLabel ?: when (action) {
                                        DirectoryPickerAction.MOVE -> "Move Here"
                                        DirectoryPickerAction.COPY -> "Copy Here"
                                        DirectoryPickerAction.EXTRACT -> "Extract Here"
                                        DirectoryPickerAction.EXPORT_SETTINGS -> "Export Here"
                                        DirectoryPickerAction.IMPORT_SETTINGS -> "Import Here"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create New Folder Dialog with smooth rounded corners
    if (showCreateDirDialog) {
        Dialog(
            onDismissRequest = { showCreateDirDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(sheetBg)
                    .border(1.5.dp, AccentGradient, RoundedCornerShape(24.dp))
                    .padding(22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StyledIcon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Folder",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Creating in: ${currentDir.name}",
                        fontSize = 11.5.sp,
                        color = secondaryText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newDirName,
                        onValueChange = { newDirName = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Folder Name", color = secondaryText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSkyBlue,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateDirDialog = false }) {
                            Text("Cancel", color = secondaryText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = newDirName.trim()
                                if (trimmed.isNotBlank()) {
                                    val newF = File(currentDir, trimmed)
                                    if (!newF.exists()) {
                                        newF.mkdirs()
                                        currentDir = newF
                                    }
                                }
                                showCreateDirDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGradient)
                        ) {
                            Text(
                                text = "Create",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
