package com.example.ui.screens.wish

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewVariants
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WishDraftReadiness
import com.example.ui.viewmodel.WishPreviewSendSummary
import com.example.ui.viewmodel.WhySignal
import com.example.ui.viewmodel.WishPreviewUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WishPreviewScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun previewControls_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(wishState())

        composeRule.setWishPreviewContent(
            state = { state },
            onBack = { actions += "back" },
            onVariantSelected = {
                actions += "variant:$it"
                state = state.copy(selectedVariant = it)
            },
            onEditedTextChange = {
                actions += "edit:$it"
                state = state.copy(editedText = it, draftReadiness = WishDraftReadiness.EDITED_READY)
            },
            onFeedbackSelected = {
                actions += "feedback:$it"
                state = state.copy(selectedFeedbackKey = it)
            },
            onRegenerate = { actions += "regenerate" },
            onSendTest = { actions += "test" },
            onReject = { actions += "reject" },
            onApprove = { actions += "approve" },
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.BACK_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(WishPreviewTestTags.VARIANT_PREFIX + "funny")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(WishPreviewTestTags.MESSAGE_FIELD)
            .performScrollTo()
            .assertIsDisplayed()
            .performTextReplacement("Custom birthday draft")
        composeRule.onNodeWithTag(WishPreviewTestTags.DRAFT_READINESS)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Edited draft will be saved when approved.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WishPreviewTestTags.WHY_PANEL)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WishPreviewTestTags.SEND_SUMMARY)
            .performScrollTo()
            .assertIsDisplayed()
        clickScrollableTag(WishPreviewTestTags.FEEDBACK_PREFIX + "too_generic")
        clickScrollableTag(WishPreviewTestTags.REGENERATE_BUTTON)
        clickScrollableTag(WishPreviewTestTags.TEST_SEND_BUTTON)
        clickScrollableTag(WishPreviewTestTags.REJECT_BUTTON)
        clickScrollableTag(WishPreviewTestTags.APPROVE_BUTTON)

        assertEquals(
            listOf(
                "back",
                "variant:funny",
                "edit:Custom birthday draft",
                "feedback:too_generic",
                "regenerate",
                "test",
                "reject",
                "approve",
            ),
            actions,
        )
    }

    @Test
    fun sendSummary_rendersApprovalPlanBeforeActions() {
        composeRule.setWishPreviewContent(
            state = {
                wishState().copy(
                    sendSummary = WishPreviewSendSummary(
                        eventType = "ANNIVERSARY",
                        channel = " ${MessageChannel.EMAIL.raw.lowercase()} ",
                        scheduledForMs = 1_800_000_000_000L,
                        approvalMode = "SMART_APPROVE",
                        usesFallback = true,
                    ),
                )
            },
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.SEND_SUMMARY)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Approval plan").assertIsDisplayed()
        composeRule.onNodeWithText("Anniversary").assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Smart Approve (default)").assertIsDisplayed()
        composeRule.onNodeWithText("Template fallback").assertIsDisplayed()
    }

    @Test
    fun blankDraftReadinessDisablesApprovalAction() {
        composeRule.setWishPreviewContent(
            state = {
                wishState().copy(
                    editedText = "",
                    draftReadiness = WishDraftReadiness.BLANK,
                )
            },
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.DRAFT_READINESS)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Write a message before approval.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WishPreviewTestTags.APPROVE_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun shortDraftReadinessDisablesApprovalAction() {
        composeRule.setWishPreviewContent(
            state = {
                wishState().copy(
                    editedText = "Too short",
                    draftReadiness = WishDraftReadiness.TOO_SHORT,
                )
            },
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.DRAFT_READINESS)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Write a longer message before approval.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WishPreviewTestTags.APPROVE_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun approvedAndErrorStates_renderExpectedContent() {
        var state by mutableStateOf(wishState().copy(approved = true))

        composeRule.setWishPreviewContent(state = { state })

        composeRule.onNodeWithTag(WishPreviewTestTags.APPROVED_MESSAGE)
            .performScrollTo()
            .assertIsDisplayed()

        state = WishPreviewUiState(
            isLoading = false,
            previewDraft = null,
            errorMessageRes = R.string.wish_preview_error_message_not_found,
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.ERROR_MESSAGE)
            .assertIsDisplayed()
    }

    @Test
    fun approvedState_withNextPendingWish_offersExplicitReviewNextAction() {
        var selectedTarget: ReviewNextTarget? = null

        composeRule.setWishPreviewContent(
            state = {
                wishState().copy(
                    approved = true,
                    nextReviewTarget = ReviewNextTarget(contactId = "contact_2", messageRef = "pm_2"),
                    remainingReviewCount = 1,
                )
            },
            onReviewNext = { selectedTarget = it },
        )

        composeRule.onNodeWithTag(WishPreviewTestTags.APPROVED_MESSAGE)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WishPreviewTestTags.REVIEW_NEXT_COUNT)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WishPreviewTestTags.REVIEW_NEXT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(ReviewNextTarget(contactId = "contact_2", messageRef = "pm_2"), selectedTarget)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setWishPreviewContent(
        state: () -> WishPreviewUiState,
        onBack: () -> Unit = {},
        onVariantSelected: (String) -> Unit = {},
        onEditedTextChange: (String) -> Unit = {},
        onFeedbackSelected: (String) -> Unit = {},
        onRegenerate: () -> Unit = {},
        onSendTest: () -> Unit = {},
        onReject: () -> Unit = {},
        onApprove: () -> Unit = {},
        onReviewNext: (ReviewNextTarget) -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                val snackbarHostState = remember { SnackbarHostState() }
                WishPreviewScreenContent(
                    state = state(),
                    snackbarHostState = snackbarHostState,
                    onBack = onBack,
                    onVariantSelected = onVariantSelected,
                    onEditedTextChange = onEditedTextChange,
                    onFeedbackSelected = onFeedbackSelected,
                    onRegenerate = onRegenerate,
                    onSendTest = onSendTest,
                    onReject = onReject,
                    onApprove = onApprove,
                    onReviewNext = onReviewNext,
                )
            }
        }
    }

    private fun clickScrollableTag(tag: String) {
        composeRule.onNodeWithTag(tag)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    private fun wishState() = WishPreviewUiState(
        previewDraft = previewDraft(),
        selectedVariant = "standard",
        editedText = "Standard birthday draft",
        isLoading = false,
        whySignals = listOf(
            WhySignal(R.string.wish_why_relationship, "FRIEND"),
            WhySignal(R.string.wish_why_language, "en"),
        ),
        sendSummary = WishPreviewSendSummary(
            eventType = "BIRTHDAY",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 1_800_000_000_000L,
            approvalMode = "VIP_APPROVE",
            usesFallback = false,
        ),
        draftReadiness = WishDraftReadiness.READY,
    )

    private fun previewDraft() = WishPreviewDraft(
        id = MessageDraftId("pm_1"),
        contactId = ContactId("contact_1"),
        occasionId = OccasionId("event_1"),
        variants = WishPreviewVariants(
            short = "Short birthday draft",
            standard = "Standard birthday draft",
            long = "Long birthday draft",
            formal = "Formal birthday draft",
            funny = "Funny birthday draft",
            emotional = "Emotional birthday draft",
        ),
        selectedVariant = "standard",
        selectedVariantText = "Standard birthday draft",
        channel = MessageChannel.SMS,
        scheduledForMs = 1_800_000_000_000L,
        approvalMode = ApprovalMode.VIP_APPROVE,
        status = MessageStatus.PENDING,
        isUsingFallback = false,
    )
}
