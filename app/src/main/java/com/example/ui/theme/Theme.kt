package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.ui.state.AppThemeVariant
import com.example.ui.state.ThemeMode
import androidx.compose.ui.text.font.FontFamily

val LocalIsDarkTheme = compositionLocalOf { false }
val LocalIsSystemFontEnabled = compositionLocalOf { false }
val LocalAppFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun isAppInDarkTheme(): Boolean {
    val localDark = LocalIsDarkTheme.current
    if (localDark) return true
    return MaterialTheme.colorScheme.surface.luminance() < 0.5f
}

private fun getPrimaryForVariant(variant: AppThemeVariant, isDark: Boolean): Color = variant.primaryColor

private fun getSecondaryForVariant(variant: AppThemeVariant): Color = variant.secondaryColor

@Composable
fun VideoPlayerTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    appScale: Float = 75f,
    appTheme: AppThemeVariant = AppThemeVariant.DYNAMIC,
    amoledBlackMode: Boolean = false,
    useSystemFont: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primaryColor = appTheme.primaryColor
    val secondaryColor = appTheme.secondaryColor

    // Keep the existing application's global gradient language, but make it follow
    // the selected App Theme everywhere the shared AccentGradient is used.
    AccentSkyBlue = primaryColor
    AccentPink = secondaryColor
    AccentGradient = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(primaryColor, secondaryColor)
    )

    // Update global background/surface colors to match the theme variant
    LightBackgroundStart = appTheme.lightBgColor
    LightBackgroundEnd = appTheme.lightBgColor
    LightGlassSurface = appTheme.lightCardColor
    LightGlassBorder = appTheme.lightBorderColor

    val effectiveAmoled = amoledBlackMode && isDark

    if (effectiveAmoled) {
        DarkBackgroundStart = AmoledBlackBackground
        DarkBackgroundEnd = AmoledBlackBackground
        DarkGlassSurface = AmoledBlackSurface
        DarkGlassBorder = AmoledBlackBorder
    } else {
        DarkBackgroundStart = appTheme.darkBgColor
        DarkBackgroundEnd = appTheme.darkBgColor
        DarkGlassSurface = appTheme.darkCardColor
        DarkGlassBorder = appTheme.darkBorderColor
    }

    val colorScheme = if (isDark) {
        if (effectiveAmoled) {
            darkColorScheme(
                primary = primaryColor,
                secondary = secondaryColor,
                background = AmoledBlackBackground,
                surface = AmoledBlackSurface,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
        } else {
            darkColorScheme(
                primary = primaryColor,
                secondary = secondaryColor,
                background = appTheme.darkBgColor,
                surface = appTheme.darkCardColor,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = DarkTextPrimary,
                onSurface = DarkTextPrimary
            )
        }
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = appTheme.lightBgColor,
            surface = appTheme.lightCardColor,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary
        )
    }

    val currentDensity = LocalDensity.current
    // 1% maps to 0.85f (85% min application size), 100% maps to 1.00f (100% full scale)
    val scaleFactor = (0.85f + 0.15f * ((appScale.coerceIn(1f, 100f) - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    val scaledDensity = Density(
        density = currentDensity.density * scaleFactor,
        fontScale = currentDensity.fontScale * scaleFactor
    )

    val typography = createLumoraTypography(useSystemFont)
    val appFontFamily = getAppFontFamily(useSystemFont)
    val defaultTextStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = appFontFamily)

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalIsDarkTheme provides isDark,
        LocalIsSystemFontEnabled provides useSystemFont,
        LocalAppFontFamily provides appFontFamily,
        androidx.compose.material3.LocalTextStyle provides defaultTextStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

