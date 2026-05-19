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
 * Classic Paper Theme for MedTrack
 * 
 * WHY: This theme uses a warm, eye-friendly palette (#F8F5E6) to reduce 
 * eye strain for doctors during long shifts. It mimics a high-quality 
 * medical paper aesthetic.
 */
private val PaperColorScheme = lightColorScheme(
    primary = PaperAccent,
    onPrimary = PaperSurface,
    primaryContainer = PaperHighlight,
    onPrimaryContainer = PaperTextPrimary,
    
    secondary = PaperTextSecondary,
    onSecondary = PaperSurface,
    secondaryContainer = PaperSurface,
    onSecondaryContainer = PaperTextPrimary,
    
    tertiary = PaperHighlight,
    onTertiary = PaperTextPrimary,
    tertiaryContainer = PaperHighlight,
    onTertiaryContainer = PaperTextPrimary,
    
    background = PaperBackground,
    onBackground = PaperTextPrimary,
    
    surface = PaperSurface,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = PaperTextSecondary,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurface,
    surfaceContainerHighest = PaperSurface,
    
    outline = PaperOutline,
    outlineVariant = PaperOutline,
    error = Color(0xFFB3261E),
    onError = PaperSurface
)

private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp)
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
            // Styling Requirement 3: Dark Status Bar Icons
            window.statusBarColor = PaperColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    // Styling Requirement 4: CompositionLocal for custom hex values
    CompositionLocalProvider(LocalPaperColors provides PaperCustomColors()) {
        MaterialTheme(
            colorScheme = PaperColorScheme,
            typography = Typography,
            shapes = PaperShapes,
            content = content
        )
    }
}
