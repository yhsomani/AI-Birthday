package com.example.ui.screens.messages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.viewmodel.PendingMessageItem
import com.example.ui.viewmodel.SentMessageItem

@Composable
internal fun PendingMessagesList(
    messages: List<PendingMessageItem>,
    emptyText: String,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onOpenContact: (String) -> Unit,
    onOpenAutomationSetup: () -> Unit,
    approvingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    showApproveAction: Boolean = true,
    cardTagPrefix: String = MessagesTestTags.PENDING_CARD_PREFIX,
) {
    MessageQueueList(
        queueItems = messages,
        emptyText = emptyText,
        key = { it.id },
    ) { item ->
        PendingMessageCard(
            item = item,
            onApprove = onApprove,
            onReject = onReject,
            onEdit = onEdit,
            onOpenContact = onOpenContact,
            onOpenAutomationSetup = onOpenAutomationSetup,
            isApproving = approvingMessageId == item.id,
            selected = item.id in selectedMessageIds,
            onToggleSelection = onToggleSelection,
            showApproveAction = showApproveAction,
            modifier = Modifier.testTag(cardTagPrefix + item.id),
        )
    }
}

@Composable
internal fun SentMessagesList(messages: List<SentMessageItem>) {
    MessageQueueList(
        queueItems = messages,
        emptyText = stringResource(R.string.messages_empty_sent),
        key = { it.id },
    ) { item ->
        SentMessageCard(
            item = item,
            modifier = Modifier.testTag(MessagesTestTags.SENT_CARD_PREFIX + item.id),
        )
    }
}

@Composable
internal fun FailedMessagesList(
    messages: List<PendingMessageItem>,
    onRetry: (String) -> Unit,
    onOpenAutomationSetup: () -> Unit,
    retryingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    MessageQueueList(
        queueItems = messages,
        emptyText = stringResource(R.string.messages_empty_failed),
        key = { it.id },
        trailingContent = {
            item(key = "failed_recovery_assistant") {
                FailedRecoveryAssistant(
                    messages = messages,
                    onOpenAutomationSetup = onOpenAutomationSetup,
                    modifier = Modifier.testTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT),
                )
            }
        },
    ) { item ->
        FailedMessageCard(
            item = item,
            onRetry = onRetry,
            isRetrying = retryingMessageId == item.id,
            selected = item.id in selectedMessageIds,
            onToggleSelection = onToggleSelection,
            modifier = Modifier.testTag(MessagesTestTags.FAILED_CARD_PREFIX + item.id),
        )
    }
}

@Composable
internal fun ApprovedMessagesList(
    messages: List<PendingMessageItem>,
    emptyText: String,
    onRevoke: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    revokingMessageId: String?,
    selectedMessageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
) {
    MessageQueueList(
        queueItems = messages,
        emptyText = emptyText,
        key = { it.id },
    ) { item ->
        ApprovedMessageCard(
            item = item,
            onRevoke = onRevoke,
            onReject = onReject,
            onEdit = onEdit,
            isRevoking = revokingMessageId == item.id,
            selected = item.id in selectedMessageIds,
            onToggleSelection = onToggleSelection,
            modifier = Modifier.testTag(MessagesTestTags.APPROVED_CARD_PREFIX + item.id),
        )
    }
}
