package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentPink

enum class FolderGlyphType {
    NONE,
    VIDEO,
    AUDIO,
    PLAYLIST
}

/**
 * Modern Clean Folder Icon:
 * - Rich warm amber-gold or custom indigo gradient folder styling matching standard system look
 * - Clean folder flap and tab geometry without cluttered video play icons
 * - High-end squircle container
 * - Unwatched / new count notification badge
 */
@Composable
fun ModernFolderIcon(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    folderColor: Color? = null,
    containerColor: Color? = null,
    newVideosCount: Int = 0,
    newCount: Int = newVideosCount,
    showBadge: Boolean = true,
    showNewBadge: Boolean = showBadge,
    isCustom: Boolean = false,
    isSelected: Boolean = false,
    showVideoGlyph: Boolean = true,
    glyphType: FolderGlyphType = if (showVideoGlyph) FolderGlyphType.VIDEO else FolderGlyphType.NONE
) {
    val actualNewCount = if (newCount != 0) newCount else newVideosCount
    val actualShowBadge = (showBadge || showNewBadge) && actualNewCount > 0

    // High-end clean aesthetic palettes (Vibrant Electric/Sky Blue for video folders, Indigo for custom playlists)
    val backFlapGradient = if (isCustom) {
        if (isDark) listOf(Color(0xFF6366F1), Color(0xFF4338CA)) else listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
    } else if (folderColor != null) {
        listOf(folderColor, folderColor.copy(alpha = 0.85f))
    } else {
        if (isDark) listOf(Color(0xFF0284C7), Color(0xFF0369A1)) else listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
    }

    val frontFlapGradient = if (isCustom) {
        if (isDark) listOf(Color(0xFFA5B4FC), Color(0xFF6366F1)) else listOf(Color(0xFFC7D2FE), Color(0xFF818CF8))
    } else if (folderColor != null) {
        listOf(folderColor.copy(alpha = 0.9f), folderColor)
    } else {
        if (isDark) listOf(Color(0xFF38BDF8), Color(0xFF0284C7)) else listOf(Color(0xFF7DD3FC), Color(0xFF0EA5E9))
    }

    val containerBg = containerColor ?: if (isDark) {
        if (isSelected) Color(0xFF1E1B4B).copy(alpha = 0.95f)
        else if (isCustom) Color(0xFF1E1B4B).copy(alpha = 0.85f)
        else Color(0xFF0B192E).copy(alpha = 0.85f)
    } else {
        if (isSelected) Color(0xFFEEF2FF).copy(alpha = 0.98f)
        else if (isCustom) Color(0xFFEEF2FF).copy(alpha = 0.95f)
        else Color(0xFFF0F9FF).copy(alpha = 0.95f)
    }

    val containerBorder = if (isDark) {
        if (isCustom) Color(0xFF3730A3).copy(alpha = 0.6f)
        else if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.8f)
        else Color(0xFF0284C7).copy(alpha = 0.40f)
    } else {
        if (isCustom) Color(0xFFC7D2FE)
        else if (isSelected) Color(0xFF38BDF8)
        else Color(0xFFBAE6FD).copy(alpha = 0.9f)
    }

    val cornerRadius = size * 0.28f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Frosted Glass / Warm Squircle Container
        Box(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = if (isSelected) 8.dp else if (isDark) 4.dp else 2.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = if (isSelected) AccentSkyBlue.copy(alpha = 0.45f) else if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFFD97706).copy(alpha = 0.15f),
                    spotColor = if (isSelected) AccentPink.copy(alpha = 0.55f) else if (isDark) Color(0xFFF59E0B).copy(alpha = 0.20f) else Color(0xFFF59E0B).copy(alpha = 0.20f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(containerBg)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, AccentGradient, RoundedCornerShape(cornerRadius))
                    } else {
                        Modifier.border(1.2.dp, containerBorder, RoundedCornerShape(cornerRadius))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 2. High-precision Clean Folder Canvas
            Canvas(modifier = Modifier.size(size * 0.62f)) {
                val w = this.size.width
                val h = this.size.height

                val r = w * 0.11f
                val tabW = w * 0.42f
                val tabH = h * 0.24f
                val backBodyTop = tabH * 0.80f

                // --- LAYER 1: Back Folder Tab & Plate ---
                val backPath = Path().apply {
                    moveTo(0f, backBodyTop + r)
                    lineTo(0f, r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(0f, 0f, 2 * r, 2 * r),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(tabW - r, 0f)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(tabW - 2 * r, 0f, tabW, 2 * r),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 45f,
                        forceMoveTo = false
                    )
                    lineTo(tabW + w * 0.14f, backBodyTop)
                    lineTo(w - r, backBodyTop)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(w - 2 * r, backBodyTop, w, backBodyTop + 2 * r),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(w, h - r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(w - 2 * r, h - 2 * r, w, h),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(r, h)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(0f, h - 2 * r, 2 * r, h),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    close()
                }

                drawPath(
                    path = backPath,
                    brush = Brush.verticalGradient(
                        colors = backFlapGradient,
                        startY = 0f,
                        endY = h
                    )
                )

                // --- LAYER 2: Inner Document Sheet Line Accent ---
                val sheetTop = tabH * 0.45f
                val sheetH = h * 0.38f
                drawRoundRect(
                    color = Color.White.copy(alpha = if (isDark) 0.40f else 0.75f),
                    topLeft = Offset(w * 0.12f, sheetTop),
                    size = Size(w * 0.76f, sheetH),
                    cornerRadius = CornerRadius(r * 0.6f, r * 0.6f)
                )

                // --- LAYER 3: Front Angled Flap (Layered Depth) ---
                val frontTop = h * 0.32f
                val frontPath = Path().apply {
                    moveTo(0f, frontTop + r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(0f, frontTop, 2 * r, frontTop + 2 * r),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(w - r, frontTop + h * 0.04f)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(w - 2 * r, frontTop + h * 0.04f, w, frontTop + h * 0.04f + 2 * r),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(w, h - r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(w - 2 * r, h - 2 * r, w, h),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(r, h)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(0f, h - 2 * r, 2 * r, h),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    close()
                }

                drawPath(
                    path = frontPath,
                    brush = Brush.verticalGradient(
                        colors = frontFlapGradient,
                        startY = frontTop,
                        endY = h
                    )
                )

                // Flap Highlight Rim / Specular Reflection
                drawLine(
                    color = Color.White.copy(alpha = 0.60f),
                    start = Offset(r, frontTop + 1.dp.toPx()),
                    end = Offset(w - r, frontTop + h * 0.04f + 1.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Center Glyphs based on glyphType
                val effectiveGlyph = if (glyphType != FolderGlyphType.NONE) glyphType else if (showVideoGlyph) FolderGlyphType.VIDEO else FolderGlyphType.NONE
                val glyphCenterY = frontTop + (h - frontTop) * 0.52f
                val glyphCenterX = w * 0.50f
                val glyphSize = w * 0.22f
                val glyphColor = if (isDark) Color.White.copy(alpha = 0.95f) else Color(0xFF0F172A).copy(alpha = 0.85f)

                when (effectiveGlyph) {
                    FolderGlyphType.VIDEO -> {
                        drawCircle(
                            color = Color.White.copy(alpha = if (isDark) 0.22f else 0.45f),
                            radius = glyphSize * 0.72f,
                            center = Offset(glyphCenterX, glyphCenterY)
                        )

                        val playTriangle = Path().apply {
                            val pR = glyphSize * 0.40f
                            moveTo(glyphCenterX - pR * 0.7f, glyphCenterY - pR)
                            lineTo(glyphCenterX + pR * 0.9f, glyphCenterY)
                            lineTo(glyphCenterX - pR * 0.7f, glyphCenterY + pR)
                            close()
                        }
                        drawPath(
                            path = playTriangle,
                            color = glyphColor
                        )
                    }
                    FolderGlyphType.AUDIO -> {
                        // Small circular backdrop
                        drawCircle(
                            color = Color.White.copy(alpha = if (isDark) 0.22f else 0.45f),
                            radius = glyphSize * 0.72f,
                            center = Offset(glyphCenterX, glyphCenterY)
                        )

                        // Crisp musical beamed notes glyph
                        val noteHeadRadius = glyphSize * 0.20f
                        val headLeftX = glyphCenterX - glyphSize * 0.28f
                        val headLeftY = glyphCenterY + glyphSize * 0.24f
                        val headRightX = glyphCenterX + glyphSize * 0.22f
                        val headRightY = glyphCenterY + glyphSize * 0.12f
                        val stemTop = glyphCenterY - glyphSize * 0.38f
                        val stemWidth = (1.4.dp).toPx().coerceAtLeast(2f)

                        // Left note head (angled ellipse)
                        drawOval(
                            color = glyphColor,
                            topLeft = Offset(headLeftX - noteHeadRadius * 1.05f, headLeftY - noteHeadRadius * 0.75f),
                            size = Size(noteHeadRadius * 2.1f, noteHeadRadius * 1.5f)
                        )
                        // Right note head (angled ellipse)
                        drawOval(
                            color = glyphColor,
                            topLeft = Offset(headRightX - noteHeadRadius * 1.05f, headRightY - noteHeadRadius * 0.75f),
                            size = Size(noteHeadRadius * 2.1f, noteHeadRadius * 1.5f)
                        )
                        // Left stem
                        drawLine(
                            color = glyphColor,
                            start = Offset(headLeftX + noteHeadRadius * 0.75f, headLeftY),
                            end = Offset(headLeftX + noteHeadRadius * 0.75f, stemTop),
                            strokeWidth = stemWidth
                        )
                        // Right stem
                        drawLine(
                            color = glyphColor,
                            start = Offset(headRightX + noteHeadRadius * 0.75f, headRightY),
                            end = Offset(headRightX + noteHeadRadius * 0.75f, stemTop - glyphSize * 0.12f),
                            strokeWidth = stemWidth
                        )
                        // Top beam connecting stems
                        val beamPath = Path().apply {
                            moveTo(headLeftX + noteHeadRadius * 0.75f, stemTop)
                            lineTo(headRightX + noteHeadRadius * 0.75f + stemWidth, stemTop - glyphSize * 0.12f)
                            lineTo(headRightX + noteHeadRadius * 0.75f + stemWidth, stemTop - glyphSize * 0.12f + glyphSize * 0.20f)
                            lineTo(headLeftX + noteHeadRadius * 0.75f, stemTop + glyphSize * 0.20f)
                            close()
                        }
                        drawPath(beamPath, color = glyphColor)
                    }

                    FolderGlyphType.PLAYLIST -> {
                        // Draw 3 horizontal playlist lines + small play triangle
                        val lineThickness = glyphSize * 0.16f
                        val lineSpacing = glyphSize * 0.32f
                        val startX = glyphCenterX - glyphSize * 0.65f
                        val endX1 = glyphCenterX + glyphSize * 0.65f
                        val endX2 = glyphCenterX + glyphSize * 0.35f
                        val endX3 = glyphCenterX + glyphSize * 0.15f
                        val startY = glyphCenterY - glyphSize * 0.32f

                        // Line 1
                        drawLine(
                            color = glyphColor,
                            start = Offset(startX, startY),
                            end = Offset(endX1, startY),
                            strokeWidth = lineThickness
                        )
                        // Line 2
                        drawLine(
                            color = glyphColor,
                            start = Offset(startX, startY + lineSpacing),
                            end = Offset(endX2, startY + lineSpacing),
                            strokeWidth = lineThickness
                        )
                        // Line 3
                        drawLine(
                            color = glyphColor,
                            start = Offset(startX, startY + lineSpacing * 2f),
                            end = Offset(endX3, startY + lineSpacing * 2f),
                            strokeWidth = lineThickness
                        )

                        // Small play triangle on right
                        val triangleLeft = glyphCenterX + glyphSize * 0.22f
                        val triangleTop = startY + lineSpacing * 0.8f
                        val triangleHeight = glyphSize * 0.75f
                        val triangleWidth = glyphSize * 0.55f

                        val playPath = Path().apply {
                            moveTo(triangleLeft, triangleTop)
                            lineTo(triangleLeft + triangleWidth, triangleTop + triangleHeight * 0.5f)
                            lineTo(triangleLeft, triangleTop + triangleHeight)
                            close()
                        }
                        drawPath(playPath, color = glyphColor)
                    }

                    FolderGlyphType.NONE -> {
                        // No inner glyph, pristine modern folder
                    }
                }
            }
        }

        // 3. New / Unwatched videos notification badge
        if (actualShowBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF3366), Color(0xFFEF4444))
                        )
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (actualNewCount > 99) "99+" else actualNewCount.toString(),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
