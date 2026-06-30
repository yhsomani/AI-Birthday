package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.ApprovalMode

@Composable
internal fun MessagePendingActionRow(
    approvalMode: ApprovalMode,
    scheduledForMs: Long,
    onReject: () -> Unit,
    rejectTestTag: String,
    onEdit: () -> Unit,
    editTestTag: String,
    isApproving: Boolean,
    showApproveAction: Boolean,
    onApprove: () -> Unit,
    approveTestTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MessageApprovalModeStatus(
            approvalMode = approvalMode,
            scheduledForMs = scheduledForMs,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            MessageRejectActionButton(
                onClick = onReject,
                testTag = rejectTestTag,
            )
            MessageEditActionButton(
                onClick = onEdit,
                testTag = editTestTag,
            )
            if (showApproveAction) {
                MessageApproveActionButton(
                    isApproving = isApproving,
                    onClick = onApprove,
                    testTag = approveTestTag,
                )
            }
        }
    }
}

@Composable
internal fun MessageFailedActionRow(
    isRetrying: Boolean,
    onRetry: () -> Unit,
    retryTestTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        MessageRetryActionButton(
            isRetrying = isRetrying,
            onClick = onRetry,
            testTag = retryTestTag,
        )
    }
}

@Composable
internal fun MessageApprovedActionRow(
    onReject: () -> Unit,
    rejectTestTag: String,
    onEdit: () -> Unit,
    editTestTag: String,
    isRevoking: Boolean,
    onRevoke: () -> Unit,
    revokeTestTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
            MessageRejectActionButton(
                onClick = onReject,
                testTag = rejectTestTag,
            )
            MessageEditActionButton(
                onClick = onEdit,
                testTag = editTestTag,
            )
            MessageRevokeActionButton(
                isRevoking = isRevoking,
                onClick = onRevoke,
                testTag = revokeTestTag,
            )
        }
    }
}
