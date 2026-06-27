package com.example.ui.screens.giftadvisor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.service.GiftSuggestion
import com.example.ui.viewmodel.GiftAdvisorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GiftAdvisorScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun suggestionsRecordFeedbackDeleteAndBack_dispatchExpectedActions() {
        val actions = mutableListOf<String>()
        var showDialog by mutableStateOf(false)
        var giftName by mutableStateOf("")
        var giftCategory by mutableStateOf("")
        var occasion by mutableStateOf("")
        var cost by mutableStateOf("")
        var receivedWell by mutableStateOf<Boolean?>(null)
        var notes by mutableStateOf("")

        composeRule.setGiftAdvisorContent(
            state = {
                GiftAdvisorUiState(
                    contact = contact(),
                    giftHistory = listOf(giftRecord()),
                    suggestions = listOf(
                        GiftSuggestion(
                            name = "Noise-canceling headphones",
                            reason = "Useful for commute and work calls",
                            estimatedCostInr = 2500,
                        )
                    ),
                    totalSpentThisYear = 1200,
                    remainingBudget = 3800,
                    isLoading = false,
                )
            },
            showDialog = { showDialog },
            giftName = { giftName },
            onGiftNameChange = { giftName = it },
            giftCategory = { giftCategory },
            onGiftCategoryChange = { giftCategory = it },
            occasion = { occasion },
            onOccasionChange = { occasion = it },
            cost = { cost },
            onCostChange = { cost = it },
            receivedWell = { receivedWell },
            onReceivedWellChange = {
                receivedWell = it
                actions += "feedback:$it"
            },
            notes = { notes },
            onNotesChange = { notes = it },
            onBack = { actions += "back" },
            onShowDialog = {
                showDialog = true
                actions += "dialog"
            },
            onDismissDialog = { showDialog = false },
            onSaveGift = {
                actions += "save:$giftName|$giftCategory|$occasion|$cost|$receivedWell|$notes"
                showDialog = false
            },
            onDeleteGift = { actions += "delete:${it.id.value}" },
            onGenerateSuggestions = { actions += "suggest" },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.GENERATE_SUGGESTIONS_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeRule.assertLazyItemVisible(GiftAdvisorTestTags.SUGGESTION_CARD_PREFIX + 0)
        composeRule.onNodeWithText("Noise-canceling headphones")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(GiftAdvisorTestTags.RECORD_FAB)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.GIFT_NAME_FIELD)
            .performTextInput("Travel journal")
        composeRule.onNodeWithTag(GiftAdvisorTestTags.GIFT_CATEGORY_FIELD)
            .performTextInput("Books")
        composeRule.onNodeWithTag(GiftAdvisorTestTags.OCCASION_FIELD)
            .performTextInput("Birthday")
        composeRule.onNodeWithTag(GiftAdvisorTestTags.COST_FIELD)
            .performTextInput("1,500")
        composeRule.onNodeWithTag(GiftAdvisorTestTags.FEEDBACK_LIKED)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.FEEDBACK_DISLIKED)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.NOTES_FIELD)
            .performScrollTo()
            .performTextInput("Loved paper quality")
        composeRule.onNodeWithTag(GiftAdvisorTestTags.SAVE_BUTTON)
            .assertIsDisplayed()
            .performClick()

        composeRule.clickLazyTag(GiftAdvisorTestTags.DELETE_BUTTON_PREFIX + "gift_1")

        assertEquals(
            listOf(
                "back",
                "suggest",
                "dialog",
                "feedback:true",
                "feedback:false",
                "save:Travel journal|Books|Birthday|1,500|false|Loved paper quality",
                "delete:gift_1",
            ),
            actions,
        )
    }

    @Test
    fun loadingErrorEmptyGeneratingAndDialogValidation_renderExpectedStates() {
        var state by mutableStateOf(GiftAdvisorUiState(isLoading = true))
        var showDialog by mutableStateOf(false)
        var attemptedSubmit by mutableStateOf(false)

        composeRule.setGiftAdvisorContent(
            state = { state },
            showDialog = { showDialog },
            attemptedSubmit = { attemptedSubmit },
            onShowDialog = { showDialog = true },
            onDismissDialog = { showDialog = false },
            onSaveGift = { attemptedSubmit = true },
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.LOADING)
            .assertIsDisplayed()

        state = GiftAdvisorUiState(
            contact = contact(),
            isLoading = false,
            errorMessageRes = R.string.gift_advisor_error_load,
        )
        composeRule.assertLazyItemVisible(GiftAdvisorTestTags.ERROR_CARD)
        composeRule.onNodeWithText(context.getString(R.string.gift_advisor_error_load))
            .assertIsDisplayed()
        composeRule.assertLazyItemVisible(GiftAdvisorTestTags.EMPTY_HISTORY)

        composeRule.onNodeWithTag(GiftAdvisorTestTags.RECORD_FAB)
            .performClick()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.SAVE_BUTTON)
            .performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.gift_required_field))
            .assertCountEquals(3)
        composeRule.onNodeWithText(context.getString(R.string.gift_advisor_error_invalid_cost))
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(GiftAdvisorTestTags.CANCEL_BUTTON)
            .performClick()
        state = state.copy(
            errorMessageRes = null,
            isGeneratingSuggestions = true,
        )
        composeRule.onNodeWithTag(GiftAdvisorTestTags.GENERATE_SUGGESTIONS_BUTTON)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.SUGGESTIONS_PROGRESS)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.SUGGESTIONS_EMPTY)
            .assertIsDisplayed()
    }

    private fun ComposeContentTestRule.setGiftAdvisorContent(
        state: () -> GiftAdvisorUiState,
        showDialog: () -> Boolean,
        giftName: () -> String = { "" },
        onGiftNameChange: (String) -> Unit = {},
        giftCategory: () -> String = { "" },
        onGiftCategoryChange: (String) -> Unit = {},
        occasion: () -> String = { "" },
        onOccasionChange: (String) -> Unit = {},
        cost: () -> String = { "" },
        onCostChange: (String) -> Unit = {},
        receivedWell: () -> Boolean? = { null },
        onReceivedWellChange: (Boolean?) -> Unit = {},
        notes: () -> String = { "" },
        onNotesChange: (String) -> Unit = {},
        attemptedSubmit: () -> Boolean = { false },
        onBack: () -> Unit = {},
        onShowDialog: () -> Unit = {},
        onDismissDialog: () -> Unit = {},
        onSaveGift: () -> Unit = {},
        onDeleteGift: (GiftHistoryRecord) -> Unit = {},
        onGenerateSuggestions: () -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                GiftAdvisorContent(
                    uiState = state(),
                    showAddDialog = showDialog(),
                    giftName = giftName(),
                    onGiftNameChange = onGiftNameChange,
                    giftCategory = giftCategory(),
                    onGiftCategoryChange = onGiftCategoryChange,
                    occasionType = occasion(),
                    onOccasionTypeChange = onOccasionChange,
                    approxCost = cost(),
                    onApproxCostChange = onCostChange,
                    receivedWellState = receivedWell(),
                    onReceivedWellChange = onReceivedWellChange,
                    giftNotes = notes(),
                    onGiftNotesChange = onNotesChange,
                    attemptedSubmit = attemptedSubmit(),
                    onBack = onBack,
                    onShowAddDialog = onShowDialog,
                    onDismissDialog = onDismissDialog,
                    onSaveGift = onSaveGift,
                    onDeleteGift = onDeleteGift,
                    onGenerateSuggestions = onGenerateSuggestions,
                )
            }
        }
    }

    private fun ComposeContentTestRule.assertLazyItemVisible(tag: String) {
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun ComposeContentTestRule.clickLazyTag(tag: String) {
        assertLazyItemVisible(tag)
        onNodeWithTag(tag).performClick()
    }

    private fun contact() = ContactGiftAdvisorProfile(
        id = ContactId("contact_1"),
        displayName = "Riya",
        nickname = null,
        relationshipType = "FRIEND",
        interestsJson = "[]",
        giftBudgetInr = 5000,
    )

    private fun giftRecord() = GiftHistoryRecord(
        id = GiftHistoryId("gift_1"),
        contactId = ContactId("contact_1"),
        giftName = "Notebook",
        giftCategory = "Stationery",
        occasionType = "Birthday",
        year = 2026,
        approxCostInr = 1200,
        receivedWell = true,
        notes = "Used daily",
    )
}
