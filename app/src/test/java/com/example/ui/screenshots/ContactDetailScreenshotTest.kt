package com.example.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.ui.screens.contacts.ContactDetailContent
import com.example.ui.screens.contacts.ContactDetailTestTags
import com.example.ui.screens.contacts.ContactPreferencesDialogBody
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
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun contactDetailProfile_typicalPhone() {
        setContactDetailContent(state = contactDetailState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_profile_typical_phone.png")
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
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun contactDetailPersonalization_compactPhoneHindiLargeFont() {
        setContactDetailContent(
            state = contactDetailState(
                contact = contactProfile(
                    displayName = "आशा मेहता",
                    contactGroup = "करीबी दोस्त",
                    nickname = null,
                    interestsJson = """["संगीत","कॉफी","शांत उत्सव"]""",
                    preferredLanguage = "hi",
                    notesText = "कॉलेज रीयूनियन में मुलाकात हुई थी और इन्हें छोटे, आत्मीय संदेश पसंद हैं।",
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
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_personalization_compact_phone_hindi_large_font.png")
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
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun contactDetailAutomationHistory_typicalPhone() {
        setContactDetailContent(
            state = contactDetailState(
                contact = contactProfile(
                    preferredChannel = MessageChannel.SMS,
                    automationMode = ApprovalMode.DEFAULT,
                ),
            ),
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.CONTENT_BOTTOM)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_automation_history_typical_phone.png")
    }

    @Test
    fun contactDetailPreferencesForm_compactPhoneLargeFont() {
        setContactPreferencesFormContent(
            contact = contactProfile(
                notesText = "Prefers warm, concise wishes with one personal memory and no work reminders.",
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_preferences_form_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun contactDetailPreferencesForm_compactPhoneHindiLargeFont() {
        setContactPreferencesFormContent(
            contact = contactProfile(
                displayName = "आशा मेहरा",
                contactGroup = "करीबी दोस्त",
                nickname = "आशा",
                preferredLanguage = "hi",
                notesText = "व्यक्तिगत याद के साथ छोटा और आत्मीय संदेश पसंद है।",
            ),
            interests = "संगीत, कॉफी, सप्ताहांत की सैर",
            sensitiveTopics = "काम का तनाव",
            lifePhase = "नया शहर",
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_DIALOG)
            .captureRoboImage(
                "src/test/screenshots/baseline/contact_detail_preferences_form_compact_phone_hindi_large_font.png"
            )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun contactDetailPreferencesForm_typicalPhone() {
        setContactPreferencesFormContent(
            contact = contactProfile(
                notesText = "Prefers warm, concise wishes with one personal memory and no work reminders.",
            ),
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_preferences_form_typical_phone.png")
    }

    @Test
    fun contactDetailPreferencesFormBottom_compactPhoneLargeFont() {
        setContactPreferencesFormContent(
            contact = contactProfile(
                notesText = "Prefers warm, concise wishes with one personal memory, a soft sign-off, and no work reminders.",
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_SKIP_AUTO_WISH)
            .performScrollTo()
        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_preferences_form_bottom_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun contactDetailPreferencesFormBottom_compactPhoneHindiLargeFont() {
        setContactPreferencesFormContent(
            contact = contactProfile(
                displayName = "आशा मेहरा",
                contactGroup = "करीबी दोस्त",
                nickname = "आशा",
                preferredLanguage = "hi",
                notesText = "व्यक्तिगत याद, नरम समापन, और काम की याद दिलाने वाली बातों से बचने वाला संदेश पसंद है।",
            ),
            interests = "संगीत, कॉफी, सप्ताहांत की सैर",
            sensitiveTopics = "काम का तनाव",
            lifePhase = "नया शहर",
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_SKIP_AUTO_WISH)
            .performScrollTo()
        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_DIALOG)
            .captureRoboImage(
                "src/test/screenshots/baseline/contact_detail_preferences_form_bottom_compact_phone_hindi_large_font.png"
            )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun contactDetailPreferencesFormBottom_typicalPhone() {
        setContactPreferencesFormContent(
            contact = contactProfile(
                notesText = "Prefers warm, concise wishes with one personal memory, a soft sign-off, and no work reminders.",
            ),
        )

        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_SKIP_AUTO_WISH)
            .performScrollTo()
        composeRule.onNodeWithTag(ContactDetailTestTags.PREFERENCES_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/contact_detail_preferences_form_bottom_typical_phone.png")
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

    private fun setContactPreferencesFormContent(
        contact: ContactDetailProfile,
        interests: String = "music, coffee, weekend hikes",
        sensitiveTopics: String = "work stress",
        lifePhase: String = "New city",
        fontScale: Float = DefaultFontScale,
    ) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            ContactPreferencesFormSnapshot(
                contact = contact,
                interests = interests,
                sensitiveTopics = sensitiveTopics,
                lifePhase = lifePhase,
            )
        }
    }

    @Composable
    private fun ContactPreferencesFormSnapshot(
        contact: ContactDetailProfile,
        interests: String,
        sensitiveTopics: String,
        lifePhase: String,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(RelateSpacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ContactDetailTestTags.PREFERENCES_DIALOG),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.relateSemanticColors.cardContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(RelateSpacing.cardContent),
                    verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.contact_preferences_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ContactPreferencesDialogBody(
                        nickname = contact.nickname.orEmpty(),
                        onNicknameChange = {},
                        relationshipType = contact.relationshipType,
                        onRelationshipTypeChange = {},
                        language = contact.preferredLanguage,
                        onLanguageChange = {},
                        channel = contact.preferredChannel,
                        onChannelChange = {},
                        formality = contact.formalityLevel,
                        onFormalityChange = {},
                        style = contact.communicationStyle,
                        onStyleChange = {},
                        automationMode = contact.automationMode,
                        onAutomationModeChange = {},
                        sendTime = contact.formattedSendTime(),
                        onSendTimeChange = {},
                        giftBudget = contact.giftBudgetInr.toString(),
                        onGiftBudgetChange = {},
                        annualBudget = contact.annualBudgetInr.toString(),
                        onAnnualBudgetChange = {},
                        interests = interests,
                        onInterestsChange = {},
                        sensitiveTopics = sensitiveTopics,
                        onSensitiveTopicsChange = {},
                        lifePhase = lifePhase,
                        onLifePhaseChange = {},
                        notes = contact.notesText,
                        onNotesChange = {},
                        skipAutoWish = contact.skipAutoWish,
                        onSkipAutoWishChange = {},
                        localError = null,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_CANCEL),
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(RelateSpacing.xs))
                        Button(
                            onClick = {},
                            modifier = Modifier.testTag(ContactDetailTestTags.PREFERENCES_SAVE),
                        ) {
                            Text(text = stringResource(R.string.save))
                        }
                    }
                }
            }
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
        displayName: String = "Asha Mehta",
        contactGroup: String = "Close friends",
        nickname: String? = "Ash",
        interestsJson: String = """["music","coffee","weekend hikes"]""",
        preferredLanguage: String = "en",
        notesText: String = "Met at a college reunion and usually prefers warm, concise messages.",
        preferredChannel: MessageChannel = MessageChannel.EMAIL,
        automationMode: ApprovalMode = ApprovalMode.VIP_APPROVE,
    ): ContactDetailProfile {
        return ContactDetailProfile(
            id = ContactId(ContactIdValue),
            displayName = displayName,
            contactGroup = contactGroup,
            healthScore = 86,
            nickname = nickname,
            birthdayDay = 10,
            birthdayMonth = 4,
            primaryPhone = "+1 555 0100",
            primaryEmail = "asha@example.com",
            relationshipType = "FRIEND",
            preferredLanguage = preferredLanguage,
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
            notesText = notesText,
        )
    }

    private fun ContactDetailProfile.formattedSendTime(): String {
        return if (customSendTimeHour != null && customSendTimeMinute != null) {
            "%02d:%02d".format(customSendTimeHour, customSendTimeMinute)
        } else {
            ""
        }
    }

    private companion object {
        const val ContactIdValue = "contact-asha"
        const val ProgressAnimationFrameMillis = 750L
    }
}
