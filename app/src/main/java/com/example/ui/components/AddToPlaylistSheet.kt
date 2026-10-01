package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.screens.AudioTrackItem
import com.example.ui.screens.VideoItem
import com.example.ui.theme.*
import com.example.util.CustomPlaylist
import com.example.util.PlaylistManager

@Composable
fun AddToPlaylistDialog(
    selectedVideos: List<VideoItem> = emptyList(),
    selectedAudioTracks: List<AudioTrackItem> = emptyList(),
    isDark: Boolean = true,
    glassBlurTransparency: Float = 85f,
    onDismiss: () -> Unit,
    onPlaylistUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val isAudio = selectedAudioTracks.isNotEmpty()
    var playlists by remember {
        mutableStateOf(
            if (isAudio) PlaylistManager.getPlaylists(context).filter { it.isAudio }
            else PlaylistManager.getPlaylists(context).filter { !it.isAudio }
        )
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    val selectedPlaylistIds = remember { mutableStateListOf<String>() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f)
    val sheetBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0F172A)
        else Color(0xFF0F172A).copy(alpha = (0.50f + 0.50f * alphaRatio).coerceIn(0.35f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.55f + 0.45f * alphaRatio).coerceIn(0.40f, 1.0f))
    }
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

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
            onDismiss()
        }

        // Clean transparent backdrop without any black tint overlay (matching FloatingDirectoryPickerSheet)
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
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.45f)
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
                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = primaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAudio) "Add to Audio Playlist" else "Add to Playlist",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        val totalSelected = if (isAudio) selectedAudioTracks.size else selectedVideos.size
                        val firstName = if (isAudio) selectedAudioTracks.firstOrNull()?.title else selectedVideos.firstOrNull()?.displayName
                        Text(
                            text = "$totalSelected selected • ${if (totalSelected == 1) (firstName ?: "1 Item") else if (isAudio) "Multiple tracks" else "Multiple videos"}",
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

                    // Create New Playlist Button
                    IconButton(
                        onClick = {
                            newPlaylistTitle = ""
                            showCreateDialog = true
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.PlaylistAdd,
                            contentDescription = "New Playlist",
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(24.dp)
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
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
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

                Spacer(modifier = Modifier.height(10.dp))

                // Create New Playlist quick row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.50f) else Color(0xFFF1F5F9).copy(alpha = 0.70f))
                        .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable {
                            newPlaylistTitle = ""
                            showCreateDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create New Playlist",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentSkyBlue
                        )
                        Text(
                            text = "Add a fresh custom collection",
                            fontSize = 11.sp,
                            color = secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Playlists List (Matching FloatingDirectoryPickerSheet style)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (filteredPlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.PlaylistAdd,
                                    contentDescription = null,
                                    tint = secondaryText.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No playlists match search" else "No playlists yet",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap 'Create New Playlist' above to create one",
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
                            items(filteredPlaylists, key = { it.id }) { playlist ->
                                val isSelected = selectedPlaylistIds.contains(playlist.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) AccentSkyBlue.copy(alpha = 0.15f)
                                            else if (isDark) Color(0xFF1E293B).copy(alpha = 0.60f)
                                            else Color(0xFFF8FAFC)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) AccentSkyBlue else borderColor.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            if (isSelected) {
                                                selectedPlaylistIds.remove(playlist.id)
                                            } else {
                                                selectedPlaylistIds.add(playlist.id)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ModernFolderIcon(
                                        isDark = isDark,
                                        size = 42.dp,
                                        isCustom = true,
                                        showBadge = false,
                                        showVideoGlyph = false
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (playlist.isAudio) "${playlist.videoPaths.size} audio track(s)" else "${playlist.videoPaths.size} video(s)",
                                                fontSize = 11.sp,
                                                color = secondaryText
                                            )
                                            if (playlist.isOnlineFolder) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "• Online",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) AccentSkyBlue else Color.Transparent
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isSelected) AccentSkyBlue else secondaryText.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            StyledIcon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Bar (Matching FloatingDirectoryPickerSheet bottom action bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
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
                            text = when {
                                selectedPlaylistIds.isEmpty() -> "No playlist selected"
                                selectedPlaylistIds.size == 1 -> {
                                    val id = selectedPlaylistIds.first()
                                    playlists.find { it.id == id }?.name ?: "1 Playlist"
                                }
                                else -> "${selectedPlaylistIds.size} Playlists"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedPlaylistIds.isNotEmpty()) AccentSkyBlue else secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            val pathsToAdd = if (isAudio) selectedAudioTracks.map { it.path } else selectedVideos.map { it.path }
                            if (isAudio && selectedAudioTracks.isNotEmpty()) {
                                com.example.util.OnlineTrackMetadataCache.saveTracks(context, selectedAudioTracks)
                                com.example.util.AudioLibraryCache.updateCachedTracks(selectedAudioTracks, context)
                            }
                            selectedPlaylistIds.forEach { id ->
                                PlaylistManager.addVideosToPlaylist(context, id, pathsToAdd)
                            }
                            Toast.makeText(
                                context,
                                "Added to ${selectedPlaylistIds.size} ${if (isAudio) "audio " else ""}playlist(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            onPlaylistUpdated()
                            onDismiss()
                        },
                        enabled = selectedPlaylistIds.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selectedPlaylistIds.isNotEmpty()) AccentGradient
                                else Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFF475569)))
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.PlaylistAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save (${selectedPlaylistIds.size})",
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

    // Create New Playlist Dialog with smooth rounded corners
    if (showCreateDialog) {
        Dialog(
            onDismissRequest = { showCreateDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(sheetBg)
                    .border(1.5.dp, AccentGradient, RoundedCornerShape(26.dp))
                    .padding(22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isAudio) "New Audio Playlist" else "New Playlist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAudio) "Enter a title for this audio playlist" else "Enter a title for this playlist",
                        fontSize = 12.sp,
                        color = secondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPlaylistTitle,
                        onValueChange = { newPlaylistTitle = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(if (isAudio) "Audio Playlist Name" else "Playlist Name", color = secondaryText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentSkyBlue,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel", color = secondaryText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = newPlaylistTitle.trim()
                                if (trimmed.isNotBlank()) {
                                    val newPl = PlaylistManager.createPlaylist(
                                        context = context,
                                        name = trimmed,
                                        mediaType = if (isAudio) "AUDIO" else "VIDEO"
                                    )
                                    playlists = if (isAudio) {
                                        PlaylistManager.getPlaylists(context).filter { it.isAudio }
                                    } else {
                                        PlaylistManager.getPlaylists(context).filter { !it.isAudio }
                                    }
                                    selectedPlaylistIds.add(newPl.id)
                                    newPlaylistTitle = ""
                                    showCreateDialog = false
                                }
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
