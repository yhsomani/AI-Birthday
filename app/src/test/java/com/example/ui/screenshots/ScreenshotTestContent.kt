package com.example.ui.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import com.example.core.ui.theme.RelateAITheme

internal const val DefaultFontScale = 1f
internal const val LargeFontScale = 1.3f

internal fun ComposeContentTestRule.setRelateScreenshotContent(
    fontScale: Float = DefaultFontScale,
    content: @Composable () -> Unit,
) {
    setContent {
        val currentDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = currentDensity.density,
                fontScale = fontScale,
            ),
        ) {
            RelateAITheme {
                content()
            }
        }
    }
}
