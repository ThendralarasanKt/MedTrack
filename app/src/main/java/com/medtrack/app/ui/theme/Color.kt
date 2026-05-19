package com.medtrack.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Classic Paper Reading Aesthetic Palette
val PaperBackground = Color(0xFFF8F5E6)
val PaperSurface = Color(0xFFFDFBF7)
val PaperTextPrimary = Color(0xFF292421)
val PaperTextSecondary = Color(0xFF6D645D)
val PaperAccent = Color(0xFF8B5E3C)
val PaperOutline = Color(0xFFD8CDB6)
val PaperHighlight = Color(0xFFFFD54F)

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
