package com.example.ui

import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTokensTest {

    @Test
    fun spacingTokens_followFourDpGridForReusableLayout() {
        assertEquals(4f, RelateSpacing.xs.value)
        assertEquals(8f, RelateSpacing.sm.value)
        assertEquals(12f, RelateSpacing.md.value)
        assertEquals(16f, RelateSpacing.lg.value)
        assertEquals(RelateSpacing.lg, RelateSpacing.screenHorizontal)
        assertEquals(RelateSpacing.lg, RelateSpacing.cardContent)
    }

    @Test
    fun shapeTokens_keepCardsAndControlsCompact() {
        assertTrue("Cards should stay at 8 dp radius or below.", RelateRadius.card.value <= 8f)
        assertEquals(8f, RelateRadius.control.value)
        assertEquals(20f, RelateRadius.pill.value)
    }

    @Test
    fun sizeTokens_keepPrimaryActionsAccessible() {
        assertTrue(
            "Primary buttons should meet or exceed the target touch height.",
            RelateSize.primaryButtonHeight >= RelateSize.minTouchTarget,
        )
        assertEquals(6f, RelateSize.statusDot.value)
        assertEquals(24f, RelateSize.iconLg.value)
        assertTrue(
            "Loading panels should reserve enough height to avoid first-paint layout jump.",
            RelateSize.loadingPanelHeight >= RelateSize.primaryButtonHeight,
        )
        assertTrue(
            "Action cards need enough height for icon, title, and wrapped supporting copy.",
            RelateSize.actionCardMinHeight >= RelateSize.minTouchTarget,
        )
        assertTrue(
            "Responsive action-card breakpoint must fit two compact action cards.",
            RelateSize.actionGridBreakpoint.value >= RelateSize.actionCardMinHeight.value * 2,
        )
        assertTrue(
            "Progress strokes should stay visible without becoming heavy.",
            RelateSize.progressStroke.value in 1f..4f,
        )
    }

    @Test
    fun alphaTokens_remainValidFractions() {
        listOf(
            RelateAlpha.disabled,
            RelateAlpha.subtle,
            RelateAlpha.feedbackContainer,
            RelateAlpha.fieldContainer,
        ).forEach { alpha ->
            assertTrue("Alpha tokens must be between 0 and 1.", alpha in 0f..1f)
        }
    }

    @Test
    fun elevationTokens_keepSurfacesSubtle() {
        assertTrue("App bar elevation should stay subtle.", RelateElevation.appBar.value in 0f..4f)
    }
}
