package com.example.ui.screens.memoryvault

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.ui.viewmodel.MemoryVaultUiState
import com.example.ui.viewmodel.MemoryVaultViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class MemoryVaultScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun addCategoryPinUnpinDeleteAndBack_dispatchExpectedActions() {
        val actions = mutableListOf<String>()
        var noteText by mutableStateOf("")
        var selectedCategory by mutableStateOf(MemoryVaultViewModel.CATEGORY_GENERAL)
        var editingNoteId by mutableStateOf<String?>(null)
        var editNoteText by mutableStateOf("")
        var editCategory by mutableStateOf(MemoryVaultViewModel.CATEGORY_GENERAL)

        composeRule.setMemoryVaultContent(
            state = {
                MemoryVaultUiState(
                    contact = ContactHeader(id = ContactId("contact_1"), displayName = "Asha"),
                    notes = listOf(
                        memoryNote(
                            id = "note_pinned",
                            noteText = "Call every Sunday",
                            category = "PREFERENCE",
                            isPinned = true,
                        ),
                        memoryNote(
                            id = "note_plain",
                            noteText = "Likes mango lassi",
                            category = MemoryVaultViewModel.CATEGORY_PRIVATE,
                        ),
                    ),
                    isLoading = false,
                )
            },
            noteText = { noteText },
            selectedCategory = { selectedCategory },
            editingNoteId = { editingNoteId },
            editNoteText = { editNoteText },
            editCategory = { editCategory },
            onNoteChange = { noteText = it },
            onCategoryChange = {
                selectedCategory = it
                actions += "category:$it"
            },
            onAdd = {
                actions += "add:$noteText:$selectedCategory"
                noteText = ""
            },
            onBack = { actions += "back" },
            onEditStart = {
                editingNoteId = it.id.value
                editNoteText = it.noteText
                editCategory = it.category
            },
            onEditTextChange = { editNoteText = it },
            onEditCategoryChange = { editCategory = it },
            onEditSave = {
                actions += "edit:${it.id.value}:$editNoteText:$editCategory"
                editingNoteId = null
                editNoteText = ""
                editCategory = MemoryVaultViewModel.CATEGORY_GENERAL
            },
            onTogglePin = { actions += "pin:${it.id.value}:${it.isPinned}" },
            onDelete = { actions += "delete:${it.id.value}" },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .assertIsDisplayed()
            .performTextInput("Met at Jaipur trip")
        composeRule.onNodeWithText(context.getString(R.string.memory_vault_ai_usage_note))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.CATEGORY_PREFIX + MemoryVaultViewModel.CATEGORY_PRIVATE)
        composeRule.onNodeWithTag(MemoryVaultTestTags.CATEGORY_PREFIX + MemoryVaultViewModel.CATEGORY_PRIVATE)
            .performClick()
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.ADD_BUTTON)
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsEnabled()
            .performClick()

        composeRule.assertLazyItemVisible(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_pinned")
        composeRule.onNodeWithText("Call every Sunday")
            .assertIsDisplayed()
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_plain")
        composeRule.onNodeWithText(context.getString(R.string.memory_category_private))
            .assertIsDisplayed()
        composeRule.clickLazyTag(MemoryVaultTestTags.EDIT_BUTTON_PREFIX + "note_plain")
        composeRule.onNodeWithTag(MemoryVaultTestTags.EDIT_FIELD_PREFIX + "note_plain")
            .assertTextContains("Likes mango lassi")
            .performTextClearance()
        composeRule.onNodeWithTag(MemoryVaultTestTags.EDIT_FIELD_PREFIX + "note_plain")
            .performTextInput("Likes kesar chai")
        composeRule.clickLazyTag(MemoryVaultTestTags.EDIT_CATEGORY_PREFIX + "GIFT")
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.EDIT_SAVE_PREFIX + "note_plain")
        composeRule.onNodeWithTag(MemoryVaultTestTags.EDIT_SAVE_PREFIX + "note_plain")
            .assertIsEnabled()
            .performClick()
        composeRule.clickLazyTag(MemoryVaultTestTags.PIN_BUTTON_PREFIX + "note_plain")
        composeRule.clickLazyTag(MemoryVaultTestTags.PIN_BUTTON_PREFIX + "note_pinned")
        composeRule.clickLazyTag(MemoryVaultTestTags.DELETE_BUTTON_PREFIX + "note_plain")

        assertEquals(
            listOf(
                "back",
                "category:PRIVATE",
                "add:Met at Jaipur trip:PRIVATE",
                "edit:note_plain:Likes kesar chai:GIFT",
                "pin:note_plain:false",
                "pin:note_pinned:true",
                "delete:note_plain",
            ),
            actions,
        )
    }

    @Test
    fun searchFiltersNotesAndShowsFilteredEmptyState() {
        var searchQuery by mutableStateOf("")

        composeRule.setMemoryVaultContent(
            state = {
                MemoryVaultUiState(
                    contact = ContactHeader(id = ContactId("contact_1"), displayName = "Asha"),
                    notes = listOf(
                        memoryNote(
                            id = "note_pinned",
                            noteText = "Call every Sunday",
                            category = "PREFERENCE",
                            isPinned = true,
                        ),
                        memoryNote(
                            id = "note_plain",
                            noteText = "Likes mango lassi",
                            category = MemoryVaultViewModel.CATEGORY_PRIVATE,
                        ),
                    ),
                    searchQuery = searchQuery,
                    isLoading = false,
                )
            },
            noteText = { "" },
            onSearchQueryChange = { searchQuery = it },
        )

        composeRule.assertLazyItemVisible(MemoryVaultTestTags.SEARCH_FIELD)
        composeRule.onNodeWithTag(MemoryVaultTestTags.SEARCH_FIELD)
            .performTextInput("mango")
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_plain")
        composeRule.onNodeWithText("Likes mango lassi")
            .assertIsDisplayed()

        composeRule.assertLazyItemVisible(MemoryVaultTestTags.SEARCH_FIELD)
        composeRule.onNodeWithTag(MemoryVaultTestTags.SEARCH_CLEAR)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MemoryVaultTestTags.SEARCH_FIELD)
            .performTextInput("zzz")
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.SEARCH_EMPTY_STATE)
        composeRule.onNodeWithText(context.getString(R.string.memory_vault_no_search_results))
            .assertIsDisplayed()
    }

    @Test
    fun loadingEmptyErrorAndValidationControls_renderExpectedStates() {
        var state by mutableStateOf(MemoryVaultUiState(isLoading = true))
        var noteText by mutableStateOf("")

        composeRule.setMemoryVaultContent(
            state = { state },
            noteText = { noteText },
            onNoteChange = { noteText = it },
        )

        composeRule.onNodeWithTag(MemoryVaultTestTags.LOADING)
            .assertIsDisplayed()

        state = MemoryVaultUiState(
            isLoading = false,
            errorMessageRes = R.string.memory_vault_error_note_too_long,
        )
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsNotEnabled()
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.ERROR_CARD)
        composeRule.onNodeWithText(context.getString(R.string.memory_vault_error_note_too_long))
            .assertIsDisplayed()
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.EMPTY_STATE)

        composeRule.assertLazyItemVisible(MemoryVaultTestTags.NOTE_FIELD)
        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .assertIsDisplayed()
            .performTextInput("   ")
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.memory_vault_error_blank_note))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .performTextClearance()
        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .performTextInput("Important note")
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsEnabled()
        composeRule.onNodeWithText(
            context.getString(
                R.string.memory_vault_note_counter,
                noteText.length,
                MemoryVaultViewModel.MAX_NOTE_LENGTH,
            )
        ).assertIsDisplayed()
    }

    @Test
    fun addButtonDispatchesOnlyWhenMeaningfulTextIsPresent() {
        val actions = mutableListOf<String>()
        var noteText by mutableStateOf("")

        composeRule.setMemoryVaultContent(
            state = { MemoryVaultUiState(isLoading = false) },
            noteText = { noteText },
            onNoteChange = { noteText = it },
            onAdd = { actions += "add:$noteText" },
        )

        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .performTextInput("   ")
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsNotEnabled()
        assertEquals(emptyList<String>(), actions)

        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .performTextClearance()
        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .performTextInput("Important note")
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.ADD_BUTTON)
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf("add:Important note"), actions)
    }

    @Test
    fun suggestedPromptPrefillsNoteAndCategory() {
        var noteText by mutableStateOf("")
        var selectedCategory by mutableStateOf(MemoryVaultViewModel.CATEGORY_GENERAL)

        composeRule.setMemoryVaultContent(
            state = { MemoryVaultUiState(isLoading = false) },
            noteText = { noteText },
            selectedCategory = { selectedCategory },
            onNoteChange = { noteText = it },
            onPromptSelected = { text, category ->
                noteText = text
                selectedCategory = category
            },
        )

        composeRule.onNodeWithText(context.getString(R.string.memory_prompt_gift_preference))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .assertTextContains(context.getString(R.string.memory_prompt_gift_preference_template))
        composeRule.assertLazyItemVisible(MemoryVaultTestTags.CATEGORY_PREFIX + "GIFT")
        assertEquals("GIFT", selectedCategory)
    }

    private fun ComposeContentTestRule.setMemoryVaultContent(
        state: () -> MemoryVaultUiState,
        noteText: () -> String,
        selectedCategory: () -> String = { MemoryVaultViewModel.CATEGORY_GENERAL },
        editingNoteId: () -> String? = { null },
        editNoteText: () -> String = { "" },
        editCategory: () -> String = { MemoryVaultViewModel.CATEGORY_GENERAL },
        onNoteChange: (String) -> Unit = {},
        onPromptSelected: (String, String) -> Unit = { _, _ -> },
        onCategoryChange: (String) -> Unit = {},
        onAdd: () -> Unit = {},
        onBack: () -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
        onEditStart: (MemoryNoteRecord) -> Unit = {},
        onEditTextChange: (String) -> Unit = {},
        onEditCategoryChange: (String) -> Unit = {},
        onEditCancel: () -> Unit = {},
        onEditSave: (MemoryNoteRecord) -> Unit = {},
        onTogglePin: (MemoryNoteRecord) -> Unit = {},
        onDelete: (MemoryNoteRecord) -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                MemoryVaultContent(
                    uiState = state(),
                    newNoteText = noteText(),
                    selectedCategory = selectedCategory(),
                    editingNoteId = editingNoteId(),
                    editNoteText = editNoteText(),
                    editCategory = editCategory(),
                    onNoteChange = onNoteChange,
                    onPromptSelected = onPromptSelected,
                    onCategoryChange = onCategoryChange,
                    onAdd = onAdd,
                    onBack = onBack,
                    onSearchQueryChange = onSearchQueryChange,
                    onEditStart = onEditStart,
                    onEditTextChange = onEditTextChange,
                    onEditCategoryChange = onEditCategoryChange,
                    onEditCancel = onEditCancel,
                    onEditSave = onEditSave,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
                )
            }
        }
    }

    private fun ComposeContentTestRule.assertLazyItemVisible(tag: String) {
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).performScrollTo()
        onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun ComposeContentTestRule.clickLazyTag(tag: String) {
        assertLazyItemVisible(tag)
        onNodeWithTag(tag).performClick()
    }

    private fun memoryNote(
        id: String,
        noteText: String,
        category: String,
        isPinned: Boolean = false,
    ) = MemoryNoteRecord(
        id = MemoryNoteId(id),
        contactId = ContactId("contact_1"),
        noteText = noteText,
        category = category,
        dateMs = 1_700_000_000_000L,
        isPinned = isPinned,
    )
}
