package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.model.ActivityLogType
import com.example.domain.model.EventType
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import com.example.domain.usecase.RevokeApprovalUseCase
import com.example.domain.service.SchedulerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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

enum class MessageReadiness {
    READY_FOR_REVIEW,
    APPROVED_SCHEDULED,
    SENDING_NOW,
    CONTACT_MISSING,
    CHANNEL_DISABLED,
    MISSING_PHONE,
    MISSING_EMAIL,
    EMAIL_SETUP_MISSING,
    FAILED_CHECK_SETUP,
}

data class PendingMessageItem(
    val entity: PendingMessageEntity,
    val contactName: String,
    val contactAvatarUrl: String? = null,
    val eventType: String = EventType.BIRTHDAY.raw,
    val readiness: MessageReadiness = MessageReadiness.READY_FOR_REVIEW,
)

data class SentMessageItem(
    val entity: SentMessageEntity,
    val contactName: String,
    val contactAvatarUrl: String? = null,
)

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

@HiltViewModel
class MessagesViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
    private val revokeApprovalUseCase: RevokeApprovalUseCase,
    private val schedulerService: SchedulerService,
    private val activityLogRepository: ActivityLogRepository,
    private val securePrefs: SecurePrefs,
) : ViewModel() {
    private companion object {
        const val TAG = "MessagesViewModel"
    }

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private var collectJob: kotlinx.coroutines.Job? = null

    init {
        startCollecting()
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            try {
                combine(
                    messageRepository.getAllPending(),
                    messageRepository.getAllSent(),
                    contactRepository.getAll(),
                    eventRepository.getAll(),
                ) { pending, sent, contacts, events ->
                    val contactMap = contacts.associateBy { it.id }
                    val eventMap = events.associateBy { it.id }

                    val needsReviewItems = mutableListOf<PendingMessageItem>()
                    val scheduledItems = mutableListOf<PendingMessageItem>()
                    val blockedItems = mutableListOf<PendingMessageItem>()
                    val failedItems = mutableListOf<PendingMessageItem>()

                    pending.forEach { msg ->
                        val contact = contactMap[msg.contactId]
                        val event = eventMap[msg.eventId]
                        val status = MessageStatus.fromRaw(msg.status)
                        val item = PendingMessageItem(
                            entity = msg,
                            contactName = contact?.name ?: msg.contactId,
                            contactAvatarUrl = contact?.profilePhotoUri,
                            eventType = event?.type ?: EventType.BIRTHDAY.raw,
                            readiness = msg.readinessFor(
                                contact = contact,
                                status = status,
                            ),
                        )

                        when (status) {
                            MessageStatus.FAILED -> failedItems.add(item)
                            MessageStatus.APPROVED,
                            MessageStatus.DISPATCHING -> {
                                if (item.readiness.blocksTaskFlow()) {
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
                                if (item.readiness.blocksTaskFlow()) {
                                    blockedItems.add(item)
                                } else {
                                    needsReviewItems.add(item)
                                }
                            }
                        }
                    }

                    val sentItems = sent.map { s ->
                        val contact = contactMap[s.contactId]
                        SentMessageItem(
                            entity = s,
                            contactName = contact?.name ?: s.contactId ?: string(R.string.messages_deleted_contact),
                            contactAvatarUrl = contact?.profilePhotoUri,
                        )
                    }

                    _uiState.value.withMessages(
                        needsReviewMessages = needsReviewItems,
                        scheduledMessages = scheduledItems,
                        blockedMessages = blockedItems,
                        sentMessages = sentItems,
                        failedMessages = failedItems,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message collection failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = string(R.string.messages_error_load))
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        startCollecting()
    }

    fun approveMessage(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(approvingMessageId = messageId)
            try {
                approvePendingMessageUseCase(messageId)
                recordMessageActivity(
                    title = string(R.string.message_activity_approved_title),
                    detail = string(R.string.message_activity_approved_detail),
                    messageId = messageId,
                )
                _uiState.value = _uiState.value.copy(approvingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message approval failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    approvingMessageId = null,
                    error = string(R.string.messages_error_approve),
                )
            }
        }
    }

    fun rejectMessage(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(rejectingMessageId = messageId)
            try {
                rejectPendingMessageUseCase(messageId)
                recordMessageActivity(
                    title = string(R.string.message_activity_rejected_title),
                    detail = string(R.string.message_activity_rejected_detail),
                    messageId = messageId,
                )
                _uiState.value = _uiState.value.copy(rejectingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message rejection failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    rejectingMessageId = null,
                    error = string(R.string.messages_error_reject),
                )
            }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(retryingMessageId = messageId)
            try {
                val pending = messageRepository.getPendingById(messageId)
                if (pending != null) {
                    messageRepository.insertPending(
                        pending.copy(
                            status = MessageStatus.APPROVED.raw,
                            scheduledForMs = System.currentTimeMillis(),
                        )
                    )
                    schedulerService.scheduleExactSend(messageId)
                    recordMessageActivity(
                        title = string(R.string.message_activity_retried_title),
                        detail = string(R.string.message_activity_retried_detail),
                        messageId = messageId,
                    )
                }
                _uiState.value = _uiState.value.copy(retryingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message retry failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    retryingMessageId = null,
                    error = string(R.string.messages_error_retry),
                )
            }
        }
    }

    fun toggleSelection(messageId: String) {
        val current = _uiState.value.selectedMessageIds
        _uiState.value = _uiState.value.copy(
            selectedMessageIds = if (messageId in current) current - messageId else current + messageId
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
    }

    fun bulkApproveSelected() {
        val ids = _uiState.value.selectedMessageIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { approvePendingMessageUseCase(it) }
                recordMessageActivity(
                    title = string(R.string.message_activity_bulk_approved_title),
                    detail = string(R.string.message_activity_bulk_approved_detail, ids.size),
                    messageId = null,
                )
                _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Bulk message approval failed", e, extras = mapOf("count" to ids.size.toString()))
                _uiState.value = _uiState.value.copy(error = string(R.string.messages_error_bulk_approve))
            }
        }
    }

    fun bulkRejectSelected() {
        val ids = _uiState.value.selectedMessageIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { rejectPendingMessageUseCase(it) }
                recordMessageActivity(
                    title = string(R.string.message_activity_bulk_rejected_title),
                    detail = string(R.string.message_activity_bulk_rejected_detail, ids.size),
                    messageId = null,
                )
                _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Bulk message rejection failed", e, extras = mapOf("count" to ids.size.toString()))
                _uiState.value = _uiState.value.copy(error = string(R.string.messages_error_bulk_reject))
            }
        }
    }

    fun bulkRetrySelected() {
        val ids = _uiState.value.selectedMessageIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { id ->
                    val pending = messageRepository.getPendingById(id)
                    if (pending != null) {
                        messageRepository.insertPending(
                            pending.copy(
                                status = MessageStatus.APPROVED.raw,
                                scheduledForMs = System.currentTimeMillis(),
                            )
                        )
                        schedulerService.scheduleExactSend(id)
                    }
                }
                recordMessageActivity(
                    title = string(R.string.message_activity_bulk_retried_title),
                    detail = string(R.string.message_activity_bulk_retried_detail, ids.size),
                    messageId = null,
                )
                _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Bulk message retry failed", e, extras = mapOf("count" to ids.size.toString()))
                _uiState.value = _uiState.value.copy(error = string(R.string.messages_error_bulk_retry))
            }
        }
    }


    fun revokeApproval(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(revokingMessageId = messageId)
            try {
                revokeApprovalUseCase(messageId)
                recordMessageActivity(
                    title = string(R.string.message_activity_approval_revoked_title),
                    detail = string(R.string.message_activity_approval_revoked_detail),
                    messageId = messageId,
                )
                _uiState.value = _uiState.value.copy(revokingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message approval revoke failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    revokingMessageId = null,
                    error = string(R.string.messages_error_revoke),
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query).withFilteredMessages()
    }

    fun selectChannelFilter(filter: MessageChannelFilter) {
        _uiState.value = _uiState.value.copy(selectedChannelFilter = filter).withFilteredMessages()
    }

    fun selectSort(sort: MessageSort) {
        _uiState.value = _uiState.value.copy(selectedSort = sort).withFilteredMessages()
    }

    private fun MessagesUiState.withMessages(
        needsReviewMessages: List<PendingMessageItem>,
        scheduledMessages: List<PendingMessageItem>,
        blockedMessages: List<PendingMessageItem>,
        sentMessages: List<SentMessageItem>,
        failedMessages: List<PendingMessageItem>,
        isLoading: Boolean,
        isRefreshing: Boolean,
    ): MessagesUiState {
        return copy(
            allNeedsReviewMessages = needsReviewMessages,
            allScheduledMessages = scheduledMessages,
            allBlockedMessages = blockedMessages,
            allTodayMessages = needsReviewMessages,
            allPendingMessages = needsReviewMessages,
            allApprovedMessages = scheduledMessages,
            allSentMessages = sentMessages,
            allFailedMessages = failedMessages,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
        ).withFilteredMessages()
    }

    private fun MessagesUiState.withFilteredMessages(): MessagesUiState {
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
            channelFilter.matches(item.entity.channel) &&
                (query.isBlank() ||
                    item.contactName.contains(query, ignoreCase = true) ||
                    item.eventType.contains(query, ignoreCase = true) ||
                    item.entity.channel.contains(query, ignoreCase = true) ||
                    item.entity.selectedVariantText.contains(query, ignoreCase = true) ||
                    item.entity.standardVariant.contains(query, ignoreCase = true))
        }
    }

    private fun List<SentMessageItem>.filterSent(
        query: String,
        channelFilter: MessageChannelFilter,
    ): List<SentMessageItem> {
        return filter { item ->
            channelFilter.matches(item.entity.channel) &&
                (query.isBlank() ||
                    item.contactName.contains(query, ignoreCase = true) ||
                    item.entity.eventType.contains(query, ignoreCase = true) ||
                    item.entity.channel.contains(query, ignoreCase = true) ||
                    item.entity.deliveryStatus.contains(query, ignoreCase = true) ||
                    item.entity.messageText.contains(query, ignoreCase = true))
        }
    }

    private fun List<PendingMessageItem>.sortPending(sort: MessageSort): List<PendingMessageItem> {
        return when (sort) {
            MessageSort.SCHEDULED_ASC -> sortedBy { it.entity.scheduledForMs }
            MessageSort.SCHEDULED_DESC -> sortedByDescending { it.entity.scheduledForMs }
            MessageSort.CONTACT_ASC -> sortedWith(compareBy<PendingMessageItem> { it.contactName.lowercase() }
                .thenBy { it.entity.scheduledForMs })
        }
    }

    private fun List<SentMessageItem>.sortSent(sort: MessageSort): List<SentMessageItem> {
        return when (sort) {
            MessageSort.SCHEDULED_ASC -> sortedBy { it.entity.sentAtMs }
            MessageSort.SCHEDULED_DESC -> sortedByDescending { it.entity.sentAtMs }
            MessageSort.CONTACT_ASC -> sortedWith(compareBy<SentMessageItem> { it.contactName.lowercase() }
                .thenByDescending { it.entity.sentAtMs })
        }
    }

    private fun MessageChannelFilter.matches(channel: String): Boolean {
        return when (this) {
            MessageChannelFilter.ALL -> true
            MessageChannelFilter.SMS -> MessageChannel.fromRaw(channel) == MessageChannel.SMS
            MessageChannelFilter.WHATSAPP -> MessageChannel.fromRaw(channel) == MessageChannel.WHATSAPP
            MessageChannelFilter.EMAIL -> MessageChannel.fromRaw(channel) == MessageChannel.EMAIL
        }
    }

    private fun PendingMessageEntity.readinessFor(
        contact: ContactEntity?,
        status: MessageStatus,
    ): MessageReadiness {
        if (contact == null) return MessageReadiness.CONTACT_MISSING
        val messageChannel = MessageChannel.fromRaw(channel)
        if (AutomationSchedulePolicy.isChannelBlocked(messageChannel, securePrefs.getChannelBlackout())) {
            return MessageReadiness.CHANNEL_DISABLED
        }

        when (messageChannel) {
            MessageChannel.SMS,
            MessageChannel.WHATSAPP -> {
                if (contact.primaryPhone.isNullOrBlank()) return MessageReadiness.MISSING_PHONE
            }
            MessageChannel.EMAIL -> {
                if (contact.primaryEmail.isNullOrBlank()) return MessageReadiness.MISSING_EMAIL
                if (securePrefs.getSenderEmail().isBlank() || securePrefs.getSenderEmailPassword().isBlank()) {
                    return MessageReadiness.EMAIL_SETUP_MISSING
                }
            }
            MessageChannel.UNKNOWN -> return MessageReadiness.CHANNEL_DISABLED
        }

        return when (status) {
            MessageStatus.APPROVED -> MessageReadiness.APPROVED_SCHEDULED
            MessageStatus.DISPATCHING -> MessageReadiness.SENDING_NOW
            MessageStatus.FAILED -> MessageReadiness.FAILED_CHECK_SETUP
            else -> MessageReadiness.READY_FOR_REVIEW
        }
    }

    private fun MessageReadiness.blocksTaskFlow(): Boolean = when (this) {
        MessageReadiness.CONTACT_MISSING,
        MessageReadiness.CHANNEL_DISABLED,
        MessageReadiness.MISSING_PHONE,
        MessageReadiness.MISSING_EMAIL,
        MessageReadiness.EMAIL_SETUP_MISSING -> true
        MessageReadiness.READY_FOR_REVIEW,
        MessageReadiness.APPROVED_SCHEDULED,
        MessageReadiness.SENDING_NOW,
        MessageReadiness.FAILED_CHECK_SETUP -> false
    }

    private suspend fun recordMessageActivity(
        title: String,
        detail: String,
        messageId: String?,
    ) {
        val entry = ActivityLogEntity(
            id = UUID.randomUUID().toString(),
            type = ActivityLogType.MESSAGE.raw,
            title = title,
            detail = detail,
            messageId = messageId,
        )
        try {
            activityLogRepository.record(entry)
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Activity log write failed", e, extras = mapOf("type" to entry.type))
        }
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }
}
