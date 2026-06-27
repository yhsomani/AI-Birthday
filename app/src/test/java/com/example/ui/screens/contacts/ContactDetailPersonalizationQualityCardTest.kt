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
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
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
            contact = contactProfile(),
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
            contact = contactProfile(
                nickname = "Ash",
                interestsJson = """["music"]""",
                notesText = "Met at college reunion.",
                preferredChannel = MessageChannel.EMAIL,
            ),
            memoryNoteCount = 1,
            memoryNoteCategorySummary = listOf(
                MemoryNoteCategoryCount(category = "GENERAL", count = 1),
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
            contact = contactProfile(
                nickname = "Ash",
                preferredChannel = MessageChannel.SMS,
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
            contact = contactProfile(
                nickname = "Ash",
                interestsJson = """["music"]""",
                notesText = "",
                preferredChannel = MessageChannel.SMS,
            ),
            memoryNoteCount = 2,
            memoryNoteCategorySummary = listOf(
                MemoryNoteCategoryCount(category = "GIFT", count = 1),
                MemoryNoteCategoryCount(category = "PREFERENCE", count = 1),
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
            contact = contactProfile(
                nickname = "Ash",
                interestsJson = """["music"]""",
                preferredChannel = MessageChannel.SMS,
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
        contact: ContactDetailProfile,
        memoryNoteCount: Int = 0,
        memoryNoteCategorySummary: List<MemoryNoteCategoryCount> = emptyList(),
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

    private fun contactProfile(
        nickname: String? = null,
        interestsJson: String = "[]",
        notesText: String = "",
        preferredChannel: MessageChannel = MessageChannel.SMS,
    ): ContactDetailProfile {
        return ContactDetailProfile(
            id = ContactId("contact_1"),
            displayName = "Asha",
            contactGroup = null,
            healthScore = 80,
            nickname = nickname,
            birthdayDay = null,
            birthdayMonth = null,
            primaryPhone = null,
            primaryEmail = null,
            relationshipType = "UNKNOWN",
            preferredLanguage = "en",
            preferredChannel = preferredChannel,
            formalityLevel = "CASUAL",
            communicationStyle = "WARM",
            automationMode = ApprovalMode.DEFAULT,
            customSendTimeHour = null,
            customSendTimeMinute = null,
            giftBudgetInr = 500,
            annualBudgetInr = 0,
            skipAutoWish = false,
            interestsJson = interestsJson,
            sensitiveTopicsJson = "[]",
            currentLifePhaseJson = "{}",
            notesText = notesText,
        )
    }
}
