package com.example.ui.screens.contacts

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.MessageChannel
import com.example.ui.viewmodel.MemoryNoteCategorySummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ContactDetailPersonalizationQualityCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun lowQualityContact_showsNextMissingDetailPrompt() {
        composeRule.setQualityCardContent(
            contact = ContactEntity(
                id = "contact_1",
                name = "Asha",
            ),
        )

        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_title, 25),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.personalization_quality_next_step,
                context.getString(R.string.personalization_quality_add_nickname),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_impact_low),
        ).assertIsDisplayed()
    }

    @Test
    fun completeContact_showsReadyPrompt() {
        composeRule.setQualityCardContent(
            contact = ContactEntity(
                id = "contact_1",
                name = "Asha",
                nickname = "Ash",
                interestsJson = """["music"]""",
                notesText = "Met at college reunion.",
                preferredChannel = MessageChannel.EMAIL.raw,
            ),
            memoryNoteCount = 1,
            memoryNoteCategorySummary = listOf(
                MemoryNoteCategorySummary(category = "GENERAL", count = 1),
            ),
        )

        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_title, 100),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_ready),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_impact_ready),
        ).assertIsDisplayed()
    }

    @Test
    fun partialQualityContact_showsSpecificContextImpact() {
        composeRule.setQualityCardContent(
            contact = ContactEntity(
                id = "contact_1",
                name = "Asha",
                nickname = "Ash",
                preferredChannel = MessageChannel.SMS.raw,
            ),
        )

        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_title, 50),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_impact_partial),
        ).assertIsDisplayed()
    }

    @Test
    fun memoryVaultNotesCompleteMemorySignal() {
        composeRule.setQualityCardContent(
            contact = ContactEntity(
                id = "contact_1",
                name = "Asha",
                nickname = "Ash",
                interestsJson = """["music"]""",
                notesText = "",
                preferredChannel = MessageChannel.SMS.raw,
            ),
            memoryNoteCount = 2,
            memoryNoteCategorySummary = listOf(
                MemoryNoteCategorySummary(category = "GIFT", count = 1),
                MemoryNoteCategorySummary(category = "PREFERENCE", count = 1),
            ),
        )

        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_title, 100),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_ready),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.personalization_quality_memory_summary,
                2,
                "${context.getString(R.string.memory_category_gift)} 1, ${context.getString(R.string.memory_category_preference)} 1",
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun missingMemoryShowsClearAddMemoryAction() {
        val actions = mutableListOf<String>()
        composeRule.setQualityCardContent(
            contact = ContactEntity(
                id = "contact_1",
                name = "Asha",
                nickname = "Ash",
                interestsJson = """["music"]""",
                preferredChannel = MessageChannel.SMS.raw,
            ),
            onAddMemory = { actions += "add-memory" },
        )

        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_add_one_memory),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(ContactDetailTestTags.PERSONALIZATION_ADD_MEMORY)
            .performClick()

        assertEquals(listOf("add-memory"), actions)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setQualityCardContent(
        contact: ContactEntity,
        memoryNoteCount: Int = 0,
        memoryNoteCategorySummary: List<MemoryNoteCategorySummary> = emptyList(),
        onAddMemory: () -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                PersonalizationQualityCard(
                    contact = contact,
                    memoryNoteCount = memoryNoteCount,
                    memoryNoteCategorySummary = memoryNoteCategorySummary,
                    onAddMemory = onAddMemory,
                )
            }
        }
    }
}
