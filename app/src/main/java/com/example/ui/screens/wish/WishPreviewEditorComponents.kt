package com.example.ui.screens.wish

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WishPreviewUiState

private val variantOptions = listOf(
    "short" to R.string.wish_variant_short,
    "standard" to R.string.wish_variant_standard,
    "long" to R.string.wish_variant_long,
    "formal" to R.string.wish_variant_formal,
    "funny" to R.string.wish_variant_funny,
    "emotional" to R.string.wish_variant_emotional,
)

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
    var showApproveDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    if (showApproveDialog) {
        WishPreviewConfirmApproveDialog(
            onConfirm = {
                showApproveDialog = false
                onApprove()
            },
            onDismiss = { showApproveDialog = false },
        )
    }

    if (showRejectDialog) {
        WishPreviewConfirmRejectDialog(
            onConfirm = {
                showRejectDialog = false
                onReject()
            },
            onDismiss = { showRejectDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Text(
            text = stringResource(R.string.wish_preview_choose_tone),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
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
                readiness = state.sendActionReadiness,
                modifier = Modifier.testTag(WishPreviewTestTags.SEND_SUMMARY),
            )
        }

        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Text(
            text = stringResource(R.string.wish_preview_message_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        RelateGlassCard {
            OutlinedTextField(
                value = state.editedText,
                onValueChange = onEditedTextChange,
                label = {
                    Text(stringResource(R.string.wish_preview_message_label))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(RelateSpacing.sm)
                    .testTag(WishPreviewTestTags.MESSAGE_FIELD),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = RelateAlpha.fieldContainer,
                    ),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = RelateAlpha.fieldContainer,
                    ),
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                ),
                minLines = 4,
                maxLines = 8,
            )
        }
        DraftReadinessMessage(
            readiness = state.draftActionReadiness,
            modifier = Modifier.testTag(WishPreviewTestTags.DRAFT_READINESS),
        )

        state.errorMessageRes?.let { errorRes ->
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.qualityMessageRes?.let { messageRes ->
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Text(
                text = messageResource(messageRes, state.qualityMessageArgRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            if (state.isRegenerating) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(RelateSize.iconSm),
                    strokeWidth = RelateSpacing.xxs,
                )
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(RelateSize.iconSm),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.wish_preview_regenerate),
                    color = MaterialTheme.colorScheme.onBackground,
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            if (state.isTestingSend) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(RelateSize.iconSm),
                    strokeWidth = RelateSpacing.xxs,
                )
            } else {
                Text(
                    text = stringResource(R.string.wish_preview_send_test),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(modifier = Modifier.height(RelateSpacing.xl))
        if (state.isApproving) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (!state.approved && !state.rejected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                Button(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(RelateSize.primaryButtonHeight)
                        .testTag(WishPreviewTestTags.REJECT_BUTTON),
                    shape = RoundedCornerShape(RelateRadius.control),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    enabled = !state.isRejecting,
                ) {
                    if (state.isRejecting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(RelateSize.iconMd),
                            strokeWidth = RelateSpacing.xxs,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.wish_preview_reject),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                RelatePrimaryButton(
                    text = stringResource(R.string.wish_preview_approve_schedule),
                    onClick = { showApproveDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(WishPreviewTestTags.APPROVE_BUTTON),
                    enabled = !state.blocksApproval,
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
