package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RelateAIColors.Primary,
    onPrimary = Color.White,
    primaryContainer = RelateAIColors.PrimaryDark,
    onPrimaryContainer = RelateAIColors.PrimaryLight,
    secondary = RelateAIColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = RelateAIColors.SecondaryDark,
    onSecondaryContainer = RelateAIColors.SecondaryLight,
    tertiary = RelateAIColors.Tertiary,
    onTertiary = Color.White,
    error = RelateAIColors.AtRisk,
    onError = Color.White,
    background = RelateAIColors.BackgroundDark,
    onBackground = RelateAIColors.OnSurfaceDark,
    surface = RelateAIColors.SurfaceDark,
    onSurface = RelateAIColors.OnSurfaceDark,
    surfaceVariant = RelateAIColors.SurfaceVariantDark,
    onSurfaceVariant = RelateAIColors.OnSurfaceVariantDark,
    outline = RelateAIColors.OutlineDark,
    outlineVariant = RelateAIColors.OutlineDark.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = RelateAIColors.Primary,
    onPrimary = Color.White,
    primaryContainer = RelateAIColors.PrimaryLight,
    onPrimaryContainer = RelateAIColors.PrimaryDark,
    secondary = RelateAIColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = RelateAIColors.SecondaryLight,
    onSecondaryContainer = RelateAIColors.SecondaryDark,
    tertiary = RelateAIColors.Tertiary,
    onTertiary = Color.White,
    error = RelateAIColors.AtRisk,
    onError = Color.White,
    background = RelateAIColors.Background,
    onBackground = RelateAIColors.OnSurface,
    surface = RelateAIColors.Surface,
    onSurface = RelateAIColors.OnSurface,
    surfaceVariant = RelateAIColors.SurfaceVariant,
    onSurfaceVariant = RelateAIColors.OnSurfaceVariant,
    outline = RelateAIColors.Outline,
    outlineVariant = RelateAIColors.Outline.copy(alpha = 0.5f)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
