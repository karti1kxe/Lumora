package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Delete confirmation used everywhere: it slides up from the bottom of the screen (never centered) with a
 * translucent, system-like background. Shows what will be deleted and asks for a final confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmSheet(
    title: String,
    message: String,
    itemNames: List<String>,
    isDark: Boolean,
    confirmLabel: String = "Delete",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (isDark) Color(0xFF1B2630).copy(alpha = 0.94f) else Color(0xFFDAE3E9).copy(alpha = 0.94f)
    val primaryText = if (isDark) Color(0xFFE6EEF2) else Color(0xFF16222A)
    val warnBg = if (isDark) Color(0xFF4A1F1D) else Color(0xFFF9DEDC)
    val warnText = if (isDark) Color(0xFFFFDAD6) else Color(0xFF410E0B)
    val listBg = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.45f)
    val accent = if (isDark) Color(0xFF5FD3E6) else Color(0xFF006877)
    val red = Color(0xFFB3261E)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = red,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(warnBg)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledIcon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = warnText,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = message,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    color = warnText,
                    modifier = Modifier.weight(1f)
                )
            }

            if (itemNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(listBg)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemNames.forEachIndexed { i, name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${i + 1}.",
                                fontSize = 16.sp,
                                color = accent,
                                modifier = Modifier.width(34.dp)
                            )
                            Text(
                                text = name,
                                fontSize = 17.sp,
                                color = primaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledIcon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Cancel", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = accent)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(red)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 40.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = confirmLabel, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }
    }
}
