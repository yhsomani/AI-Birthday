package com.example.ui.screens.wish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.readiness.RelationshipReadinessState
import com.example.ui.viewmodel.ReviewNextTarget
import com.example.ui.viewmodel.WhySignal
import com.example.ui.viewmodel.WishPreviewUiState

@Composable
internal fun DraftReadinessMessage(
    readiness: RelationshipActionReadiness,
    modifier: Modifier = Modifier,
) {
    Text(
        text = readiness.label(),
        style = MaterialTheme.typography.bodySmall,
        color = if (readiness.state == RelationshipReadinessState.ACTION_REQUIRED) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.padding(top = RelateSpacing.sm),
    )
}

@Composable
private fun RelationshipActionReadiness.label(): String = when (primaryReason) {
    RelationshipReadinessReason.DRAFT_READY -> stringResource(R.string.wish_preview_readiness_ready)
    RelationshipReadinessReason.DRAFT_EDITED_READY -> stringResource(R.string.wish_preview_readiness_edited)
    RelationshipReadinessReason.DRAFT_TOO_SHORT -> stringResource(R.string.wish_preview_readiness_short)
    RelationshipReadinessReason.DRAFT_BLANK -> stringResource(R.string.wish_preview_readiness_blank)
    else -> stringResource(R.string.wish_preview_readiness_ready)
}

@Composable
internal fun ReviewResultPanel(
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
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag(messageTag),
        )
        val nextTarget = state.nextReviewTarget
        if (nextTarget != null) {
            Text(
                text = reviewQueueText(state.remainingReviewCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun messageResource(messageRes: Int, argRes: Int?): String {
    return if (argRes != null) {
        stringResource(messageRes, stringResource(argRes))
    } else {
        stringResource(messageRes)
    }
}

@Composable
internal fun FeedbackChip(
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
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
internal fun WhyThisMessagePanel(
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
                color = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun ToneChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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

@Composable
internal fun WishPreviewConfirmApproveDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(WishPreviewTestTags.CONFIRM_APPROVE_DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.wish_preview_confirm_approve_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.wish_preview_confirm_approve_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(WishPreviewTestTags.CONFIRM_APPROVE_ACTION),
                onClick = onConfirm,
            ) {
                Text(
                    text = stringResource(R.string.wish_preview_confirm_approve_action),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(WishPreviewTestTags.CONFIRM_APPROVE_CANCEL),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun WishPreviewConfirmRejectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(WishPreviewTestTags.CONFIRM_REJECT_DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.wish_preview_confirm_reject_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.wish_preview_confirm_reject_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(WishPreviewTestTags.CONFIRM_REJECT_ACTION),
                onClick = onConfirm,
            ) {
                Text(
                    text = stringResource(R.string.wish_preview_confirm_reject_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(WishPreviewTestTags.CONFIRM_REJECT_CANCEL),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
