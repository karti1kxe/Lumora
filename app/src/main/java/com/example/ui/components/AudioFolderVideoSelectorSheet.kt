package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.VideoFolder
import com.example.ui.screens.VideoFolderScanner
import com.example.ui.screens.VideoItem
import com.example.ui.state.AppLanguage
import com.example.ui.state.ThemeMode
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val SelectionGradient = Brush.linearGradient(
    listOf(
        Color(0xFF38BDF8),
        Color(0xFF818CF8),
        Color(0xFFC084FC),
        Color(0xFFF472B6)
    )
)

/**
 * Bottom Sheet Modal for selecting target folders and individual videos for preferred audio rules.
 * Features:
 * - Touch & hold (Long click) on any folder -> selects that entire folder with a gradient outline.
 * - Single click on any folder -> opens the folder to view its videos.
 * - Inside folder: Touch & hold or tap on video -> selects/deselects that video with gradient outline.
 * - "Select All" button inside folder to select all videos in that folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFolderVideoSelectorSheet(
    initialSelectedFolders: Set<String>,
    initialSelectedVideos: Set<String>,
    onSaveSelection: (folders: Set<String>, videos: Set<String>) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean = false,
    appScale: Float = 75f,
    glassBlurTransparency: Float = 85f,
    lang: AppLanguage = AppLanguage.ENGLISH,
    sheetTitle: String = "Audio Filter Folders & Videos"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Calculate dynamic scaled density so the entire Selector Sheet UI scales with appScale
    val currentDensity = LocalDensity.current
    val currentAppScale = appScale.coerceIn(1f, 100f)
    val scaleFactor = (0.85f + 0.15f * ((currentAppScale - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )
    }

    val alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
    val primaryTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryTextColor = if (isDark) DarkTextSecondary else LightTextSecondary
    val sheetBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0F172A)
        else Color(0xFF0F172A).copy(alpha = (0.40f + 0.60f * alphaRatio).coerceIn(0.25f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFF8FAFC)
        else Color(0xFFF8FAFC).copy(alpha = (0.40f + 0.60f * alphaRatio).coerceIn(0.25f, 1.0f))
    }
    val cardBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF1E293B)
        else Color(0xFF1E293B).copy(alpha = (0.35f + 0.65f * alphaRatio).coerceIn(0.20f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color.White
        else Color.White.copy(alpha = (0.45f + 0.55f * alphaRatio).coerceIn(0.25f, 1.0f))
    }
    val normalBorder = if (isDark) Color.White.copy(alpha = (0.08f + 0.12f * alphaRatio).coerceIn(0.08f, 0.25f)) else Color.Black.copy(alpha = (0.06f + 0.08f * alphaRatio).coerceIn(0.06f, 0.20f))

    // Selection sets
    val selectedFolders = remember { mutableStateListOf<String>().apply { addAll(initialSelectedFolders) } }
    val selectedVideos = remember { mutableStateListOf<String>().apply { addAll(initialSelectedVideos) } }

    // Loaded Folders State (Instantly populated from persistent cache)
    val initialCached = remember { com.example.util.VideoLibraryCache.loadCachedFolders(context) }
    var foldersList by remember { mutableStateOf<List<VideoFolder>>(initialCached ?: emptyList()) }
    var isLoading by remember { mutableStateOf(initialCached.isNullOrEmpty()) }

    // Navigation State
    var currentFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (foldersList.isEmpty()) {
            isLoading = true
        }
        withContext(Dispatchers.IO) {
            val scanned = VideoFolderScanner.scanVideoFolders(context)
            withContext(Dispatchers.Main) {
                foldersList = scanned
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
            // =====================================================================
            // 1. TOP HEADER & TITLE
            // =====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentSkyBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.FolderSpecial,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = sheetTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentFolder == null) {
                                "${foldersList.size} folders available"
                            } else {
                                "${currentFolder!!.videos.size} videos in folder"
                            },
                            fontSize = 11.5.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = secondaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =====================================================================
            // INTERACTIVE BREADCRUMB PATH (No back button needed)
            // =====================================================================
            val folderBreadcrumbScroll = rememberScrollState()
            LaunchedEffect(currentFolder?.path) {
                folderBreadcrumbScroll.animateScrollTo(folderBreadcrumbScroll.maxValue)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.65f) else Color(0xFFF1F5F9))
                    .border(1.dp, normalBorder, RoundedCornerShape(10.dp))
                    .horizontalScroll(folderBreadcrumbScroll)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledIcon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Internal Storage",
                    fontSize = 12.5.sp,
                    fontWeight = if (currentFolder == null) FontWeight.Bold else FontWeight.Medium,
                    color = if (currentFolder == null) AccentSkyBlue else secondaryTextColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = currentFolder != null) {
                            currentFolder = null
                            searchQuery = ""
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                if (currentFolder != null) {
                    StyledIcon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = secondaryTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )

                    Text(
                        text = currentFolder!!.name,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSkyBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =====================================================================
            // 2. SEARCH / FILTER & FOLDER ACTIONS
            // =====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (currentFolder == null) "Search folders..." else "Search videos in ${currentFolder!!.name}...",
                            fontSize = 12.5.sp,
                            color = secondaryTextColor.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        StyledIcon(
                                imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(17.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                StyledIcon(
                                imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyBlue,
                        unfocusedBorderColor = normalBorder,
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor,
                        cursorColor = AccentSkyBlue
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                )

                // "Select All" button inside folder view
                if (currentFolder != null) {
                    val folderVideos = currentFolder!!.videos
                    val allFolderVideosSelected = folderVideos.isNotEmpty() && folderVideos.all { v ->
                        selectedVideos.contains(v.path) || selectedVideos.contains(v.displayName)
                    }

                    Button(
                        onClick = {
                            if (allFolderVideosSelected) {
                                // Deselect all videos in this folder
                                folderVideos.forEach { v ->
                                    selectedVideos.remove(v.path)
                                    selectedVideos.remove(v.displayName)
                                }
                            } else {
                                // Select all videos in this folder
                                folderVideos.forEach { v ->
                                    if (!selectedVideos.contains(v.path)) {
                                        selectedVideos.add(v.path)
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allFolderVideosSelected) AccentSkyBlue else if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                            contentColor = if (allFolderVideosSelected) Color.White else primaryTextColor
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        StyledIcon(
                                imageVector = Icons.Outlined.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (allFolderVideosSelected) "Deselect" else "Select All",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =====================================================================
            // 3. FOLDERS / VIDEOS LIST
            // =====================================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = AccentSkyBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Scanning folders...",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                } else if (currentFolder == null) {
                    // ROOT VIEW: LIST OF ALL FOLDERS
                    val filteredFolders = remember(foldersList, searchQuery) {
                        if (searchQuery.isBlank()) foldersList
                        else foldersList.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.path.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filteredFolders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No folders found matching '$searchQuery'" else "No video folders found on device",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(filteredFolders, key = { it.path }) { folder ->
                                val isSelected = selectedFolders.contains(folder.path) || selectedFolders.contains(folder.name)

                                SelectableFolderItemCard(
                                    folder = folder,
                                    isSelected = isSelected,
                                    isDark = isDark,
                                    cardBg = cardBg,
                                    normalBorder = normalBorder,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onClick = {
                                        currentFolder = folder
                                        searchQuery = ""
                                    },
                                    onLongClick = {
                                        if (isSelected) {
                                            selectedFolders.remove(folder.path)
                                            selectedFolders.remove(folder.name)
                                        } else {
                                            selectedFolders.add(folder.path)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // INSIDE FOLDER VIEW: LIST OF VIDEOS IN CURRENT FOLDER
                    val folderVideos = currentFolder!!.videos
                    val filteredVideos = remember(folderVideos, searchQuery) {
                        if (searchQuery.isBlank()) folderVideos
                        else folderVideos.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                            it.path.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    val isParentFolderSelected = selectedFolders.contains(currentFolder!!.path) || selectedFolders.contains(currentFolder!!.name)

                    if (filteredVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No videos found matching '$searchQuery'" else "This folder has no videos",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(filteredVideos, key = { it.path }) { video ->
                                val isVideoDirectlySelected = selectedVideos.contains(video.path) || selectedVideos.contains(video.displayName)
                                val isSelected = isVideoDirectlySelected || isParentFolderSelected

                                SelectableVideoItemCard(
                                    video = video,
                                    isSelected = isSelected,
                                    isImplicitViaFolder = isParentFolderSelected && !isVideoDirectlySelected,
                                    isDark = isDark,
                                    cardBg = cardBg,
                                    normalBorder = normalBorder,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onToggleSelect = {
                                        if (isVideoDirectlySelected) {
                                            selectedVideos.remove(video.path)
                                            selectedVideos.remove(video.displayName)
                                        } else {
                                            selectedVideos.add(video.path)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = normalBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // =====================================================================
            // 4. BOTTOM STICKY ACTION BAR
            // =====================================================================
            val totalSelectedFolders = selectedFolders.size
            val totalSelectedVideos = selectedVideos.size
            val hasSelection = totalSelectedFolders > 0 || totalSelectedVideos > 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasSelection) {
                            "Selected: $totalSelectedFolders Folder${if (totalSelectedFolders == 1) "" else "s"}, $totalSelectedVideos Video${if (totalSelectedVideos == 1) "" else "s"}"
                        } else {
                            "No items selected"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSelection) AccentSkyBlue else secondaryTextColor
                    )
                    if (hasSelection) {
                        Text(
                            text = "Auto language rule will apply only to these",
                            fontSize = 10.5.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSelection) {
                        OutlinedButton(
                            onClick = {
                                selectedFolders.clear()
                                selectedVideos.clear()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = "Clear",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveSelection(selectedFolders.toSet(), selectedVideos.toSet())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentGradient)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StyledIcon(
                                imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Done",
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
}
}

/**
 * Selectable Folder Card with:
 * - Touch & Hold (Long Press) -> Selects Folder with **Gradient Outline Border**.
 * - Single Click -> Opens Folder to view videos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableFolderItemCard(
    folder: VideoFolder,
    isSelected: Boolean,
    isDark: Boolean,
    cardBg: Color,
    normalBorder: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardModifier = if (isSelected) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF0FDF4))
            .border(2.5.dp, SelectionGradient, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, normalBorder, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp)
    }

    Box(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Folder Icon Container
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) SelectionGradient
                            else Brush.linearGradient(
                                listOf(
                                    AccentSkyBlue.copy(alpha = 0.20f),
                                    AccentSkyBlue.copy(alpha = 0.08f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = if (isSelected) Icons.Outlined.FolderSpecial else Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else AccentSkyBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = folder.path,
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${folder.videoCount} videos",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = folder.formattedSize,
                                fontSize = 10.5.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }

            // Selection Checkmark / Indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) SelectionGradient
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .border(
                        if (isSelected) 0.dp else 1.5.dp,
                        if (isSelected) Color.Transparent else secondaryTextColor.copy(alpha = 0.35f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Selectable Video Card with:
 * - Touch & Hold (Long Press) OR Tap -> Selects/Deselects Video with **Gradient Outline Border**.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableVideoItemCard(
    video: VideoItem,
    isSelected: Boolean,
    isImplicitViaFolder: Boolean,
    isDark: Boolean,
    cardBg: Color,
    normalBorder: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onToggleSelect: () -> Unit
) {
    val cardModifier = if (isSelected) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF0FDF4))
            .border(2.dp, SelectionGradient, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onToggleSelect,
                onLongClick = onToggleSelect
            )
            .padding(10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, normalBorder, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onToggleSelect,
                onLongClick = onToggleSelect
            )
            .padding(10.dp)
    }

    Box(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) SelectionGradient
                            else Brush.linearGradient(
                                listOf(
                                    AccentSkyBlue.copy(alpha = 0.15f),
                                    AccentSkyBlue.copy(alpha = 0.05f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else AccentSkyBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = video.durationFormatted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentSkyBlue
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                        Text(
                            text = video.formattedSize,
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
                        if (video.resolution.isNotEmpty()) {
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            )
                            Text(
                                text = video.resolution,
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }
            }

            // Right selection checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) SelectionGradient
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .border(
                        if (isSelected) 0.dp else 1.5.dp,
                        if (isSelected) Color.Transparent else secondaryTextColor.copy(alpha = 0.35f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
