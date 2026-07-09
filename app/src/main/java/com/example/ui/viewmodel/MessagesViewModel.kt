package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.ActivityLogType
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import com.example.domain.usecase.RevokeApprovalUseCase
import com.example.domain.usecase.RetryFailedMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
    private val revokeApprovalUseCase: RevokeApprovalUseCase,
    private val retryFailedMessageUseCase: RetryFailedMessageUseCase,
    private val activityLogRepository: ActivityLogRepository,
    private val preferencesRepository: PreferencesRepository,
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
                    messageRepository.getPendingListItems(),
                    messageRepository.getSentListItems(),
                    contactRepository.getMessageContexts(),
                    eventRepository.getEventListItems(),
                    preferencesRepository.observeChanges().onStart { emit(Unit) },
                ) { pending, sent, contacts, events, _ ->
                    _uiState.value.withMessageLists(
                        pendingMessages = pending,
                        sentMessages = sent,
                        contactContexts = contacts,
                        eventItems = events,
                        deletedContactName = string(R.string.messages_deleted_contact),
                        readinessConfig = MessagesReadinessConfig(
                            channelBlackoutJson = preferencesRepository.getChannelBlackout(),
                            senderEmail = preferencesRepository.getSenderEmail(),
                            senderEmailPassword = preferencesRepository.getSenderEmailPassword(),
                            quietHoursStart = preferencesRepository.getQuietHoursStart(),
                            quietHoursEnd = preferencesRepository.getQuietHoursEnd(),
                            blackoutDatesJson = preferencesRepository.getBlackoutDates(),
                        ),
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
                when (retryFailedMessageUseCase(messageId)) {
                    is RetryFailedMessageUseCase.RetryOutcome.RetryQueued -> {
                        recordMessageActivity(
                            title = string(R.string.message_activity_retried_title),
                            detail = string(R.string.message_activity_retried_detail),
                            messageId = messageId,
                        )
                        _uiState.value = _uiState.value.copy(retryingMessageId = null)
                    }
                    RetryFailedMessageUseCase.RetryOutcome.PendingNotFound,
                    is RetryFailedMessageUseCase.RetryOutcome.NotFailed -> {
                        _uiState.value = _uiState.value.copy(
                            retryingMessageId = null,
                            error = string(R.string.messages_error_retry),
                        )
                    }
                }
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
                val retriedCount = ids.count { id ->
                    retryFailedMessageUseCase(id) is RetryFailedMessageUseCase.RetryOutcome.RetryQueued
                }
                if (retriedCount == 0) {
                    _uiState.value = _uiState.value.copy(error = string(R.string.messages_error_bulk_retry))
                    return@launch
                }
                recordMessageActivity(
                    title = string(R.string.message_activity_bulk_retried_title),
                    detail = string(R.string.message_activity_bulk_retried_detail, retriedCount),
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

    private suspend fun recordMessageActivity(
        title: String,
        detail: String,
        messageId: String?,
    ) {
        val entry = ActivityLogRecord(
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
