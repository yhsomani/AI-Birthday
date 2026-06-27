package com.example.ui.screens.activity

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.ActivityHistoryUiState
import com.example.ui.viewmodel.ActivityLogDateFilter
import com.example.ui.viewmodel.ActivityLogStatusFilter
import com.example.ui.viewmodel.ActivityLogTypeFilter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ActivityHistoryScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun populatedHistory_dispatchesSearchFiltersBackAndOpenRoute() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(
            ActivityHistoryUiState(
                isLoading = false,
                entries = listOf(activityLog()),
            )
        )

        composeRule.setActivityHistoryContent(
            state = { state },
            onBack = { actions += "back" },
            onOpenRoute = { actions += "route:$it" },
            onSearchQueryChange = {
                actions += "search:$it"
                state = state.copy(searchQuery = it)
            },
            onTypeFilterSelected = {
                actions += "type:${it.name}"
                state = state.copy(selectedTypeFilter = it)
            },
            onDateFilterSelected = {
                actions += "date:${it.name}"
                state = state.copy(selectedDateFilter = it)
            },
            onStatusFilterSelected = {
                actions += "status:${it.name}"
                state = state.copy(selectedStatusFilter = it)
            },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(ActivityHistoryTestTags.SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("error")
        composeRule.onNodeWithTag(ActivityHistoryTestTags.TYPE_FILTER_PREFIX + ActivityLogTypeFilter.DISPATCH.name)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ActivityHistoryTestTags.TYPE_FILTER_PREFIX + ActivityLogTypeFilter.BACKUP.name)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ActivityHistoryTestTags.DATE_FILTER_PREFIX + ActivityLogDateFilter.LAST_7_DAYS.name)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ActivityHistoryTestTags.STATUS_FILTER_PREFIX + ActivityLogStatusFilter.RESOLVED.name)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ActivityHistoryTestTags.LOG_CARD_PREFIX + "log_1")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Message approved")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ActivityHistoryTestTags.OPEN_ROUTE_PREFIX + "log_1")
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(
                "back",
                "search:error",
                "type:DISPATCH",
                "type:BACKUP",
                "date:LAST_7_DAYS",
                "status:RESOLVED",
                "route:messages/today",
            ),
            actions,
        )
    }

    @Test
    fun loadingEmptyAndErrorStates_renderExpectedContent() {
        var state by mutableStateOf(ActivityHistoryUiState(isLoading = true))

        composeRule.setActivityHistoryContent(state = { state })

        composeRule.onNodeWithTag(ActivityHistoryTestTags.LOADING)
            .assertIsDisplayed()

        state = ActivityHistoryUiState(isLoading = false)
        composeRule.onNodeWithTag(ActivityHistoryTestTags.EMPTY)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.activity_history_empty))
            .assertIsDisplayed()

        state = ActivityHistoryUiState(
            isLoading = false,
            errorMessageRes = R.string.activity_history_error_load,
        )
        composeRule.onNodeWithTag(ActivityHistoryTestTags.ERROR)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.activity_history_error_load))
            .assertIsDisplayed()
    }

    private fun ComposeContentTestRule.setActivityHistoryContent(
        state: () -> ActivityHistoryUiState,
        onBack: () -> Unit = {},
        onOpenRoute: (String) -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
        onTypeFilterSelected: (ActivityLogTypeFilter) -> Unit = {},
        onDateFilterSelected: (ActivityLogDateFilter) -> Unit = {},
        onStatusFilterSelected: (ActivityLogStatusFilter) -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                ActivityHistoryContent(
                    state = state(),
                    onBack = onBack,
                    onOpenRoute = onOpenRoute,
                    onSearchQueryChange = onSearchQueryChange,
                    onTypeFilterSelected = onTypeFilterSelected,
                    onDateFilterSelected = onDateFilterSelected,
                    onStatusFilterSelected = onStatusFilterSelected,
                )
            }
        }
    }

    private fun activityLog() = ActivityLogEntity(
        id = "log_1",
        type = "MESSAGE",
        title = "Message approved",
        detail = "Approved from notification.",
        severity = "WARNING",
        status = "RESOLVED",
        actionRoute = "messages/today",
        createdAtMs = 1_700_000_000_000L,
    )
}
