package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.ui.state.ControlArea
import com.example.ui.state.LayoutOrientationMode
import com.example.ui.state.PlayerControlId
import com.example.ui.state.PlayerLayoutConfig
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary

private val ReorderSelectionGradient = Brush.linearGradient(
    listOf(
        Color(0xFF38BDF8),
        Color(0xFF818CF8),
        Color(0xFFC084FC),
        Color(0xFFF472B6)
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlLayoutEditor(
    config: PlayerLayoutConfig,
    onConfigChange: (PlayerLayoutConfig) -> Unit,
    onResetToDefault: () -> Unit,
    isDark: Boolean = false,
    transparencyPercent: Float = 85f,
    modifier: Modifier = Modifier
) {
    var selectedOrientation by remember { mutableStateOf(LayoutOrientationMode.PORTRAIT) }
    var selectedArea by remember { mutableStateOf(ControlArea.PORTRAIT_BOTTOM) }

    val primaryTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryTextColor = if (isDark) DarkTextSecondary else LightTextSecondary
    val alphaRatio = (transparencyPercent / 100f).coerceIn(0.15f, 1.0f)
    val cardBackground = if (isDark) {
        Color(0xFF1E293B).copy(alpha = (0.35f + 0.55f * alphaRatio).coerceIn(0.20f, 0.95f))
    } else {
        Color(0xFFF8FAFC).copy(alpha = (0.45f + 0.50f * alphaRatio).coerceIn(0.30f, 0.95f))
    }
    val cardBorder = if (isDark) Color.White.copy(alpha = (0.08f + 0.12f * alphaRatio).coerceIn(0.08f, 0.20f)) else Color.Black.copy(alpha = (0.05f + 0.10f * alphaRatio).coerceIn(0.06f, 0.18f))

    // Ensure selectedArea matches the selected orientation mode
    if (selectedArea.orientation != selectedOrientation) {
        selectedArea = when (selectedOrientation) {
            LayoutOrientationMode.PORTRAIT -> ControlArea.PORTRAIT_BOTTOM
            LayoutOrientationMode.LANDSCAPE -> ControlArea.TOP_RIGHT
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        // =====================================================================
        // 1. TOP SEGMENTED SWITCHER: [ 📱 Portrait Mode ] vs [ 🖥️ Landscape Mode ]
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.8f) else Color(0xFFE2E8F0))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LayoutOrientationMode.values().forEach { mode ->
                    val isSelected = selectedOrientation == mode
                    val totalControlsCount = when (mode) {
                        LayoutOrientationMode.PORTRAIT -> config.portraitTopControls.size + config.portraitBottomControls.size
                        LayoutOrientationMode.LANDSCAPE -> config.topRightControls.size + config.bottomLeftControls.size + config.bottomRightControls.size
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) AccentGradient
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable {
                                selectedOrientation = mode
                                selectedArea = when (mode) {
                                    LayoutOrientationMode.PORTRAIT -> ControlArea.PORTRAIT_BOTTOM
                                    LayoutOrientationMode.LANDSCAPE -> ControlArea.TOP_RIGHT
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (mode == LayoutOrientationMode.LANDSCAPE) {
                                StyledIcon(
                                    drawableRes = com.example.R.drawable.lumora_tablet,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                StyledIcon(
                                    imageVector = mode.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = mode.title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) Color.White else primaryTextColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =====================================================================
        // 2. SUB-AREA SELECTOR TABS (Portrait has 2, Landscape has 3)
        // =====================================================================
        val availableAreasForMode = ControlArea.values().filter { it.orientation == selectedOrientation }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableAreasForMode.forEach { area ->
                val isSelected = selectedArea == area
                val isEnabled = config.isAreaEnabled(area)

                val bgGradient = if (isSelected) {
                    AccentGradient
                } else {
                    Brush.linearGradient(
                        listOf(
                            if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                            if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgGradient)
                        .clickable { selectedArea = area }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = area.shortTitle,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else primaryTextColor
                        )

                        // Status Icon if hidden
                        if (!isEnabled) {
                            StyledIcon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = "Hidden",
                                tint = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFFEF4444),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =====================================================================
        // 3. MASTER SECTION TOGGLE (ON / OFF - GAYAB / SHOW) & RESET
        // =====================================================================
        val isCurrentAreaEnabled = config.isAreaEnabled(selectedArea)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBackground)
                .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrentAreaEnabled) AccentSkyBlue.copy(alpha = 0.18f)
                                else Color(0xFF64748B).copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                                imageVector = if (isCurrentAreaEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = if (isCurrentAreaEnabled) AccentSkyBlue else Color(0xFF94A3B8),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column {
                        Text(
                            text = selectedArea.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Text(
                            text = if (isCurrentAreaEnabled) "Enabled in player (Visible)" else "Hidden in player (Gayab)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrentAreaEnabled) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = isCurrentAreaEnabled,
                        onCheckedChange = { checked ->
                            onConfigChange(config.withAreaEnabled(selectedArea, checked))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentSkyBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Reset Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedArea.description.uppercase(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onResetToDefault,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentSkyBlue
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset All",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 4. ACTIVE CONTROLS IN SELECTED AREA
        // =====================================================================
        val activeControls = config.getControlsForArea(selectedArea)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBackground)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (activeControls.isEmpty()) {
                Text(
                    text = "No tools active in this section. Tap '+' on available tools below to add.",
                    fontSize = 12.5.sp,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            } else {
                ReorderableControlList(
                    area = selectedArea,
                    items = activeControls,
                    allAreasInMode = availableAreasForMode,
                    isDark = isDark,
                    onReorder = { newItems ->
                        onConfigChange(config.withAreaUpdated(selectedArea, newItems))
                    },
                    onRemove = { controlId ->
                        val updated = activeControls.filter { it != controlId }
                        onConfigChange(config.withAreaUpdated(selectedArea, updated))
                    },
                    onTransferToArea = { controlId, targetArea ->
                        // Remove from current area
                        val updatedSource = activeControls.filter { it != controlId }
                        val intermediateConfig = config.withAreaUpdated(selectedArea, updatedSource)
                        
                        // Add to target area
                        val targetList = intermediateConfig.getControlsForArea(targetArea).toMutableList()
                        val isTargetTop = targetArea == ControlArea.TOP_RIGHT || targetArea == ControlArea.PORTRAIT_TOP
                        if (isTargetTop) {
                            val moreIdx = targetList.indexOf(PlayerControlId.MORE_OPTIONS)
                            if (moreIdx >= 0) {
                                targetList.add(moreIdx, controlId)
                            } else {
                                targetList.add(controlId)
                            }
                        } else {
                            targetList.add(controlId)
                        }
                        onConfigChange(intermediateConfig.withAreaUpdated(targetArea, targetList))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // =====================================================================
        // 5. AVAILABLE CONTROLS POOL (FILTERED FOR CURRENT ORIENTATION)
        // =====================================================================
        val availableControls = config.getAvailableControls(selectedOrientation)

        Text(
            text = "AVAILABLE TOOLS (TAP '+' TO ADD TO ${selectedArea.shortTitle.uppercase()})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryTextColor,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBackground)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (availableControls.isEmpty()) {
                Text(
                    text = "All tools are active in your ${selectedOrientation.shortTitle.lowercase()} layout.",
                    fontSize = 12.5.sp,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableControls.forEach { controlId ->
                        AvailableControlPill(
                            control = controlId,
                            isDark = isDark,
                            onAdd = {
                                val currentList = config.getControlsForArea(selectedArea).toMutableList()
                                val isTopArea = selectedArea == ControlArea.TOP_RIGHT || selectedArea == ControlArea.PORTRAIT_TOP
                                if (isTopArea) {
                                    val moreIdx = currentList.indexOf(PlayerControlId.MORE_OPTIONS)
                                    if (moreIdx >= 0) {
                                        currentList.add(moreIdx, controlId)
                                    } else {
                                        currentList.add(controlId)
                                    }
                                } else {
                                    currentList.add(controlId)
                                }
                                onConfigChange(config.withAreaUpdated(selectedArea, currentList))
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders active items inside an area with touch & hold (long-press) continuous free drag reordering,
 * section transfer (⇄), and removal (-).
 * Supports uninterrupted dragging across the entire list, smooth spring layout animations,
 * and strictly locks More Options to the last position.
 */
@Composable
private fun ReorderableControlList(
    area: ControlArea,
    items: List<PlayerControlId>,
    allAreasInMode: List<ControlArea>,
    isDark: Boolean,
    onReorder: (List<PlayerControlId>) -> Unit,
    onRemove: (PlayerControlId) -> Unit,
    onTransferToArea: (PlayerControlId, ControlArea) -> Unit
) {
    val primaryTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryTextColor = if (isDark) DarkTextSecondary else LightTextSecondary
    val otherAreas = allAreasInMode.filter { it != area }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val currentItemsState by rememberUpdatedState(items)
    val onReorderState by rememberUpdatedState(onReorder)

    var draggingId by remember { mutableStateOf<PlayerControlId?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredItemHeightPx by remember { mutableFloatStateOf(0f) }

    val effectiveHeight = if (measuredItemHeightPx > 0f) {
        measuredItemHeightPx
    } else {
        with(density) { 56.dp.toPx() }
    }

    val isTopArea = area == ControlArea.TOP_RIGHT || area == ControlArea.PORTRAIT_TOP
    val hasMoreOptions = isTopArea && items.contains(PlayerControlId.MORE_OPTIONS)
    val maxMovableIndex = if (hasMoreOptions) {
        (items.indexOf(PlayerControlId.MORE_OPTIONS) - 1).coerceAtLeast(0)
    } else {
        (items.size - 1).coerceAtLeast(0)
    }

    val fromIndex = draggingId?.let { id -> items.indexOf(id) } ?: -1
    val hoverIndex = if (fromIndex != -1 && effectiveHeight > 0f) {
        val steps = (dragOffsetY / effectiveHeight).roundToInt()
        (fromIndex + steps).coerceIn(0, maxMovableIndex)
    } else {
        -1
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { index, controlId ->
            val isPermanent = controlId.isPermanent || controlId == PlayerControlId.MORE_OPTIONS
            val isBeingDragged = (draggingId == controlId)
            var showTransferMenu by remember { mutableStateOf(false) }

            val targetTranslationY = when {
                isBeingDragged -> 0f // applied directly via graphicsLayer translationY
                draggingId != null && fromIndex != -1 && hoverIndex != -1 -> {
                    when {
                        fromIndex < hoverIndex && index in (fromIndex + 1)..hoverIndex -> -effectiveHeight
                        fromIndex > hoverIndex && index in hoverIndex until fromIndex -> effectiveHeight
                        else -> 0f
                    }
                }
                else -> 0f
            }

            val animatedTranslationY by animateFloatAsState(
                targetValue = targetTranslationY,
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = 800f
                ),
                label = "ItemTranslationY_$controlId"
            )

            val visualTranslationY = if (isBeingDragged) dragOffsetY else animatedTranslationY

            val itemBg = if (isDark) {
                if (isBeingDragged) Color(0xFF1E293B) else Color(0xFF334155).copy(alpha = 0.75f)
            } else {
                if (isBeingDragged) Color(0xFFF1F5F9) else Color.White
            }

            val itemBorderModifier = if (isBeingDragged) {
                Modifier.border(2.dp, ReorderSelectionGradient, RoundedCornerShape(12.dp))
            } else {
                val normalBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                Modifier.border(1.dp, normalBorder, RoundedCornerShape(12.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isBeingDragged) 99f else 1f)
                    .graphicsLayer {
                        translationY = visualTranslationY
                        if (isBeingDragged) {
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 18.dp.toPx()
                            shape = RoundedCornerShape(12.dp)
                            clip = false
                        }
                    }
                    .then(if (isBeingDragged) Modifier.shadow(18.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(itemBg)
                    .then(itemBorderModifier)
                    .onGloballyPositioned { coordinates ->
                        if (draggingId == null && coordinates.size.height > 0) {
                            measuredItemHeightPx = coordinates.size.height.toFloat() + with(density) { 6.dp.toPx() }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Draggable Area: Touch & hold drag gesture attached here
                    val dragGestureModifier = if (!isPermanent) {
                        Modifier.pointerInput(controlId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } catch (_: Throwable) {}
                                    draggingId = controlId
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingId == controlId) {
                                        dragOffsetY += dragAmount.y
                                    }
                                },
                                onDragEnd = {
                                    val currentList = currentItemsState
                                    val fIdx = currentList.indexOf(controlId)
                                    val effH = if (measuredItemHeightPx > 0f) measuredItemHeightPx else with(density) { 56.dp.toPx() }
                                    if (fIdx != -1 && effH > 0f) {
                                        val steps = (dragOffsetY / effH).roundToInt()
                                        val tIdx = (fIdx + steps).coerceIn(0, maxMovableIndex)
                                        if (tIdx != fIdx) {
                                            val mutable = currentList.toMutableList()
                                            val item = mutable.removeAt(fIdx)
                                            mutable.add(tIdx, item)
                                            onReorderState(mutable.toList())
                                        }
                                    }
                                    draggingId = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffsetY = 0f
                                }
                            )
                        }
                    } else {
                        Modifier
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .then(dragGestureModifier)
                    ) {
                        if (isPermanent) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Permanent Item",
                                tint = secondaryTextColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            // Touch & hold drag indicator icon (No numbers)
                            StyledIcon(
                                imageVector = Icons.Outlined.DragIndicator,
                                contentDescription = "Touch and hold to drag",
                                tint = if (isBeingDragged) AccentSkyBlue else secondaryTextColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Control Icon Container
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AccentSkyBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (controlId.lumoraDrawableRes != null) {
                                StyledIcon(
                                    drawableRes = controlId.lumoraDrawableRes,
                                    contentDescription = controlId.displayName,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            } else {
                                StyledIcon(
                                    imageVector = controlId.icon,
                                    contentDescription = controlId.displayName,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = controlId.displayName,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isPermanent) {
                                Text(
                                    text = "Fixed (Last Position)",
                                    fontSize = 10.5.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }

                    // Right: Remove Action (Red Minus Pill - Instant, responsive removal)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isPermanent) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = androidx.compose.material3.ripple(bounded = true, radius = 18.dp)
                                    ) {
                                        if (draggingId == controlId) {
                                            draggingId = null
                                            dragOffsetY = 0f
                                        }
                                        onRemove(controlId)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.Remove,
                                    contentDescription = "Remove Control",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(17.dp)
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
 * A pill representing an available control that can be added into the current area with a '+' tap.
 */
@Composable
private fun AvailableControlPill(
    control: PlayerControlId,
    isDark: Boolean,
    onAdd: () -> Unit
) {
    val primaryTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
    val itemBg = if (isDark) Color(0xFF334155) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(itemBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onAdd() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Control Icon
            if (control.lumoraDrawableRes != null) {
                StyledIcon(
                    drawableRes = control.lumoraDrawableRes,
                    contentDescription = null,
                    tint = AccentSkyBlue,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                StyledIcon(
                    imageVector = control.icon,
                    contentDescription = null,
                    tint = AccentSkyBlue,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = control.displayName,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            // Green/Blue Plus Add Badge
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                                imageVector = Icons.Outlined.Add,
                    contentDescription = "Add",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
