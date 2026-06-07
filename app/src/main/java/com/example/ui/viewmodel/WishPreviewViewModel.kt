package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WishPreviewUiState(
    val pendingMessage: PendingMessageEntity? = null,
    val selectedVariant: String = "standard",
    val editedText: String = "",
    val isLoading: Boolean = true,
    val isApproving: Boolean = false,
    val isRejecting: Boolean = false,
    val approved: Boolean = false,
    val rejected: Boolean = false,
    val error: String? = null,
    val testSent: Boolean = false
)

private val variantOptions = listOf(
    "short" to "Short",
    "standard" to "Standard",
    "long" to "Long",
    "formal" to "Formal",
    "funny" to "Funny",
    "emotional" to "Emotional",
)

@HiltViewModel
class WishPreviewViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishPreviewUiState())
    val uiState: StateFlow<WishPreviewUiState> = _uiState.asStateFlow()

    fun loadPending(pendingId: String) {
        viewModelScope.launch {
            try {
                val allPending = messageRepository.getAllPending().first()
                val pending = allPending.find { it.id == pendingId }
                if (pending != null) {
                    _uiState.value = WishPreviewUiState(
                        pendingMessage = pending,
                        selectedVariant = pending.selectedVariant,
                        editedText = pending.selectedVariantText,
                        isLoading = false,
                    )
                } else {
                    _uiState.value = WishPreviewUiState(
                        isLoading = false,
                        error = "Message not found.",
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WishPreviewUiState(
                    isLoading = false,
                    error = "Failed to load message: ${e.message}",
                )
            }
        }
    }

    fun selectVariant(variant: String) {
        val msg = _uiState.value.pendingMessage ?: return
        val text = when (variant) {
            "short" -> msg.shortVariant
            "standard" -> msg.standardVariant
            "long" -> msg.longVariant
            "formal" -> msg.formalVariant
            "funny" -> msg.funnyVariant
            "emotional" -> msg.emotionalVariant
            else -> msg.standardVariant
        }
        _uiState.value = _uiState.value.copy(
            selectedVariant = variant,
            editedText = text,
        )
    }

    fun sendTestToMyself() {
        // In a real implementation this would trigger an SMS or email to the logged in user's profile.
        // For F-039 implementation, we simulate the success state.
        _uiState.value = _uiState.value.copy(testSent = true)
    }

    fun dismissTestSent() {
        _uiState.value = _uiState.value.copy(testSent = false)
    }

    fun updateEditedText(text: String) {
        _uiState.value = _uiState.value.copy(editedText = text)
    }

    fun approve() {
        val pendingId = _uiState.value.pendingMessage?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isApproving = true, error = null)
            val finalText = _uiState.value.editedText
            when (val result = approvePendingMessageUseCase(pendingId, finalText)) {
                is ApprovePendingMessageUseCase.ApprovalOutcome.Approved -> {
                    _uiState.value = _uiState.value.copy(
                        isApproving = false,
                        approved = true,
                    )
                }
                is ApprovePendingMessageUseCase.ApprovalOutcome.PendingNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isApproving = false,
                        error = "Message not found.",
                    )
                }
            }
        }
    }

    fun reject() {
        val pendingId = _uiState.value.pendingMessage?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRejecting = true, error = null)
            when (val result = rejectPendingMessageUseCase(pendingId)) {
                is RejectPendingMessageUseCase.RejectionOutcome.Rejected -> {
                    _uiState.value = _uiState.value.copy(
                        isRejecting = false,
                        rejected = true,
                    )
                }
                is RejectPendingMessageUseCase.RejectionOutcome.PendingNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isRejecting = false,
                        error = "Message not found.",
                    )
                }
            }
        }
    }
}
