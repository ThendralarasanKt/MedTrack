package com.medtrack.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Google Native clinical palette from spec/mockup. */
val PaperBackground = Color(0xFFF8F9FA)
val PaperSurface = Color(0xFFFFFFFF)
val PaperTextPrimary = Color(0xFF202124)
val PaperTextSecondary = Color(0xFF5F6368)
val PaperAccent = Color(0xFF1A73E8)
val PaperOutline = Color(0xFFE0E2E6)
val PaperHighlight = Color(0xFFE8F0FE)

data class PaperCustomColors(
    val background: Color = PaperBackground,
    val surface: Color = PaperSurface,
    val textPrimary: Color = PaperTextPrimary,
    val textSecondary: Color = PaperTextSecondary,
    val accent: Color = PaperAccent,
    val outline: Color = PaperOutline,
    val highlight: Color = PaperHighlight,
    val danger: Color = Color(0xFFD93025),
    val dangerContainer: Color = Color(0xFFFCE8E6),
    val warning: Color = Color(0xFFB06000),
    val warningContainer: Color = Color(0xFFFEF7E0),
    val success: Color = Color(0xFF137333),
    val successContainer: Color = Color(0xFFE6F4EA),
    val purple: Color = Color(0xFF7627BB),
    val purpleContainer: Color = Color(0xFFF3E8FD),
    val searchFill: Color = Color(0xFFF1F3F4),
    val mutedLine: Color = Color(0xFFE8EAED),
    val body: Color = Color(0xFF3C4043)
)

val LocalPaperColors = staticCompositionLocalOf { PaperCustomColors() }
