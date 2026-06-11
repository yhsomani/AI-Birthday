package com.example.ui.screens.memoryvault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.MemoryVaultUiState
import com.example.ui.viewmodel.MemoryVaultViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
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

        composeRule.setMemoryVaultContent(
            state = {
                MemoryVaultUiState(
                    contact = ContactEntity(id = "contact_1", name = "Asha"),
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
                            category = "GIFT",
                        ),
                    ),
                    isLoading = false,
                )
            },
            noteText = { noteText },
            selectedCategory = { selectedCategory },
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
            onTogglePin = { actions += "pin:${it.id}:${it.isPinned}" },
            onDelete = { actions += "delete:${it.id}" },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MemoryVaultTestTags.NOTE_FIELD)
            .assertIsDisplayed()
            .performTextInput("Met at Jaipur trip")
        composeRule.onNodeWithTag(MemoryVaultTestTags.CATEGORY_PREFIX + "GIFT")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MemoryVaultTestTags.ADD_BUTTON)
            .assertIsEnabled()
            .performClick()

        composeRule.assertLazyItemVisible(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_pinned")
        composeRule.onNodeWithText("Call every Sunday")
            .assertIsDisplayed()
        composeRule.clickLazyTag(MemoryVaultTestTags.PIN_BUTTON_PREFIX + "note_plain")
        composeRule.clickLazyTag(MemoryVaultTestTags.PIN_BUTTON_PREFIX + "note_pinned")
        composeRule.clickLazyTag(MemoryVaultTestTags.DELETE_BUTTON_PREFIX + "note_plain")

        assertEquals(
            listOf(
                "back",
                "category:GIFT",
                "add:Met at Jaipur trip:GIFT",
                "pin:note_plain:false",
                "pin:note_pinned:true",
                "delete:note_plain",
            ),
            actions,
        )
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

    private fun ComposeContentTestRule.setMemoryVaultContent(
        state: () -> MemoryVaultUiState,
        noteText: () -> String,
        selectedCategory: () -> String = { MemoryVaultViewModel.CATEGORY_GENERAL },
        onNoteChange: (String) -> Unit = {},
        onCategoryChange: (String) -> Unit = {},
        onAdd: () -> Unit = {},
        onBack: () -> Unit = {},
        onTogglePin: (MemoryNoteEntity) -> Unit = {},
        onDelete: (MemoryNoteEntity) -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                MemoryVaultContent(
                    uiState = state(),
                    newNoteText = noteText(),
                    selectedCategory = selectedCategory(),
                    onNoteChange = onNoteChange,
                    onCategoryChange = onCategoryChange,
                    onAdd = onAdd,
                    onBack = onBack,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
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

    private fun memoryNote(
        id: String,
        noteText: String,
        category: String,
        isPinned: Boolean = false,
    ) = MemoryNoteEntity(
        id = id,
        contactId = "contact_1",
        noteText = noteText,
        category = category,
        dateMs = 1_700_000_000_000L,
        isPinned = isPinned,
    )
}
