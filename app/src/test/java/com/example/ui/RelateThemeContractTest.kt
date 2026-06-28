package com.example.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateAITheme
import com.example.core.ui.theme.RelateCard
import com.example.core.ui.theme.RelateCardBorder
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnPrimary
import com.example.core.ui.theme.RelateOnSurface
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateSurface
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.core.ui.theme.RelateWarning
import com.example.core.ui.theme.relateSemanticColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class RelateThemeContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultTheme_usesValidatedDarkColorScheme() {
        var primary: Color? = null
        var background: Color? = null
        var onBackground: Color? = null
        var surface: Color? = null
        var onSurface: Color? = null
        var surfaceVariant: Color? = null
        var onSurfaceVariant: Color? = null
        var cardContainer: Color? = null
        var cardOutline: Color? = null
        var success: Color? = null
        var onSuccess: Color? = null
        var successContainer: Color? = null
        var warning: Color? = null
        var onWarning: Color? = null
        var warningContainer: Color? = null
        var info: Color? = null
        var onInfo: Color? = null
        var infoContainer: Color? = null

        composeRule.setContent {
            RelateAITheme {
                val semanticColors = MaterialTheme.relateSemanticColors
                primary = MaterialTheme.colorScheme.primary
                background = MaterialTheme.colorScheme.background
                onBackground = MaterialTheme.colorScheme.onBackground
                surface = MaterialTheme.colorScheme.surface
                onSurface = MaterialTheme.colorScheme.onSurface
                surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                cardContainer = semanticColors.cardContainer
                cardOutline = semanticColors.cardOutline
                success = semanticColors.success
                onSuccess = semanticColors.onSuccess
                successContainer = semanticColors.successContainer
                warning = semanticColors.warning
                onWarning = semanticColors.onWarning
                warningContainer = semanticColors.warningContainer
                info = semanticColors.info
                onInfo = semanticColors.onInfo
                infoContainer = semanticColors.infoContainer
            }
        }

        composeRule.runOnIdle {
            assertEquals(RelatePrimary, primary)
            assertEquals(RelateDarkBackground, background)
            assertEquals(RelateOnBackground, onBackground)
            assertEquals(RelateSurface, surface)
            assertEquals(RelateOnSurface, onSurface)
            assertEquals(RelateSurfaceVariant, surfaceVariant)
            assertEquals(RelateOnSurfaceVariant, onSurfaceVariant)
            assertEquals(RelateCard, cardContainer)
            assertEquals(RelateCardBorder, cardOutline)
            assertEquals(RelateSuccess, success)
            assertEquals(RelateDarkBackground, onSuccess)
            assertEquals(RelateSuccess.copy(alpha = RelateAlpha.feedbackContainer), successContainer)
            assertEquals(RelateWarning, warning)
            assertEquals(RelateDarkBackground, onWarning)
            assertEquals(RelateWarning.copy(alpha = RelateAlpha.feedbackContainer), warningContainer)
            assertEquals(RelatePrimary, info)
            assertEquals(RelateOnPrimary, onInfo)
            assertEquals(RelatePrimary.copy(alpha = RelateAlpha.feedbackContainer), infoContainer)
        }
    }
}
