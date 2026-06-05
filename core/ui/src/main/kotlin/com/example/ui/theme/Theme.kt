package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════
// RelateAI Material 3 Theme
// Neon Glassmorphic Dark Mode
// ═══════════════════════════════════════════════

private val RelateAIDarkColorScheme = darkColorScheme(
    // Primary
    primary = NeonViolet,
    onPrimary = TextOnPrimary,
    primaryContainer = NeonVioletContainer,
    onPrimaryContainer = NeonVioletLight,

    // Secondary
    secondary = ElectricCyan,
    onSecondary = OnElectricCyan,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricCyanLight,

    // Tertiary
    tertiary = CyberRose,
    onTertiary = OnCyberRose,
    tertiaryContainer = CyberRoseContainer,
    onTertiaryContainer = CyberRoseLight,

    // Error
    error = ErrorRed,
    onError = OnError,
    errorContainer = ErrorRedContainer,
    onErrorContainer = ErrorRed,

    // Background
    background = ObsidianBlack,
    onBackground = TextPrimary,

    // Surface
    surface = ObsidianBlack,
    onSurface = TextPrimary,
    surfaceVariant = DarkSlate,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,

    // Outline
    outline = Outline,
    outlineVariant = OutlineVariant,

    // Inverse
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,

    // Scrim
    scrim = ObsidianBlack
)

@Composable
fun RelateAITheme(
    content: @Composable () -> Unit
) {
    val colorScheme = RelateAIDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ObsidianBlack.toArgb()
            window.navigationBarColor = ObsidianBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RelateAITypography,
        shapes = RelateAIShapes,
        content = content
    )
}
