package com.example.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.message.toChatHistoryMessageItems
import com.example.domain.model.message.ChatHistoryMessageItem
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
    val messages: List<ChatHistoryMessageItem> = emptyList(),
    val totalMessageCount: Int = messages.size,
    val searchQuery: String = "",
    val errorMessageRes: Int? = null
)

@HiltViewModel
class ChatHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _uiState = MutableStateFlow(ChatHistoryUiState(contactId = contactId))
    val uiState: StateFlow<ChatHistoryUiState> = _uiState.asStateFlow()
    private var loadedMessages: List<ChatHistoryMessageItem> = emptyList()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            try {
                messageRepository.getSentByContactFlow(contactId, HISTORY_LIMIT).collect { history ->
                    loadedMessages = history.toChatHistoryMessageItems()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            messages = filterMessages(loadedMessages, it.searchQuery),
                            totalMessageCount = loadedMessages.size,
                            errorMessageRes = null,
                        )
                    }
                }
            } catch (e: Exception) {
                loadedMessages = emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        messages = emptyList(),
                        totalMessageCount = 0,
                        errorMessageRes = R.string.chat_history_error_load,
                    )
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                messages = filterMessages(loadedMessages, query),
                errorMessageRes = null,
            )
        }
    }

    private fun filterMessages(
        messages: List<ChatHistoryMessageItem>,
        query: String,
    ): List<ChatHistoryMessageItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return messages

        return messages.filter { message ->
            message.messageText.contains(normalizedQuery, ignoreCase = true) ||
                message.channel.raw.contains(normalizedQuery, ignoreCase = true) ||
                message.channel.name.contains(normalizedQuery, ignoreCase = true)
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 100
    }
}
