package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.PendingMessageItem
import com.example.ui.viewmodel.SentMessageItem

@Composable
internal fun PendingMessagesList(
    messages: List<PendingMessageItem>,
    emptyText: String,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
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

@Composable
private fun PendingMessageCard(
    item: PendingMessageItem,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    isApproving: Boolean,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    showApproveAction: Boolean,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            MessageSelectableCardHeader(
                selected = selected,
                onToggleSelection = { onToggleSelection(item.id) },
                selectionTestTag = MessagesTestTags.SELECT_PREFIX + item.id,
                contactName = item.contactName,
                contactAvatarUrl = item.contactAvatarUrl,
                secondaryContent = {
                    MessagePendingMetadataRow(
                        eventTypeRaw = item.eventType,
                        channelRaw = item.channel.raw,
                        channelTestTag = MessagesTestTags.CHANNEL_PREFIX + item.id,
                    )
                },
                trailingContent = {
                    MessageDateLabel(
                        epochMs = item.scheduledForMs,
                        modifier = Modifier.align(Alignment.Top),
                    )
                },
            )

            MessagePendingCardBody(
                reviewPreviewText = item.reviewPreviewText,
                readiness = item.readiness,
                readinessTestTag = MessagesTestTags.READINESS_PREFIX + item.id,
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))

            MessagePendingActionRow(
                approvalMode = item.approvalMode,
                scheduledForMs = item.scheduledForMs,
                onReject = { onReject(item.id) },
                rejectTestTag = MessagesTestTags.PENDING_REJECT_PREFIX + item.id,
                onEdit = { onEdit(item.contactId, item.id) },
                editTestTag = MessagesTestTags.PENDING_EDIT_PREFIX + item.id,
                isApproving = isApproving,
                showApproveAction = showApproveAction,
                onApprove = { onApprove(item.id) },
                approveTestTag = MessagesTestTags.PENDING_APPROVE_PREFIX + item.id,
            )
        }
    }
}

@Composable
private fun SentMessageCard(
    item: SentMessageItem,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MessageContactAvatar(
                contactName = item.contactName,
                contactAvatarUrl = item.contactAvatarUrl,
            )
            Spacer(modifier = Modifier.width(RelateSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                MessageSentCardHeader(
                    contactName = item.contactName,
                    sentAtMs = item.sentAtMs,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                MessageSentBody(messageText = item.messageText)
                Spacer(modifier = Modifier.height(RelateSpacing.xxs))
                MessageSentChannelLabel(
                    channelRaw = item.channel.raw,
                    modifier = Modifier.testTag(MessagesTestTags.CHANNEL_PREFIX + item.id),
                )
            }
        }
    }
}

@Composable
private fun FailedMessageCard(
    item: PendingMessageItem,
    onRetry: (String) -> Unit,
    isRetrying: Boolean,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            MessageSelectableCardHeader(
                selected = selected,
                onToggleSelection = { onToggleSelection(item.id) },
                selectionTestTag = MessagesTestTags.SELECT_PREFIX + item.id,
                contactName = item.contactName,
                contactAvatarUrl = item.contactAvatarUrl,
                secondaryContent = {
                    MessageScheduledSubtext(scheduledForMs = item.scheduledForMs)
                },
                trailingContent = {
                    MessageFailedStatusIcon()
                },
            )
            MessageReviewCardBody(
                messageText = item.messageText,
                readiness = item.readiness,
                readinessTestTag = MessagesTestTags.READINESS_PREFIX + item.id,
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))
            MessageFailedActionRow(
                isRetrying = isRetrying,
                onRetry = { onRetry(item.id) },
                retryTestTag = MessagesTestTags.FAILED_RETRY_PREFIX + item.id,
            )
        }
    }
}

@Composable
private fun ApprovedMessageCard(
    item: PendingMessageItem,
    onRevoke: (String) -> Unit,
    onReject: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    isRevoking: Boolean,
    selected: Boolean,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            MessageSelectableCardHeader(
                selected = selected,
                onToggleSelection = { onToggleSelection(item.id) },
                selectionTestTag = MessagesTestTags.SELECT_PREFIX + item.id,
                contactName = item.contactName,
                contactAvatarUrl = item.contactAvatarUrl,
                secondaryContent = {
                    MessageScheduledSubtext(scheduledForMs = item.scheduledForMs)
                },
                trailingContent = {
                    MessageApprovedStatusIcon()
                },
            )
            MessageReviewCardBody(
                messageText = item.messageText,
                readiness = item.readiness,
                readinessTestTag = MessagesTestTags.READINESS_PREFIX + item.id,
            )

            Spacer(modifier = Modifier.height(RelateSpacing.md))
            MessageApprovedActionRow(
                onReject = { onReject(item.id) },
                rejectTestTag = MessagesTestTags.APPROVED_REJECT_PREFIX + item.id,
                onEdit = { onEdit(item.contactId, item.id) },
                editTestTag = MessagesTestTags.APPROVED_EDIT_PREFIX + item.id,
                isRevoking = isRevoking,
                onRevoke = { onRevoke(item.id) },
                revokeTestTag = MessagesTestTags.APPROVED_REVOKE_PREFIX + item.id,
            )
        }
    }
}
