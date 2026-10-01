package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Ensures any Compose Dialog window expands strictly edge-to-edge across 100% of the display,
 * including behind the status bar, camera notch / punch-hole cutout, and navigation bar,
 * with fully transparent system window backgrounds.
 */
fun configureEdgeToEdgeDialogWindow(
    view: android.view.View,
    isLightStatusBars: Boolean = false,
    isLightNavBars: Boolean = false
) {
    fun applyConfig() {
        var window: android.view.Window? = (view as? DialogWindowProvider)?.window
        if (window == null) {
            var current: android.view.ViewParent? = view.parent
            while (current != null) {
                if (current is DialogWindowProvider) {
                    window = current.window
                    break
                }
                current = current.parent
            }
        }

        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            win.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                win.attributes = win.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            win.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            win.decorView.setPadding(0, 0, 0, 0)
            win.decorView.fitsSystemWindows = false
            win.statusBarColor = android.graphics.Color.TRANSPARENT
            win.navigationBarColor = android.graphics.Color.TRANSPARENT
            win.setDimAmount(0.0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Prevent the system from drawing its own light/dark contrast
                // scrim behind the status/nav bars, which otherwise shows up as
                // a stray solid strip on top of our own transparent scrim,
                // breaking the seamless edge-to-edge look of the dialog.
                win.isStatusBarContrastEnforced = false
                win.isNavigationBarContrastEnforced = false
            }

            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = isLightStatusBars
            insetsController.isAppearanceLightNavigationBars = isLightNavBars
        }
    }

    applyConfig()
    view.post { applyConfig() }
}

/**
 * Liquid Glass design token constants:
 * Matches SettingsSheet backdrop and opacity level exactly (0.65f dark / 0.70f light frosted glass, soft scrim)
 */
object LiquidGlassDefaults {
    val DarkGlassFill = Color(0xFF0B1120).copy(alpha = 0.65f)
    val DarkGlassFillElevated = Color(0xFF1E293B).copy(alpha = 0.35f)
    val LightGlassFill = Color(0xFFF8FAFC).copy(alpha = 0.70f)
    val LightGlassFillElevated = Color(0xFFFFFFFF).copy(alpha = 0.45f)

    val DarkGlassBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            Color(0xFF38BDF8).copy(alpha = 0.40f),
            Color(0xFF818CF8).copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.10f)
        )
    )

    val LightGlassBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF38BDF8).copy(alpha = 0.45f),
            Color(0xFF6366F1).copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.60f),
            Color(0xFF0284C7).copy(alpha = 0.30f)
        )
    )

    val BackdropScrimDark = Color.Transparent
    val BackdropScrimLight = Color.Transparent

    fun getDynamicGlassFill(isDark: Boolean, transparencyPercent: Float = 65f): Color {
        val alphaRatio = (transparencyPercent / 100f).coerceIn(0.10f, 1.0f)
        return if (isDark) {
            if (alphaRatio >= 0.99f) Color(0xFF0B1120)
            else Color(0xFF0B1120).copy(alpha = (0.12f + 0.85f * alphaRatio).coerceIn(0.10f, 1.0f))
        } else {
            if (alphaRatio >= 0.99f) Color(0xFFFFFFFF)
            else Color(0xFFFFFFFF).copy(alpha = (0.12f + 0.85f * alphaRatio).coerceIn(0.10f, 1.0f))
        }
    }

    fun getDynamicGlassFillElevated(isDark: Boolean, transparencyPercent: Float = 65f): Color {
        val alphaRatio = (transparencyPercent / 100f).coerceIn(0.10f, 1.0f)
        return if (isDark) {
            Color(0xFF1E293B).copy(alpha = (0.08f + 0.50f * alphaRatio).coerceIn(0.08f, 0.90f))
        } else {
            Color(0xFFFFFFFF).copy(alpha = (0.08f + 0.50f * alphaRatio).coerceIn(0.08f, 0.90f))
        }
    }
}

@Composable
fun LiquidGlassCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    transparencyPercent: Float = 65f,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val alphaRatio = (transparencyPercent / 100f).coerceIn(0.10f, 1.0f)
    val fill = LiquidGlassDefaults.getDynamicGlassFill(isDark, transparencyPercent)
    val borderBrush = if (isDark) LiquidGlassDefaults.DarkGlassBorderBrush else LiquidGlassDefaults.LightGlassBorderBrush

    Box(
        modifier = modifier
            .then(
                if (alphaRatio > 0.35f) {
                    Modifier.shadow(
                        elevation = elevation * alphaRatio,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.20f * alphaRatio),
                        spotColor = Color.Black.copy(alpha = 0.30f * alphaRatio)
                    )
                } else Modifier
            )
            .clip(shape)
            .background(fill)
            .border(1.2.dp, borderBrush, shape)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun LiquidGlassDialog(
    isDark: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            configureEdgeToEdgeDialogWindow(
                view = view,
                isLightStatusBars = !isDark,
                isLightNavBars = !isDark
            )
            onDispose {}
        }

        // Clean transparent backdrop behind popup (no dark UI layer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            // Click interceptor so clicking inside dialog does not dismiss
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                content = content
            )
        }
    }
}

@Composable
fun LiquidGlassBottomSheetDialog(
    isDark: Boolean,
    onDismissRequest: () -> Unit,
    transparencyPercent: Float = 65f,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val fill = LiquidGlassDefaults.getDynamicGlassFill(isDark, transparencyPercent)
    val borderBrush = if (isDark) LiquidGlassDefaults.DarkGlassBorderBrush else LiquidGlassDefaults.LightGlassBorderBrush
    val bottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            configureEdgeToEdgeDialogWindow(
                view = view,
                isLightStatusBars = !isDark,
                isLightNavBars = !isDark
            )
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            val alphaRatio = (transparencyPercent / 100f).coerceIn(0.10f, 1.0f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        if (alphaRatio > 0.35f) {
                            Modifier.shadow(
                                elevation = 16.dp * alphaRatio,
                                shape = bottomSheetShape,
                                ambientColor = Color.Black.copy(alpha = 0.20f * alphaRatio),
                                spotColor = Color.Black.copy(alpha = 0.30f * alphaRatio)
                            )
                        } else Modifier
                    )
                    .clip(bottomSheetShape)
                    .background(fill)
                    .border(1.2.dp, borderBrush, bottomSheetShape)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
