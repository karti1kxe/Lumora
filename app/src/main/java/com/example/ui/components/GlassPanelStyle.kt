package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The app-size setting (1..100), provided once by the app theme so every panel / sheet can follow it. */
val LocalAppScale = compositionLocalOf { 75f }

/** The "UI Glass Blur & Opacity" setting (10..100), provided once by the app theme. Not used by the video player's own controls. */
val LocalPanelTransparency = compositionLocalOf { 85f }

/**
 * Background of the app's panels and bottom sheets (Delete, Download, Sort & View, Settings, playlist sheets ...): the colour
 * of the Delete panel. The opacity follows the glass slider gently (about 73 % at 10 %, 94 % at the default 85 %, 98 % at 100 %).
 * The video player's own control panels keep their separate look.
 */
fun glassPanelColor(isDark: Boolean, glassBlurTransparency: Float): Color {
    val factor = (glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
    val alpha = (0.70f + 0.28f * factor).coerceIn(0.70f, 0.98f)
    // Theme = System and the phone is in light mode: every panel / popup uses the same white as the folder & video cards
    if (LumoraSurfaceStyle.systemMode && !isDark) return Color.White.copy(alpha = alpha)
    return (if (isDark) Color(0xFF1B2630) else Color(0xFFDAE3E9)).copy(alpha = alpha)
}

/** Same density factor the Settings and Sort & View panels use, so a sheet gets bigger / smaller with the app-size setting. */
@Composable
fun PanelScaled(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val appScale = LocalAppScale.current
    val factor = (0.85f + 0.15f * ((appScale.coerceIn(1f, 100f) - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaled = remember(base.density, base.fontScale, factor) {
        Density(density = base.density * factor, fontScale = base.fontScale * factor)
    }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/** Set by [com.example.ui.theme.VideoPlayerTheme]: true while Settings > Appearance is on "System". */
object LumoraSurfaceStyle {
    @Volatile var systemMode: Boolean = false
}
