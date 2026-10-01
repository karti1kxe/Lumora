package com.example.ui.components

import com.example.R

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import android.view.WindowManager
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import android.util.Log

/**
 * Returns the version-appropriate runtime media permissions array:
 * - Android 13+ (API 33+): READ_MEDIA_VIDEO and READ_MEDIA_AUDIO
 * - Android 9 and below (API <= 28): READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
 * - Android 10-12 (API 29-32): READ_EXTERNAL_STORAGE
 */
fun getRequiredMediaPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/**
 * Android 11+ exposes "All files access" as a special app access setting rather than a
 * normal runtime permission dialog. When granted, the app can enumerate shared storage
 * directly and is no longer dependent on OEM-specific MediaStore indexing gaps.
 */
fun hasAllFilesAccess(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}

/**
 * Checks for complete media-library access needed by Lumora: both video and audio on
 * Android 13+, legacy shared-storage access on older releases, or All Files Access.
 */
fun hasVideoPermission(context: Context): Boolean {
    // On Android 11+ Lumora intentionally requires the user to grant the
    // system's All Files Access special access. Runtime media permissions are
    // requested as part of the same onboarding flow, but this gate is what
    // guarantees complete folder enumeration across OEM MediaStore variants.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    }

    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Opens standard Application Details Settings so the user can manually enable permissions.
 */
fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("PermissionHandler", "Failed to open application details settings", e)
    }
}

/**
 * Launches Android 11+ All Files Access (MANAGE_EXTERNAL_STORAGE) settings page if needed.
 */
fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                Log.e("PermissionHandler", "Failed to launch all files access settings", err)
                openAppSettings(context)
            }
        }
    } else {
        openAppSettings(context)
    }
}

@Composable
fun FirstLaunchPermissionHandler(
    onPermissionChanged: () -> Unit = {}
) {
    // Kept lightweight for backwards-compatibility
}

@Composable
fun StoragePermissionDialog(
    isDark: Boolean,
    appScale: Float = 75f,
    glassBlurTransparency: Float = 85f,
    onRequestPermissions: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    var hasAttemptedRequest by remember { mutableStateOf(false) }

    // Calculate dynamic scaled density so the entire Storage Permission dialog scales with appScale
    val currentDensity = LocalDensity.current
    val currentAppScale = appScale.coerceIn(1f, 100f)
    val scaleFactor = (0.85f + 0.15f * ((currentAppScale - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )
    }

    val alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
    val cardBackground = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF0F172A)
        else Color(0xFF0F172A).copy(alpha = (0.40f + 0.60f * alphaRatio).coerceIn(0.25f, 1.0f))
    } else {
        if (alphaRatio >= 0.99f) Color.White
        else Color.White.copy(alpha = (0.45f + 0.55f * alphaRatio).coerceIn(0.25f, 1.0f))
    }
    val cardBorder = if (isDark) {
        Color(0xFF38BDF8).copy(alpha = (0.25f + 0.45f * alphaRatio).coerceIn(0.20f, 0.70f))
    } else {
        Color(0xFF0284C7).copy(alpha = (0.20f + 0.40f * alphaRatio).coerceIn(0.15f, 0.60f))
    }
    val bulletBoxBg = if (isDark) {
        if (alphaRatio >= 0.99f) Color(0xFF1E293B).copy(alpha = 0.70f)
        else Color(0xFF1E293B).copy(alpha = (0.25f + 0.55f * alphaRatio).coerceIn(0.15f, 0.80f))
    } else {
        if (alphaRatio >= 0.99f) Color(0xFFF8FAFC)
        else Color(0xFFF8FAFC).copy(alpha = (0.35f + 0.55f * alphaRatio).coerceIn(0.20f, 0.90f))
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAttemptedRequest = true
        val granted = hasVideoPermission(context) || permissions.values.any { it }
        Log.d("PermissionHandler", "RequestMultiplePermissions callback: granted=$granted, permissions=$permissions, SDK=${Build.VERSION.SDK_INT}")
        if (granted) {
            onPermissionGranted()
        }
    }

    // Auto-detect if permission was granted via app settings or external prompt
    val isGranted = hasVideoPermission(context)
    LaunchedEffect(isGranted) {
        if (isGranted) {
            onPermissionGranted()
        }
    }

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
                    isLightStatusBars = false,
                    isLightNavBars = false
                )
                val window = (view as? DialogWindowProvider)?.window
                window?.statusBarColor = android.graphics.Color.BLACK
                window?.navigationBarColor = android.graphics.Color.BLACK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                onDispose {
                    window?.statusBarColor = android.graphics.Color.TRANSPARENT
                    window?.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
            }

            // Liquid glass backdrop with click outside to dismiss - deep dark backdrop covering entire notch & display
            val scrimAlpha = (0.55f + 0.30f * alphaRatio).coerceIn(0.50f, 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Dialog Card (Liquid glass frosted surface)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(cardBackground)
                        .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Icon Header
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(AccentGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(
                                drawableRes = R.drawable.lumora_folder_with_files,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Media & Storage Access",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "To scan your video albums and load subtitles from device storage, please grant media access.",
                            fontSize = 13.sp,
                            color = secondaryText,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.5.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Feature highlights
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(bulletBoxBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_library,
                                    contentDescription = null,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Automatic folder & video detection",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_subtitles,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Full subtitle (.srt, .ass, .vtt) loading",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_shield_check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "100% offline & local on your device",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Not now
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF1E293B).copy(alpha = (0.45f + 0.55f * alphaRatio).coerceIn(0.3f, 1f)) else Color(0xFFF1F5F9).copy(alpha = (0.50f + 0.50f * alphaRatio).coerceIn(0.4f, 1f)))
                                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .clickable { onDismiss() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Not Now",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryText
                                )
                            }

                            // Primary Action: Grant Access (Request Multiple Permissions)
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AccentGradient)
                                    .clickable {
                                        hasAttemptedRequest = true
                                        if (onRequestPermissions != null) {
                                            onRequestPermissions()
                                        } else {
                                            val perms = getRequiredMediaPermissions()
                                            mediaPermissionLauncher.launch(perms)
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Grant Access",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Secondary fallback: App Settings button if permission was denied or requested previously
                        if (hasAttemptedRequest && !hasVideoPermission(context)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            openAllFilesAccessSettings(context)
                                        } else {
                                            openAppSettings(context)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_settings_minimalistic,
                                    contentDescription = null,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Open All Files Access" else "Open App Settings",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentSkyBlue
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
 * Checks if notification permission is granted on Android 13+ (API 33+).
 * Returns true automatically on API < 33.
 */
fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

