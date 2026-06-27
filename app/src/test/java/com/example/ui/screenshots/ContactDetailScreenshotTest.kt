package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.ui.screens.contacts.ContactDetailContent
import com.example.ui.screens.contacts.ContactDetailTestTags
import com.example.ui.viewmodel.ContactDetailUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactDetailScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contactDetailProfile_compactPhone() {
        setContactDetailContent(state = contactDetailState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_profile_compact_phone.png")
    }

    @Test
    fun contactDetailPersonalization_compactPhoneLargeFont() {
        setContactDetailContent(
            state = contactDetailState(
                contact = contactProfile(
                    nickname = null,
                    interestsJson = "[]",
                    preferredChannel = MessageChannel.UNKNOWN,
                ),
                memoryNoteCount = 0,
                memoryNoteCategorySummary = emptyList(),
                preferenceMessageRes = com.example.R.string.contact_detail_preferences_saved,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.SECTION_PERSONALIZATION)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_personalization_compact_phone_large_font.png")
    }

    @Test
    fun contactDetailAutomationHistory_compactPhoneLargeFont() {
        setContactDetailContent(
            state = contactDetailState(
                contact = contactProfile(
                    preferredChannel = MessageChannel.SMS,
                    automationMode = ApprovalMode.DEFAULT,
                ),
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.CONTENT_BOTTOM)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_automation_history_compact_phone_large_font.png")
    }

    @Test
    fun contactDetailLoading_compactPhone() {
        setContactDetailContent(
            state = ContactDetailUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_loading_compact_phone.png")
    }

    private fun setContactDetailContent(
        state: ContactDetailUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            ContactDetailContent(
                contactId = ContactIdValue,
                state = state,
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun contactDetailState(
        contact: ContactDetailProfile = contactProfile(),
        memoryNoteCount: Int = 3,
        memoryNoteCategorySummary: List<MemoryNoteCategoryCount> = listOf(
            MemoryNoteCategoryCount(category = "PREFERENCE", count = 2),
            MemoryNoteCategoryCount(category = "GIFT", count = 1),
        ),
        preferenceMessageRes: Int? = null,
    ): ContactDetailUiState {
        return ContactDetailUiState(
            contact = contact,
            memoryNoteCount = memoryNoteCount,
            memoryNoteCategorySummary = memoryNoteCategorySummary,
            upcomingEventDaysLeft = 12,
            isLoading = false,
            preferenceMessageRes = preferenceMessageRes,
        )
    }

    private fun contactProfile(
        nickname: String? = "Ash",
        interestsJson: String = """["music","coffee","weekend hikes"]""",
        preferredChannel: MessageChannel = MessageChannel.EMAIL,
        automationMode: ApprovalMode = ApprovalMode.VIP_APPROVE,
    ): ContactDetailProfile {
        return ContactDetailProfile(
            id = ContactId(ContactIdValue),
            displayName = "Asha Mehta",
            contactGroup = "Close friends",
            healthScore = 86,
            nickname = nickname,
            birthdayDay = 10,
            birthdayMonth = 4,
            primaryPhone = "+1 555 0100",
            primaryEmail = "asha@example.com",
            relationshipType = "FRIEND",
            preferredLanguage = "en",
            preferredChannel = preferredChannel,
            formalityLevel = "CASUAL",
            communicationStyle = "WARM",
            automationMode = automationMode,
            customSendTimeHour = 9,
            customSendTimeMinute = 30,
            giftBudgetInr = 1500,
            annualBudgetInr = 5000,
            skipAutoWish = false,
            interestsJson = interestsJson,
            sensitiveTopicsJson = """["work stress"]""",
            currentLifePhaseJson = """{"phase":"New city"}""",
            notesText = "Met at a college reunion and usually prefers warm, concise messages.",
        )
    }

    private companion object {
        const val ContactIdValue = "contact-asha"
        const val ProgressAnimationFrameMillis = 750L
    }
}
