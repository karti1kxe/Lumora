package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Accent Gradient Colors (Sky Blue to Magenta/Pink default)
var AccentSkyBlue = Color(0xFF38BDF8)
var AccentPink = Color(0xFFEC4899)
var AccentGradient = Brush.linearGradient(
    colors = listOf(AccentSkyBlue, AccentPink)
)

// Light Mode Colors
var LightBackgroundStart = Color(0xFFF8FAFC)
var LightBackgroundEnd = Color(0xFFF8FAFC)
var LightGlassSurface = Color(0xFFF8FAFC)
var LightGlassBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)
val LightIconContainerBg = Color(0xFFE0F2FE)
val LightIconTint = Color(0xFF0284C7)

// Dark Mode Colors
var DarkBackgroundStart = Color(0xFF090D16)
var DarkBackgroundEnd = Color(0xFF090D16)
var DarkGlassSurface = Color(0xFF090D16)
var DarkGlassBorder = Color(0xFF243350)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkIconContainerBg = Color(0xFF0F2B48)
val DarkIconTint = Color(0xFF38BDF8)

// AMOLED Black Colors
val AmoledBlackBackground = Color(0xFF000000)
val AmoledBlackSurface = Color(0xFF07090E)
val AmoledBlackBorder = Color(0xFF1E293B)

