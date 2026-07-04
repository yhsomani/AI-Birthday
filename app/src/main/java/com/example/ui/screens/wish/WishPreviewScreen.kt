package com.example.ui.screens.wish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.feedback.asString
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WishPreviewUiState
import com.example.ui.viewmodel.WishPreviewViewModel

internal object WishPreviewTestTags {
    const val BACK_BUTTON = "wish_preview_back"
    const val MESSAGE_FIELD = "wish_preview_message_field"
    const val DRAFT_READINESS = "wish_preview_draft_readiness"
    const val SEND_SUMMARY = "wish_preview_send_summary"
    const val WHY_PANEL = "wish_preview_why_panel"
    const val REGENERATE_BUTTON = "wish_preview_regenerate"
    const val TEST_SEND_BUTTON = "wish_preview_test_send"
    const val REJECT_BUTTON = "wish_preview_reject"
    const val APPROVE_BUTTON = "wish_preview_approve"
    const val APPROVED_MESSAGE = "wish_preview_approved_message"
    const val REJECTED_MESSAGE = "wish_preview_rejected_message"
    const val REVIEW_NEXT_BUTTON = "wish_preview_review_next"
    const val REVIEW_NEXT_COUNT = "wish_preview_review_next_count"
    const val ERROR_MESSAGE = "wish_preview_error_message"
    const val CONTENT_BOTTOM = "wish_preview_content_bottom"
    const val VARIANT_PREFIX = "wish_preview_variant_"
    const val FEEDBACK_PREFIX = "wish_preview_feedback_"
    const val CONFIRM_APPROVE_DIALOG = "wish_preview_confirm_approve_dialog"
    const val CONFIRM_APPROVE_ACTION = "wish_preview_confirm_approve_action"
    const val CONFIRM_APPROVE_CANCEL = "wish_preview_confirm_approve_cancel"
    const val CONFIRM_REJECT_DIALOG = "wish_preview_confirm_reject_dialog"
    const val CONFIRM_REJECT_ACTION = "wish_preview_confirm_reject_action"
    const val CONFIRM_REJECT_CANCEL = "wish_preview_confirm_reject_cancel"
}

@Composable
fun WishPreviewScreen(
    contactId: String,
    messageRef: String,
    onBack: () -> Unit = {},
    onSent: () -> Unit = {},
    onReviewNext: (contactId: String, messageRef: String) -> Unit = { _, _ -> },
    viewModel: WishPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val testSentMessage = stringResource(R.string.wish_preview_test_sent)
    val feedbackText = state.feedbackEvent?.message?.asString()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.testSent) {
        if (state.testSent) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(testSentMessage)
                viewModel.dismissTestSent()
            }
        }
    }

    LaunchedEffect(state.feedbackEvent?.id, feedbackText) {
        if (feedbackText != null) {
            snackbarHostState.showSnackbar(feedbackText)
            viewModel.clearFeedbackEvent()
        }
    }


    LaunchedEffect(messageRef) {
        viewModel.loadPending(messageRef)
    }

    LaunchedEffect(state.approved, state.nextReviewTarget) {
        if (state.approved && state.nextReviewTarget == null) {
            onSent()
        }
    }

    WishPreviewScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onVariantSelected = viewModel::selectVariant,
        onEditedTextChange = viewModel::updateEditedText,
        onFeedbackSelected = viewModel::submitFeedback,
        onRegenerate = viewModel::regenerate,
        onSendTest = viewModel::sendTestToMyself,
        onReject = viewModel::reject,
        onApprove = viewModel::approve,
        onReviewNext = { target -> onReviewNext(target.contactId, target.messageRef) },
    )
}

@Composable
internal fun WishPreviewScreenContent(
    state: WishPreviewUiState,
    snackbarHostState: SnackbarHostState,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = RelateSpacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(WishPreviewTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.wish_preview_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.errorMessageRes != null && state.previewDraft == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(state.errorMessageRes ?: R.string.wish_preview_error_unknown),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(WishPreviewTestTags.ERROR_MESSAGE),
                    )
                }
            } else {
                WishPreviewContent(
                    state = state,
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(RelateSpacing.screenHorizontal),
        )
    }
}
