package com.example.ui.screens.contacts

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.ui.theme.RelateAITheme
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
                preferredChannel = "EMAIL",
            ),
        )

        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_title, 100),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.personalization_quality_ready),
        ).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setQualityCardContent(
        contact: ContactEntity,
    ) {
        setContent {
            RelateAITheme {
                PersonalizationQualityCard(contact = contact)
            }
        }
    }
}
