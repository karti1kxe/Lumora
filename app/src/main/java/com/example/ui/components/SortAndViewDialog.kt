package com.example.ui.components

import com.example.R

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary

enum class SortField {
    TITLE, DURATION, DATE, SIZE, COUNT
}

enum class SortDirection {
    ASCENDING, DESCENDING
}

enum class ViewMode {
    FOLDER, TREE, LIBRARY
}

enum class LayoutMode {
    LIST, GRID
}

data class VisibleFields(
    val showThumbnails: Boolean = true,
    val showExtension: Boolean = true,
    val showDuration: Boolean = false,
    val showSubtitleIndicator: Boolean = true,
    val showFullName: Boolean = false,
    val showSize: Boolean = true,
    val showResolution: Boolean = true,
    val showFramerate: Boolean = false,
    val showDate: Boolean = false,
    val showProgressBar: Boolean = true,
    val showPath: Boolean = false,
    val showVideoCount: Boolean = true,
    val showNewBadge: Boolean = false,
    val showAudioType: Boolean = true
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortAndViewOptionsPopup(
    isDark: Boolean,
    sortField: SortField,
    sortDirection: SortDirection,
    viewMode: ViewMode,
    layoutMode: LayoutMode,
    visibleFields: VisibleFields,
    isInsideFolder: Boolean = false,
    isMusicMode: Boolean = false,
    isRecentsMode: Boolean = false,
    isPlaylistMode: Boolean = false,
    isOnlineMode: Boolean = false,
    isOnlinePlaylist: Boolean = false,
    isOnlineAudioFolder: Boolean = false,
    appScale: Float = 75f,
    glassBlurTransparency: Float = 85f,
    onSortFieldChange: (SortField) -> Unit,
    onSortDirectionChange: (SortDirection) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onVisibleFieldsChange: (VisibleFields) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val accentBlue = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val activeTintBg = if (isDark) Color(0xFF0284C7).copy(alpha = 0.28f) else Color(0xFFE0F2FE).copy(alpha = 0.85f)
    val pillBorderColor = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.70f) else Color(0xFF0284C7).copy(alpha = 0.65f)
    val dividerColor = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color(0xFF38BDF8).copy(alpha = 0.30f)
    val alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
    val dialogSurface = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0B1120) else Color(0xFF0B1120).copy(alpha = (0.40f + 0.60f * alphaRatio).coerceIn(0.25f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFFFFFFF) else Color(0xFFFFFFFF).copy(alpha = (0.40f + 0.60f * alphaRatio).coerceIn(0.25f, 1.0f))
    }
    val dialogBorderBrush = if (isDark) LiquidGlassDefaults.DarkGlassBorderBrush else LiquidGlassDefaults.LightGlassBorderBrush

    // Calculate dynamic scaled density so the entire Sort & View Options UI scales with appScale
    val currentDensity = LocalDensity.current
    val currentAppScale = appScale.coerceIn(1f, 100f)
    val scaleFactor = (0.85f + 0.15f * ((currentAppScale - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )
    }

    var isFieldsExpanded by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            val view = LocalView.current
            DisposableEffect(view) {
                configureEdgeToEdgeDialogWindow(
                    view = view,
                    isLightStatusBars = !isDark,
                    isLightNavBars = !isDark
                )
                onDispose {}
            }

            // Clean transparent backdrop with touch dismissal (no dark background UI)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
            // Liquid Glass Dialog Card
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.40f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogSurface)
                    .border(1.2.dp, dialogBorderBrush, RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Title Header
                    Text(
                        text = "Sort & View Options",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                        color = dividerColor,
                        thickness = 1.dp
                    )

                    // 1. SECTION: SORT BY
                    Text(
                        text = "Sort by",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sort cards adapt to the current level:
                    // - Online Audio Folder: ONLY Date and Title (Duration and Size removed)
                    // - Count: Folder views only (Home Video root, Home Audio root, Online Audio root)
                    // - Duration: Media items inside folders & playlists (positioned directly after Title)
                    // - Size: Available in all views except Online Playlists (Sections 11 & 12)
                    val isOnlineMediaFolder = isOnlineAudioFolder
                    val showCountSort = !isInsideFolder && !isPlaylistMode && !isRecentsMode && !isOnlineMediaFolder
                    val showDurationSort = (isInsideFolder || isPlaylistMode) && !isOnlineMediaFolder
                    val showSizeSort = !isOnlinePlaylist && !isOnlineMediaFolder

                    LaunchedEffect(isOnlineMediaFolder, showDurationSort, showCountSort, showSizeSort) {
                        if (isOnlineMediaFolder) {
                            if (sortField != SortField.DATE && sortField != SortField.TITLE) {
                                onSortFieldChange(SortField.DATE)
                            }
                        } else {
                            if (!showDurationSort && sortField == SortField.DURATION) {
                                onSortFieldChange(SortField.TITLE)
                            }
                            if (!showCountSort && sortField == SortField.COUNT) {
                                onSortFieldChange(SortField.TITLE)
                            }
                            if (!showSizeSort && sortField == SortField.SIZE) {
                                onSortFieldChange(SortField.TITLE)
                            }
                        }
                    }

                    if (isOnlineMediaFolder) {
                        // Online Audio folder: Keep ONLY 2 options: "Date" and "Title"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SortTypeCard(
                                label = "Date", icon = R.drawable.lumora_calendar,
                                isSelected = sortField == SortField.DATE, primaryText = primaryText,
                                secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                isDark = isDark, modifier = Modifier.weight(1f),
                                onClick = { onSortFieldChange(SortField.DATE) }
                            )
                            SortTypeCard(
                                label = "Title", icon = R.drawable.lumora_text,
                                isSelected = sortField == SortField.TITLE, primaryText = primaryText,
                                secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                isDark = isDark, modifier = Modifier.weight(1f),
                                onClick = { onSortFieldChange(SortField.TITLE) }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SortTypeCard(
                                label = "Title", icon = R.drawable.lumora_text,
                                isSelected = sortField == SortField.TITLE, primaryText = primaryText,
                                secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                isDark = isDark, modifier = Modifier.weight(1f),
                                onClick = { onSortFieldChange(SortField.TITLE) }
                            )
                            if (showDurationSort) {
                                SortTypeCard(
                                    label = "Duration", icon = R.drawable.lumora_clock_circle,
                                    isSelected = sortField == SortField.DURATION, primaryText = primaryText,
                                    secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                    isDark = isDark, modifier = Modifier.weight(1f),
                                    onClick = { onSortFieldChange(SortField.DURATION) }
                                )
                            }
                            SortTypeCard(
                                label = "Date", icon = R.drawable.lumora_calendar,
                                isSelected = sortField == SortField.DATE, primaryText = primaryText,
                                secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                isDark = isDark, modifier = Modifier.weight(1f),
                                onClick = { onSortFieldChange(SortField.DATE) }
                            )
                            if (showSizeSort) {
                                SortTypeCard(
                                    label = "Size", icon = R.drawable.lumora_size,
                                    isSelected = sortField == SortField.SIZE, primaryText = primaryText,
                                    secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                    isDark = isDark, modifier = Modifier.weight(1f),
                                    onClick = { onSortFieldChange(SortField.SIZE) }
                                )
                            }
                            if (showCountSort) {
                                SortTypeCard(
                                    label = "Count", icon = R.drawable.lumora_count,
                                    isSelected = sortField == SortField.COUNT, primaryText = primaryText,
                                    secondaryText = secondaryText, accentBlue = accentBlue, activeBg = activeTintBg,
                                    isDark = isDark, modifier = Modifier.weight(1f),
                                    onClick = { onSortFieldChange(SortField.COUNT) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2-Option Direction Pill Toggle (e.g. Smallest / Largest or A-Z / Z-A)
                    val (leftLabel, rightLabel, isLeftSelected) = when (sortField) {
                        SortField.TITLE -> Triple("A-Z", "Z-A", sortDirection == SortDirection.ASCENDING)
                        SortField.DURATION -> Triple("Longest", "Shortest", sortDirection == SortDirection.DESCENDING)
                        SortField.DATE -> Triple("Newest", "Oldest", sortDirection == SortDirection.DESCENDING)
                        SortField.SIZE -> Triple("Smallest", "Largest", sortDirection == SortDirection.ASCENDING)
                        SortField.COUNT -> Triple("Fewest", "Most", sortDirection == SortDirection.ASCENDING)
                    }

                    SortDirectionPillToggle(
                        leftLabel = leftLabel,
                        rightLabel = rightLabel,
                        isLeftSelected = isLeftSelected,
                        accentBlue = accentBlue,
                        activeBg = activeTintBg,
                        borderColor = pillBorderColor,
                        primaryText = primaryText,
                        onLeftClick = {
                            when (sortField) {
                                SortField.TITLE -> onSortDirectionChange(SortDirection.ASCENDING)
                                SortField.DURATION -> onSortDirectionChange(SortDirection.DESCENDING)
                                SortField.DATE -> onSortDirectionChange(SortDirection.DESCENDING)
                                SortField.SIZE -> onSortDirectionChange(SortDirection.ASCENDING)
                                SortField.COUNT -> onSortDirectionChange(SortDirection.ASCENDING)
                            }
                        },
                        onRightClick = {
                            when (sortField) {
                                SortField.TITLE -> onSortDirectionChange(SortDirection.DESCENDING)
                                SortField.DURATION -> onSortDirectionChange(SortDirection.ASCENDING)
                                SortField.DATE -> onSortDirectionChange(SortDirection.ASCENDING)
                                SortField.SIZE -> onSortDirectionChange(SortDirection.DESCENDING)
                                SortField.COUNT -> onSortDirectionChange(SortDirection.DESCENDING)
                            }
                        }
                    )

                    if (!isOnlineAudioFolder) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                            color = dividerColor,
                            thickness = 1.dp
                        )

                        // 2. SECTION: VIEW MODE
                        Text(
                            text = "View Mode",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        ViewModePillToggle(
                            selectedMode = viewMode,
                            accentBlue = accentBlue,
                            activeBg = activeTintBg,
                            borderColor = pillBorderColor,
                            primaryText = primaryText,
                            onSelectMode = onViewModeChange
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                        color = dividerColor,
                        thickness = 1.dp
                    )

                    // 3. SECTION: LAYOUT
                    Text(
                        text = "Layout",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LayoutPillToggle(
                        selectedLayout = layoutMode,
                        accentBlue = accentBlue,
                        activeBg = activeTintBg,
                        borderColor = pillBorderColor,
                        primaryText = primaryText,
                        onSelectLayout = onLayoutModeChange
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                        color = dividerColor,
                        thickness = 1.dp
                    )

                    // 4. SECTION: FIELDS (Expandable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isFieldsExpanded = !isFieldsExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fields",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        StyledIcon(
                                imageVector = if (isFieldsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (isFieldsExpanded) "Collapse Fields" else "Expand Fields",
                            tint = primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isFieldsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isOnlineAudioFolder) {
                                // ONLINE AUDIO FOLDER:
                                // Keep ONLY: Thumbnails, Duration, Date, Audio Type, Progress Bar (Extension is not offered online)
                                // COMPLETELY REMOVE: Size, Path, Subtitle Indicator, Framerate, Resolution, Video Count, New Badge, Full Name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FieldChip(
                                        label = "Thumbnails",
                                        isActive = visibleFields.showThumbnails,
                                        modifier = Modifier.weight(1.15f),
                                        onClick = { onVisibleFieldsChange(visibleFields.copy(showThumbnails = !visibleFields.showThumbnails)) }
                                    )
                                    FieldChip(
                                        label = "Duration",
                                        isActive = visibleFields.showDuration,
                                        modifier = Modifier.weight(1.0f),
                                        onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FieldChip(
                                        label = "Date",
                                        isActive = visibleFields.showDate,
                                        modifier = Modifier.weight(0.9f),
                                        onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                    )
                                    FieldChip(
                                        label = "Audio Type",
                                        isActive = visibleFields.showAudioType,
                                        modifier = Modifier.weight(1.15f),
                                        onClick = { onVisibleFieldsChange(visibleFields.copy(showAudioType = !visibleFields.showAudioType)) }
                                    )
                                    FieldChip(
                                        label = "Progress Bar",
                                        isActive = visibleFields.showProgressBar,
                                        modifier = Modifier.weight(1.35f),
                                        onClick = { onVisibleFieldsChange(visibleFields.copy(showProgressBar = !visibleFields.showProgressBar)) }
                                    )
                                }
                            } else if (isOnlinePlaylist) {
                                // SECTIONS 11 & 12: ONLINE PLAYLISTS
                                if (!isInsideFolder) {
                                    if (isMusicMode) {
                                        // 12. ONLINE PLAYLIST (AUDIO) - Root / Folder View
                                        // Fields: Total Media, Total Duration, Date
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Total Media",
                                                isActive = visibleFields.showVideoCount,
                                                modifier = Modifier.weight(1.1f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showVideoCount = !visibleFields.showVideoCount)) }
                                            )
                                            FieldChip(
                                                label = "Total Duration",
                                                isActive = visibleFields.showDuration,
                                                modifier = Modifier.weight(1.25f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                            )
                                            FieldChip(
                                                label = "Date",
                                                isActive = visibleFields.showDate,
                                                modifier = Modifier.weight(0.85f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                            )
                                        }
                                    } else {
                                        // 11. ONLINE PLAYLIST (VIDEO) - Root / Folder View
                                        // Fields: Total Media, Total Duration, Date, Resolution
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Total Media",
                                                isActive = visibleFields.showVideoCount,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showVideoCount = !visibleFields.showVideoCount)) }
                                            )
                                            FieldChip(
                                                label = "Total Duration",
                                                isActive = visibleFields.showDuration,
                                                modifier = Modifier.weight(1.2f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Date",
                                                isActive = visibleFields.showDate,
                                                modifier = Modifier.weight(0.85f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                            )
                                            FieldChip(
                                                label = "Resolution",
                                                isActive = visibleFields.showResolution,
                                                modifier = Modifier.weight(1.15f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showResolution = !visibleFields.showResolution)) }
                                            )
                                        }
                                    }
                                } else {
                                    if (isMusicMode) {
                                        // 12. ONLINE PLAYLIST (AUDIO) - Inside Playlist (Audio Files)
                                        // Fields: Thumbnails, Duration, Extension (lyrics), Date, Audio Type, Progress Bar, Path
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Thumbnails",
                                                isActive = visibleFields.showThumbnails,
                                                modifier = Modifier.weight(1.25f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showThumbnails = !visibleFields.showThumbnails)) }
                                            )
                                            FieldChip(
                                                label = "Duration",
                                                isActive = visibleFields.showDuration,
                                                modifier = Modifier.weight(1.05f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                            )
                                            FieldChip(
                                                label = "Extension",
                                                isActive = visibleFields.showExtension,
                                                modifier = Modifier.weight(1.05f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showExtension = !visibleFields.showExtension)) }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Date",
                                                isActive = visibleFields.showDate,
                                                modifier = Modifier.weight(0.9f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                            )
                                            FieldChip(
                                                label = "Audio Type",
                                                isActive = visibleFields.showAudioType,
                                                modifier = Modifier.weight(1.15f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showAudioType = !visibleFields.showAudioType)) }
                                            )
                                            FieldChip(
                                                label = "Progress Bar",
                                                isActive = visibleFields.showProgressBar,
                                                modifier = Modifier.weight(1.3f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showProgressBar = !visibleFields.showProgressBar)) }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Path",
                                                isActive = visibleFields.showPath,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showPath = !visibleFields.showPath)) }
                                            )
                                        }
                                    } else {
                                        // 11. ONLINE PLAYLIST (VIDEO) - Inside Playlist (Video Files)
                                        // Fields: Thumbnails, Duration, Framerate, Date, Resolution, Progress Bar
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Thumbnails",
                                                isActive = visibleFields.showThumbnails,
                                                modifier = Modifier.weight(1.25f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showThumbnails = !visibleFields.showThumbnails)) }
                                            )
                                            FieldChip(
                                                label = "Duration",
                                                isActive = visibleFields.showDuration,
                                                modifier = Modifier.weight(1.05f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                            )
                                            FieldChip(
                                                label = "Framerate",
                                                isActive = visibleFields.showFramerate,
                                                modifier = Modifier.weight(1.05f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showFramerate = !visibleFields.showFramerate)) }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FieldChip(
                                                label = "Date",
                                                isActive = visibleFields.showDate,
                                                modifier = Modifier.weight(0.85f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                            )
                                            FieldChip(
                                                label = "Resolution",
                                                isActive = visibleFields.showResolution,
                                                modifier = Modifier.weight(1.15f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showResolution = !visibleFields.showResolution)) }
                                            )
                                            FieldChip(
                                                label = "Progress Bar",
                                                isActive = visibleFields.showProgressBar,
                                                modifier = Modifier.weight(1.35f),
                                                onClick = { onVisibleFieldsChange(visibleFields.copy(showProgressBar = !visibleFields.showProgressBar)) }
                                            )
                                        }
                                    }
                                }
                            } else if (!isInsideFolder) {
                                // ROOT / FOLDER VIEWS:
                                // Section 1 (Home Video), Section 2 (Home Audio), Section 5 Root (Playlist Video),
                                // Section 6 Root (Playlist Audio), Section 8 (Online Audio), Recents Root
                                if (isMusicMode) {
                                    // SECTIONS 2, 6 (Root), 8: AUDIO FOLDERS / ROOT
                                    // Fields: Path, Total Media, Total Duration, Folder Size, Date
                                    // HATAO: Full Name, Resolution, New Badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Path",
                                            isActive = visibleFields.showPath,
                                            modifier = Modifier.weight(0.85f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showPath = !visibleFields.showPath)) }
                                        )
                                        FieldChip(
                                            label = "Total Media",
                                            isActive = visibleFields.showVideoCount,
                                            modifier = Modifier.weight(1.25f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showVideoCount = !visibleFields.showVideoCount)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Total Duration",
                                            isActive = visibleFields.showDuration,
                                            modifier = Modifier.weight(1.35f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                        )
                                        FieldChip(
                                            label = "Folder Size",
                                            isActive = visibleFields.showSize,
                                            modifier = Modifier.weight(1.15f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showSize = !visibleFields.showSize)) }
                                        )
                                        FieldChip(
                                            label = "Date",
                                            isActive = visibleFields.showDate,
                                            modifier = Modifier.weight(0.8f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                        )
                                    }
                                } else {
                                    // SECTIONS 1, 5 (Root), 7: VIDEO FOLDERS / ROOT (and Recents Root)
                                    // Fields: Path, Total Media, Total Duration, Folder Size, Date, Resolution
                                    // HATAO: Full Name, New Badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Path",
                                            isActive = visibleFields.showPath,
                                            modifier = Modifier.weight(0.85f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showPath = !visibleFields.showPath)) }
                                        )
                                        FieldChip(
                                            label = "Total Media",
                                            isActive = visibleFields.showVideoCount,
                                            modifier = Modifier.weight(1.25f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showVideoCount = !visibleFields.showVideoCount)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Total Duration",
                                            isActive = visibleFields.showDuration,
                                            modifier = Modifier.weight(1.35f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                        )
                                        FieldChip(
                                            label = "Folder Size",
                                            isActive = visibleFields.showSize,
                                            modifier = Modifier.weight(1.15f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showSize = !visibleFields.showSize)) }
                                        )
                                        FieldChip(
                                            label = "Date",
                                            isActive = visibleFields.showDate,
                                            modifier = Modifier.weight(0.8f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Resolution",
                                            isActive = visibleFields.showResolution,
                                            modifier = Modifier.weight(1.2f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showResolution = !visibleFields.showResolution)) }
                                        )
                                    }
                                }
                            } else {
                                // INSIDE FOLDER / ITEM VIEWS:
                                // Section 3 (Video Inside), Section 4 (Audio Inside), Section 5 Inside (Playlist Video),
                                // Section 6 Inside (Playlist Audio), Section 10 (Online Audio Inside), Recents Items
                                if (isMusicMode) {
                                    // SECTIONS 4, 6 (Inside), 10: AUDIO FILES
                                    // Fields: Thumbnails, Duration, Extension (lyrics), Date, Size, Audio Type (stereo etc), Progress Bar, Path
                                    // HATAO: Full Name
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Thumbnails",
                                            isActive = visibleFields.showThumbnails,
                                            modifier = Modifier.weight(1.25f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showThumbnails = !visibleFields.showThumbnails)) }
                                        )
                                        FieldChip(
                                            label = "Duration",
                                            isActive = visibleFields.showDuration,
                                            modifier = Modifier.weight(1.05f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                        )
                                        FieldChip(
                                            label = "Extension",
                                            isActive = visibleFields.showExtension,
                                            modifier = Modifier.weight(1.05f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showExtension = !visibleFields.showExtension)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Date",
                                            isActive = visibleFields.showDate,
                                            modifier = Modifier.weight(0.9f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                        )
                                        FieldChip(
                                            label = "Size",
                                            isActive = visibleFields.showSize,
                                            modifier = Modifier.weight(0.9f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showSize = !visibleFields.showSize)) }
                                        )
                                        FieldChip(
                                            label = "Audio Type",
                                            isActive = visibleFields.showAudioType,
                                            modifier = Modifier.weight(1.15f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showAudioType = !visibleFields.showAudioType)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Progress Bar",
                                            isActive = visibleFields.showProgressBar,
                                            modifier = Modifier.weight(1.3f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showProgressBar = !visibleFields.showProgressBar)) }
                                        )
                                        FieldChip(
                                            label = "Path",
                                            isActive = visibleFields.showPath,
                                            modifier = Modifier.weight(0.9f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showPath = !visibleFields.showPath)) }
                                        )
                                    }
                                } else {
                                    // SECTIONS 3, 5 (Inside), 9: VIDEO FILES (and Recents Items)
                                    // Fields: Thumbnails, Duration, Extension, Subtitle Indicator, Framerate, Date, Size, Resolution, Progress Bar, Path
                                    // HATAO: Full Name
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Thumbnails",
                                            isActive = visibleFields.showThumbnails,
                                            modifier = Modifier.weight(1.25f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showThumbnails = !visibleFields.showThumbnails)) }
                                        )
                                        FieldChip(
                                            label = "Duration",
                                            isActive = visibleFields.showDuration,
                                            modifier = Modifier.weight(1.05f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDuration = !visibleFields.showDuration)) }
                                        )
                                        FieldChip(
                                            label = "Extension",
                                            isActive = visibleFields.showExtension,
                                            modifier = Modifier.weight(1.05f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showExtension = !visibleFields.showExtension)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Subtitle Indicator",
                                            isActive = visibleFields.showSubtitleIndicator,
                                            modifier = Modifier.weight(1.55f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showSubtitleIndicator = !visibleFields.showSubtitleIndicator)) }
                                        )
                                        FieldChip(
                                            label = "Framerate",
                                            isActive = visibleFields.showFramerate,
                                            modifier = Modifier.weight(1.1f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showFramerate = !visibleFields.showFramerate)) }
                                        )
                                        FieldChip(
                                            label = "Date",
                                            isActive = visibleFields.showDate,
                                            modifier = Modifier.weight(0.8f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showDate = !visibleFields.showDate)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Size",
                                            isActive = visibleFields.showSize,
                                            modifier = Modifier.weight(0.9f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showSize = !visibleFields.showSize)) }
                                        )
                                        FieldChip(
                                            label = "Resolution",
                                            isActive = visibleFields.showResolution,
                                            modifier = Modifier.weight(1.15f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showResolution = !visibleFields.showResolution)) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FieldChip(
                                            label = "Progress Bar",
                                            isActive = visibleFields.showProgressBar,
                                            modifier = Modifier.weight(1.4f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showProgressBar = !visibleFields.showProgressBar)) }
                                        )
                                        FieldChip(
                                            label = "Path",
                                            isActive = visibleFields.showPath,
                                            modifier = Modifier.weight(0.9f),
                                            onClick = { onVisibleFieldsChange(visibleFields.copy(showPath = !visibleFields.showPath)) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Done Button placed cleanly on the bottom right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag("sort_done_button")
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGradient)
                                .clickable { onDismiss() }
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 14.5.sp,
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

/**
 * Top Card for Sort Field (Title, Date, Size)
 */
@Composable
private fun SortTypeCard(
    label: String,
    icon: Int,
    isSelected: Boolean,
    primaryText: Color,
    secondaryText: Color,
    accentBlue: Color,
    activeBg: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val inactiveBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.40f) else Color(0xFFF1F5F9).copy(alpha = 0.70f)
    val cardBorder = if (isSelected) Color(0x40FFFFFF) else if (isDark) Color(0xFF334155).copy(alpha = 0.4f) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected) Modifier.background(AccentGradient)
                else Modifier.background(inactiveBg)
            )
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StyledIcon(
                                drawableRes = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else primaryText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else primaryText
            )
        }
    }
}

/**
 * 2-Option Direction Pill (e.g. ^ Smallest | v Largest)
 */
@Composable
private fun SortDirectionPillToggle(
    leftLabel: String,
    rightLabel: String,
    isLeftSelected: Boolean,
    accentBlue: Color,
    activeBg: Color,
    borderColor: Color,
    primaryText: Color,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .border(1.2.dp, borderColor, shape)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left option
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (isLeftSelected) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable(onClick = onLeftClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StyledIcon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = null,
                    tint = if (isLeftSelected) Color.White else primaryText,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = leftLabel,
                    fontSize = 13.sp,
                    fontWeight = if (isLeftSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isLeftSelected) Color.White else primaryText
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = borderColor,
            thickness = 1.2.dp
        )

        // Right option
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (!isLeftSelected) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable(onClick = onRightClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StyledIcon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (!isLeftSelected) Color.White else primaryText,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = rightLabel,
                    fontSize = 13.sp,
                    fontWeight = if (!isLeftSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (!isLeftSelected) Color.White else primaryText
                )
            }
        }
    }
}

/**
 * 3-Option View Mode Pill (Folder, Tree, Library)
 */
@Composable
private fun ViewModePillToggle(
    selectedMode: ViewMode,
    accentBlue: Color,
    activeBg: Color,
    borderColor: Color,
    primaryText: Color,
    onSelectMode: (ViewMode) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .border(1.2.dp, borderColor, shape)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (selectedMode == ViewMode.FOLDER) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable { onSelectMode(ViewMode.FOLDER) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedMode == ViewMode.FOLDER) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "Folder",
                    fontSize = 13.sp,
                    fontWeight = if (selectedMode == ViewMode.FOLDER) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedMode == ViewMode.FOLDER) Color.White else primaryText
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = borderColor,
            thickness = 1.2.dp
        )

        // Tree
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (selectedMode == ViewMode.TREE) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable { onSelectMode(ViewMode.TREE) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedMode == ViewMode.TREE) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "Tree",
                    fontSize = 13.sp,
                    fontWeight = if (selectedMode == ViewMode.TREE) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedMode == ViewMode.TREE) Color.White else primaryText
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = borderColor,
            thickness = 1.2.dp
        )

        // Library
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (selectedMode == ViewMode.LIBRARY) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable { onSelectMode(ViewMode.LIBRARY) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedMode == ViewMode.LIBRARY) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "Library",
                    fontSize = 13.sp,
                    fontWeight = if (selectedMode == ViewMode.LIBRARY) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedMode == ViewMode.LIBRARY) Color.White else primaryText
                )
            }
        }
    }
}

/**
 * 2-Option Layout Pill (List | Grid)
 */
@Composable
private fun LayoutPillToggle(
    selectedLayout: LayoutMode,
    accentBlue: Color,
    activeBg: Color,
    borderColor: Color,
    primaryText: Color,
    onSelectLayout: (LayoutMode) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .border(1.2.dp, borderColor, shape)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (selectedLayout == LayoutMode.LIST) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable { onSelectLayout(LayoutMode.LIST) },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StyledIcon(
                                imageVector = Icons.Outlined.ViewList,
                    contentDescription = "List",
                    tint = if (selectedLayout == LayoutMode.LIST) Color.White else primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "List",
                    fontSize = 13.sp,
                    fontWeight = if (selectedLayout == LayoutMode.LIST) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedLayout == LayoutMode.LIST) Color.White else primaryText
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = borderColor,
            thickness = 1.2.dp
        )

        // Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (selectedLayout == LayoutMode.GRID) Modifier.background(AccentGradient)
                    else Modifier.background(Color.Transparent)
                )
                .clickable { onSelectLayout(LayoutMode.GRID) },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                StyledIcon(
                                imageVector = Icons.Outlined.GridView,
                    contentDescription = "Grid",
                    tint = if (selectedLayout == LayoutMode.GRID) Color.White else primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Grid",
                    fontSize = 13.sp,
                    fontWeight = if (selectedLayout == LayoutMode.GRID) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selectedLayout == LayoutMode.GRID) Color.White else primaryText
                )
            }
        }
    }
}

/**
 * Metadata Field Chip matching screenshot design with AccentGradient
 */
@Composable
private fun FieldChip(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .then(
                if (isActive) {
                    Modifier
                        .background(AccentGradient)
                        .border(1.dp, Color(0x40FFFFFF), shape)
                } else {
                    Modifier
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.40f))
                        .border(1.dp, colorScheme.outline.copy(alpha = 0.50f), shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color.White else colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
