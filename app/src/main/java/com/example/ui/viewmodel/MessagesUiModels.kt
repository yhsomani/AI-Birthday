package com.example.ui.viewmodel

import com.example.domain.automation.MessageOperationalReadiness
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.SentMessageListItem
import com.example.domain.model.occasion.OccasionType
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.readiness.RelationshipReadinessAction
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.readiness.RelationshipReadinessState

typealias MessageReadiness = MessageOperationalReadiness

enum class MessageChannelFilter {
    ALL,
    SMS,
    WHATSAPP,
    EMAIL,
}

enum class MessageSort {
    SCHEDULED_ASC,
    SCHEDULED_DESC,
    CONTACT_ASC,
}

enum class MessageActionRoute {
    NONE,
    WISH,
    CONTACT,
    AUTOMATION_SETUP,
}

data class PendingMessageItem(
    val message: PendingMessageListItem,
    val contactName: String,
    val contactAvatarUrl: String? = null,
    val eventType: String = OccasionType.BIRTHDAY.raw,
    val readiness: MessageReadiness = MessageReadiness.READY_FOR_REVIEW,
    val actionReadiness: RelationshipActionReadiness = RelationshipActionReadinessPolicy.fromMessageOperationalReadiness(
        readiness = readiness,
        relatedMessageId = message.id.value,
        relatedContactId = message.contactId.value,
        relatedEventId = message.occasionId.value,
    ),
) {
    val id: String
        get() = message.id.value

    val contactId: String
        get() = message.contactId.value

    val scheduledForMs: Long
        get() = message.scheduledForMs

    val channel: MessageChannel
        get() = message.channel

    val approvalMode: ApprovalMode
        get() = message.approvalMode

    val selectedVariantText: String
        get() = message.selectedVariantText

    val standardVariant: String
        get() = message.standardVariant

    val reviewPreviewText: String
        get() = if (message.editedByUser) {
            message.userEditedText ?: message.selectedVariantText
        } else {
            message.selectedVariantText
        }

    val messageText: String
        get() = message.selectedVariantText.ifBlank { message.standardVariant }

    val qualityScore: Int
        get() = message.qualityScore

    val isUsingFallback: Boolean
        get() = message.isUsingFallback

    val blocksTaskFlow: Boolean
        get() = actionReadiness.state == RelationshipReadinessState.ACTION_REQUIRED

    val requiresContactOrChannelFix: Boolean
        get() = actionReadiness.blockers.any { blocker ->
            blocker.action in setOf(
                RelationshipReadinessAction.OPEN_CONTACT,
                RelationshipReadinessAction.CONFIGURE_CHANNEL,
                RelationshipReadinessAction.CONFIGURE_EMAIL,
            )
        }

    val primaryActionRoute: MessageActionRoute
        get() = actionReadiness.toMessageActionRoute()
}

data class SentMessageItem(
    val message: SentMessageListItem,
    val contactName: String,
    val contactAvatarUrl: String? = null,
) {
    val id: String
        get() = message.id.value

    val channel: MessageChannel
        get() = message.channel

    val sentAtMs: Long
        get() = message.sentAtMs

    val messageText: String
        get() = message.messageText
}

data class MessagesUiState(
    val allNeedsReviewMessages: List<PendingMessageItem> = emptyList(),
    val needsReviewMessages: List<PendingMessageItem> = emptyList(),
    val allScheduledMessages: List<PendingMessageItem> = emptyList(),
    val scheduledMessages: List<PendingMessageItem> = emptyList(),
    val allBlockedMessages: List<PendingMessageItem> = emptyList(),
    val blockedMessages: List<PendingMessageItem> = emptyList(),
    val allTodayMessages: List<PendingMessageItem> = emptyList(),
    val todayMessages: List<PendingMessageItem> = emptyList(),
    val allPendingMessages: List<PendingMessageItem> = emptyList(),
    val pendingMessages: List<PendingMessageItem> = emptyList(),
    val allApprovedMessages: List<PendingMessageItem> = emptyList(),
    val approvedMessages: List<PendingMessageItem> = emptyList(),
    val allSentMessages: List<SentMessageItem> = emptyList(),
    val sentMessages: List<SentMessageItem> = emptyList(),
    val allFailedMessages: List<PendingMessageItem> = emptyList(),
    val failedMessages: List<PendingMessageItem> = emptyList(),
    val searchQuery: String = "",
    val selectedChannelFilter: MessageChannelFilter = MessageChannelFilter.ALL,
    val selectedSort: MessageSort = MessageSort.SCHEDULED_ASC,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val approvingMessageId: String? = null,
    val rejectingMessageId: String? = null,
    val revokingMessageId: String? = null,
    val retryingMessageId: String? = null,
    val selectedMessageIds: Set<String> = emptySet(),
    val error: String? = null,
)

private fun RelationshipActionReadiness.toMessageActionRoute(): MessageActionRoute {
    return when (primaryAction) {
        RelationshipReadinessAction.REVIEW_MESSAGE,
        RelationshipReadinessAction.EDIT_DRAFT -> MessageActionRoute.WISH
        RelationshipReadinessAction.OPEN_CONTACT -> MessageActionRoute.CONTACT
        RelationshipReadinessAction.CONFIGURE_CHANNEL -> when (primaryReason) {
            RelationshipReadinessReason.CONTACT_MISSING,
            RelationshipReadinessReason.MISSING_PHONE,
            RelationshipReadinessReason.MISSING_EMAIL -> MessageActionRoute.CONTACT
            else -> MessageActionRoute.AUTOMATION_SETUP
        }
        RelationshipReadinessAction.CONFIGURE_EMAIL,
        RelationshipReadinessAction.OPEN_SETUP,
        RelationshipReadinessAction.CHECK_SETUP,
        RelationshipReadinessAction.CONNECT_AI,
        RelationshipReadinessAction.ENABLE_AI_GENERATION,
        RelationshipReadinessAction.FIX_CONTACT_SYNC,
        RelationshipReadinessAction.SYNC_CONTACTS -> MessageActionRoute.AUTOMATION_SETUP
        RelationshipReadinessAction.NONE,
        RelationshipReadinessAction.WAIT,
        RelationshipReadinessAction.REVIEW_MESSAGES,
        RelationshipReadinessAction.CREATE_BACKUP,
        RelationshipReadinessAction.REFRESH_BACKUP -> MessageActionRoute.NONE
    }
}
