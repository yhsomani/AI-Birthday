package com.example.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.GenerateMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val generateMessageUseCase: GenerateMessageUseCase
) : ViewModel() {

    fun approveMessage(pendingMessageId: String, finalEditedText: String) {
        viewModelScope.launch {
            approvePendingMessageUseCase(pendingMessageId)
        }
    }

    fun regenerateMessage(contactId: String, eventId: String) {
        viewModelScope.launch {
            generateMessageUseCase(eventId)
        }
    }
}
