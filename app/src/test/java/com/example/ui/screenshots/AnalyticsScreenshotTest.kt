package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.analytics.AnalyticsContent
import com.example.ui.screens.analytics.AnalyticsScreenTestTags
import com.example.ui.viewmodel.AnalyticsUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyticsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun analyticsPopulated_compactPhone() {
        setAnalyticsContent(state = populatedAnalyticsState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_populated_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun analyticsPopulated_typicalPhone() {
        setAnalyticsContent(state = populatedAnalyticsState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_populated_typical_phone.png")
    }

    @Test
    fun analyticsReportingSections_compactPhoneLargeFont() {
        setAnalyticsContent(
            state = populatedAnalyticsState(),
            fontScale = LargeFontScale,
        )

        scrollTo(AnalyticsScreenTestTags.NEGLECTED_SECTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_reporting_sections_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun analyticsReportingSections_compactPhoneHindiLargeFont() {
        setAnalyticsContent(
            state = populatedAnalyticsState().copy(
                topNeglectedContacts = listOf(
                    "रिया मेहता (24)",
                    "अर्जुन राव (31)",
                    "माया पटेल (36)",
                ),
            ),
            fontScale = LargeFontScale,
        )

        scrollTo(AnalyticsScreenTestTags.NEGLECTED_SECTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_reporting_sections_compact_phone_hindi_large_font.png")
    }

    @Test
    fun analyticsEmpty_compactPhone() {
        setAnalyticsContent(state = AnalyticsUiState(isLoading = false))

        scrollTo(AnalyticsScreenTestTags.MONTHLY_SECTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun analyticsEmpty_typicalPhone() {
        setAnalyticsContent(state = AnalyticsUiState(isLoading = false))

        scrollTo(AnalyticsScreenTestTags.MONTHLY_SECTION)
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_empty_typical_phone.png")
    }

    @Test
    fun analyticsLoading_compactPhone() {
        setAnalyticsContent(
            state = AnalyticsUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun analyticsLoading_typicalPhone() {
        setAnalyticsContent(
            state = AnalyticsUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/analytics_loading_typical_phone.png")
    }

    private fun setAnalyticsContent(
        state: AnalyticsUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            AnalyticsContent(
                state = state,
                onNavigateToActivityHistory = {},
                onExportReport = {},
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(tag))
    }

    private fun populatedAnalyticsState(): AnalyticsUiState {
        return AnalyticsUiState(
            totalWishesSent = 42,
            totalContacts = 18,
            pendingApprovals = 5,
            upcomingEventsCount = 7,
            relationshipCounts = mapOf(
                "FAMILY" to 6,
                "FRIEND" to 7,
                "WORK" to 3,
                "CLOSE_FRIEND" to 2,
            ),
            healthCounts = mapOf(
                "Healthy (70%+)" to 9,
                "Needs Attention" to 6,
                "At Risk" to 3,
            ),
            monthlyCounts = listOf(
                "Jan" to 3f,
                "Feb" to 5f,
                "Mar" to 2f,
                "Apr" to 8f,
                "May" to 6f,
                "Jun" to 9f,
            ),
            deliveryReliabilityPercent = 92,
            responseRatePercent = 64,
            personalizationCoveragePercent = 78,
            topNeglectedContacts = listOf(
                "Riya Mehta (24)",
                "Arjun Rao (31)",
                "Maya Patel (36)",
            ),
            isLoading = false,
        )
    }

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
    }
}
