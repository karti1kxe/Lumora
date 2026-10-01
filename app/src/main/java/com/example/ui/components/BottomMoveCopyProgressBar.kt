package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun BottomMoveCopyProgressBar(
    isDark: Boolean,
    alphaRatio: Float = 0.85f,
    isMove: Boolean = true,
    isDelete: Boolean = false,
    customActionText: String? = null,
    customIcon: ImageVector? = null,
    progressFraction: Float,
    currentItemName: String = "",
    completedCount: Int = 0,
    totalCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val barBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0F172A)
        else Color(0xFF0F172A).copy(alpha = (0.70f + 0.30f * alphaRatio).coerceIn(0.40f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.75f + 0.25f * alphaRatio).coerceIn(0.45f, 1.0f))
    }
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val barShape = RoundedCornerShape(26.dp)

    val animatedFraction by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100),
        label = "progressFraction"
    )

    val percentage = (animatedFraction * 100f).coerceIn(0f, 100f).toInt()
    val actionText = customActionText ?: when {
        isDelete -> "Deleting..."
        isMove -> "Moving..."
        else -> "Copying..."
    }
    val icon = customIcon ?: when {
        isDelete -> Icons.Outlined.DeleteOutline
        isMove -> Icons.Outlined.DriveFileMove
        else -> Icons.Outlined.ContentCopy
    }
    val tintColor = when {
        isDelete -> Color(0xFFEF4444)
        isMove -> AccentSkyBlue
        else -> Color(0xFF818CF8)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 18.dp,
                shape = barShape,
                ambientColor = if (isDelete) Color(0xFFEF4444).copy(alpha = 0.35f) else AccentSkyBlue.copy(alpha = 0.35f),
                spotColor = if (isDelete) Color(0xFFDC2626).copy(alpha = 0.40f) else AccentPink.copy(alpha = 0.40f)
            )
            .clip(barShape)
            .background(barBg)
            .border(
                width = 1.5.dp,
                brush = if (isDelete) androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444).copy(alpha = 0.5f)) else AccentGradient,
                shape = barShape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    StyledIcon(imageVector = icon,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = actionText,
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (currentItemName.isNotBlank()) {
                        Text(
                            text = currentItemName,
                            color = secondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "$percentage%",
                    color = tintColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Progress Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedFraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isDelete) androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444)) else AccentGradient)
                )
            }
        }
    }
}
