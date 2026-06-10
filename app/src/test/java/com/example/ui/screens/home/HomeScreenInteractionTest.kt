package com.example.ui.screens.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.RelationshipPlannerItem
import com.example.ui.viewmodel.UpcomingBirthday
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HomeScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun dashboardCardsAndLinks_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(
                syncError = "Google sync failed",
                readinessTitle = "Setup needs attention",
                readinessDetail = "Open AI Doctor to fix contact sync.",
            ),
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
            .assertIsDisplayed()
            .performClick()

        clickTaggedCard(HomeScreenTestTags.READINESS_BANNER)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_ANALYTICS)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_ACTIVITY_HISTORY)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_STYLE_COACH)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_AUTOMATION_SETUP)
        clickTaggedCard(HomeScreenTestTags.QUICK_ACTION_BACKUP_RESTORE)
        clickTaggedCard(HomeScreenTestTags.PLANNER_ITEM_PREFIX + "contact-1")

        assertEquals(
            listOf(
                "settings",
                "automation",
                "analytics",
                "activity",
                "style",
                "automation",
                "backup",
                "contact:contact-1",
            ),
            actions,
        )
    }

    @Test
    fun syncErrorControls_dispatchRetryAndDismiss() {
        val actions = mutableListOf<String>()

        setHomeContent(
            actions = actions,
            state = populatedHomeState(syncError = "Unable to sync contacts."),
        )

        composeRule.onNodeWithTag(HomeScreenTestTags.SYNC_ERROR_CARD)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sync_error_retry))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.sync_error_dismiss))
            .performClick()

        assertEquals(listOf("retry", "dismiss"), actions)
    }

    private fun setHomeContent(
        actions: MutableList<String>,
        state: HomeUiState,
    ) {
        composeRule.setContent {
            RelateAITheme {
                HomeContent(
                    state = state,
                    onNavigateToContact = { actions += "contact:$it" },
                    onNavigateToSettings = { actions += "settings" },
                    onNavigateToAnalytics = { actions += "analytics" },
                    onNavigateToActivityHistory = { actions += "activity" },
                    onNavigateToStyleCoach = { actions += "style" },
                    onNavigateToBackupRestore = { actions += "backup" },
                    onNavigateToAutomationSetup = { actions += "automation" },
                    onRetrySync = { actions += "retry" },
                    onDismissSyncError = { actions += "dismiss" },
                )
            }
        }
    }

    private fun clickTaggedCard(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }

    private fun populatedHomeState(
        syncError: String? = null,
        readinessTitle: String? = null,
        readinessDetail: String? = null,
    ) = HomeUiState(
        userName = "Yash",
        healthScore = 82,
        pendingCount = 2,
        upcomingEventsCount = 4,
        contactCount = 12,
        sentCount = 8,
        upcomingBirthdays = listOf(UpcomingBirthday(name = "Asha", date = "Jun 15")),
        isLoading = false,
        syncError = syncError,
        readinessTitle = readinessTitle,
        readinessDetail = readinessDetail,
        plannerItems = listOf(
            RelationshipPlannerItem(
                title = "Reconnect with Asha",
                detail = "Relationship health is 42.",
                contactId = "contact-1",
            ),
        ),
    )
}
