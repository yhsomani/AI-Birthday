package com.example.ui.screens.wish

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.ReviewNextTarget
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
                state = state.copy(editedText = it)
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
            .assertIsDisplayed()
            .performTextReplacement("Custom birthday draft")
        composeRule.onNodeWithTag(WishPreviewTestTags.WHY_PANEL)
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
    fun approvedAndErrorStates_renderExpectedContent() {
        var state by mutableStateOf(wishState().copy(approved = true))

        composeRule.setWishPreviewContent(state = { state })

        composeRule.onNodeWithTag(WishPreviewTestTags.APPROVED_MESSAGE)
            .performScrollTo()
            .assertIsDisplayed()

        state = WishPreviewUiState(
            isLoading = false,
            pendingMessage = null,
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
        pendingMessage = pendingMessage(),
        selectedVariant = "standard",
        editedText = "Standard birthday draft",
        isLoading = false,
        whySignals = listOf(
            WhySignal(R.string.wish_why_relationship, "FRIEND"),
            WhySignal(R.string.wish_why_language, "en"),
        ),
    )

    private fun pendingMessage() = PendingMessageEntity(
        id = "pm_1",
        contactId = "contact_1",
        eventId = "event_1",
        shortVariant = "Short birthday draft",
        standardVariant = "Standard birthday draft",
        longVariant = "Long birthday draft",
        formalVariant = "Formal birthday draft",
        funnyVariant = "Funny birthday draft",
        emotionalVariant = "Emotional birthday draft",
        selectedVariant = "standard",
        selectedVariantText = "Standard birthday draft",
        channel = "SMS",
        scheduledForMs = 1_800_000_000_000L,
        approvalMode = "VIP_APPROVE",
        status = "PENDING",
    )
}
