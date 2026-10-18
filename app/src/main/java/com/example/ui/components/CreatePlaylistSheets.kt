package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary

/** Same translucent "liquid glass" container colour the app's other bottom sheets use. */
@Composable
private fun playlistSheetContainerColor(isDark: Boolean, glassBlurTransparency: Float): Color {
    return glassPanelColor(isDark, glassBlurTransparency)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistBottomSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = playlistSheetContainerColor(isDark, glassBlurTransparency),
        scrimColor = Color.Black.copy(alpha = 0.35f),
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
        PanelScaled {
        // imePadding keeps the fields and the buttons above the keyboard; the column scrolls if it does not fit
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 20.dp)
        ) {
            content()
        }
    }
}
}

// ----------------------------------------------------------------------------------------------
// 1. Playlist Options: Create empty playlist / Add Playlist from URL
// ----------------------------------------------------------------------------------------------

@Composable
fun PlaylistOptionsSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    onDismiss: () -> Unit,
    onCreateEmpty: () -> Unit,
    onAddFromUrl: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    PlaylistBottomSheet(isDark, glassBlurTransparency, onDismiss) {
        Text(
            text = "Playlist Options",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        PlaylistOptionRow(
            isDark = isDark,
            title = "Create empty playlist",
            subtitle = "Create a new blank playlist",
            icon = { tint ->
                StyledIcon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            },
            onClick = onCreateEmpty
        )
        Spacer(modifier = Modifier.height(10.dp))
        PlaylistOptionRow(
            isDark = isDark,
            title = "Add Playlist from URL",
            subtitle = "Import a playlist from a web URL",
            icon = { tint ->
                StyledIcon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            },
            onClick = onAddFromUrl
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PlaylistOptionRow(
    isDark: Boolean,
    title: String,
    subtitle: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val rowBg = if (isDark) Color.White.copy(alpha = 0.07f) else Color(0xFF0F172A).copy(alpha = 0.06f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(AccentSkyBlue)
        Spacer(modifier = Modifier.width(18.dp))
        Column {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = primaryText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 13.sp, color = secondaryText)
        }
    }
}

// ----------------------------------------------------------------------------------------------
// 2. Create Empty Playlist (name + OK / Cancel)
// ----------------------------------------------------------------------------------------------

@Composable
fun CreateEmptyPlaylistSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    title: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Throwable) {}
    }

    fun submit() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showError = true
        } else {
            onCreate(trimmed)
        }
    }

    PlaylistBottomSheet(isDark, glassBlurTransparency, onDismiss) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                showError = false
            },
            label = { Text("Playlist name") },
            singleLine = true,
            isError = showError,
            supportingText = if (showError) { { Text("Enter a playlist name") } } else null,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Spacer(modifier = Modifier.height(18.dp))
        SheetButtonsRow(
            isDark = isDark,
            confirmLabel = "OK",
            confirmEnabled = true,
            onCancel = onDismiss,
            onConfirm = { submit() }
        )
    }
}

// ----------------------------------------------------------------------------------------------
// 3. Add Playlist from URL / local M3U file
// ----------------------------------------------------------------------------------------------

@Composable
fun AddPlaylistFromUrlSheet(
    isDark: Boolean,
    glassBlurTransparency: Float,
    isImporting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAddFromUrl: (url: String, userAgent: String) -> Unit,
    onChooseLocalFile: () -> Unit,
    onInputChanged: () -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    var url by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    val dividerColor = secondaryText.copy(alpha = 0.35f)

    PlaylistBottomSheet(isDark, glassBlurTransparency, onDismiss) {
        Text(
            text = "Add Playlist",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                onInputChanged()
            },
            label = { Text("Playlist URL") },
            singleLine = true,
            enabled = !isImporting,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Supports M3U/M3U8 and web playlists such as YouTube",
            fontSize = 12.sp,
            color = secondaryText,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 14.dp)
        )
        OutlinedTextField(
            value = userAgent,
            onValueChange = {
                userAgent = it
                onInputChanged()
            },
            label = { Text("Custom User-Agent (optional)") },
            singleLine = true,
            enabled = !isImporting,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (url.isNotBlank() && !isImporting) onAddFromUrl(url, userAgent)
            }),
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                fontSize = 12.5.sp,
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
            Text(
                text = "OR",
                fontSize = 12.sp,
                color = secondaryText,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color(0xFF0F172A).copy(alpha = 0.06f))
                .clickable(enabled = !isImporting, onClick = onChooseLocalFile)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledIcon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Choose Local M3U File",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SheetButtonsRow(
            isDark = isDark,
            confirmLabel = "Add from URL",
            confirmEnabled = url.isNotBlank() && !isImporting,
            isLoading = isImporting,
            onCancel = onDismiss,
            onConfirm = { onAddFromUrl(url, userAgent) }
        )
    }
}

// ----------------------------------------------------------------------------------------------
// shared Cancel / confirm row
// ----------------------------------------------------------------------------------------------

@Composable
private fun SheetButtonsRow(
    isDark: Boolean,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    isLoading: Boolean = false
) {
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCancel)
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Text(text = "Cancel", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AccentSkyBlue)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(CircleShape)
                .then(
                    if (confirmEnabled) Modifier.background(AccentGradient)
                    else Modifier.background(secondaryText.copy(alpha = 0.18f))
                )
                .clickable(enabled = confirmEnabled, onClick = onConfirm)
                .padding(horizontal = 34.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = secondaryText
                )
            } else {
                Text(
                    text = confirmLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (confirmEnabled) Color.White else secondaryText.copy(alpha = 0.7f)
                )
            }
        }
    }
}
