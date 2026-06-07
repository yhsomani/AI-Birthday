package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingMessageItem(
    val entity: PendingMessageEntity,
    val contactName: String,
)

data class SentMessageItem(
    val entity: SentMessageEntity,
    val contactName: String,
)

data class MessagesUiState(
    val pendingMessages: List<PendingMessageItem> = emptyList(),
    val sentMessages: List<SentMessageItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
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
            combine(
                messageRepository.getAllPending(),
                messageRepository.getAllSent(),
                contactRepository.getAll(),
            ) { pending, sent, contacts ->
                val contactMap = contacts.associate { it.id to it.name }
                MessagesUiState(
                    pendingMessages = pending.map {
                        PendingMessageItem(it, contactMap[it.contactId] ?: it.contactId)
                    },
                    sentMessages = sent.map {
                        SentMessageItem(it, contactMap[it.contactId] ?: it.contactId ?: "Deleted Contact")
                    },
                    isLoading = false,
                    isRefreshing = _uiState.value.isRefreshing,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            combine(
                messageRepository.getAllPending(),
                messageRepository.getAllSent(),
                contactRepository.getAll(),
            ) { pending, sent, contacts ->
                val contactMap = contacts.associate { it.id to it.name }
                MessagesUiState(
                    pendingMessages = pending.map {
                        PendingMessageItem(it, contactMap[it.contactId] ?: it.contactId)
                    },
                    sentMessages = sent.map {
                        SentMessageItem(it, contactMap[it.contactId] ?: it.contactId ?: "Deleted Contact")
                    },
                    isLoading = false,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
