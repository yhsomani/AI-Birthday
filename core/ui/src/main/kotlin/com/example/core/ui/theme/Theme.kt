package com.example.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val RelateDarkColorScheme = darkColorScheme(
    primary = RelatePrimary,
    onPrimary = RelateOnPrimary,
    primaryContainer = RelatePrimaryContainer,
    secondary = RelateSecondary,
    onSecondary = RelateOnSecondary,
    tertiary = RelateTertiary,
    onTertiary = RelateOnTertiary,
    background = RelateDarkBackground,
    onBackground = RelateOnBackground,
    surface = RelateSurface,
    onSurface = RelateOnSurface,
    surfaceVariant = RelateSurfaceVariant,
    onSurfaceVariant = RelateOnSurfaceVariant,
    outline = RelateOutline,
    error = RelateError,
    onError = RelateOnError,
)

@Composable
fun RelateAITheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRelateSemanticColors provides RelateDarkSemanticColors) {
        MaterialTheme(
            colorScheme = RelateDarkColorScheme,
            typography = RelateTypography,
            content = content,
        )
    }
}
