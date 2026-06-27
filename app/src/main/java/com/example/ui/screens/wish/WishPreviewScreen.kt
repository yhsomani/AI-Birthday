package com.example.ui.screens.wish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.occasion.OccasionType
import com.example.ui.feedback.asString
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WishDraftReadiness
import com.example.ui.viewmodel.WishPreviewSendSummary
import com.example.ui.viewmodel.WishPreviewUiState
import com.example.ui.viewmodel.WishPreviewViewModel
import com.example.ui.viewmodel.WhySignal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val variantOptions = listOf(
    "short" to R.string.wish_variant_short,
    "standard" to R.string.wish_variant_standard,
    "long" to R.string.wish_variant_long,
    "formal" to R.string.wish_variant_formal,
    "funny" to R.string.wish_variant_funny,
    "emotional" to R.string.wish_variant_emotional,
)

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
            .background(RelateDarkBackground),
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
                        tint = RelateOnBackground,
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
                    CircularProgressIndicator(color = RelatePrimary)
                }
            } else if (state.errorMessageRes != null && state.previewDraft == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(state.errorMessageRes ?: R.string.wish_preview_error_unknown),
                        color = RelateOnSurfaceVariant,
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

@Composable
internal fun WishPreviewContent(
    state: WishPreviewUiState,
    onVariantSelected: (String) -> Unit = {},
    onEditedTextChange: (String) -> Unit = {},
    onFeedbackSelected: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onSendTest: () -> Unit = {},
    onReject: () -> Unit = {},
    onApprove: () -> Unit = {},
    onReviewNext: (ReviewNextTarget) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Text(
            text = stringResource(R.string.wish_preview_choose_tone),
            style = MaterialTheme.typography.titleSmall,
            color = RelatePrimary,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            variantOptions.take(3).forEach { (key, labelRes) ->
                ToneChip(
                    label = stringResource(labelRes),
                    isSelected = state.selectedVariant == key,
                    onClick = { onVariantSelected(key) },
                    modifier = Modifier.testTag(WishPreviewTestTags.VARIANT_PREFIX + key),
                )
            }
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            variantOptions.drop(3).forEach { (key, labelRes) ->
                ToneChip(
                    label = stringResource(labelRes),
                    isSelected = state.selectedVariant == key,
                    onClick = { onVariantSelected(key) },
                    modifier = Modifier.testTag(WishPreviewTestTags.VARIANT_PREFIX + key),
                )
            }
        }

        state.sendSummary?.let { summary ->
            Spacer(modifier = Modifier.height(RelateSpacing.lg))
            WishSendSummaryCard(
                summary = summary,
                modifier = Modifier.testTag(WishPreviewTestTags.SEND_SUMMARY),
            )
        }

        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Text(
            text = stringResource(R.string.wish_preview_message_label),
            style = MaterialTheme.typography.titleSmall,
            color = RelatePrimary,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        RelateGlassCard {
            OutlinedTextField(
                value = state.editedText,
                onValueChange = onEditedTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RelateSpacing.sm)
                    .testTag(WishPreviewTestTags.MESSAGE_FIELD),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RelatePrimary,
                    unfocusedBorderColor = RelateSurfaceVariant,
                    focusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                    unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
                    focusedTextColor = RelateOnBackground,
                    unfocusedTextColor = RelateOnBackground,
                ),
                minLines = 4,
                maxLines = 8,
            )
        }
        DraftReadinessMessage(
            readiness = state.draftReadiness,
            modifier = Modifier.testTag(WishPreviewTestTags.DRAFT_READINESS),
        )

        state.errorMessageRes?.let { errorRes ->
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }

        state.qualityMessageRes?.let { messageRes ->
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = messageResource(messageRes, state.qualityMessageArgRes),
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }

        if (state.whySignals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(RelateSpacing.lg))
            WhyThisMessagePanel(
                signals = state.whySignals,
                modifier = Modifier.testTag(WishPreviewTestTags.WHY_PANEL),
            )
        }

        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Text(
            text = stringResource(R.string.wish_preview_feedback_title),
            style = MaterialTheme.typography.titleSmall,
            color = RelatePrimary,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        state.feedbackOptions.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            ) {
                rowOptions.forEach { option ->
                    FeedbackChip(
                        label = stringResource(option.labelRes),
                        isSelected = state.selectedFeedbackKey == option.key,
                        onClick = { onFeedbackSelected(option.key) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(WishPreviewTestTags.FEEDBACK_PREFIX + option.key),
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
        }
        state.feedbackMessageRes?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Button(
            onClick = onRegenerate,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WishPreviewTestTags.REGENERATE_BUTTON),
            enabled = !state.isRegenerating && !state.isApproving && !state.isRejecting && !state.isTestingSend,
            shape = RoundedCornerShape(RelateRadius.control),
            colors = ButtonDefaults.buttonColors(
                containerColor = RelateSurfaceVariant,
            ),
        ) {
            if (state.isRegenerating) {
                CircularProgressIndicator(
                    color = RelateOnBackground,
                    modifier = Modifier.size(RelateSize.iconSm),
                    strokeWidth = RelateSpacing.xxs,
                )
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = RelateOnBackground,
                    modifier = Modifier.size(RelateSize.iconSm),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.wish_preview_regenerate),
                    color = RelateOnBackground,
                )
            }
        }

        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Button(
            onClick = onSendTest,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WishPreviewTestTags.TEST_SEND_BUTTON),
            enabled = !state.isTestingSend && !state.isRegenerating && !state.isApproving && !state.isRejecting,
            shape = RoundedCornerShape(RelateRadius.control),
            colors = ButtonDefaults.buttonColors(
                containerColor = RelateSurfaceVariant,
            ),
        ) {
            if (state.isTestingSend) {
                CircularProgressIndicator(
                    color = RelateOnBackground,
                    modifier = Modifier.size(RelateSize.iconSm),
                    strokeWidth = RelateSpacing.xxs,
                )
            } else {
                Text(
                    text = stringResource(R.string.wish_preview_send_test),
                    color = RelateOnBackground,
                )
            }
        }

        Spacer(modifier = Modifier.height(RelateSpacing.xl))
        if (state.isApproving) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else if (!state.approved && !state.rejected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(RelateSize.primaryButtonHeight)
                        .testTag(WishPreviewTestTags.REJECT_BUTTON),
                    shape = RoundedCornerShape(RelateRadius.control),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelateSurfaceVariant,
                    ),
                    enabled = !state.isRejecting,
                ) {
                    if (state.isRejecting) {
                        CircularProgressIndicator(
                            color = RelateOnBackground,
                            modifier = Modifier.size(RelateSize.iconMd),
                            strokeWidth = RelateSpacing.xxs,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.wish_preview_reject),
                            style = MaterialTheme.typography.labelLarge,
                            color = RelateOnBackground,
                        )
                    }
                }
                RelatePrimaryButton(
                    text = stringResource(R.string.wish_preview_approve_schedule),
                    onClick = onApprove,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(WishPreviewTestTags.APPROVE_BUTTON),
                    enabled = !state.draftReadiness.blocksApproval(),
                )
            }
        } else if (state.approved) {
            ReviewResultPanel(
                message = stringResource(R.string.wish_preview_approved),
                messageTag = WishPreviewTestTags.APPROVED_MESSAGE,
                state = state,
                onReviewNext = onReviewNext,
            )
        } else if (state.rejected) {
            ReviewResultPanel(
                message = stringResource(R.string.wish_preview_rejected),
                messageTag = WishPreviewTestTags.REJECTED_MESSAGE,
                state = state,
                onReviewNext = onReviewNext,
            )
        }

        Spacer(
            modifier = Modifier
                .height(RelateSpacing.xl)
                .testTag(WishPreviewTestTags.CONTENT_BOTTOM),
        )
    }
}

@Composable
private fun DraftReadinessMessage(
    readiness: WishDraftReadiness,
    modifier: Modifier = Modifier,
) {
    Text(
        text = readiness.label(),
        style = MaterialTheme.typography.bodySmall,
        color = if (readiness.blocksApproval()) {
            MaterialTheme.colorScheme.error
        } else {
            RelateOnSurfaceVariant
        },
        modifier = modifier.padding(top = RelateSpacing.sm),
    )
}

@Composable
private fun WishDraftReadiness.label(): String = when (this) {
    WishDraftReadiness.READY -> stringResource(R.string.wish_preview_readiness_ready)
    WishDraftReadiness.EDITED_READY -> stringResource(R.string.wish_preview_readiness_edited)
    WishDraftReadiness.TOO_SHORT -> stringResource(R.string.wish_preview_readiness_short)
    WishDraftReadiness.BLANK -> stringResource(R.string.wish_preview_readiness_blank)
}

private fun WishDraftReadiness.blocksApproval(): Boolean {
    return this == WishDraftReadiness.BLANK || this == WishDraftReadiness.TOO_SHORT
}

@Composable
private fun WishSendSummaryCard(
    summary: WishPreviewSendSummary,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()) }
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.wish_preview_send_summary_title),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_event),
                value = eventTypeLabel(summary.eventType),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_route),
                value = channelLabel(summary.channel),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_schedule),
                value = dateFormat.format(Date(summary.scheduledForMs)),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_approval),
                value = approvalModeLabel(summary.approvalMode),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_quality),
                value = if (summary.usesFallback) {
                    stringResource(R.string.wish_preview_summary_quality_fallback)
                } else {
                    stringResource(R.string.wish_preview_summary_quality_ai)
                },
            )
        }
    }
}

@Composable
private fun SendSummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = RelateOnSurfaceVariant,
            modifier = Modifier.weight(RelateFraction.metadataLabel),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(RelateFraction.metadataValue),
        )
    }
}

@Composable
private fun eventTypeLabel(eventType: String): String = when (OccasionType.fromRaw(eventType)) {
    OccasionType.BIRTHDAY -> stringResource(R.string.event_type_birthday)
    OccasionType.ANNIVERSARY -> stringResource(R.string.event_type_anniversary)
    OccasionType.WORK_ANNIVERSARY -> stringResource(R.string.event_type_work_anniversary)
    else -> stringResource(R.string.event_type_custom)
}

@Composable
private fun channelLabel(channel: String): String = when (MessageChannel.fromRaw(channel)) {
    MessageChannel.SMS -> stringResource(R.string.channel_sms)
    MessageChannel.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannel.EMAIL -> stringResource(R.string.channel_email)
    MessageChannel.UNKNOWN -> channel
}

@Composable
private fun approvalModeLabel(approvalMode: String): String = when (ApprovalMode.fromRaw(approvalMode)) {
    ApprovalMode.FULLY_AUTO -> stringResource(R.string.automation_mode_fully_auto)
    ApprovalMode.SMART_APPROVE -> stringResource(R.string.automation_mode_smart_approve_default)
    ApprovalMode.VIP_APPROVE -> stringResource(R.string.automation_mode_vip_approve)
    ApprovalMode.ALWAYS_ASK -> stringResource(R.string.automation_mode_always_ask)
    ApprovalMode.DEFAULT,
    ApprovalMode.UNKNOWN -> stringResource(R.string.automation_mode_default)
}

@Composable
private fun ReviewResultPanel(
    message: String,
    messageTag: String,
    state: WishPreviewUiState,
    onReviewNext: (ReviewNextTarget) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = RelatePrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag(messageTag),
        )
        val nextTarget = state.nextReviewTarget
        if (nextTarget != null) {
            Text(
                text = reviewQueueText(state.remainingReviewCount),
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
                modifier = Modifier.testTag(WishPreviewTestTags.REVIEW_NEXT_COUNT),
            )
            RelatePrimaryButton(
                text = stringResource(R.string.wish_preview_review_next),
                onClick = { onReviewNext(nextTarget) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WishPreviewTestTags.REVIEW_NEXT_BUTTON),
            )
        }
    }
}

@Composable
private fun reviewQueueText(remainingReviewCount: Int): String {
    return if (remainingReviewCount == 1) {
        stringResource(R.string.wish_preview_review_next_count_one)
    } else {
        stringResource(R.string.wish_preview_review_next_count_many, remainingReviewCount)
    }
}

@Composable
private fun messageResource(messageRes: Int, argRes: Int?): String {
    return if (argRes != null) {
        stringResource(messageRes, stringResource(argRes))
    } else {
        stringResource(messageRes)
    }
}

@Composable
private fun FeedbackChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(RelateSize.compactButtonHeight)
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) RelatePrimary else RelateSurfaceVariant,
                shape = RoundedCornerShape(RelateRadius.pill),
            )
            .padding(horizontal = RelateSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WhyThisMessagePanel(
    signals: List<WhySignal>,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.wish_why_title),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            signals.forEach { signal ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(signal.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                        modifier = Modifier.weight(RelateFraction.metadataLabel),
                    )
                    Text(
                        text = signal.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(RelateFraction.metadataValue),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToneChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) RelatePrimary else RelateSurfaceVariant,
                shape = RoundedCornerShape(RelateRadius.pill),
            )
            .padding(horizontal = RelateSpacing.lg, vertical = RelateSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
