package com.example.ui.components

import com.example.R

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerMediaTrack
import com.example.player.SubtitleFontManager
import com.example.ui.state.PlayerSettings
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentPink
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Whether [track] is a genuinely native ASS/SSA subtitle, as opposed to another text format
 * (SRT/VTT/SAMI/MicroDVD/...) that gets converted to an .ass file purely so libass can render
 * it. "Override ASS/SSA Styles" and "Advanced ASS/SSA" only make sense for a real ASS/SSA
 * track that already carries its own embedded styling to override or edit — showing them for
 * every subtitle format was confusing (and, worse, made the override toggle look like it was
 * required for plain-text subtitles to pick up any styling at all). For external tracks the
 * original file extension (still known even after conversion) is authoritative; for embedded
 * tracks we fall back to the container-reported codec.
 */
private fun isNativeAssSsaTrack(track: PlayerMediaTrack?): Boolean {
    if (track == null) return false
    val ext = track.originalFilename.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    if (ext.isNotBlank()) return ext == "ass" || ext == "ssa"
    val codec = track.codec.lowercase(java.util.Locale.ROOT)
    return codec == "ass" || codec == "ssa"
}

/**
 * Whether [track] is an image/bitmap-based subtitle (PGS/SUP, VobSub, DVB, CEA-608/708,
 * ARIB B24, BDN, ...). These carry no editable text at all, so any "edit this subtitle" affordance
 * should be hidden for them rather than opening an editor with nothing it can do.
 */
fun isImageBasedSubtitleTrack(track: PlayerMediaTrack?): Boolean {
    if (track == null) return false
    val codec = track.codec.lowercase(java.util.Locale.ROOT)
    return codec in setOf(
        "hdmv_pgs_subtitle", "pgssub", "dvd_subtitle", "vobsub", "dvb_subtitle", "dvbsub",
        "xsub", "eia_608", "cea608", "eia_708", "cea708", "arib_caption"
    )
}

enum class SubtitleStyleAccordionSection {
    TYPOGRAPHY,
    COLORS,
    MISCELLANEOUS,
    ADVANCED_ASS
}

enum class ColorTarget {
    TEXT,
    BORDER,
    BACKGROUND,
    SHADOW
}

enum class TypographySubTab {
    BORDER,
    BACKGROUND,
    SHADOW,
    FONT
}

private enum class ArcPickerType {
    HUE,
    SATURATION,
    VALUE
}

/**
 * Enhanced, Highly-Polished Subtitle Style Editor Panel
 *
 * Implements:
 * 1. Image 2 Style Typography Row (Single horizontal row with gradient selected style, direct Font Size slider,
 *    Border/Background/Shadow/Font tabs, and Letter/Word spacing slider).
 * 2. Image 1 3-Arc Circular Color Picker with 3 interactive curved arc sliders (full 0°–360° Hue rainbow spectrum, Saturation, Brightness/Value)
 *    and Center Preview circle.
 * 3. Functional state updates guaranteeing all subtitle settings remain independent and persistent without resetting each other.
 * 4. Full-featured, detailed Miscellaneous section.
 */
@Composable
fun SubtitleStylePanel(
    settings: PlayerSettings,
    context: android.content.Context,
    isLandscape: Boolean,
    glassBlurTransparency: Float,
    activeSubtitleTrack: PlayerMediaTrack? = null,
    onSettingsChange: (PlayerSettings) -> Unit,
    onDismissRequest: () -> Unit,
    onBackToSubtitleTracks: () -> Unit = {},
    onLoadAdvancedAssSource: (suspend (Int) -> com.example.player.AdvancedAssSource?)? = null
) {
    var activeSection by remember { mutableStateOf<SubtitleStyleAccordionSection?>(SubtitleStyleAccordionSection.TYPOGRAPHY) }

    val currentSettings by rememberUpdatedState(settings)
    val onSettingsChangeUpdated by rememberUpdatedState(onSettingsChange)

    val updateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit = remember {
        { transform ->
            val updated = transform(currentSettings)
            onSettingsChangeUpdated(updated)
        }
    }

    PlayerGlassModalSheet(
        panelKey = "subtitle_delay",
        isLandscape = isLandscape,
        openOnRightSide = true,
        heightFraction = if (isLandscape) 0.88f else 0.78f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Subtitle Style",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalSheetNormalTextColor.current
                    )
                    if (activeSubtitleTrack != null) {
                        val trackLabel = activeSubtitleTrack.title.ifBlank { "Track ${activeSubtitleTrack.id}" }
                        Text(
                            text = "Target: $trackLabel",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        headerEndContent = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBackToSubtitleTracks() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Tracks",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // =========================================================================
            // 1. SECTION: TYPOGRAPHY
            // =========================================================================
            AccordionSectionCard(
                title = "Typography",
                icon = Icons.Outlined.TextFields,
                expanded = activeSection == SubtitleStyleAccordionSection.TYPOGRAPHY,
                onToggle = {
                    activeSection = if (activeSection == SubtitleStyleAccordionSection.TYPOGRAPHY) null else SubtitleStyleAccordionSection.TYPOGRAPHY
                }
            ) {
                EnhancedTypographyContent(
                    settings = currentSettings,
                    context = context,
                    onUpdateSettings = updateSettings
                )
            }

            // =========================================================================
            // 2. SECTION: COLORS
            // =========================================================================
            AccordionSectionCard(
                title = "Colors",
                icon = Icons.Outlined.ColorLens,
                expanded = activeSection == SubtitleStyleAccordionSection.COLORS,
                onToggle = {
                    activeSection = if (activeSection == SubtitleStyleAccordionSection.COLORS) null else SubtitleStyleAccordionSection.COLORS
                }
            ) {
                EnhancedColorsContent(
                    settings = currentSettings,
                    onUpdateSettings = updateSettings
                )
            }

            // =========================================================================
            // 3. SECTION: MISCELLANEOUS
            // =========================================================================
            AccordionSectionCard(
                title = "Miscellaneous",
                icon = Icons.Outlined.Tune,
                expanded = activeSection == SubtitleStyleAccordionSection.MISCELLANEOUS,
                onToggle = {
                    activeSection = if (activeSection == SubtitleStyleAccordionSection.MISCELLANEOUS) null else SubtitleStyleAccordionSection.MISCELLANEOUS
                }
            ) {
                EnhancedMiscContent(
                    settings = currentSettings,
                    isAssSsaTrack = isNativeAssSsaTrack(activeSubtitleTrack),
                    onUpdateSettings = updateSettings
                )
            }

            // =========================================================================
            // 4. SECTION: ADVANCED ASS/SSA (only when the toggle in Miscellaneous is ON,
            // and only for a genuinely native ASS/SSA track — per-style editing has nothing
            // to act on for any other subtitle format)
            // =========================================================================
            if (currentSettings.advancedAssEnabled && isNativeAssSsaTrack(activeSubtitleTrack)) {
                AccordionSectionCard(
                    title = "Advanced ASS/SSA",
                    icon = Icons.Outlined.Palette,
                    expanded = activeSection == SubtitleStyleAccordionSection.ADVANCED_ASS,
                    onToggle = {
                        activeSection = if (activeSection == SubtitleStyleAccordionSection.ADVANCED_ASS) null else SubtitleStyleAccordionSection.ADVANCED_ASS
                    }
                ) {
                    AdvancedAssStyleEditorContent(
                        settings = currentSettings,
                        context = context,
                        activeSubtitleTrack = activeSubtitleTrack,
                        onLoadAdvancedAssSource = onLoadAdvancedAssSource,
                        onUpdateSettings = updateSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// =============================================================================
// ACCORDION SECTION CONTAINER
// =============================================================================
@Composable
private fun AccordionSectionCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SheetButtonBgColor)
            .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledIcon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = SheetButtonTextColor
            )
            StyledIcon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(19.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

// =============================================================================
// 1. ENHANCED TYPOGRAPHY SECTION (Image 2 style)
// =============================================================================
@Composable
private fun EnhancedTypographyContent(
    settings: PlayerSettings,
    context: android.content.Context,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit
) {
    var selectedSubTab by remember { mutableStateOf<TypographySubTab>(TypographySubTab.BORDER) }

    val installedFonts = remember(settings.subtitleFontsReloadNonce, settings.showVideoEmbeddedSubtitleFonts) {
        SubtitleFontManager.getInstalledFonts(
            context,
            includeVideoExtracted = settings.showVideoEmbeddedSubtitleFonts
        )
    }

    // -------------------------------------------------------------------------
    // Top Row: [ B ]  [ I ]  [ ≡ ]  [ ≣ ]  [ ▤ ]  [ \T Reset ]
    // Single horizontal scrollable row with gradient active style
    // -------------------------------------------------------------------------
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // [ B ] Bold Toggle
        GradientIconPill(
            label = "B",
            icon = Icons.Filled.FormatBold,
            isSelected = settings.subtitleBold,
            contentDescription = "Bold",
            onClick = {
                onUpdateSettings { it.copy(subtitleBold = !it.subtitleBold) }
            }
        )

        // [ I ] Italic Toggle
        GradientIconPill(
            label = "I",
            icon = Icons.Filled.FormatItalic,
            isSelected = settings.subtitleItalic,
            contentDescription = "Italic",
            onClick = {
                onUpdateSettings { it.copy(subtitleItalic = !it.subtitleItalic) }
            }
        )

        // [ U ] Underline Toggle
        GradientIconPill(
            label = "U",
            icon = null,
            drawableRes = R.drawable.lumora_text_underline,
            isSelected = settings.subtitleUnderline,
            contentDescription = "Underline",
            onClick = {
                onUpdateSettings { it.copy(subtitleUnderline = !it.subtitleUnderline) }
            }
        )

        // [ ≡ ] Left Align
        GradientIconPill(
            label = "≡",
            icon = null,
            drawableRes = R.drawable.lumora_align_left,
            isSelected = settings.subtitleAlignment.equals("left", ignoreCase = true),
            contentDescription = "Align Left",
            onClick = {
                onUpdateSettings { it.copy(subtitleAlignment = "left") }
            }
        )

        // [ ≣ ] Center Align
        GradientIconPill(
            label = "≣",
            icon = Icons.Filled.FormatAlignCenter,
            isSelected = settings.subtitleAlignment.equals("center", ignoreCase = true) || settings.subtitleAlignment.isBlank(),
            contentDescription = "Align Center",
            onClick = {
                onUpdateSettings { it.copy(subtitleAlignment = "center") }
            }
        )

        // [ ▤ ] Right Align
        GradientIconPill(
            label = "▤",
            icon = null,
            drawableRes = R.drawable.lumora_align_right,
            isSelected = settings.subtitleAlignment.equals("right", ignoreCase = true),
            contentDescription = "Align Right",
            onClick = {
                onUpdateSettings { it.copy(subtitleAlignment = "right") }
            }
        )

        // [ \T Reset ] Reset Typography Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(10.dp))
                .clickable {
                    onUpdateSettings {
                        it.copy(
                            subtitleBold = true,
                            subtitleItalic = false,
                            subtitleUnderline = false,
                            subtitleAlignment = "center",
                            subtitleFontSize = 52f,
                            subtitleBorderStyle = "outline_shadow",
                            subtitleBorderSize = 3f,
                            subtitleShadowOffset = 1.5f,
                            subtitleShadowOffsetX = 2f,
                            subtitleShadowOffsetY = 2f,
                            subtitleShadowBlur = 4f,
                            subtitleBackgroundPadding = 8f,
                            subtitleBackgroundCornerRadius = 8f,
                            subtitleLetterSpacing = 0f,
                            selectedSubtitleFont = ""
                        )
                    }
                }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                StyledIcon(
                    imageVector = Icons.Filled.FormatColorReset,
                    contentDescription = "Reset Typography",
                    tint = SheetButtonTextColor,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Reset",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetButtonTextColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // -------------------------------------------------------------------------
    // Font Size Direct Slider with Title Above
    // -------------------------------------------------------------------------
    FullSliderCard(
        title = "Font Size",
        value = settings.subtitleFontSize,
        unit = "sp",
        min = 12f,
        max = 110f,
        step = 1f,
        format = "%.0f",
        onChange = { newSize ->
            onUpdateSettings { it.copy(subtitleFontSize = newSize) }
        }
    )

    // -------------------------------------------------------------------------
    // 4 Category Tabs: [ Border ]  [ Background ]  [ Shadow ]  [ Font ]
    // -------------------------------------------------------------------------
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TypographySubTab.entries.forEach { tab ->
            val isSelected = selectedSubTab == tab
            val tabTitle = when (tab) {
                TypographySubTab.BORDER -> "Border"
                TypographySubTab.BACKGROUND -> "Background"
                TypographySubTab.SHADOW -> "Shadow"
                TypographySubTab.FONT -> "Font"
            }

            val gradientBrush = if (isSelected) {
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                )
            } else null

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (gradientBrush != null) Modifier.background(gradientBrush)
                        else Modifier.background(SheetButtonBgColor)
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else SheetButtonBorderColor,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { selectedSubTab = tab }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabTitle,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else SheetButtonTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Selected Category Sliders & Controls
    // -------------------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SheetButtonBgColor)
            .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        when (selectedSubTab) {
            // 1. BORDER SUB-TAB
            TypographySubTab.BORDER -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Border Style Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "none" to "None",
                            "outline" to "Outline",
                            "shadow" to "Shadow",
                            "outline_shadow" to "Outline+Shadow"
                        ).forEach { (styleKey, styleName) ->
                            val isStyleSelected = settings.subtitleBorderStyle == styleKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isStyleSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (isStyleSelected) MaterialTheme.colorScheme.primary else SheetButtonBorderColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onUpdateSettings { it.copy(subtitleBorderStyle = styleKey) }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = styleName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isStyleSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isStyleSelected) MaterialTheme.colorScheme.primary else SheetButtonTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Border Size Slider
                    FullSliderCard(
                        title = "Border Size",
                        value = settings.subtitleBorderSize,
                        unit = "pt",
                        min = 0f,
                        max = 20f,
                        step = 0.5f,
                        format = "%.1f",
                        onChange = { newSize ->
                            onUpdateSettings { it.copy(subtitleBorderSize = newSize) }
                        }
                    )
                }
            }

            // 2. BACKGROUND SUB-TAB (2 Sliders: Size/Padding + Corner Radius)
            TypographySubTab.BACKGROUND -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FullSliderCard(
                        title = "Background Padding / Size",
                        value = settings.subtitleBackgroundPadding,
                        unit = "dp",
                        min = 0f,
                        max = 30f,
                        step = 1f,
                        format = "%.0f",
                        onChange = { newPadding ->
                            onUpdateSettings { it.copy(subtitleBackgroundPadding = newPadding) }
                        }
                    )

                    FullSliderCard(
                        title = "Background Corner Radius",
                        value = settings.subtitleBackgroundCornerRadius,
                        unit = "dp",
                        min = 0f,
                        max = 30f,
                        step = 1f,
                        format = "%.0f",
                        onChange = { newRadius ->
                            onUpdateSettings { it.copy(subtitleBackgroundCornerRadius = newRadius) }
                        }
                    )
                }
            }

            // 3. SHADOW SUB-TAB (3 Sliders: Shadow Blur/Size + X-Offset + Y-Offset)
            TypographySubTab.SHADOW -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FullSliderCard(
                        title = "Shadow Blur",
                        value = settings.subtitleShadowBlur,
                        unit = "px",
                        min = 0f,
                        max = 20f,
                        step = 0.5f,
                        format = "%.1f",
                        onChange = { newBlur ->
                            onUpdateSettings { it.copy(subtitleShadowBlur = newBlur) }
                        }
                    )

                    FullSliderCard(
                        title = "Shadow Offset / Size",
                        value = settings.subtitleShadowOffset,
                        unit = "pt",
                        min = 0f,
                        max = 20f,
                        step = 0.5f,
                        format = "%.1f",
                        onChange = { newOffset ->
                            onUpdateSettings { it.copy(subtitleShadowOffset = newOffset) }
                        }
                    )

                    FullSliderCard(
                        title = "Shadow Horizontal (Left ↔ Right)",
                        value = settings.subtitleShadowOffsetX,
                        unit = "pt",
                        min = -15f,
                        max = 15f,
                        step = 0.5f,
                        format = "%+.1f",
                        onChange = { newX ->
                            onUpdateSettings { it.copy(subtitleShadowOffsetX = newX) }
                        }
                    )

                    FullSliderCard(
                        title = "Shadow Vertical (Up ↕ Down)",
                        value = settings.subtitleShadowOffsetY,
                        unit = "pt",
                        min = -15f,
                        max = 15f,
                        step = 0.5f,
                        format = "%+.1f",
                        onChange = { newY ->
                            onUpdateSettings { it.copy(subtitleShadowOffsetY = newY) }
                        }
                    )
                }
            }

            // 4. FONT SUB-TAB
            TypographySubTab.FONT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // System default font
                    FontItemRow(
                        name = "Default (System Font)",
                        isSelected = settings.selectedSubtitleFont.isBlank(),
                        onSelect = {
                            onUpdateSettings { it.copy(selectedSubtitleFont = "") }
                        }
                    )

                    installedFonts.forEach { fontFile ->
                        val originalName = SubtitleFontManager.getOriginalFontName(fontFile)
                        val isSelected = settings.selectedSubtitleFont.equals(fontFile.name, ignoreCase = true) ||
                                settings.selectedSubtitleFont.equals(fontFile.nameWithoutExtension, ignoreCase = true) ||
                                settings.selectedSubtitleFont.equals(originalName, ignoreCase = true)
                        FontItemRow(
                            name = originalName,
                            isSelected = isSelected,
                            onSelect = {
                                onUpdateSettings { it.copy(selectedSubtitleFont = fontFile.name) }
                            }
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Word / Character Spacing Slider
    // -------------------------------------------------------------------------
    FullSliderCard(
        title = "Character & Word Spacing",
        value = settings.subtitleLetterSpacing,
        unit = "pt",
        min = -5f,
        max = 20f,
        step = 0.5f,
        format = "%+.1f",
        onChange = { newSpacing ->
            onUpdateSettings { it.copy(subtitleLetterSpacing = newSpacing) }
        }
    )
}

// =============================================================================
// GRADIENT ICON PILL (Image 2 style)
// =============================================================================
@Composable
private fun GradientIconPill(
    label: String,
    icon: ImageVector?,
    isSelected: Boolean,
    contentDescription: String?,
    onClick: () -> Unit,
    drawableRes: Int? = null
) {
    val gradientBrush = if (isSelected) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            )
        )
    } else null

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (gradientBrush != null) Modifier.background(gradientBrush)
                else Modifier.background(SheetButtonBgColor)
            )
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else SheetButtonBorderColor,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            StyledIcon(
                drawableRes = drawableRes,
                contentDescription = contentDescription,
                tint = if (isSelected) Color.White else SheetButtonTextColor,
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            StyledIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isSelected) Color.White else SheetButtonTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// =============================================================================
// FULL SLIDER CARD WITH +/- BUTTONS
// =============================================================================
@Composable
private fun FullSliderCard(
    title: String,
    value: Float,
    unit: String = "",
    min: Float,
    max: Float,
    step: Float,
    format: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = SheetButtonTextColor
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${String.format(Locale.US, format, value)} $unit".trim(),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(SheetButtonBgColor)
                    .border(1.dp, SheetButtonBorderColor, CircleShape)
                    .clickable(enabled = value > min) {
                        onChange((value - step).coerceAtLeast(min))
                    },
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease",
                    tint = if (value > min) SheetButtonTextColor else Color(0xFF94A3B8),
                    modifier = Modifier.size(15.dp)
                )
            }

            Slider(
                value = value.coerceIn(min, max),
                onValueChange = onChange,
                valueRange = min..max,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AccentPink,
                    activeTrackColor = AccentSkyBlue,
                    inactiveTrackColor = SheetButtonBorderColor
                )
            )

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(SheetButtonBgColor)
                    .border(1.dp, SheetButtonBorderColor, CircleShape)
                    .clickable(enabled = value < max) {
                        onChange((value + step).coerceAtMost(max))
                    },
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase",
                    tint = if (value < max) SheetButtonTextColor else Color(0xFF94A3B8),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// =============================================================================
// 2. ENHANCED COLORS SECTION WITH 3-ARC CIRCULAR COLOR PICKER (Image 1 style)
// =============================================================================
@Composable
private fun EnhancedColorsContent(
    settings: PlayerSettings,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit
) {
    var activeColorTarget by remember { mutableStateOf<ColorTarget>(ColorTarget.TEXT) }

    // 4 Color Swatches: [Text] [Border] [Background] [Shadow] + [Reset Colors]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ColorTarget.entries.forEach { target ->
            val isSelected = activeColorTarget == target
            val targetLabel = when (target) {
                ColorTarget.TEXT -> "Text"
                ColorTarget.BORDER -> "Border"
                ColorTarget.BACKGROUND -> "Bg"
                ColorTarget.SHADOW -> "Shadow"
            }
            val targetColorLong = when (target) {
                ColorTarget.TEXT -> settings.subtitleTextColor
                ColorTarget.BORDER -> settings.subtitleBorderColor
                ColorTarget.BACKGROUND -> settings.subtitleBackgroundColor
                ColorTarget.SHADOW -> settings.subtitleShadowColor
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        else SheetButtonBgColor
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else SheetButtonBorderColor,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { activeColorTarget = target }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                            .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(targetColorLong.toInt()))
                        )
                    }
                    Text(
                        text = targetLabel,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else SheetButtonTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Reset Colors Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(10.dp))
                .clickable {
                    onUpdateSettings {
                        it.copy(
                            subtitleTextColor = 0xFFFFFFFFL,
                            subtitleBorderColor = 0xFF000000L,
                            subtitleBackgroundColor = 0x99000000L,
                            subtitleShadowColor = 0xBF000000L
                        )
                    }
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            StyledIcon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = "Reset Colors",
                tint = SheetButtonTextColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // -------------------------------------------------------------------------
    // 3-Arc Circular Interactive Color Picker Card (Exact Image 1 Design)
    // -------------------------------------------------------------------------
    val currentColorLong = when (activeColorTarget) {
        ColorTarget.TEXT -> settings.subtitleTextColor
        ColorTarget.BORDER -> settings.subtitleBorderColor
        ColorTarget.BACKGROUND -> settings.subtitleBackgroundColor
        ColorTarget.SHADOW -> settings.subtitleShadowColor
    }

    val activeTargetTitle = when (activeColorTarget) {
        ColorTarget.TEXT -> "Text Color"
        ColorTarget.BORDER -> "Border Color"
        ColorTarget.BACKGROUND -> "Background Color"
        ColorTarget.SHADOW -> "Shadow Color"
    }

    key(activeColorTarget) {
        ThreeArcCircularColorPickerCard(
            title = activeTargetTitle,
            colorLong = currentColorLong,
            onColorChange = { updatedColorLong ->
                onUpdateSettings { latest ->
                    when (activeColorTarget) {
                        ColorTarget.TEXT -> latest.copy(subtitleTextColor = updatedColorLong)
                        ColorTarget.BORDER -> latest.copy(subtitleBorderColor = updatedColorLong)
                        ColorTarget.BACKGROUND -> latest.copy(subtitleBackgroundColor = updatedColorLong)
                        ColorTarget.SHADOW -> latest.copy(subtitleShadowColor = updatedColorLong)
                    }
                }
            }
        )
    }
}

// =============================================================================
// THREE-ARC CIRCULAR COLOR PICKER (Matching Image 1)
// =============================================================================
@Composable
internal fun ThreeArcCircularColorPickerCard(
    title: String,
    colorLong: Long,
    onColorChange: (Long) -> Unit
) {
    val onColorChangeUpdated by rememberUpdatedState(onColorChange)

    val initialHsv = remember(colorLong) {
        val array = FloatArray(3)
        AndroidColor.colorToHSV(colorLong.toInt(), array)
        array
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(Color(colorLong.toInt()).alpha) }
    var activeArc by remember { mutableStateOf<ArcPickerType?>(null) }

    fun emitColor(h: Float, s: Float, v: Float, a: Float) {
        val alphaInt = (a * 255f).roundToInt().coerceIn(0, 255)
        val argb = AndroidColor.HSVToColor(alphaInt, floatArrayOf(h, s, v))
        onColorChangeUpdated(argb.toLong() and 0xFFFFFFFFL)
    }

    val centerPreviewColor = remember(hue, saturation, value, alpha) {
        val alphaInt = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val argb = AndroidColor.HSVToColor(alphaInt, floatArrayOf(hue, saturation, value))
        Color(argb)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SheetButtonBgColor)
            .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        // The 3-Arc Canvas with Center Preview Circle
        Box(
            modifier = Modifier
                .size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Tap and drag on the color-wheel arcs are combined in a
                        // single pointerInput block (both launched as sibling
                        // coroutines) instead of two separate stacked
                        // pointerInput modifiers, which used to race for the
                        // first touch and could require a second tap/drag to
                        // register.
                        kotlinx.coroutines.coroutineScope {
                        launch {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val dist = sqrt(dx * dx + dy * dy)
                            val maxR = size.width / 2f
                            val arcRadius = maxR * 0.78f
                            val touchBand = maxR * 0.38f

                            if (dist in (arcRadius - touchBand)..(arcRadius + touchBand)) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f

                                if (angle in 30f..150f) {
                                    val progress = ((angle - 40f) / 100f).coerceIn(0f, 1f)
                                    hue = progress * 360f
                                    if (value < 0.15f) value = 1.0f
                                    if (saturation < 0.15f) saturation = 1.0f
                                    emitColor(hue, saturation, value, alpha)
                                } else if (angle in 150f..270f) {
                                    val progress = ((angle - 160f) / 100f).coerceIn(0f, 1f)
                                    saturation = progress
                                    if (value < 0.15f) value = 1.0f
                                    emitColor(hue, saturation, value, alpha)
                                } else if (angle >= 270f || angle <= 30f) {
                                    val normalizedAngle = if (angle < 100f) angle + 360f else angle
                                    val progress = ((normalizedAngle - 280f) / 100f).coerceIn(0f, 1f)
                                    value = progress
                                    emitColor(hue, saturation, value, alpha)
                                }
                            }
                        }
                        }
                        launch {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f

                                activeArc = when {
                                    angle in 30f..150f -> ArcPickerType.HUE
                                    angle in 150f..270f -> ArcPickerType.SATURATION
                                    angle >= 270f || angle <= 30f -> ArcPickerType.VALUE
                                    else -> null
                                }

                                when (activeArc) {
                                    ArcPickerType.HUE -> {
                                        val progress = ((angle - 40f) / 100f).coerceIn(0f, 1f)
                                        hue = progress * 360f
                                        if (value < 0.15f) value = 1.0f
                                        if (saturation < 0.15f) saturation = 1.0f
                                        emitColor(hue, saturation, value, alpha)
                                    }
                                    ArcPickerType.SATURATION -> {
                                        val progress = ((angle - 160f) / 100f).coerceIn(0f, 1f)
                                        saturation = progress
                                        if (value < 0.15f) value = 1.0f
                                        emitColor(hue, saturation, value, alpha)
                                    }
                                    ArcPickerType.VALUE -> {
                                        val normalizedAngle = if (angle < 100f) angle + 360f else angle
                                        val progress = ((normalizedAngle - 280f) / 100f).coerceIn(0f, 1f)
                                        value = progress
                                        emitColor(hue, saturation, value, alpha)
                                    }
                                    null -> {}
                                }
                            },
                            onDragEnd = { activeArc = null },
                            onDragCancel = { activeArc = null }
                        ) { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = change.position.x - center.x
                            val dy = change.position.y - center.y
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < 0) angle += 360f

                            val currentArc = activeArc ?: when {
                                angle in 30f..150f -> ArcPickerType.HUE
                                angle in 150f..270f -> ArcPickerType.SATURATION
                                angle >= 270f || angle <= 30f -> ArcPickerType.VALUE
                                else -> null
                            }

                            when (currentArc) {
                                ArcPickerType.HUE -> {
                                    val progress = ((angle - 40f) / 100f).coerceIn(0f, 1f)
                                    hue = progress * 360f
                                    if (value < 0.15f) value = 1.0f
                                    if (saturation < 0.15f) saturation = 1.0f
                                    emitColor(hue, saturation, value, alpha)
                                }
                                ArcPickerType.SATURATION -> {
                                    val progress = ((angle - 160f) / 100f).coerceIn(0f, 1f)
                                    saturation = progress
                                    if (value < 0.15f) value = 1.0f
                                    emitColor(hue, saturation, value, alpha)
                                }
                                ArcPickerType.VALUE -> {
                                    val normalizedAngle = if (angle < 100f) angle + 360f else angle
                                    val progress = ((normalizedAngle - 280f) / 100f).coerceIn(0f, 1f)
                                    value = progress
                                    emitColor(hue, saturation, value, alpha)
                                }
                                null -> {}
                            }
                        }
                        }
                        }
                    }
            ) {
                drawThreeArcColorPicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    currentColor = centerPreviewColor
                )
            }

            // Center Circular Live Color Preview (Matching Image 1 center circle with dark border)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF1E293B), CircleShape)
                    .shadow(elevation = 6.dp, shape = CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(centerPreviewColor)
                )
            }
        }

        // Alpha / Opacity Slider (Clean & smooth)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Opacity / Alpha",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SheetButtonTextColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGradient)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(alpha * 100f).roundToInt()}%",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Slider(
                value = alpha,
                onValueChange = { newAlpha ->
                    alpha = newAlpha
                    emitColor(hue, saturation, value, newAlpha)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentPink,
                    activeTrackColor = AccentSkyBlue,
                    inactiveTrackColor = SheetButtonBorderColor
                )
            )
        }
    }
}

/**
 * Draws the 3 disconnected curved arcs matching Image 1:
 * - Bottom Arc: Full Rainbow Hue spectrum 0°–360° (40° to 140°, span 100°)
 * - Left Arc: Saturation gradient (160° to 260°, span 100°)
 * - Right/Top Arc: Brightness/Value gradient (280° to 380° / 20°, span 100°)
 * - Each arc has a distinct draggable thumb knob with border filled with the current tone!
 */
private fun DrawScope.drawThreeArcColorPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    currentColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = size.width / 2f
    val arcRadius = maxRadius * 0.78f
    val strokeWidth = 10.dp.toPx()
    val knobRadius = 11.5.dp.toPx()

    val pureHueColor = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, 1f, 1f)))

    // -------------------------------------------------------------------------
    // 1. BOTTOM ARC: Full Rainbow Hue Spectrum 0° to 360° along (40° to 140°)
    // -------------------------------------------------------------------------
    drawArc(
        color = Color(0xFF1E293B),
        startAngle = 38f,
        sweepAngle = 104f,
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round)
    )

    val hueSegments = 120
    val hueStepSweep = 100f / hueSegments
    for (i in 0 until hueSegments) {
        val segHue = (i.toFloat() / hueSegments) * 360f
        val segColor = Color(AndroidColor.HSVToColor(255, floatArrayOf(segHue, 1f, 1f)))
        drawArc(
            color = segColor,
            startAngle = 40f + (i * hueStepSweep),
            sweepAngle = hueStepSweep + 0.6f,
            useCenter = false,
            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(
                width = strokeWidth,
                cap = if (i == 0 || i == hueSegments - 1) StrokeCap.Round else StrokeCap.Butt
            )
        )
    }

    // Knob for Hue
    val hueProgress = (hue / 360f).coerceIn(0f, 1f)
    val hueAngleDeg = 40f + (hueProgress * 100f)
    val hueRad = Math.toRadians(hueAngleDeg.toDouble())
    val hueKnobPos = Offset(
        (center.x + arcRadius * cos(hueRad)).toFloat(),
        (center.y + arcRadius * sin(hueRad)).toFloat()
    )
    drawCircle(
        color = Color(0xFF1E293B),
        radius = knobRadius + 2.dp.toPx(),
        center = hueKnobPos,
        style = Fill
    )
    drawCircle(
        color = pureHueColor,
        radius = knobRadius,
        center = hueKnobPos,
        style = Fill
    )

    // -------------------------------------------------------------------------
    // 2. LEFT ARC: Saturation Gradient (160° to 260°, span 100°)
    // -------------------------------------------------------------------------
    drawArc(
        color = Color(0xFF1E293B),
        startAngle = 158f,
        sweepAngle = 104f,
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round)
    )

    val satSegments = 80
    val satStepSweep = 100f / satSegments
    for (i in 0 until satSegments) {
        val segSat = i.toFloat() / satSegments
        val segColor = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, segSat, 1f)))
        drawArc(
            color = segColor,
            startAngle = 160f + (i * satStepSweep),
            sweepAngle = satStepSweep + 0.6f,
            useCenter = false,
            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(
                width = strokeWidth,
                cap = if (i == 0 || i == satSegments - 1) StrokeCap.Round else StrokeCap.Butt
            )
        )
    }

    // Knob for Saturation
    val satProgress = saturation.coerceIn(0f, 1f)
    val satAngleDeg = 160f + (satProgress * 100f)
    val satRad = Math.toRadians(satAngleDeg.toDouble())
    val satKnobPos = Offset(
        (center.x + arcRadius * cos(satRad)).toFloat(),
        (center.y + arcRadius * sin(satRad)).toFloat()
    )
    val currentSatTone = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, saturation, 1f)))
    drawCircle(
        color = Color(0xFF1E293B),
        radius = knobRadius + 2.dp.toPx(),
        center = satKnobPos,
        style = Fill
    )
    drawCircle(
        color = currentSatTone,
        radius = knobRadius,
        center = satKnobPos,
        style = Fill
    )

    // -------------------------------------------------------------------------
    // 3. RIGHT/TOP ARC: Value / Brightness Gradient (280° to 380° / 20°, span 100°)
    // -------------------------------------------------------------------------
    drawArc(
        color = Color(0xFF1E293B),
        startAngle = 278f,
        sweepAngle = 104f,
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round)
    )

    val valSegments = 80
    val valStepSweep = 100f / valSegments
    for (i in 0 until valSegments) {
        val segVal = i.toFloat() / valSegments
        val segColor = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, saturation.coerceAtLeast(0.05f), segVal)))
        drawArc(
            color = segColor,
            startAngle = 280f + (i * valStepSweep),
            sweepAngle = valStepSweep + 0.6f,
            useCenter = false,
            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(
                width = strokeWidth,
                cap = if (i == 0 || i == valSegments - 1) StrokeCap.Round else StrokeCap.Butt
            )
        )
    }

    // Knob for Value/Brightness
    val valProgress = value.coerceIn(0f, 1f)
    val valAngleDeg = 280f + (valProgress * 100f)
    val valRad = Math.toRadians(valAngleDeg.toDouble())
    val valKnobPos = Offset(
        (center.x + arcRadius * cos(valRad)).toFloat(),
        (center.y + arcRadius * sin(valRad)).toFloat()
    )
    val currentValTone = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, saturation, value)))
    drawCircle(
        color = Color(0xFF1E293B),
        radius = knobRadius + 2.dp.toPx(),
        center = valKnobPos,
        style = Fill
    )
    drawCircle(
        color = currentValTone,
        radius = knobRadius,
        center = valKnobPos,
        style = Fill
    )
}

// =============================================================================
// 3. ENHANCED MISCELLANEOUS SECTION (Full, Expandable, Detailed Layout)
// =============================================================================
@Composable
private fun EnhancedMiscContent(
    settings: PlayerSettings,
    isAssSsaTrack: Boolean,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // These two only apply to a genuinely native ASS/SSA subtitle (they force/edit its own
        // embedded styling) — hidden for every other subtitle format, where the typography/
        // colour settings above always apply directly with no toggle needed.
        if (isAssSsaTrack) {
            // Detailed Switch: Override ASS/SSA
            DetailedSwitchRow(
                title = "Override ASS/SSA Styles",
                description = "Force custom styling over embedded ASS subtitles",
                checked = settings.overrideAssSsaSubtitles,
                onCheckedChange = { isChecked ->
                    onUpdateSettings { it.copy(overrideAssSsaSubtitles = isChecked) }
                }
            )

            // Detailed Switch: Advanced ASS/SSA per-style editor
            DetailedSwitchRow(
                title = "Advanced ASS/SSA",
                description = "Edit every style of the current ASS/SSA subtitle individually",
                checked = settings.advancedAssEnabled,
                onCheckedChange = { isChecked ->
                    onUpdateSettings { it.copy(advancedAssEnabled = isChecked) }
                }
            )
        }

        // Detailed Switch: Scale with Window
        DetailedSwitchRow(
            title = "Scale with Window",
            description = "Resize subtitles proportionally with player dimensions",
            checked = settings.scaleSubtitlesByWindow,
            onCheckedChange = { isChecked ->
                onUpdateSettings { it.copy(scaleSubtitlesByWindow = isChecked) }
            }
        )

        // Detailed Switch: Blend with Video
        DetailedSwitchRow(
            title = "Blend Subtitles with Video",
            description = "Apply video color grading and filters to subtitles",
            checked = settings.subtitleBlendWithVideo,
            onCheckedChange = { isChecked ->
                onUpdateSettings { it.copy(subtitleBlendWithVideo = isChecked) }
            }
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Full Subtitle Scale Factor Slider
        FullSliderCard(
            title = "Subtitle Scale Factor",
            value = settings.subtitleScale,
            unit = "x",
            min = 0.5f,
            max = 3.0f,
            step = 0.05f,
            format = "%.2f",
            onChange = { newScale ->
                onUpdateSettings { it.copy(subtitleScale = newScale) }
            }
        )

        // Vertical Position Control with [ TOP ] [ BOTTOM ] direction origin
        val isTopSelected = settings.subtitlePositionDirection == "TOP_TO_BOTTOM"
        val isBottomSelected = settings.subtitlePositionDirection == "BOTTOM_TO_TOP"

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vertical Position",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetButtonTextColor
                )
                val uiPercent = if (isTopSelected) {
                    settings.subtitlePosition.coerceIn(0f, 150f).roundToInt()
                } else {
                    (100f - settings.subtitlePosition).coerceIn(0f, 100f).roundToInt()
                }
                Text(
                    text = "$uiPercent%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // [ TOP ] [ BOTTOM ] Direction Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // TOP Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTopSelected) AccentGradient else SolidColor(Color.Transparent))
                        .border(
                            1.dp,
                            if (isTopSelected) Color.Transparent else SheetButtonBorderColor,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            val currentMpvPos = settings.subtitlePosition
                            onUpdateSettings {
                                it.copy(
                                    subtitlePositionDirection = "TOP_TO_BOTTOM",
                                    subtitlePosition = currentMpvPos
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TOP",
                        fontSize = 12.5.sp,
                        fontWeight = if (isTopSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTopSelected) Color.White else SheetButtonTextColor
                    )
                }

                // BOTTOM Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBottomSelected) AccentGradient else SolidColor(Color.Transparent))
                        .border(
                            1.dp,
                            if (isBottomSelected) Color.Transparent else SheetButtonBorderColor,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            val currentMpvPos = settings.subtitlePosition
                            onUpdateSettings {
                                it.copy(
                                    subtitlePositionDirection = "BOTTOM_TO_TOP",
                                    subtitlePosition = currentMpvPos
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BOTTOM",
                        fontSize = 12.5.sp,
                        fontWeight = if (isBottomSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isBottomSelected) Color.White else SheetButtonTextColor
                    )
                }
            }

            // Slider is displayed when TOP or BOTTOM is active
            if (isTopSelected || isBottomSelected) {
                val isTop = isTopSelected
                val uiValue = if (isTop) {
                    settings.subtitlePosition.coerceIn(0f, 100f)
                } else {
                    (100f - settings.subtitlePosition).coerceIn(0f, 100f)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTop) "Origin: Top → Bottom" else "Origin: Bottom → Top",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGradient)
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${uiValue.roundToInt()}%",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Slider(
                        value = uiValue,
                        onValueChange = { newUiVal ->
                            val newMpvPos = if (isTop) {
                                newUiVal
                            } else {
                                100f - newUiVal
                            }
                            onUpdateSettings {
                                it.copy(subtitlePosition = newMpvPos)
                            }
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPink,
                            activeTrackColor = AccentSkyBlue,
                            inactiveTrackColor = SheetButtonBorderColor
                        )
                    )
                }
            }
        }

        // Reset Miscellaneous Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(10.dp))
                .clickable {
                    onUpdateSettings {
                        it.copy(
                            overrideAssSsaSubtitles = false,
                            scaleSubtitlesByWindow = true,
                            subtitleBlendWithVideo = false,
                            subtitleScale = 1.0f,
                            subtitlePosition = 100f,
                            subtitlePositionDirection = "BOTTOM_TO_TOP"
                        )
                    }
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StyledIcon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset Miscellaneous",
                    tint = SheetButtonTextColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Reset Miscellaneous Settings",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetButtonTextColor
                )
            }
        }
    }
}

@Composable
private fun DetailedSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SheetButtonBgColor)
            .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SheetButtonTextColor
            )
            Text(
                text = description,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF475569),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = LocalSheetSecondaryTextColor.current,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = SheetButtonBorderColor
            )
        )
    }
}

@Composable
private fun FontItemRow(
    name: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else Color.Transparent
            )
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else SheetButtonTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isSelected) {
            StyledIcon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

