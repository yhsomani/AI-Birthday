package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

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
    val todayMessages: List<PendingMessageItem> = emptyList(),
    val pendingMessages: List<PendingMessageItem> = emptyList(),
    val sentMessages: List<SentMessageItem> = emptyList(),
    val failedMessages: List<PendingMessageItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val approvingMessageId: String? = null,
    val rejectingMessageId: String? = null,
    val retryingMessageId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
) : ViewModel() {

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
    
                    pending.forEach { msg ->
                        val contact = contactMap[msg.contactId]
                        val event = eventMap[msg.eventId]
                        val item = PendingMessageItem(
                            entity = msg,
                            contactName = contact?.name ?: msg.contactId,
                            contactAvatarUrl = contact?.profilePhotoUri,
                            eventType = event?.type ?: "BIRTHDAY"
                        )
    
                        when (msg.status) {
                            "FAILED" -> failedItems.add(item)
                            "SENT", "REJECTED", "EXPIRED" -> {
                                // Do not show these in pending/today/failed lists
                            }
                            else -> {
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
    
                    _uiState.value.copy(
                        todayMessages = todayItems,
                        pendingMessages = pendingItems,
                        sentMessages = sentItems,
                        failedMessages = failedItems,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                android.util.Log.e("MessagesViewModel", "Error collecting messages and contacts", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
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
                _uiState.value = _uiState.value.copy(approvingMessageId = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    approvingMessageId = null,
                    error = "Failed to approve: ${e.localizedMessage}"
                )
            }
        }
    }

    fun rejectMessage(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(rejectingMessageId = messageId)
            try {
                rejectPendingMessageUseCase(messageId)
                _uiState.value = _uiState.value.copy(rejectingMessageId = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    rejectingMessageId = null,
                    error = "Failed to reject: ${e.localizedMessage}"
                )
            }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(retryingMessageId = messageId)
            try {
                messageRepository.updatePendingStatus(messageId, "PENDING")
                _uiState.value = _uiState.value.copy(retryingMessageId = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    retryingMessageId = null,
                    error = "Failed to retry: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
