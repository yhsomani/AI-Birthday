package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.resilience.StructuredLogger
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
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

data class PendingMessageItem(
    val entity: PendingMessageEntity,
    val contactName: String,
    val contactAvatarUrl: String? = null,
    val eventType: String = "BIRTHDAY",
)

data class SentMessageItem(
    val entity: SentMessageEntity,
    val contactName: String,
    val contactAvatarUrl: String? = null,
)

data class MessagesUiState(
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
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
    private val revokeApprovalUseCase: RevokeApprovalUseCase,
    private val schedulerService: SchedulerService,
    private val activityLogRepository: ActivityLogRepository,
) : ViewModel() {
    private companion object {
        const val TAG = "MessagesViewModel"
        const val LOAD_FAILED_MESSAGE = "Unable to load messages. Please try again."
        const val APPROVE_FAILED_MESSAGE = "Unable to approve the message. Please try again."
        const val REJECT_FAILED_MESSAGE = "Unable to reject the message. Please try again."
        const val RETRY_FAILED_MESSAGE = "Unable to retry the message. Please try again."
        const val REVOKE_FAILED_MESSAGE = "Unable to revoke approval. Please try again."
        const val BULK_APPROVE_FAILED_MESSAGE = "Unable to approve the selected messages. Please try again."
        const val BULK_REJECT_FAILED_MESSAGE = "Unable to reject the selected messages. Please try again."
        const val BULK_RETRY_FAILED_MESSAGE = "Unable to retry the selected messages. Please try again."
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
    
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    val endOfTodayMs = calendar.timeInMillis
    
                    val thirtyDaysCalendar = Calendar.getInstance()
                    thirtyDaysCalendar.add(Calendar.DAY_OF_YEAR, 30)
                    thirtyDaysCalendar.set(Calendar.HOUR_OF_DAY, 23)
                    thirtyDaysCalendar.set(Calendar.MINUTE, 59)
                    thirtyDaysCalendar.set(Calendar.SECOND, 59)
                    thirtyDaysCalendar.set(Calendar.MILLISECOND, 999)
                    val endOfThirtyDaysMs = thirtyDaysCalendar.timeInMillis

                    val todayItems = mutableListOf<PendingMessageItem>()
                    val pendingItems = mutableListOf<PendingMessageItem>()
                    val failedItems = mutableListOf<PendingMessageItem>()
                    val approvedItems = mutableListOf<PendingMessageItem>()
    
                    pending.forEach { msg ->
                        val contact = contactMap[msg.contactId]
                        val event = eventMap[msg.eventId]
                        val item = PendingMessageItem(
                            entity = msg,
                            contactName = contact?.name ?: msg.contactId,
                            contactAvatarUrl = contact?.profilePhotoUri,
                            eventType = event?.type ?: "BIRTHDAY"
                        )
    
                        when (MessageStatus.fromRaw(msg.status)) {
                            MessageStatus.FAILED -> failedItems.add(item)
                            MessageStatus.APPROVED -> approvedItems.add(item)
                            MessageStatus.SENT,
                            MessageStatus.REJECTED,
                            MessageStatus.EXPIRED -> {
                                // Do not show these in pending/today/failed lists
                            }
                            MessageStatus.PENDING,
                            MessageStatus.DISPATCHING,
                            MessageStatus.UNKNOWN -> {
                                if (msg.scheduledForMs <= endOfTodayMs) {
                                    todayItems.add(item)
                                } else if (msg.scheduledForMs <= endOfThirtyDaysMs) {
                                    pendingItems.add(item)
                                }
                            }
                        }
                    }
    
                    val sentItems = sent.map { s ->
                        val contact = contactMap[s.contactId]
                        SentMessageItem(
                            entity = s,
                            contactName = contact?.name ?: s.contactId ?: "Deleted Contact",
                            contactAvatarUrl = contact?.profilePhotoUri,
                        )
                    }
    
                    _uiState.value.withMessages(
                        todayMessages = todayItems,
                        pendingMessages = pendingItems,
                        approvedMessages = approvedItems,
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
                _uiState.value = _uiState.value.copy(isLoading = false, error = LOAD_FAILED_MESSAGE)
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
                    title = "Message approved",
                    detail = "A pending message was approved.",
                    messageId = messageId,
                )
                _uiState.value = _uiState.value.copy(approvingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message approval failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    approvingMessageId = null,
                    error = APPROVE_FAILED_MESSAGE,
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
                    title = "Message rejected",
                    detail = "A pending message was rejected.",
                    messageId = messageId,
                )
                _uiState.value = _uiState.value.copy(rejectingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message rejection failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    rejectingMessageId = null,
                    error = REJECT_FAILED_MESSAGE,
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
                            status = "APPROVED",
                            scheduledForMs = System.currentTimeMillis(),
                        )
                    )
                    schedulerService.scheduleExactSend(messageId)
                    recordMessageActivity(
                        title = "Message retried",
                        detail = "A failed message was queued for retry.",
                        messageId = messageId,
                    )
                }
                _uiState.value = _uiState.value.copy(retryingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message retry failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    retryingMessageId = null,
                    error = RETRY_FAILED_MESSAGE,
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
                    title = "Messages approved",
                    detail = "${ids.size} pending messages were approved.",
                    messageId = null,
                )
                _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Bulk message approval failed", e, extras = mapOf("count" to ids.size.toString()))
                _uiState.value = _uiState.value.copy(error = BULK_APPROVE_FAILED_MESSAGE)
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
                    title = "Messages rejected",
                    detail = "${ids.size} pending messages were rejected.",
                    messageId = null,
                )
                _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Bulk message rejection failed", e, extras = mapOf("count" to ids.size.toString()))
                _uiState.value = _uiState.value.copy(error = BULK_REJECT_FAILED_MESSAGE)
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
                            pending.copy(status = "APPROVED", scheduledForMs = System.currentTimeMillis())
                        )
                        schedulerService.scheduleExactSend(id)
                    }
                }
                recordMessageActivity(
                    title = "Messages retried",
                    detail = "${ids.size} failed messages were queued for retry.",
                    messageId = null,
                )
                _uiState.value = _uiState.value.copy(selectedMessageIds = emptySet())
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Bulk message retry failed", e, extras = mapOf("count" to ids.size.toString()))
                _uiState.value = _uiState.value.copy(error = BULK_RETRY_FAILED_MESSAGE)
            }
        }
    }


    fun revokeApproval(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(revokingMessageId = messageId)
            try {
                revokeApprovalUseCase(messageId)
                recordMessageActivity(
                    title = "Approval revoked",
                    detail = "A message approval was revoked.",
                    messageId = messageId,
                )
                _uiState.value = _uiState.value.copy(revokingMessageId = null)
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Message approval revoke failed", e, extras = mapOf("messageId" to messageId))
                _uiState.value = _uiState.value.copy(
                    revokingMessageId = null,
                    error = REVOKE_FAILED_MESSAGE,
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
        todayMessages: List<PendingMessageItem>,
        pendingMessages: List<PendingMessageItem>,
        approvedMessages: List<PendingMessageItem>,
        sentMessages: List<SentMessageItem>,
        failedMessages: List<PendingMessageItem>,
        isLoading: Boolean,
        isRefreshing: Boolean,
    ): MessagesUiState {
        return copy(
            allTodayMessages = todayMessages,
            allPendingMessages = pendingMessages,
            allApprovedMessages = approvedMessages,
            allSentMessages = sentMessages,
            allFailedMessages = failedMessages,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
        ).withFilteredMessages()
    }

    private fun MessagesUiState.withFilteredMessages(): MessagesUiState {
        val query = searchQuery.trim()
        return copy(
            todayMessages = allTodayMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort),
            pendingMessages = allPendingMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort),
            approvedMessages = allApprovedMessages.filterPending(query, selectedChannelFilter).sortPending(selectedSort),
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

    private suspend fun recordMessageActivity(
        title: String,
        detail: String,
        messageId: String?,
    ) {
        val entry = ActivityLogEntity(
            id = UUID.randomUUID().toString(),
            type = "MESSAGE",
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
}
