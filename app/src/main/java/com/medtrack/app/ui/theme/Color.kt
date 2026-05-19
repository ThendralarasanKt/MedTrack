package com.medtrack.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Clean clinical palette inspired by native iOS utility apps.
val PaperBackground = Color(0xFFF5F5F7)
val PaperSurface = Color(0xFFFFFFFF)
val PaperTextPrimary = Color(0xFF1D1D1F)
val PaperTextSecondary = Color(0xFF6E6E73)
val PaperAccent = Color(0xFF007AFF)
val PaperOutline = Color(0xFFE5E5EA)
val PaperHighlight = Color(0xFFEAF2FF)

// Custom Palette for CompositionLocal
data class PaperCustomColors(
    val background: Color = PaperBackground,
    val surface: Color = PaperSurface,
    val textPrimary: Color = PaperTextPrimary,
    val textSecondary: Color = PaperTextSecondary,
    val accent: Color = PaperAccent,
    val outline: Color = PaperOutline,
    val highlight: Color = PaperHighlight
)

val LocalPaperColors = staticCompositionLocalOf { PaperCustomColors() }
