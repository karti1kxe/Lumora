package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.AppLanguage
import com.example.ui.state.AppStrings
import com.example.ui.state.AppViewModel
import com.example.ui.state.UiState
import com.example.ui.theme.*
import com.example.util.FolderDateUtils
import com.example.util.LumoraSettingsXmlManager
import java.io.File

/**
 * Advanced Settings Panel
 * Provides full Export and Import functionality for Lumora Settings
 * formatted as "Lumora Setting save.xml".
 */
@Composable
fun AdvancedSettingsPanel(
    uiState: UiState,
    viewModel: AppViewModel? = null,
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color,
    alphaFactor: Float,
    lang: AppLanguage
) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    var showExportPicker by remember { mutableStateOf(false) }
    var showImportPicker by remember { mutableStateOf(false) }
    var pendingImportFile by remember { mutableStateOf<File?>(null) }
    var pendingParsedSettings by remember { mutableStateOf<LumoraSettingsXmlManager.ParsedSettings?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp)
    ) {
        // AI FEATURES (Settings Dashboard > Advanced)
        AiFeaturesSettingsPanel(
            isDark = isDark,
            cardBg = cardBg,
            borderColor = borderColor
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION HEADER: BACKUP & RESTORE
        Text(
            text = "BACKUP & RESTORE",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryText,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Glass Card Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SETTING 1: EXPORT SETTINGS
                SettingsActionCardRow(
                    icon = Icons.Outlined.FileDownload,
                    title = AppStrings.getExportSettingsTitle(lang),
                    subtitle = AppStrings.getExportSettingsDesc(lang),
                    actionLabel = "Export",
                    testTag = "export_settings_button",
                    isDark = isDark,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    borderColor = borderColor,
                    onClick = {
                        showExportPicker = true
                    }
                )

                HorizontalDivider(color = borderColor.copy(alpha = 0.5f))

                // SETTING 2: IMPORT SETTINGS
                SettingsActionCardRow(
                    icon = Icons.Outlined.FileUpload,
                    title = AppStrings.getImportSettingsTitle(lang),
                    subtitle = AppStrings.getImportSettingsDesc(lang),
                    actionLabel = "Import",
                    testTag = "import_settings_button",
                    isDark = isDark,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    borderColor = borderColor,
                    onClick = {
                        showImportPicker = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info card explaining file persistence
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0x1A38BDF8) else Color(0x100284C7))
                .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                StyledIcon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = AccentSkyBlue,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (lang) {
                        AppLanguage.ENGLISH -> "Settings are saved to '${LumoraSettingsXmlManager.EXPORT_FILE_NAME}'. You can backup or restore appearance, gestures, audio, subtitle, and layout preferences at any time."
                        AppLanguage.HINGLISH -> "Settings '${LumoraSettingsXmlManager.EXPORT_FILE_NAME}' mein save hoti hain. Aap kabhi bhi apni appearance, gestures, audio, subtitle aur layout preferences backup ya restore kar sakte hain."
                        AppLanguage.HINDI -> "सेटिंग्स '${LumoraSettingsXmlManager.EXPORT_FILE_NAME}' में सुरक्षित की जाती हैं। आप किसी भी समय दिखावट, जेस्चर, ऑडियो, सबटाइटल और लेआउट प्राथमिकताओं का बैकअप या पुनर्स्थापना कर सकते हैं।"
                    },
                    fontSize = 11.5.sp,
                    color = secondaryText,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // DIRECTORY PICKER: EXPORT MODE
    if (showExportPicker) {
        FloatingDirectoryPickerSheet(
            action = DirectoryPickerAction.EXPORT_SETTINGS,
            itemCount = 1,
            itemDescription = "Settings XML",
            isDark = isDark,
            glassBlurTransparency = uiState.glassBlurTransparency,
            customTitle = "Select Destination Folder",
            customActionLabel = "Export Here",
            customActionIcon = Icons.Outlined.FileDownload,
            onConfirmTargetDir = { dir ->
                showExportPicker = false
                val result = LumoraSettingsXmlManager.exportSettingsToFile(context, uiState, dir)
                result.fold(
                    onSuccess = { file ->
                        val msg = when (lang) {
                            AppLanguage.ENGLISH -> "Settings exported to '${file.name}'"
                            AppLanguage.HINGLISH -> "Settings '${file.name}' mein export ho gayi"
                            AppLanguage.HINDI -> "सेटिंग्स '${file.name}' में निर्यात कर दी गईं"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    },
                    onFailure = { err ->
                        val errMsg = when (lang) {
                            AppLanguage.ENGLISH -> "Failed to export settings: ${err.localizedMessage ?: "Unknown error"}"
                            AppLanguage.HINGLISH -> "Settings export fail ho gaya: ${err.localizedMessage ?: "Error"}"
                            AppLanguage.HINDI -> "सेटिंग्स निर्यात विफल: ${err.localizedMessage ?: "त्रुटि"}"
                        }
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = {
                showExportPicker = false
            }
        )
    }

    // DIRECTORY / FILE PICKER: IMPORT MODE
    if (showImportPicker) {
        FloatingDirectoryPickerSheet(
            action = DirectoryPickerAction.IMPORT_SETTINGS,
            itemCount = 1,
            itemDescription = "Settings XML",
            isDark = isDark,
            glassBlurTransparency = uiState.glassBlurTransparency,
            customTitle = "Select Settings XML",
            allowedFileExtensions = setOf("xml"),
            onFileSelected = { file ->
                val parseResult = LumoraSettingsXmlManager.parseSettingsFile(context, file, uiState)
                parseResult.fold(
                    onSuccess = { parsed ->
                        pendingImportFile = file
                        pendingParsedSettings = parsed
                        showImportPicker = false
                        showConfirmDialog = true
                    },
                    onFailure = { err ->
                        val errMsg = when (lang) {
                            AppLanguage.ENGLISH -> "Invalid or corrupted settings XML: ${err.localizedMessage ?: "Error"}"
                            AppLanguage.HINGLISH -> "Invalid settings XML file: ${err.localizedMessage ?: "Error"}"
                            AppLanguage.HINDI -> "अमान्य सेटिंग्स XML फ़ाइल: ${err.localizedMessage ?: "त्रुटि"}"
                        }
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = {
                showImportPicker = false
            }
        )
    }

    // CONFIRMATION POPUP DIALOG (Centered glass island, within phone screen ratio, no minus pill or white box artifact)
    if (showConfirmDialog && pendingImportFile != null && pendingParsedSettings != null) {
        val file = pendingImportFile!!
        val parsed = pendingParsedSettings!!

        LiquidGlassDialog(
            isDark = isDark,
            onDismissRequest = {
                showConfirmDialog = false
                pendingImportFile = null
                pendingParsedSettings = null
            }
        ) {
            val alphaRatio = (uiState.glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
            val dialogBg = if (isDark) {
                if (alphaRatio >= 0.99f) Color(0xFF0F172A)
                else Color(0xFF0F172A).copy(alpha = (0.15f + 0.80f * alphaRatio).coerceIn(0.12f, 0.98f))
            } else {
                if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
                else Color(0xFFFFFFFF).copy(alpha = (0.15f + 0.80f * alphaRatio).coerceIn(0.12f, 0.98f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(dialogBg)
                    .border(1.2.dp, borderColor, RoundedCornerShape(26.dp))
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon Badge (Centered circular gradient, no minus handle)
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentSkyBlue.copy(alpha = 0.25f), AccentPink.copy(alpha = 0.20f))
                                )
                            )
                            .border(1.2.dp, AccentSkyBlue.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.SettingsBackupRestore,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Confirmation question: "क्या आप इस सेटिंग को लागू करना चाहते हैं?"
                    Text(
                        text = AppStrings.getImportConfirmMessage(lang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = AppStrings.getImportConfirmDesc(lang),
                        fontSize = 12.sp,
                        color = secondaryText,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // File Information (Clean translucent badge without any opaque white rectangular panel)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0x1A38BDF8) else Color(0x120284C7))
                            .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${FolderDateUtils.formatFileSize(file.length())})",
                            fontSize = 11.sp,
                            color = secondaryText
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Action Buttons: CANCEL & CONFIRM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // CANCEL BUTTON
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                pendingImportFile = null
                                pendingParsedSettings = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0x20FFFFFF) else Color(0x10000000),
                                contentColor = primaryText
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("import_cancel_button")
                        ) {
                            Text(
                                text = AppStrings.getCancelButton(lang),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // CONFIRM BUTTON
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                val currentFile = pendingImportFile
                                val currentParsed = pendingParsedSettings
                                pendingImportFile = null
                                pendingParsedSettings = null

                                if (currentParsed != null) {
                                    LumoraSettingsXmlManager.applySettings(context, currentParsed, viewModel)
                                    val successMsg = when (lang) {
                                        AppLanguage.ENGLISH -> "Settings applied successfully"
                                        AppLanguage.HINGLISH -> "Settings successfully apply ho gayi"
                                        AppLanguage.HINDI -> "सेटिंग्स सफलतापूर्वक लागू हो गईं"
                                    }
                                    Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentGradient)
                                .testTag("import_confirm_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AppStrings.getConfirmButton(lang),
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
    }
}

@Composable
private fun SettingsActionCardRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    testTag: String,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0x3038BDF8) else Color(0x180284C7)),
            contentAlignment = Alignment.Center
        ) {
            StyledIcon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDark) AccentSkyBlue else Color(0xFF0284C7),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AccentSkyBlue.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentSkyBlue.copy(alpha = 0.35f)),
            onClick = onClick,
            modifier = Modifier.testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StyledIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentSkyBlue,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentSkyBlue
                )
            }
        }
    }
}
