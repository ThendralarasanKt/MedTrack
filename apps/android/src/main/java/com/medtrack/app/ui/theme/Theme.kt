package com.medtrack.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Google Native clinical theme (Material 3).
 *
 * White surfaces on #F8F9FA with Google Blue accents, tuned for ward lighting.
 */
private val ClinicalColorScheme = lightColorScheme(
    primary = PaperAccent,
    onPrimary = Color.White,
    primaryContainer = PaperHighlight,
    onPrimaryContainer = PaperAccent,

    secondary = PaperTextSecondary,
    onSecondary = PaperSurface,
    secondaryContainer = Color(0xFFF1F3F4),
    onSecondaryContainer = PaperTextPrimary,

    tertiary = Color(0xFF7627BB),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FD),
    onTertiaryContainer = Color(0xFF7627BB),

    background = PaperBackground,
    onBackground = PaperTextPrimary,

    surface = PaperSurface,
    onSurface = PaperTextPrimary,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = PaperTextSecondary,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurface,
    surfaceContainerHighest = PaperSurface,

    outline = PaperOutline,
    outlineVariant = Color(0xFFE8EAED),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFD93025)
)

private val ClinicalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun MedTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ClinicalColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    CompositionLocalProvider(LocalPaperColors provides PaperCustomColors()) {
        MaterialTheme(
            colorScheme = ClinicalColorScheme,
            typography = Typography,
            shapes = ClinicalShapes,
            content = content
        )
    }
}
