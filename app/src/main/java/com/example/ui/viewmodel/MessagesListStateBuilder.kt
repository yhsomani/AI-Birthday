package com.example.ui.viewmodel

import com.example.domain.automation.MessageOperationalReadinessPolicy
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.SentMessageListItem
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.domain.readiness.RelationshipActionReadinessPolicy

internal data class MessagesReadinessConfig(
    val channelBlackoutJson: String,
    val senderEmail: String,
    val senderEmailPassword: String,
    val quietHoursStart: Int,
    val quietHoursEnd: Int,
    val blackoutDatesJson: String,
)

internal fun MessagesUiState.withMessageLists(
    pendingMessages: List<PendingMessageListItem>,
    sentMessages: List<SentMessageListItem>,
    contactContexts: List<ContactMessageContext>,
    eventItems: List<EventListItem>,
    deletedContactName: String,
    readinessConfig: MessagesReadinessConfig,
    isLoading: Boolean,
    isRefreshing: Boolean,
): MessagesUiState {
    val contactMap = contactContexts.associateBy { it.id.value }
    val eventMap = eventItems.associateBy { it.id.value }

    val needsReviewItems = mutableListOf<PendingMessageItem>()
    val scheduledItems = mutableListOf<PendingMessageItem>()
    val blockedItems = mutableListOf<PendingMessageItem>()
    val failedItems = mutableListOf<PendingMessageItem>()

    pendingMessages.forEach { msg ->
        val contact = contactMap[msg.contactId.value]
        val event = eventMap[msg.occasionId.value]
        val readiness = msg.readinessFor(
            contact = contact,
            config = readinessConfig,
        )
        val item = PendingMessageItem(
            message = msg,
            contactName = contact?.displayName ?: msg.contactId.value,
            contactAvatarUrl = contact?.avatarUrl,
            eventType = event?.type?.raw ?: OccasionType.BIRTHDAY.raw,
            readiness = readiness,
            actionReadiness = RelationshipActionReadinessPolicy.fromMessageOperationalReadiness(
                readiness = readiness,
                relatedMessageId = msg.id.value,
                relatedContactId = msg.contactId.value,
                relatedEventId = msg.occasionId.value,
            ),
        )

        when (msg.status) {
            MessageStatus.FAILED -> failedItems.add(item)
            MessageStatus.APPROVED,
            MessageStatus.DISPATCHING -> {
                if (item.blocksTaskFlow) {
                    blockedItems.add(item)
                } else {
                    scheduledItems.add(item)
                }
            }
            MessageStatus.SENT,
            MessageStatus.REJECTED,
            MessageStatus.EXPIRED -> {
                // Do not show these in task-state pending lists.
            }
            MessageStatus.PENDING,
            MessageStatus.UNKNOWN -> {
                if (item.blocksTaskFlow) {
                    blockedItems.add(item)
                } else {
                    needsReviewItems.add(item)
                }
            }
        }
    }

    val sentItems = sentMessages.map { sent ->
        val contact = sent.contactId?.value?.let { contactMap[it] }
        SentMessageItem(
            message = sent,
            contactName = contact?.displayName
                ?: sent.contactId?.value
                ?: deletedContactName,
            contactAvatarUrl = contact?.avatarUrl,
        )
    }

    return copy(
        allNeedsReviewMessages = needsReviewItems,
        allScheduledMessages = scheduledItems,
        allBlockedMessages = blockedItems,
        allTodayMessages = needsReviewItems,
        allPendingMessages = needsReviewItems,
        allApprovedMessages = scheduledItems,
        allSentMessages = sentItems,
        allFailedMessages = failedItems,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
    ).withFilteredMessages()
}

internal fun MessagesUiState.withFilteredMessages(): MessagesUiState {
    val query = searchQuery.trim()
    val filteredNeedsReview = allNeedsReviewMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort)
    val filteredScheduled = allScheduledMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort)
    val filteredBlocked = allBlockedMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort)
    return copy(
        needsReviewMessages = filteredNeedsReview,
        scheduledMessages = filteredScheduled,
        blockedMessages = filteredBlocked,
        todayMessages = filteredNeedsReview,
        pendingMessages = filteredNeedsReview,
        approvedMessages = filteredScheduled,
        sentMessages = allSentMessages.filterSent(query, selectedChannelFilter).sortSent(selectedSort),
        failedMessages = allFailedMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort),
    )
}

private fun List<PendingMessageItem>.filterPending(
    query: String,
    channelFilter: MessageChannelFilter,
): List<PendingMessageItem> {
    return filter { item ->
        channelFilter.matches(item.channel) &&
            (query.isBlank() ||
                item.contactName.contains(query, ignoreCase = true) ||
                item.eventType.contains(query, ignoreCase = true) ||
                item.channel.raw.contains(query, ignoreCase = true) ||
                item.selectedVariantText.contains(query, ignoreCase = true) ||
                item.standardVariant.contains(query, ignoreCase = true))
    }
}

private fun List<SentMessageItem>.filterSent(
    query: String,
    channelFilter: MessageChannelFilter,
): List<SentMessageItem> {
    return filter { item ->
        channelFilter.matches(item.channel) &&
            (query.isBlank() ||
                item.contactName.contains(query, ignoreCase = true) ||
                item.message.occasionType.contains(query, ignoreCase = true) ||
                item.channel.raw.contains(query, ignoreCase = true) ||
                item.message.deliveryStatus.raw.contains(query, ignoreCase = true) ||
                item.messageText.contains(query, ignoreCase = true))
    }
}

private fun List<PendingMessageItem>.sortPending(sort: MessageSort): List<PendingMessageItem> {
    return when (sort) {
        MessageSort.SCHEDULED_ASC -> sortedBy { it.scheduledForMs }
        MessageSort.SCHEDULED_DESC -> sortedByDescending { it.scheduledForMs }
        MessageSort.CONTACT_ASC -> sortedWith(compareBy<PendingMessageItem> { it.contactName.lowercase() }
            .thenBy { it.scheduledForMs })
    }
}

private fun List<SentMessageItem>.sortSent(sort: MessageSort): List<SentMessageItem> {
    return when (sort) {
        MessageSort.SCHEDULED_ASC -> sortedBy { it.sentAtMs }
        MessageSort.SCHEDULED_DESC -> sortedByDescending { it.sentAtMs }
        MessageSort.CONTACT_ASC -> sortedWith(compareBy<SentMessageItem> { it.contactName.lowercase() }
            .thenByDescending { it.sentAtMs })
    }
}

private fun MessageChannelFilter.matches(channel: MessageChannel): Boolean {
    return when (this) {
        MessageChannelFilter.ALL -> true
        MessageChannelFilter.SMS -> channel == MessageChannel.SMS
        MessageChannelFilter.WHATSAPP -> channel == MessageChannel.WHATSAPP
        MessageChannelFilter.EMAIL -> channel == MessageChannel.EMAIL
    }
}

private fun PendingMessageListItem.readinessFor(
    contact: ContactMessageContext?,
    config: MessagesReadinessConfig,
): MessageReadiness = MessageOperationalReadinessPolicy.evaluate(
    message = this,
    contact = contact,
    channelBlackoutJson = config.channelBlackoutJson,
    senderEmail = config.senderEmail,
    senderEmailPassword = config.senderEmailPassword,
    quietHoursStart = config.quietHoursStart,
    quietHoursEnd = config.quietHoursEnd,
    blackoutDatesJson = config.blackoutDatesJson,
)
