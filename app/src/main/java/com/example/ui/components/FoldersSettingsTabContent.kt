package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AudioFolderItem
import com.example.ui.screens.VideoFolder
import com.example.ui.screens.VideoFolderScanner
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.util.AudioLibraryCache
import com.example.util.FolderBlockListManager
import com.example.util.FolderDateUtils
import com.example.util.PrivateFolder
import com.example.util.PrivateMediaType
import com.example.util.PrivateVaultManager
import com.example.util.VideoLibraryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FolderSettingsSelectionMode {
    NONE,
    BLOCK_LIST,
    PRIVATE_VAULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersSettingsTabContent(
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color,
    primaryText: Color,
    secondaryText: Color,
    glassBlurTransparency: Float = 85f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize managers
    LaunchedEffect(Unit) {
        FolderBlockListManager.init(context)
        PrivateVaultManager.init(context)
    }

    val blockedVideoIds by FolderBlockListManager.blockedVideoFolderIds
    val blockedAudioPaths by FolderBlockListManager.blockedAudioFolderPaths
    val privateFolders by PrivateVaultManager.privateFolders
    val isVaultAuthenticated by PrivateVaultManager.isSessionAuthenticated

    var videoFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var audioFolders by remember { mutableStateOf<List<AudioFolderItem>>(emptyList()) }
    var isLoadingFolders by remember { mutableStateOf(true) }

    // Refresh folders asynchronously without UI stutter
    fun refreshFolderLists() {
        coroutineScope.launch(Dispatchers.IO) {
            val vFolders = VideoFolderScanner.scanVideoFolders(context)
            val aTracks = AudioLibraryCache.getOrScanAudio(context, force = false)
            val aFolders = aTracks.groupBy { track ->
                val p = track.path
                if (p.contains('/')) p.substringBeforeLast('/') else "Music"
            }.map { (folderPath, tracks) ->
                val folderName = if (folderPath.contains('/')) folderPath.substringAfterLast('/') else folderPath
                AudioFolderItem(
                    name = folderName,
                    path = folderPath,
                    trackCount = tracks.size,
                    totalDurationMs = tracks.sumOf { it.durationMs },
                    totalSizeBytes = tracks.sumOf { it.sizeBytes },
                    dateModified = tracks.maxOfOrNull { it.dateModified } ?: 0L,
                    isNew = false
                )
            }
            withContext(Dispatchers.Main) {
                videoFolders = vFolders
                audioFolders = aFolders
                isLoadingFolders = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshFolderLists()
    }

    // Selection Mode States
    var selectionMode by remember { mutableStateOf(FolderSettingsSelectionMode.NONE) }
    val selectedBlockVideos = remember { mutableStateListOf<String>() }
    val selectedBlockAudios = remember { mutableStateListOf<String>() }

    val selectedVaultVideos = remember { mutableStateListOf<String>() }
    val selectedVaultAudios = remember { mutableStateListOf<String>() }

    // UI Expansion States
    var isBlockListExpanded by remember { mutableStateOf(false) }
    var isPrivateVaultExpanded by remember { mutableStateOf(false) }

    // Bottom Sheet States for Vault
    var showVaultSetupSheet by remember { mutableStateOf(false) }
    var showVaultUnlockSheet by remember { mutableStateOf(false) }

    // Bottom Processing Progress States (Identical to Home Screen)
    var isOperating by remember { mutableStateOf(false) }
    var operationActionText by remember { mutableStateOf("") }
    var operationProgressFraction by remember { mutableStateOf(0f) }
    var operationCurrentItemName by remember { mutableStateOf("") }
    var operationCompletedCount by remember { mutableStateOf(0) }
    var operationTotalCount by remember { mutableStateOf(0) }
    var operationIsDelete by remember { mutableStateOf(false) }
    var operationIsMove by remember { mutableStateOf(true) }

    // Derived states for performance
    val totalBlockSelected by remember {
        derivedStateOf { selectedBlockVideos.size + selectedBlockAudios.size }
    }
    val totalVaultSelected by remember {
        derivedStateOf { selectedVaultVideos.size + selectedVaultAudios.size }
    }
    val totalBlockedCount by remember {
        derivedStateOf { blockedVideoIds.size + blockedAudioPaths.size }
    }

    // Helper functions to enter/exit selection modes
    fun enterBlockListSelection() {
        selectedBlockVideos.clear()
        selectedBlockVideos.addAll(blockedVideoIds)
        selectedBlockAudios.clear()
        selectedBlockAudios.addAll(blockedAudioPaths)
        selectionMode = FolderSettingsSelectionMode.BLOCK_LIST
        isBlockListExpanded = true
    }

    fun enterVaultSelection() {
        selectedVaultVideos.clear()
        selectedVaultAudios.clear()
        selectionMode = FolderSettingsSelectionMode.PRIVATE_VAULT
        isPrivateVaultExpanded = true
    }

    fun exitSelectionMode() {
        selectionMode = FolderSettingsSelectionMode.NONE
        selectedBlockVideos.clear()
        selectedBlockAudios.clear()
        selectedVaultVideos.clear()
        selectedVaultAudios.clear()
    }

    // Common action to confirm block list
    fun applyBlockListChanges() {
        isOperating = true
        operationActionText = "Updating Block List..."
        operationProgressFraction = 0.5f
        operationIsMove = false
        operationIsDelete = false
        operationCurrentItemName = "Saving blocked folders"
        coroutineScope.launch {
            FolderBlockListManager.updateBlockList(
                context = context,
                videoFolderIds = selectedBlockVideos.toSet(),
                audioFolderPaths = selectedBlockAudios.toSet()
            )
            operationProgressFraction = 1f
            delay(200)
            isOperating = false
            exitSelectionMode()
            Toast.makeText(context, "Block list updated", Toast.LENGTH_SHORT).show()
        }
    }

    // Common action to confirm moving to vault
    fun applyVaultChanges() {
        val vidsToMove = videoFolders.filter { selectedVaultVideos.contains(it.id) }
        val audsToMove = audioFolders.filter { selectedVaultAudios.contains(it.path) }
        val totalToMove = vidsToMove.size + audsToMove.size
        if (totalToMove == 0) {
            Toast.makeText(context, "No folders selected", Toast.LENGTH_SHORT).show()
            return
        }
        exitSelectionMode()
        isOperating = true
        operationActionText = "Securing into Private Vault..."
        operationProgressFraction = 0f
        operationCompletedCount = 0
        operationTotalCount = totalToMove
        operationIsMove = true
        operationIsDelete = false

        coroutineScope.launch {
            var idx = 0
            for (vf in vidsToMove) {
                operationCurrentItemName = vf.name
                PrivateVaultManager.makeVideoFolderPrivate(context, vf) { frac, _, _, name ->
                    operationProgressFraction = (idx + frac) / totalToMove.toFloat().coerceAtLeast(1f)
                    operationCurrentItemName = name
                }
                idx++
                operationCompletedCount = idx
            }
            for (af in audsToMove) {
                operationCurrentItemName = af.name
                PrivateVaultManager.makeAudioFolderPrivate(context, af) { frac, _, _, name ->
                    operationProgressFraction = (idx + frac) / totalToMove.toFloat().coerceAtLeast(1f)
                    operationCurrentItemName = name
                }
                idx++
                operationCompletedCount = idx
            }
            operationProgressFraction = 1f
            delay(200)
            isOperating = false
            refreshFolderLists()
            Toast.makeText(context, "Moved $totalToMove folder(s) to Private Vault", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
        ) {
            // ================= IN-PLACE SELECTION HEADER BANNER ================= //
            AnimatedVisibility(
                visible = selectionMode != FolderSettingsSelectionMode.NONE,
                enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200))
            ) {
                val isBlockMode = (selectionMode == FolderSettingsSelectionMode.BLOCK_LIST)
                val totalSelected = if (isBlockMode) totalBlockSelected else totalVaultSelected

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isBlockMode) Color(0xFFEF4444).copy(alpha = 0.12f)
                            else AccentSkyBlue.copy(alpha = 0.15f)
                        )
                        .border(
                            1.5.dp,
                            if (isBlockMode) Color(0xFFEF4444).copy(alpha = 0.45f)
                            else AccentSkyBlue.copy(alpha = 0.55f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isBlockMode) Color(0xFFEF4444) else AccentSkyBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(imageVector = if (isBlockMode) Icons.Outlined.Block else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isBlockMode) "Block List Selection" else "Private Vault Selection",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = "$totalSelected folder(s) selected",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick Select All / Deselect All (Large Touch Target 44dp)
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(cardBg)
                                    .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                                    .clickable {
                                        if (isBlockMode) {
                                            val allVidIds = videoFolders.map { it.id }.toSet()
                                            val allAudPaths = audioFolders.map { it.path }.toSet()
                                            if (selectedBlockVideos.size == allVidIds.size && selectedBlockAudios.size == allAudPaths.size) {
                                                selectedBlockVideos.clear()
                                                selectedBlockAudios.clear()
                                            } else {
                                                selectedBlockVideos.clear()
                                                selectedBlockVideos.addAll(allVidIds)
                                                selectedBlockAudios.clear()
                                                selectedBlockAudios.addAll(allAudPaths)
                                            }
                                        } else {
                                            val availableVids = videoFolders.filter { !PrivateVaultManager.isFolderPrivate(it.id) }.map { it.id }
                                            val availableAuds = audioFolders.filter { !PrivateVaultManager.isFolderPrivate(it.path) }.map { it.path }
                                            if (selectedVaultVideos.size == availableVids.size && selectedVaultAudios.size == availableAuds.size) {
                                                selectedVaultVideos.clear()
                                                selectedVaultAudios.clear()
                                            } else {
                                                selectedVaultVideos.clear()
                                                selectedVaultVideos.addAll(availableVids)
                                                selectedVaultAudios.clear()
                                                selectedVaultAudios.addAll(availableAuds)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Toggle All",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                            }

                            // Close / Cancel Selection (40dp touch target)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(cardBg)
                                    .border(1.dp, borderColor, CircleShape)
                                    .clickable { exitSelectionMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cancel",
                                    tint = secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ================= SECTION 1: VIDEO FOLDERS ================= //
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                StyledIcon(imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = AccentSkyBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VIDEO FOLDERS (${videoFolders.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryText,
                    letterSpacing = 0.5.sp
                )
            }

            if (isLoadingFolders && videoFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = AccentSkyBlue,
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (videoFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No video folders detected",
                        fontSize = 13.5.sp,
                        color = secondaryText
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    videoFolders.forEach { folder ->
                        key(folder.id) {
                            val isBlocked = blockedVideoIds.contains(folder.id)
                            val isPrivate = PrivateVaultManager.isFolderPrivate(folder.id) || PrivateVaultManager.isFolderPrivate(folder.path)
                            val createdDateMillis = FolderDateUtils.getFolderCreatedDateMillis(folder.path, folder.lastModifiedDate)
                            val createdDateStr = FolderDateUtils.formatCreatedDate(createdDateMillis)

                            val isSelectionActive = (selectionMode != FolderSettingsSelectionMode.NONE)
                            val isChecked = when (selectionMode) {
                                FolderSettingsSelectionMode.BLOCK_LIST -> selectedBlockVideos.contains(folder.id)
                                FolderSettingsSelectionMode.PRIVATE_VAULT -> selectedVaultVideos.contains(folder.id)
                                FolderSettingsSelectionMode.NONE -> false
                            }
                            val isSelectable = if (selectionMode == FolderSettingsSelectionMode.PRIVATE_VAULT) !isPrivate else true

                            FolderMetadataCard(
                                isDark = isDark,
                                name = folder.name,
                                countText = "${folder.allVideosCount} Videos",
                                sizeText = folder.formattedSize,
                                createdDateText = "Created: $createdDateStr",
                                isCustom = folder.isCustom,
                                glyphType = FolderGlyphType.VIDEO,
                                statusBadge = if (isBlocked) "Blocked" else if (isPrivate) "Private" else null,
                                isSelectionMode = isSelectionActive,
                                isSelected = isChecked,
                                isSelectable = isSelectable,
                                selectionTint = if (selectionMode == FolderSettingsSelectionMode.BLOCK_LIST) Color(0xFFEF4444) else AccentSkyBlue,
                                onCardClick = {
                                    if (selectionMode == FolderSettingsSelectionMode.BLOCK_LIST) {
                                        if (selectedBlockVideos.contains(folder.id)) {
                                            selectedBlockVideos.remove(folder.id)
                                        } else {
                                            selectedBlockVideos.add(folder.id)
                                        }
                                    } else if (selectionMode == FolderSettingsSelectionMode.PRIVATE_VAULT && isSelectable) {
                                        if (selectedVaultVideos.contains(folder.id)) {
                                            selectedVaultVideos.remove(folder.id)
                                        } else {
                                            selectedVaultVideos.add(folder.id)
                                        }
                                    }
                                },
                                cardBg = cardBg,
                                borderColor = borderColor,
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ================= SECTION 2: AUDIO FOLDERS ================= //
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                StyledIcon(imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = AccentPink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AUDIO FOLDERS (${audioFolders.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryText,
                    letterSpacing = 0.5.sp
                )
            }

            if (isLoadingFolders && audioFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = AccentPink,
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (audioFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No audio folders detected",
                        fontSize = 13.5.sp,
                        color = secondaryText
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    audioFolders.forEach { folder ->
                        key(folder.path) {
                            val isBlocked = blockedAudioPaths.contains(folder.path)
                            val isPrivate = PrivateVaultManager.isFolderPrivate(folder.path)
                            val createdDateMillis = FolderDateUtils.getFolderCreatedDateMillis(folder.path, folder.dateModified * 1000L)
                            val createdDateStr = FolderDateUtils.formatCreatedDate(createdDateMillis)

                            val isSelectionActive = (selectionMode != FolderSettingsSelectionMode.NONE)
                            val isChecked = when (selectionMode) {
                                FolderSettingsSelectionMode.BLOCK_LIST -> selectedBlockAudios.contains(folder.path)
                                FolderSettingsSelectionMode.PRIVATE_VAULT -> selectedVaultAudios.contains(folder.path)
                                FolderSettingsSelectionMode.NONE -> false
                            }
                            val isSelectable = if (selectionMode == FolderSettingsSelectionMode.PRIVATE_VAULT) !isPrivate else true

                            FolderMetadataCard(
                                isDark = isDark,
                                name = folder.name,
                                countText = "${folder.trackCount} Tracks",
                                sizeText = folder.formattedSize,
                                createdDateText = "Created: $createdDateStr",
                                isCustom = false,
                                glyphType = FolderGlyphType.AUDIO,
                                statusBadge = if (isBlocked) "Blocked" else if (isPrivate) "Private" else null,
                                isSelectionMode = isSelectionActive,
                                isSelected = isChecked,
                                isSelectable = isSelectable,
                                selectionTint = if (selectionMode == FolderSettingsSelectionMode.BLOCK_LIST) Color(0xFFEF4444) else AccentPink,
                                onCardClick = {
                                    if (selectionMode == FolderSettingsSelectionMode.BLOCK_LIST) {
                                        if (selectedBlockAudios.contains(folder.path)) {
                                            selectedBlockAudios.remove(folder.path)
                                        } else {
                                            selectedBlockAudios.add(folder.path)
                                        }
                                    } else if (selectionMode == FolderSettingsSelectionMode.PRIVATE_VAULT && isSelectable) {
                                        if (selectedVaultAudios.contains(folder.path)) {
                                            selectedVaultAudios.remove(folder.path)
                                        } else {
                                            selectedVaultAudios.add(folder.path)
                                        }
                                    }
                                },
                                cardBg = cardBg,
                                borderColor = borderColor,
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ================= SECTION 3: BLOCK LIST CARD (WITH GENEROUS TOUCH AREA) ================= //
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardBg)
                    .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with min 56dp height and 48dp+ interactive area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isBlockListExpanded = !isBlockListExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "BLOCK LIST",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = if (totalBlockedCount > 0) "$totalBlockedCount folder(s) blocked" else "No folders blocked",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(imageVector = if (isBlockListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isBlockListExpanded) "Collapse" else "Expand",
                                tint = secondaryText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isBlockListExpanded,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 14.dp),
                                color = borderColor.copy(alpha = 0.6f)
                            )

                            // Action button: Sleek & rounded 22dp corners
                            val isCurrentlySelectingBlock = (selectionMode == FolderSettingsSelectionMode.BLOCK_LIST)
                            if (isCurrentlySelectingBlock) {
                                val totalSelected = totalBlockSelected
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { exitSelectionMode() },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(22.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                                    ) {
                                        Text("Cancel", color = secondaryText, fontSize = 13.sp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(AccentGradient)
                                            .clickable { applyBlockListChanges() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StyledIcon(imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Confirm Block List ($totalSelected)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(AccentSkyBlue.copy(alpha = 0.15f))
                                        .border(1.dp, AccentSkyBlue.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                                        .clickable { enterBlockListSelection() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StyledIcon(imageVector = Icons.Outlined.Block,
                                            contentDescription = null,
                                            tint = AccentSkyBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Select Folders to Block",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentSkyBlue
                                        )
                                    }
                                }
                            }

                            if (totalBlockedCount > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "CURRENTLY BLOCKED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryText,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // List blocked video folders
                                    videoFolders.filter { blockedVideoIds.contains(it.id) }.forEach { folder ->
                                        key("block_v_${folder.id}") {
                                            BlockedFolderRowItem(
                                                isDark = isDark,
                                                name = folder.name,
                                                typeLabel = "Video • ${folder.allVideosCount} items",
                                                isAudio = false,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                                onUnblock = {
                                                    coroutineScope.launch {
                                                        FolderBlockListManager.unblockVideoFolder(context, folder.id)
                                                        Toast.makeText(context, "Unblocked ${folder.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // List blocked audio folders
                                    audioFolders.filter { blockedAudioPaths.contains(it.path) }.forEach { folder ->
                                        key("block_a_${folder.path}") {
                                            BlockedFolderRowItem(
                                                isDark = isDark,
                                                name = folder.name,
                                                typeLabel = "Audio • ${folder.trackCount} items",
                                                isAudio = true,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                                onUnblock = {
                                                    coroutineScope.launch {
                                                        FolderBlockListManager.unblockAudioFolder(context, folder.path)
                                                        Toast.makeText(context, "Unblocked ${folder.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ================= SECTION 4: PRIVATE VAULT CARD (WITH GENEROUS TOUCH AREA) ================= //
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardBg)
                    .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with min 56dp height and 48dp+ interactive area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPrivateVaultExpanded = !isPrivateVaultExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (PrivateVaultManager.isSetup(context)) {
                                            if (isVaultAuthenticated) AccentSkyBlue.copy(alpha = 0.15f)
                                            else Color(0xFF6366F1).copy(alpha = 0.15f)
                                        } else Color(0xFF94A3B8).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(imageVector = if (!PrivateVaultManager.isSetup(context)) Icons.Outlined.Lock
                                    else if (isVaultAuthenticated) Icons.Filled.LockOpen
                                    else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (!PrivateVaultManager.isSetup(context)) secondaryText
                                    else if (isVaultAuthenticated) AccentSkyBlue
                                    else Color(0xFF6366F1),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "PRIVATE VAULT",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = if (!PrivateVaultManager.isSetup(context)) "Tap to set up password"
                                    else if (isVaultAuthenticated) "Unlocked • ${privateFolders.size} folder(s) protected"
                                    else "Locked with password",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(imageVector = if (isPrivateVaultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isPrivateVaultExpanded) "Collapse" else "Expand",
                                tint = secondaryText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isPrivateVaultExpanded,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 14.dp),
                                color = borderColor.copy(alpha = 0.6f)
                            )

                            if (!PrivateVaultManager.isSetup(context)) {
                                // Vault Setup Prompt with sleek 22dp corners
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(AccentGradient)
                                        .clickable { showVaultSetupSheet = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StyledIcon(imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Set Up Private Vault",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else if (!isVaultAuthenticated) {
                                // Vault Unlock Button with sleek 22dp corners
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(Brush.linearGradient(listOf(Color(0xFF6366F1), AccentSkyBlue)))
                                        .clickable { showVaultUnlockSheet = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StyledIcon(imageVector = Icons.Filled.LockOpen,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Unlock Vault with Password",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                // Vault Unlocked State: Action Buttons
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val isCurrentlySelectingVault = (selectionMode == FolderSettingsSelectionMode.PRIVATE_VAULT)
                                    if (isCurrentlySelectingVault) {
                                        val totalSelected = totalVaultSelected
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { exitSelectionMode() },
                                                modifier = Modifier.height(44.dp),
                                                shape = RoundedCornerShape(22.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                                            ) {
                                                Text("Cancel", color = secondaryText, fontSize = 13.sp)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(AccentGradient)
                                                    .clickable { applyVaultChanges() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    StyledIcon(imageVector = Icons.Outlined.Lock,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Confirm Vault ($totalSelected)",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(AccentSkyBlue.copy(alpha = 0.15f))
                                                    .border(1.dp, AccentSkyBlue.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                                                    .clickable { enterVaultSelection() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    StyledIcon(imageVector = Icons.Outlined.Lock,
                                                        contentDescription = null,
                                                        tint = AccentSkyBlue,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Select Folders for Vault",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AccentSkyBlue
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(cardBg)
                                                    .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                                                    .clickable {
                                                        PrivateVaultManager.clearSession()
                                                        Toast.makeText(context, "Vault Locked", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    StyledIcon(imageVector = Icons.Filled.Lock,
                                                        contentDescription = null,
                                                        tint = secondaryText,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(text = "Lock", fontSize = 12.5.sp, color = secondaryText, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }

                                    if (privateFolders.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "PROTECTED VAULT FOLDERS (${privateFolders.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = secondaryText,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            privateFolders.forEach { privFolder ->
                                                key("vault_${privFolder.id}") {
                                                    PrivateFolderItemCard(
                                                        isDark = isDark,
                                                        folder = privFolder,
                                                        cardBg = cardBg,
                                                        borderColor = borderColor,
                                                        primaryText = primaryText,
                                                        secondaryText = secondaryText,
                                                        onRestore = {
                                                            isOperating = true
                                                            operationActionText = "Restoring folder from Vault..."
                                                            operationProgressFraction = 0f
                                                            operationCurrentItemName = privFolder.name
                                                            operationCompletedCount = 0
                                                            operationTotalCount = 1
                                                            operationIsMove = true
                                                            operationIsDelete = false

                                                            coroutineScope.launch {
                                                                val ok = PrivateVaultManager.restorePrivateFolder(
                                                                    context = context,
                                                                    privateFolder = privFolder
                                                                ) { fraction, _, _, name ->
                                                                    operationProgressFraction = fraction
                                                                    operationCurrentItemName = name
                                                                }
                                                                operationProgressFraction = 1f
                                                                delay(200)
                                                                isOperating = false
                                                                if (ok) {
                                                                    Toast.makeText(context, "Restored ${privFolder.name} successfully", Toast.LENGTH_SHORT).show()
                                                                    refreshFolderLists()
                                                                } else {
                                                                    Toast.makeText(context, "Failed to restore ${privFolder.name}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
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
            }
            Spacer(modifier = Modifier.height(60.dp))
        }

        // ================= BOTTOM PROGRESS BAR UI (EXACTLY IDENTICAL TO HOME SCREEN!) ================= //
        AnimatedVisibility(
            visible = isOperating,
            enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(animationSpec = tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            BottomMoveCopyProgressBar(
                isDark = isDark,
                alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f),
                isMove = operationIsMove,
                isDelete = operationIsDelete,
                customActionText = operationActionText,
                customIcon = if (operationActionText.contains("Vault", ignoreCase = true)) Icons.Outlined.Lock else null,
                progressFraction = operationProgressFraction,
                currentItemName = operationCurrentItemName,
                completedCount = operationCompletedCount,
                totalCount = operationTotalCount
            )
        }
    }

    // ================= BOTTOM SHEET 1: PRIVATE VAULT SETUP (WITH TRANSPARENCY SUPPORT) ================= //
    if (showVaultSetupSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var securityAnswer by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val sheetAlphaFactor = (glassBlurTransparency / 100f).coerceIn(0.20f, 1.0f)
        val sheetContainerColor = if (isDark) {
            if (sheetAlphaFactor >= 0.99f) Color(0xFF0F172A)
            else Color(0xFF0F172A).copy(alpha = (0.70f + 0.30f * sheetAlphaFactor).coerceIn(0.40f, 1.0f))
        } else {
            if (sheetAlphaFactor >= 0.99f) Color(0xFFFFFFFF)
            else Color(0xFFFFFFFF).copy(alpha = (0.75f + 0.25f * sheetAlphaFactor).coerceIn(0.45f, 1.0f))
        }

        ModalBottomSheet(
            onDismissRequest = { showVaultSetupSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = sheetContainerColor,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Set Up Private Vault",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Text(
                            text = "Hashed with PBKDF2. Required to unlock private files.",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sleek & Rounded password inputs
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Password", fontSize = 12.5.sp) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            StyledIcon(imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyBlue,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = AccentSkyBlue
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Confirm Password", fontSize = 12.5.sp) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyBlue,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = AccentSkyBlue
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Security Question (for recovery):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentSkyBlue
                        )
                        Text(
                            text = PrivateVaultManager.DEFAULT_SECURITY_QUESTION,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = securityAnswer,
                    onValueChange = {
                        securityAnswer = it
                        errorMessage = null
                    },
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Your Answer", fontSize = 12.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyBlue,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = AccentSkyBlue
                    )
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Sleek & Rounded action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showVaultSetupSheet = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = secondaryText, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AccentGradient)
                            .clickable {
                                if (newPassword.isBlank()) {
                                    errorMessage = "Please enter a password"
                                    return@clickable
                                }
                                if (newPassword != confirmPassword) {
                                    errorMessage = "Passwords do not match"
                                    return@clickable
                                }
                                if (securityAnswer.isBlank()) {
                                    errorMessage = "Please answer the security question"
                                    return@clickable
                                }
                                val ok = PrivateVaultManager.setupVault(context, newPassword, securityAnswer)
                                if (ok) {
                                    showVaultSetupSheet = false
                                    isPrivateVaultExpanded = true
                                    Toast.makeText(context, "Private Vault initialized successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = "Failed to initialize vault"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Vault",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // ================= BOTTOM SHEET 2: VAULT UNLOCK (WITH TRANSPARENCY SUPPORT) ================= //
    if (showVaultUnlockSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var passwordInput by remember { mutableStateOf("") }
        var answerInput by remember { mutableStateOf("") }
        var isPwdVisible by remember { mutableStateOf(false) }
        var unlockError by remember { mutableStateOf<String?>(null) }

        val sheetAlphaFactor = (glassBlurTransparency / 100f).coerceIn(0.20f, 1.0f)
        val sheetContainerColor = if (isDark) {
            if (sheetAlphaFactor >= 0.99f) Color(0xFF0F172A)
            else Color(0xFF0F172A).copy(alpha = (0.70f + 0.30f * sheetAlphaFactor).coerceIn(0.40f, 1.0f))
        } else {
            if (sheetAlphaFactor >= 0.99f) Color(0xFFFFFFFF)
            else Color(0xFFFFFFFF).copy(alpha = (0.75f + 0.25f * sheetAlphaFactor).coerceIn(0.45f, 1.0f))
        }

        ModalBottomSheet(
            onDismissRequest = { showVaultUnlockSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = sheetContainerColor,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Unlock Private Vault",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Text(
                            text = "Enter password or answer security question.",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sleek & Rounded password inputs
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        unlockError = null
                    },
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Password", fontSize = 12.5.sp) },
                    visualTransformation = if (isPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPwdVisible = !isPwdVisible }) {
                            StyledIcon(imageVector = if (isPwdVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyBlue,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = AccentSkyBlue
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Or verify via question: ${PrivateVaultManager.DEFAULT_SECURITY_QUESTION}",
                    fontSize = 11.5.sp,
                    color = secondaryText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                OutlinedTextField(
                    value = answerInput,
                    onValueChange = {
                        answerInput = it
                        unlockError = null
                    },
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Security Answer", fontSize = 12.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyBlue,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = AccentSkyBlue
                    )
                )

                if (unlockError != null) {
                    Text(
                        text = unlockError ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Sleek & Rounded action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showVaultUnlockSheet = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = secondaryText, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AccentGradient)
                            .clickable {
                                val ok = PrivateVaultManager.authenticateSession(context, passwordInput, answerInput)
                                if (ok) {
                                    showVaultUnlockSheet = false
                                    isPrivateVaultExpanded = true
                                    Toast.makeText(context, "Vault Unlocked", Toast.LENGTH_SHORT).show()
                                } else {
                                    unlockError = "Incorrect password or security answer"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Unlock Vault",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderMetadataCard(
    isDark: Boolean,
    name: String,
    countText: String,
    sizeText: String,
    createdDateText: String,
    isCustom: Boolean,
    glyphType: FolderGlyphType,
    statusBadge: String?,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isSelectable: Boolean = true,
    selectionTint: Color = AccentSkyBlue,
    onCardClick: (() -> Unit)? = null,
    cardBg: Color,
    borderColor: Color,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    // Generous touch target with 48dp+ interactive area across whole card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) selectionTint else borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                enabled = onCardClick != null && isSelectable,
                onClick = { onCardClick?.invoke() }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModernFolderIcon(
                isDark = isDark,
                size = 46.dp,
                isCustom = isCustom,
                glyphType = glyphType,
                showBadge = false
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!isSelectable) secondaryText.copy(alpha = 0.5f) else primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (statusBadge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (statusBadge == "Blocked") Color(0xFFEF4444).copy(alpha = 0.15f)
                                    else Color(0xFF6366F1).copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (statusBadge == "Blocked") Color(0xFFEF4444) else Color(0xFF6366F1)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                // EXACT 3 METADATA ITEMS: COUNT • SIZE • CREATED DATE
                Text(
                    text = "$countText • $sizeText • $createdDateText",
                    fontSize = 11.5.sp,
                    color = if (!isSelectable) secondaryText.copy(alpha = 0.5f) else secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // SELECTION CIRCLE ON THE RIGHT END OF FOLDER ITEM WITH 44dp MIN HITBOX
            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isSelectable) {
                        StyledIcon(imageVector = Icons.Default.Lock,
                            contentDescription = "Already in Vault",
                            tint = secondaryText.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        StyledIcon(imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) selectionTint else secondaryText.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedFolderRowItem(
    isDark: Boolean,
    name: String,
    typeLabel: String,
    isAudio: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onUnblock: () -> Unit
) {
    // Generous touch area across the row + 44dp touch area for unblock action
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            ModernFolderIcon(
                isDark = isDark,
                size = 34.dp,
                glyphType = if (isAudio) FolderGlyphType.AUDIO else FolderGlyphType.VIDEO,
                showBadge = false
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = typeLabel,
                    fontSize = 11.sp,
                    color = secondaryText
                )
            }
        }
        // Easy-to-tap 44dp button
        IconButton(
            onClick = onUnblock,
            modifier = Modifier.size(44.dp)
        ) {
            StyledIcon(imageVector = Icons.Default.Close,
                contentDescription = "Unblock",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PrivateFolderItemCard(
    isDark: Boolean,
    folder: PrivateFolder,
    cardBg: Color,
    borderColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onRestore: () -> Unit
) {
    // Generous touch area across the card + 44dp restore button
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFFF1F5F9))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ModernFolderIcon(
                    isDark = isDark,
                    size = 40.dp,
                    glyphType = if (folder.mediaType == PrivateMediaType.AUDIO) FolderGlyphType.AUDIO else FolderGlyphType.VIDEO,
                    showBadge = false
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = folder.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${folder.itemCount} ${if (folder.mediaType == PrivateMediaType.VIDEO) "Videos" else "Tracks"} • ${folder.formattedSize} • Created: ${folder.formattedDate}",
                        fontSize = 11.sp,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Easy-to-tap 44dp restore button
            IconButton(
                onClick = onRestore,
                modifier = Modifier.size(44.dp)
            ) {
                StyledIcon(imageVector = Icons.Outlined.Restore,
                    contentDescription = "Restore to public storage",
                    tint = AccentSkyBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
