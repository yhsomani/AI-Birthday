package com.example.ui.screens.analytics

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.AnalyticsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class AnalyticsScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun populatedAnalytics_dispatchesActivityAndExportActions() {
        val actions = mutableListOf<String>()

        composeRule.setAnalyticsContent(
            state = { populatedState() },
            onNavigateToActivityHistory = { actions += "activity" },
            onExportReport = { actions += "export" },
        )

        composeRule.onNodeWithText(context.getString(R.string.analytics))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AnalyticsScreenTestTags.ACTIVITY_HISTORY_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(AnalyticsScreenTestTags.EXPORT_BUTTON)
            .assertIsDisplayed()
            .performClick()

        composeRule.assertTaggedSectionVisible(AnalyticsScreenTestTags.MONTHLY_SECTION)
        composeRule.onNodeWithText("Jan").assertIsDisplayed()
        composeRule.assertTaggedSectionVisible(AnalyticsScreenTestTags.DISTRIBUTION_SECTION)
        composeRule.onNodeWithText(context.getString(R.string.contact_filter_family)).assertIsDisplayed()
        composeRule.assertTaggedSectionVisible(AnalyticsScreenTestTags.GROWTH_SECTION)
        composeRule.onNodeWithText(context.getString(R.string.analytics_delivery_reliability)).assertIsDisplayed()
        composeRule.assertTaggedSectionVisible(AnalyticsScreenTestTags.NEGLECTED_SECTION)
        composeRule.onNodeWithText("Riya (24)").assertIsDisplayed()

        assertEquals(listOf("activity", "export"), actions)
    }

    @Test
    fun loadingEmptyAndExportingStates_renderExpectedControls() {
        var state by mutableStateOf(AnalyticsUiState(isLoading = true))

        composeRule.setAnalyticsContent(state = { state })

        composeRule.onNodeWithTag(AnalyticsScreenTestTags.LOADING)
            .assertIsDisplayed()

        state = AnalyticsUiState(isLoading = false)
        composeRule.assertTaggedSectionVisible(AnalyticsScreenTestTags.MONTHLY_SECTION)
        composeRule.onNodeWithText(context.getString(R.string.analytics_no_wishes_this_year))
            .assertIsDisplayed()
        composeRule.assertTaggedSectionVisible(AnalyticsScreenTestTags.NEGLECTED_SECTION)
        composeRule.onNodeWithText(context.getString(R.string.analytics_no_neglected_contacts))
            .assertIsDisplayed()

        state = AnalyticsUiState(isLoading = false, isExporting = true)
        composeRule.onNodeWithTag(AnalyticsScreenTestTags.EXPORT_BUTTON)
            .assertIsNotEnabled()
    }

    private fun ComposeContentTestRule.setAnalyticsContent(
        state: () -> AnalyticsUiState,
        onNavigateToActivityHistory: () -> Unit = {},
        onExportReport: () -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                AnalyticsContent(
                    state = state(),
                    onNavigateToActivityHistory = onNavigateToActivityHistory,
                    onExportReport = onExportReport,
                )
            }
        }
    }

    private fun ComposeContentTestRule.assertTaggedSectionVisible(tag: String) {
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun populatedState() = AnalyticsUiState(
        totalWishesSent = 8,
        totalContacts = 12,
        pendingApprovals = 3,
        upcomingEventsCount = 4,
        relationshipCounts = mapOf(
            "FAMILY" to 5,
            "FRIEND" to 4,
            "WORK" to 2,
            "CLOSE_FRIEND" to 1,
        ),
        healthCounts = mapOf(
            "Healthy (70%+)" to 6,
            "Needs Attention" to 4,
            "At Risk" to 2,
        ),
        monthlyCounts = listOf("Jan" to 2f, "Feb" to 4f),
        deliveryReliabilityPercent = 88,
        responseRatePercent = 50,
        personalizationCoveragePercent = 75,
        topNeglectedContacts = listOf("Riya (24)"),
        isLoading = false,
    )
}
