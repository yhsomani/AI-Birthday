package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.SentMessageListItem
import com.example.ui.screens.messages.MessagesContent
import com.example.ui.screens.messages.MessagesTestTags
import com.example.ui.viewmodel.MessageReadiness
import com.example.ui.viewmodel.MessagesUiState
import com.example.ui.viewmodel.PendingMessageItem
import com.example.ui.viewmodel.SentMessageItem
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
class MessagesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun messagesNeedsReview_compactPhone() {
        setMessagesContent(state = messagesState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_needs_review_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun messagesNeedsReview_typicalPhone() {
        setMessagesContent(state = messagesState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_needs_review_typical_phone.png")
    }

    @Test
    fun messagesNeedsReview_compactPhoneLargeFont() {
        setMessagesContent(
            state = messagesState(),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_needs_review_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun messagesNeedsReview_compactPhoneHindiLargeFont() {
        setMessagesContent(
            state = messagesState(
                needsReviewSelectedText = "जन्मदिन मुबारक हो, तारा। उम्मीद है आपका दिन अपनापन, सुकून और छोटी खुशियों से भरा रहे।",
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_needs_review_compact_phone_hindi_large_font.png")
    }

    @Test
    fun messagesFailedRecovery_compactPhoneLargeFont() {
        setMessagesContent(
            state = messagesState(),
            initialPage = FailedTabIndex,
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_failed_recovery_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun messagesFailedRecovery_typicalPhone() {
        setMessagesContent(
            state = messagesState(),
            initialPage = FailedTabIndex,
        )

        composeRule.onNodeWithTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_failed_recovery_typical_phone.png")
    }

    @Test
    fun messagesRejectDialog_compactPhone() {
        setMessagesContent(state = messagesState())

        composeRule.onNodeWithTag(MessagesTestTags.PENDING_REJECT_PREFIX + NeedsReviewId)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.REJECT_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/messages_reject_dialog_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun messagesRejectDialog_typicalPhone() {
        setMessagesContent(state = messagesState())

        composeRule.onNodeWithTag(MessagesTestTags.PENDING_REJECT_PREFIX + NeedsReviewId)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.REJECT_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/messages_reject_dialog_typical_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun messagesRejectDialog_compactPhoneHindiLargeFont() {
        setMessagesContent(
            state = messagesState(),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(MessagesTestTags.PENDING_REJECT_PREFIX + NeedsReviewId)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.REJECT_DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/messages_reject_dialog_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun messagesFailedRecovery_compactPhoneHindiLargeFont() {
        setMessagesContent(
            state = messagesState(
                failedSelectedText = "जन्मदिन मुबारक हो, फेय। आज आपके लिए थोड़ी अतिरिक्त खुशी और अपनापन भेज रहा हूं।",
            ),
            initialPage = FailedTabIndex,
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_failed_recovery_compact_phone_hindi_large_font.png")
    }

    @Test
    fun messagesLoading_compactPhone() {
        setMessagesContent(
            state = MessagesUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun messagesLoading_typicalPhone() {
        setMessagesContent(
            state = MessagesUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/messages_loading_typical_phone.png")
    }

    private fun setMessagesContent(
        state: MessagesUiState,
        initialPage: Int = NeedsReviewTabIndex,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            MessagesContent(
                state = state,
                initialPage = initialPage,
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun messagesState(
        needsReviewSelectedText: String = "Happy birthday, Tara. I hope your day feels thoughtful, warm, and full of small reminders that you are appreciated.",
        failedSelectedText: String = "Happy birthday, Faye. Sending a little extra cheer your way today.",
    ): MessagesUiState {
        return MessagesUiState(
            needsReviewMessages = listOf(
                pendingItem(
                    id = NeedsReviewId,
                    contactId = "contact-tara",
                    contactName = "Tara",
                    channel = MessageChannel.SMS.raw,
                    eventType = "BIRTHDAY",
                    selectedText = needsReviewSelectedText,
                ),
            ),
            scheduledMessages = listOf(
                pendingItem(
                    id = ScheduledId,
                    contactId = "contact-dev",
                    contactName = "Dev",
                    channel = MessageChannel.WHATSAPP.raw,
                    eventType = "WORK_ANNIVERSARY",
                    status = "APPROVED",
                    readiness = MessageReadiness.APPROVED_SCHEDULED,
                    selectedText = "Congrats on the work anniversary, Dev. Wishing you a strong year ahead.",
                ),
            ),
            blockedMessages = listOf(
                pendingItem(
                    id = BlockedId,
                    contactId = "contact-blake",
                    contactName = "Blake",
                    channel = MessageChannel.EMAIL.raw,
                    eventType = "ANNIVERSARY",
                    readiness = MessageReadiness.MISSING_EMAIL,
                    selectedText = "Happy anniversary, Blake. Hope you both get time to celebrate.",
                ),
            ),
            sentMessages = listOf(sentItem()),
            failedMessages = listOf(
                pendingItem(
                    id = FailedId,
                    contactId = "contact-faye",
                    contactName = "Faye",
                    channel = MessageChannel.SMS.raw,
                    eventType = "BIRTHDAY",
                    status = "FAILED",
                    readiness = MessageReadiness.FAILED_CHECK_SETUP,
                    selectedText = failedSelectedText,
                ),
            ),
            isLoading = false,
        )
    }

    private fun pendingItem(
        id: String,
        contactId: String,
        contactName: String,
        channel: String,
        eventType: String,
        selectedText: String,
        status: String = "PENDING",
        readiness: MessageReadiness = MessageReadiness.READY_FOR_REVIEW,
    ): PendingMessageItem {
        return PendingMessageItem(
            message = PendingMessageListItem(
                id = MessageDraftId(id),
                contactId = ContactId(contactId),
                occasionId = OccasionId("event-$id"),
                selectedVariantText = selectedText,
                standardVariant = selectedText,
                channel = MessageChannel.fromRaw(channel),
                scheduledForMs = ScheduledAtMs,
                approvalMode = ApprovalMode.UNKNOWN,
                status = MessageStatus.fromRaw(status),
                editedByUser = false,
                userEditedText = null,
            ),
            contactName = contactName,
            eventType = eventType,
            readiness = readiness,
        )
    }

    private fun sentItem(): SentMessageItem {
        return SentMessageItem(
            message = SentMessageListItem(
                id = SentMessageId(SentId),
                contactId = ContactId("contact-sam"),
                occasionType = "BIRTHDAY",
                messageText = "Sent birthday wish for Sam.",
                channel = MessageChannel.EMAIL,
                sentAtMs = ScheduledAtMs,
                deliveryStatus = MessageDeliveryStatus.SENT,
            ),
            contactName = "Sam",
        )
    }

    private companion object {
        const val NeedsReviewTabIndex = 0
        const val FailedTabIndex = 4
        const val ProgressAnimationFrameMillis = 750L
        const val ScheduledAtMs = 1_800_000_000_000L
        const val NeedsReviewId = "needs-review-1"
        const val ScheduledId = "scheduled-1"
        const val BlockedId = "blocked-1"
        const val SentId = "sent-1"
        const val FailedId = "failed-1"
    }
}
