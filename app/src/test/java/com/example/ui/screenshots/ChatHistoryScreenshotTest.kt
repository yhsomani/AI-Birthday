package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageChannel
import com.example.ui.screens.chat.ChatHistoryContent
import com.example.ui.screens.chat.ChatHistoryUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale
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
class ChatHistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatHistoryPopulated_compactPhone() {
        setChatHistoryContent(state = populatedChatHistoryState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_populated_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun chatHistoryPopulated_typicalPhone() {
        setChatHistoryContent(state = populatedChatHistoryState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_populated_typical_phone.png")
    }

    @Test
    fun chatHistoryLongMessage_compactPhoneLargeFont() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
                messages = listOf(
                    sentMessage(
                        id = "sent_long",
                        messageText = "Wishing you a calm, joyful birthday and a year full of steady wins, warm people, and time for the things you care about most.",
                        channel = MessageChannel.EMAIL.raw,
                        sentAtMs = 1_767_688_200_000L,
                    ),
                ),
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_long_message_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun chatHistoryLongMessage_compactPhoneHindiLargeFont() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("hi-IN"))
        try {
            setChatHistoryContent(
                state = ChatHistoryUiState(
                    isLoading = false,
                    contactId = ContactId,
                    messages = listOf(
                        sentMessage(
                            id = "sent_long_hi",
                            messageText = "आपके जन्मदिन पर ढेर सारी शुभकामनाएं। उम्मीद है आपका दिन शांत, खुशियों भरा और अपने लोगों के साथ यादगार रहे।",
                            channel = MessageChannel.EMAIL.raw,
                            sentAtMs = 1_767_688_200_000L,
                        ),
                    ),
                ),
                fontScale = LargeFontScale,
            )

            composeRule.onRoot()
                .captureRoboImage("src/test/screenshots/baseline/chat_history_long_message_compact_phone_hindi_large_font.png")
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun chatHistoryEmpty_compactPhone() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun chatHistoryEmpty_compactPhoneHindiLargeFont() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_empty_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun chatHistoryEmpty_typicalPhone() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_empty_typical_phone.png")
    }

    @Test
    fun chatHistoryError_compactPhone() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
                errorMessageRes = R.string.chat_history_error_load,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_error_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun chatHistoryError_compactPhoneHindiLargeFont() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
                errorMessageRes = R.string.chat_history_error_load,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_error_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun chatHistoryError_typicalPhone() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = false,
                contactId = ContactId,
                errorMessageRes = R.string.chat_history_error_load,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_error_typical_phone.png")
    }

    @Test
    fun chatHistoryLoading_compactPhone() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = true,
                contactId = ContactId,
            ),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun chatHistoryLoading_typicalPhone() {
        setChatHistoryContent(
            state = ChatHistoryUiState(
                isLoading = true,
                contactId = ContactId,
            ),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/chat_history_loading_typical_phone.png")
    }

    private fun setChatHistoryContent(
        state: ChatHistoryUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            ChatHistoryContent(
                uiState = state,
                onBack = {},
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun populatedChatHistoryState(): ChatHistoryUiState {
        return ChatHistoryUiState(
            isLoading = false,
            contactId = ContactId,
            messages = listOf(
                sentMessage(
                    id = "sent_whatsapp",
                    messageText = "Happy birthday, Riya. Hope your day feels easy, bright, and full of people who make you smile.",
                    channel = MessageChannel.WHATSAPP.raw,
                    sentAtMs = 1_767_688_200_000L,
                ),
                sentMessage(
                    id = "sent_sms",
                    messageText = "Thinking of you on your milestone day. Have a wonderful celebration.",
                    channel = MessageChannel.SMS.raw,
                    sentAtMs = 1_764_582_600_000L,
                    occasionType = "ANNIVERSARY",
                ),
                sentMessage(
                    id = "sent_email",
                    messageText = "Congratulations on the new role. Wishing you a strong start.",
                    channel = MessageChannel.EMAIL.raw,
                    sentAtMs = 1_760_176_800_000L,
                    occasionType = "MILESTONE",
                ),
            ),
        )
    }

    private fun sentMessage(
        id: String,
        messageText: String,
        channel: String,
        sentAtMs: Long,
        occasionType: String = "BIRTHDAY",
    ) = SentMessageEntity(
        id = id,
        contactId = ContactId,
        eventType = occasionType,
        occasionType = occasionType,
        eventYear = 2026,
        messageText = messageText,
        channel = channel,
        sentAtMs = sentAtMs,
        deliveryStatus = "SENT",
    )

    private companion object {
        const val ContactId = "contact_riya"
        const val ProgressAnimationFrameMillis = 750L
    }
}
