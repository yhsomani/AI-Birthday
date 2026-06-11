package com.example.ui.screens.contacts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.ContactFilter
import com.example.ui.viewmodel.ContactListUiState
import com.example.ui.viewmodel.ContactSort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ContactListScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun searchFiltersSortsAndRows_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(contactListState())

        composeRule.setContent {
            RelateAITheme {
                ContactListContent(
                    state = state,
                    onContactClick = { actions += "contact:$it" },
                    onSearchQueryChange = {
                        actions += "search:$it"
                        state = state.copy(searchQuery = it)
                    },
                    onClearSearch = {
                        actions += "clear"
                        state = state.copy(searchQuery = "")
                    },
                    onFilterSelected = { actions += "filter:${it.name}" },
                    onSortSelected = { actions += "sort:${it.name}" },
                )
            }
        }

        composeRule.onNodeWithTag(ContactListTestTags.SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("Bo")
        composeRule.onNodeWithContentDescription(context.getString(R.string.clear_search))
            .performClick()

        clickTaggedItem(ContactListTestTags.FILTER_PREFIX + ContactFilter.FRIENDS.name)
        clickTaggedItem(ContactListTestTags.SORT_PREFIX + ContactSort.HEALTH_ASC.name)
        clickTaggedItem(ContactListTestTags.ROW_PREFIX + "c2")

        assertEquals(
            listOf(
                "search:Bo",
                "clear",
                "filter:FRIENDS",
                "sort:HEALTH_ASC",
                "contact:c2",
            ),
            actions,
        )
    }

    @Test
    fun syncErrorControls_dispatchRefreshAndDismiss() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                ContactListContent(
                    state = contactListState(syncError = "Unable to sync contacts."),
                    onRefresh = { actions += "refresh" },
                    onDismissSyncError = { actions += "dismiss" },
                )
            }
        }

        composeRule.onNodeWithTag(ContactListTestTags.SYNC_ERROR_CARD)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.sync_error_retry))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.sync_error_dismiss))
            .performClick()

        assertEquals(listOf("refresh", "dismiss"), actions)
    }

    private fun clickTaggedItem(tag: String) {
        composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }

    private fun contactListState(syncError: String? = null): ContactListUiState {
        val contacts = listOf(
            ContactEntity(
                id = "c1",
                name = "Alice",
                relationshipType = "FAMILY",
                contactGroup = "Family",
                healthScore = 90,
            ),
            ContactEntity(
                id = "c2",
                name = "Bob",
                relationshipType = "FRIEND",
                contactGroup = "Friends",
                healthScore = 55,
            ),
        )
        return ContactListUiState(
            allContacts = contacts,
            contacts = contacts,
            isLoading = false,
            syncError = syncError,
        )
    }
}
