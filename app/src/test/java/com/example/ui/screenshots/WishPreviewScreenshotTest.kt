package com.example.ui.screenshots

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewVariants
import com.example.ui.screens.wish.WishPreviewScreenContent
import com.example.ui.screens.wish.WishPreviewTestTags
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WishDraftReadiness
import com.example.ui.viewmodel.WishPreviewSendSummary
import com.example.ui.viewmodel.WishPreviewUiState
import com.example.ui.viewmodel.WhySignal
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
class WishPreviewScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wishPreviewEditor_compactPhone() {
        setWishPreviewContent(state = wishPreviewState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_editor_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun wishPreviewEditor_typicalPhone() {
        setWishPreviewContent(state = wishPreviewState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_editor_typical_phone.png")
    }

    @Test
    fun wishPreviewEditor_compactPhoneLargeFont() {
        setWishPreviewContent(
            state = wishPreviewState(),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_editor_compact_phone_large_font.png")
    }

    @Test
    fun wishPreviewBlocked_compactPhoneLargeFont() {
        setWishPreviewContent(
            state = wishPreviewState().copy(
                editedText = "Too short",
                draftReadiness = WishDraftReadiness.TOO_SHORT,
                selectedFeedbackKey = "too_generic",
                feedbackMessageRes = R.string.wish_preview_feedback_saved,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.CONTENT_BOTTOM)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_blocked_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun wishPreviewBlocked_compactPhoneHindiLargeFont() {
        setWishPreviewContent(
            state = wishPreviewHindiState().copy(
                editedText = "थोड़ा छोटा",
                draftReadiness = WishDraftReadiness.TOO_SHORT,
                selectedFeedbackKey = "wrong_language",
                feedbackMessageRes = R.string.wish_preview_feedback_saved,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.CONTENT_BOTTOM)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_blocked_compact_phone_hindi_large_font.png")
    }

    @Test
    fun wishPreviewApproved_compactPhone() {
        setWishPreviewContent(
            state = wishPreviewState().copy(
                approved = true,
                nextReviewTarget = ReviewNextTarget(
                    contactId = "contact-mira",
                    messageRef = "draft-mira",
                ),
                remainingReviewCount = 2,
            ),
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.CONTENT_BOTTOM)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_approved_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun wishPreviewApproved_typicalPhone() {
        setWishPreviewContent(
            state = wishPreviewState().copy(
                approved = true,
                nextReviewTarget = ReviewNextTarget(
                    contactId = "contact-mira",
                    messageRef = "draft-mira",
                ),
                remainingReviewCount = 2,
            ),
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.CONTENT_BOTTOM)
            .performScrollTo()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_approved_typical_phone.png")
    }

    @Test
    fun wishPreviewLoading_compactPhone() {
        setWishPreviewContent(
            state = WishPreviewUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun wishPreviewLoading_typicalPhone() {
        setWishPreviewContent(
            state = WishPreviewUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/wish_preview_loading_typical_phone.png")
    }

    private fun setWishPreviewContent(
        state: WishPreviewUiState,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            val snackbarHostState = remember { SnackbarHostState() }
            WishPreviewScreenContent(
                state = state,
                snackbarHostState = snackbarHostState,
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun wishPreviewState(): WishPreviewUiState {
        return WishPreviewUiState(
            previewDraft = previewDraft(),
            selectedVariant = "standard",
            editedText = "Happy birthday, Asha. I hope today brings you a calm morning, a loud laugh, and a few reminders of how much you matter to the people around you.",
            isLoading = false,
            whySignals = listOf(
                WhySignal(R.string.wish_why_relationship, "Close friend"),
                WhySignal(R.string.wish_why_language, "English"),
                WhySignal(R.string.wish_why_memories, "3 memory notes"),
                WhySignal(R.string.wish_why_gifts, "2 gift records"),
            ),
            sendSummary = WishPreviewSendSummary(
                eventType = "BIRTHDAY",
                channel = MessageChannel.SMS.raw,
                scheduledForMs = ScheduledAtMs,
                approvalMode = ApprovalMode.VIP_APPROVE.raw,
                usesFallback = false,
            ),
            draftReadiness = WishDraftReadiness.READY,
        )
    }

    private fun wishPreviewHindiState(): WishPreviewUiState {
        return WishPreviewUiState(
            previewDraft = previewDraftHindi(),
            selectedVariant = "standard",
            editedText = "जन्मदिन मुबारक हो, आशा। उम्मीद है आज की सुबह शांत हो, खूब हंसी मिले और आपको यह महसूस हो कि आप कितने मायने रखती हैं।",
            isLoading = false,
            whySignals = listOf(
                WhySignal(R.string.wish_why_relationship, "करीबी दोस्त"),
                WhySignal(R.string.wish_why_language, "हिंदी"),
                WhySignal(R.string.wish_why_memories, "3 मेमोरी नोट"),
                WhySignal(R.string.wish_why_gifts, "2 गिफ्ट रिकॉर्ड"),
            ),
            sendSummary = WishPreviewSendSummary(
                eventType = "जन्मदिन",
                channel = MessageChannel.SMS.raw,
                scheduledForMs = ScheduledAtMs,
                approvalMode = ApprovalMode.VIP_APPROVE.raw,
                usesFallback = false,
            ),
            draftReadiness = WishDraftReadiness.READY,
        )
    }

    private fun previewDraft(): WishPreviewDraft {
        return WishPreviewDraft(
            id = MessageDraftId("draft-asha"),
            contactId = ContactId("contact-asha"),
            occasionId = OccasionId("event-asha-birthday"),
            variants = WishPreviewVariants(
                short = "Happy birthday, Asha. Hope today feels easy and joyful.",
                standard = "Happy birthday, Asha. I hope today brings you a calm morning, a loud laugh, and a few reminders of how much you matter to the people around you.",
                long = "Happy birthday, Asha. I hope the day gives you space to feel celebrated, rest well, and enjoy the people who know how much heart you bring into everything.",
                formal = "Wishing you a very happy birthday, Asha. May the year ahead bring good health, meaningful progress, and many happy moments.",
                funny = "Happy birthday, Asha. May your cake be excellent, your notifications manageable, and your birthday plans require no project management.",
                emotional = "Happy birthday, Asha. I am grateful for your kindness, your steady presence, and the way you make ordinary days feel lighter.",
            ),
            selectedVariant = "standard",
            selectedVariantText = "Happy birthday, Asha. I hope today brings you a calm morning, a loud laugh, and a few reminders of how much you matter to the people around you.",
            channel = MessageChannel.SMS,
            scheduledForMs = ScheduledAtMs,
            approvalMode = ApprovalMode.VIP_APPROVE,
            status = MessageStatus.PENDING,
            isUsingFallback = false,
        )
    }

    private fun previewDraftHindi(): WishPreviewDraft {
        return WishPreviewDraft(
            id = MessageDraftId("draft-asha"),
            contactId = ContactId("contact-asha"),
            occasionId = OccasionId("event-asha-birthday"),
            variants = WishPreviewVariants(
                short = "जन्मदिन मुबारक हो, आशा। आज का दिन आसान और खुशियों भरा रहे।",
                standard = "जन्मदिन मुबारक हो, आशा। उम्मीद है आज की सुबह शांत हो, खूब हंसी मिले और आपको यह महसूस हो कि आप कितने मायने रखती हैं।",
                long = "जन्मदिन मुबारक हो, आशा। उम्मीद है आज आपको आराम, अपनापन और उन लोगों के साथ समय मिले जो आपकी कद्र करते हैं।",
                formal = "आशा, आपको जन्मदिन की हार्दिक शुभकामनाएं। आने वाला साल सेहत, प्रगति और अच्छे पलों से भरा रहे।",
                funny = "जन्मदिन मुबारक हो, आशा। केक अच्छा हो, नोटिफिकेशन संभल जाएं और प्लान बिना प्रोजेक्ट मैनेजमेंट के पूरे हों।",
                emotional = "जन्मदिन मुबारक हो, आशा। आपकी दयालुता और स्थिर साथ के लिए मैं सच में आभारी हूं।",
            ),
            selectedVariant = "standard",
            selectedVariantText = "जन्मदिन मुबारक हो, आशा। उम्मीद है आज की सुबह शांत हो, खूब हंसी मिले और आपको यह महसूस हो कि आप कितने मायने रखती हैं।",
            channel = MessageChannel.SMS,
            scheduledForMs = ScheduledAtMs,
            approvalMode = ApprovalMode.VIP_APPROVE,
            status = MessageStatus.PENDING,
            isUsingFallback = false,
        )
    }

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
        const val ScheduledAtMs = 1_800_000_000_000L
    }
}
