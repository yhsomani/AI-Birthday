package com.example.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatHistoryUiState(
    val isLoading: Boolean = true,
    val contactId: String = "",
    val messages: List<SentMessageEntity> = emptyList(),
    val errorMessageRes: Int? = null
)

@HiltViewModel
class ChatHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _uiState = MutableStateFlow(ChatHistoryUiState(contactId = contactId))
    val uiState: StateFlow<ChatHistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            try {
                messageRepository.getSentByContactFlow(contactId, 100).collect { history ->
                    _uiState.update {
                        it.copy(isLoading = false, messages = history, errorMessageRes = null)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessageRes = R.string.chat_history_error_load)
                }
            }
        }
    }
}
