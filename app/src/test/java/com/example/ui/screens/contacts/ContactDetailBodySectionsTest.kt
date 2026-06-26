package com.example.ui.screens.contacts

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
class ContactDetailBodySectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sections_areLabeledAndActionsRemainExplicit() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ContactDetailBodySections(
                        contactId = "contact_1",
                        contact = ContactEntity(
                            id = "contact_1",
                            name = "Asha",
                            birthdayDay = 10,
                            birthdayMonth = 4,
                            primaryPhone = "+15550001",
                            primaryEmail = "asha@example.com",
                            preferredChannel = MessageChannel.EMAIL.raw,
                        ),
                        memoryNoteCount = 1,
                        memoryNoteCategorySummary = listOf(
                            MemoryNoteCategorySummary(category = "GENERAL", count = 1),
                        ),
                        upcomingBirthdayDaysLeft = 5,
                        onNavigateToMemoryVault = { actions += "memory:$it" },
                        onNavigateToGiftAdvisor = { actions += "gift:$it" },
                        onNavigateToChatHistory = { actions += "chat:$it" },
                        onEditPreferences = { actions += "edit" },
                        onGenerateWish = { actions += "generate" },
                        onMarkVip = { actions += "vip" },
                        onSetWhatsApp = { actions += "whatsapp" },
                        onSetSms = { actions += "sms" },
                    )
                }
            }
        }

        assertSectionVisible(
            ContactDetailTestTags.SECTION_ESSENTIALS,
            R.string.contact_detail_section_essentials,
        )
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_generate_ai_wish))
            .performClick()

        assertSectionVisible(
            ContactDetailTestTags.SECTION_PERSONALIZATION,
            R.string.contact_detail_section_personalization,
        )
        composeRule.onNodeWithTag(ContactDetailTestTags.ACTION_ADD_MEMORY)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ContactDetailTestTags.ACTION_ADD_GIFT)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(ContactDetailTestTags.ACTION_EDIT_PREFERENCES)
            .performScrollTo()
            .performClick()

        assertSectionVisible(
            ContactDetailTestTags.SECTION_AUTOMATION,
            R.string.contact_detail_section_automation,
        )
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_mark_vip))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_set_whatsapp))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_set_sms))
            .performClick()

        assertSectionVisible(
            ContactDetailTestTags.SECTION_HISTORY,
            R.string.contact_detail_section_history,
        )
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_memory_vault))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_gift_advisor))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.contact_detail_chat_history))
            .performClick()

        assertEquals(
            listOf(
                "generate",
                "memory:contact_1",
                "gift:contact_1",
                "edit",
                "vip",
                "whatsapp",
                "sms",
                "memory:contact_1",
                "gift:contact_1",
                "chat:contact_1",
            ),
            actions,
        )
    }

    private fun assertSectionVisible(tag: String, titleRes: Int) {
        composeRule.onNodeWithTag(tag)
            .performScrollTo()
        composeRule.onNodeWithText(context.getString(titleRes))
            .assertIsDisplayed()
    }
}
