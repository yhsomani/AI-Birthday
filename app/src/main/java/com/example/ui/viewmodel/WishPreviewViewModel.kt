package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageFeedbackRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RegeneratePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AiFeedbackOption(
    val key: String,
    val labelRes: Int,
    val instruction: String,
)

data class WhySignal(
    val labelRes: Int,
    val value: String,
)

private val aiFeedbackOptions = listOf(
    AiFeedbackOption(
        key = "too_generic",
        labelRes = R.string.wish_feedback_too_generic,
        instruction = "Make it more personal. Use a specific memory, interest, nickname, or relationship detail from the contact context.",
    ),
    AiFeedbackOption(
        key = "too_formal",
        labelRes = R.string.wish_feedback_too_formal,
        instruction = "Make it more casual and natural, like a real personal message instead of a polished greeting.",
    ),
    AiFeedbackOption(
        key = "wrong_language",
        labelRes = R.string.wish_feedback_wrong_language,
        instruction = "Regenerate in the contact's preferred language and keep the wording culturally natural.",
    ),
    AiFeedbackOption(
        key = "too_long",
        labelRes = R.string.wish_feedback_too_long,
        instruction = "Make the message shorter, tighter, and easier to send without losing warmth.",
    ),
    AiFeedbackOption(
        key = "not_warm",
        labelRes = R.string.wish_feedback_not_warm,
        instruction = "Make it warmer and more emotionally specific without sounding dramatic or artificial.",
    ),
    AiFeedbackOption(
        key = "repetitive",
        labelRes = R.string.wish_feedback_repetitive,
        instruction = "Avoid the current wording and any previous wishes. Use a different structure, reference, and opening line.",
    ),
)

data class WishPreviewUiState(
    val pendingMessage: PendingMessageEntity? = null,
    val selectedVariant: String = "standard",
    val editedText: String = "",
    val isLoading: Boolean = true,
    val isApproving: Boolean = false,
    val isRejecting: Boolean = false,
    val isRegenerating: Boolean = false,
    val approved: Boolean = false,
    val rejected: Boolean = false,
    val errorMessageRes: Int? = null,
    val testSent: Boolean = false,
    val usedFallback: Boolean = false,
    val qualityMessageRes: Int? = null,
    val qualityMessageArgRes: Int? = null,
    val feedbackOptions: List<AiFeedbackOption> = aiFeedbackOptions,
    val selectedFeedbackKey: String? = null,
    val feedbackMessageRes: Int? = null,
    val whySignals: List<WhySignal> = emptyList(),
)

@HiltViewModel
class WishPreviewViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val messageFeedbackRepository: MessageFeedbackRepository,
    private val contactRepository: ContactRepository,
    private val memoryNoteRepository: MemoryNoteRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
    private val regeneratePendingMessageUseCase: RegeneratePendingMessageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishPreviewUiState())
    val uiState: StateFlow<WishPreviewUiState> = _uiState.asStateFlow()

    fun loadPending(messageRef: String) {
        viewModelScope.launch {
            try {
                val pending = messageRepository.getPendingById(messageRef)
                    ?: messageRepository.getPendingByEventId(messageRef)
                if (pending != null) {
                    val whySignals = buildWhySignals(pending)
                    _uiState.value = WishPreviewUiState(
                        pendingMessage = pending,
                        selectedVariant = pending.selectedVariant,
                        editedText = pending.selectedVariantText,
                        isLoading = false,
                        usedFallback = pending.isUsingFallback,
                        whySignals = whySignals,
                        qualityMessageRes = if (pending.isUsingFallback) {
                            R.string.wish_preview_quality_template_used
                        } else {
                            null
                        },
                    )
                } else {
                    _uiState.value = WishPreviewUiState(
                        isLoading = false,
                        errorMessageRes = R.string.wish_preview_error_message_not_found,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WishPreviewUiState(
                    isLoading = false,
                    errorMessageRes = R.string.wish_preview_error_load,
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

    fun regenerate() {
        val pendingId = _uiState.value.pendingMessage?.id ?: return
        val draft = _uiState.value.editedText
        val feedback = selectedFeedback()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRegenerating = true,
                errorMessageRes = null,
                qualityMessageRes = null,
                qualityMessageArgRes = null,
            )
            when (val result = regeneratePendingMessageUseCase(pendingId, draft, feedback?.instruction)) {
                RegeneratePendingMessageUseCase.Outcome.AiDisabled -> {
                    _uiState.value = _uiState.value.copy(
                        isRegenerating = false,
                        errorMessageRes = R.string.wish_preview_error_ai_disabled,
                    )
                }
                RegeneratePendingMessageUseCase.Outcome.ContextNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isRegenerating = false,
                        errorMessageRes = R.string.wish_preview_error_context_not_found,
                    )
                }
                RegeneratePendingMessageUseCase.Outcome.PendingNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isRegenerating = false,
                        errorMessageRes = R.string.wish_preview_error_message_not_found,
                    )
                }
                is RegeneratePendingMessageUseCase.Outcome.Regenerated -> {
                    val updated = messageRepository.getPendingById(result.pendingId)
                    if (updated != null) {
                        val feedbackId = messageFeedbackRepository
                            .getLatestForPendingMessage(pendingId)
                            ?.takeIf { it.reasonKey == feedback?.key }
                            ?.id
                        if (feedbackId != null) {
                            messageFeedbackRepository.markApplied(feedbackId)
                        }
                        _uiState.value = _uiState.value.copy(
                            pendingMessage = updated,
                            selectedVariant = updated.selectedVariant,
                            editedText = updated.selectedVariantText,
                            isRegenerating = false,
                            usedFallback = result.usedFallback,
                            whySignals = buildWhySignals(updated),
                            qualityMessageRes = if (result.usedFallback) {
                                R.string.wish_preview_quality_template_used
                            } else if (feedback != null) {
                                R.string.wish_preview_quality_regenerated_with_feedback
                            } else {
                                R.string.wish_preview_quality_regenerated
                            },
                            qualityMessageArgRes = feedback?.labelRes,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isRegenerating = false,
                            errorMessageRes = R.string.wish_preview_error_message_not_found,
                        )
                    }
                }
            }
        }
    }

    fun submitFeedback(key: String) {
        val option = aiFeedbackOptions.firstOrNull { it.key == key } ?: return
        val pending = _uiState.value.pendingMessage
        _uiState.value = _uiState.value.copy(
            selectedFeedbackKey = key,
            feedbackMessageRes = R.string.wish_preview_feedback_saved,
            qualityMessageRes = R.string.wish_preview_quality_next_regeneration,
            qualityMessageArgRes = option.labelRes,
        )
        if (pending != null) {
            viewModelScope.launch {
                messageFeedbackRepository.record(
                    MessageFeedbackEntity(
                        id = UUID.randomUUID().toString(),
                        pendingMessageId = pending.id,
                        contactId = pending.contactId,
                        eventId = pending.eventId,
                        reasonKey = option.key,
                        instruction = option.instruction,
                        draftText = _uiState.value.editedText,
                    )
                )
                activityLogRepository.record(
                    ActivityLogEntity(
                        id = UUID.randomUUID().toString(),
                        type = "AI",
                        title = "AI feedback saved",
                        detail = option.instruction,
                        contactId = pending.contactId,
                        eventId = pending.eventId,
                        messageId = pending.id,
                        severity = "INFO",
                        status = "OPEN",
                        actionRoute = "wish/${pending.contactId}/${pending.id}",
                        metadataJson = "{\"feedback\":\"${option.key}\"}",
                    )
                )
            }
        }
    }

    fun updateEditedText(text: String) {
        _uiState.value = _uiState.value.copy(editedText = text)
    }

    fun approve() {
        val pendingId = _uiState.value.pendingMessage?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isApproving = true, errorMessageRes = null)
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
                        errorMessageRes = R.string.wish_preview_error_message_not_found,
                    )
                }
            }
        }
    }

    fun reject() {
        val pendingId = _uiState.value.pendingMessage?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRejecting = true, errorMessageRes = null)
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
                        errorMessageRes = R.string.wish_preview_error_message_not_found,
                    )
                }
            }
        }
    }

    private fun selectedFeedback(): AiFeedbackOption? {
        val key = _uiState.value.selectedFeedbackKey ?: return null
        return aiFeedbackOptions.firstOrNull { it.key == key }
    }

    private suspend fun buildWhySignals(pending: PendingMessageEntity): List<WhySignal> {
        val contact = contactRepository.getById(pending.contactId)
        val memoryCount = memoryNoteRepository.getByContact(pending.contactId).size
        val giftCount = giftHistoryRepository.getByContact(pending.contactId).size
        val previousWishes = messageRepository.getSentByContact(pending.contactId, 10).size
        return listOf(
            WhySignal(R.string.wish_why_relationship, contact?.relationshipType ?: "UNKNOWN"),
            WhySignal(R.string.wish_why_language, contact?.preferredLanguage ?: "en"),
            WhySignal(R.string.wish_why_channel, pending.channel),
            WhySignal(R.string.wish_why_tone, pending.selectedVariant),
            WhySignal(R.string.wish_why_memories, memoryCount.toString()),
            WhySignal(R.string.wish_why_gifts, giftCount.toString()),
            WhySignal(R.string.wish_why_previous, previousWishes.toString()),
        )
    }
}
