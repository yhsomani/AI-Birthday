package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageFeedbackRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.model.common.MessageDraftId
import com.example.domain.message.WishDraftReadinessPolicy
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.RegeneratePendingMessageUseCase
import com.example.domain.usecase.RejectPendingMessageUseCase
import com.example.domain.usecase.TestSendUseCase
import com.example.ui.feedback.FeedbackEvent
import com.example.ui.feedback.FeedbackType
import com.example.ui.feedback.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class WishPreviewViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    activityLogRepository: ActivityLogRepository,
    private val messageFeedbackRepository: MessageFeedbackRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val memoryNoteRepository: MemoryNoteRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deviceReadinessReader: WishPreviewDeviceReadinessReader,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
    private val regeneratePendingMessageUseCase: RegeneratePendingMessageUseCase,
    private val testSendUseCase: TestSendUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishPreviewUiState())
    val uiState: StateFlow<WishPreviewUiState> = _uiState.asStateFlow()
    private val feedbackRecorder = WishPreviewFeedbackRecorder(
        messageFeedbackRepository = messageFeedbackRepository,
        activityLogRepository = activityLogRepository,
    )
    private var loadJob: Job? = null

    fun loadPending(messageRef: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessageRes = null)
                messageRepository.getWishPreviewDraftByRef(messageRef)
                    .flatMapLatest { draft ->
                        if (draft == null) {
                            flowOf(WishPreviewLiveData(draft = null))
                        } else {
                            combine(
                                combine(
                                    contactRepository.getWishContextFlow(draft.contactId.value),
                                    memoryNoteRepository.countByContactFlow(draft.contactId.value),
                                    giftHistoryRepository.countByContactFlow(draft.contactId.value),
                                    messageRepository.countSentByContact(draft.contactId.value),
                                    eventRepository.getOccasionTypeByIdFlow(draft.occasionId.value),
                                ) { contact, memoryCount, giftCount, previousWishes, eventType ->
                                    WishPreviewContextData(
                                        contact = contact,
                                        memoryCount = memoryCount,
                                        giftCount = giftCount,
                                        previousWishes = previousWishes,
                                        eventType = eventType,
                                    )
                                },
                                messageRepository.getWishPreviewReviewQueue(),
                                contactRepository.getMessageContexts(),
                                preferencesRepository.observeChanges().onStart { emit(Unit) },
                            ) { context, reviewQueue, messageContacts, _ ->
                                WishPreviewLiveData(
                                    draft = draft,
                                    contact = context.contact,
                                    memoryCount = context.memoryCount,
                                    giftCount = context.giftCount,
                                    previousWishes = context.previousWishes,
                                    eventType = context.eventType,
                                    routeContact = messageContacts.firstOrNull {
                                        it.id == draft.contactId
                                    },
                                    channelBlackoutJson = preferencesRepository.getChannelBlackout(),
                                    blackoutDatesJson = preferencesRepository.getBlackoutDates(),
                                    quietHoursStart = preferencesRepository.getQuietHoursStart(),
                                    quietHoursEnd = preferencesRepository.getQuietHoursEnd(),
                                    senderEmail = preferencesRepository.getSenderEmail(),
                                    senderEmailPassword = preferencesRepository.getSenderEmailPassword(),
                                    deviceReadiness = deviceReadinessReader.snapshot(),
                                    reviewQueue = reviewQueue,
                                )
                            }
                        }
                    }
                    .collect { data ->
                        _uiState.value = data.toUiState(_uiState.value)
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
        val draft = _uiState.value.previewDraft ?: return
        val text = draft.variantText(variant)
        _uiState.value = _uiState.value.copy(
            selectedVariant = variant,
            editedText = text,
            draftReadiness = draft.evaluateDraftReadiness(text, variant),
        )
    }

    fun sendTestToMyself() {
        val draft = _uiState.value.editedText
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingSend = true, errorMessageRes = null)
            val feedback = when (testSendUseCase(draft)) {
                TestSendUseCase.Outcome.Sent -> FeedbackEvent(
                    message = UiText.Resource(R.string.wish_preview_test_sent),
                    type = FeedbackType.SUCCESS,
                )
                TestSendUseCase.Outcome.MissingEmailSetup -> FeedbackEvent(
                    message = UiText.Resource(R.string.wish_preview_test_missing_email),
                    type = FeedbackType.ERROR,
                )
                TestSendUseCase.Outcome.BlankMessage -> FeedbackEvent(
                    message = UiText.Resource(R.string.wish_preview_test_blank),
                    type = FeedbackType.ERROR,
                )
                TestSendUseCase.Outcome.SendFailed -> FeedbackEvent(
                    message = UiText.Resource(R.string.wish_preview_test_failed),
                    type = FeedbackType.ERROR,
                )
            }
            _uiState.value = _uiState.value.copy(
                isTestingSend = false,
                feedbackEvent = feedback,
            )
        }
    }

    fun dismissTestSent() {
        _uiState.value = _uiState.value.copy(testSent = false)
    }

    fun clearFeedbackEvent() {
        _uiState.value = _uiState.value.copy(feedbackEvent = null)
    }

    fun regenerate() {
        val pendingId = _uiState.value.previewDraft?.id?.value ?: return
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
                    val feedbackId = messageFeedbackRepository
                        .getLatestForPendingMessage(MessageDraftId(pendingId))
                        ?.takeIf { it.reasonKey == feedback?.key }
                        ?.id
                    if (feedbackId != null) {
                        messageFeedbackRepository.markApplied(feedbackId)
                    }
                    _uiState.value = _uiState.value.copy(
                        isRegenerating = false,
                        usedFallback = result.usedFallback,
                        qualityMessageRes = if (result.usedFallback) {
                            R.string.wish_preview_quality_template_used
                        } else if (feedback != null) {
                            R.string.wish_preview_quality_regenerated_with_feedback
                        } else {
                            R.string.wish_preview_quality_regenerated
                        },
                        qualityMessageArgRes = feedback?.labelRes,
                    )
                }
            }
        }
    }

    fun submitFeedback(key: String) {
        val option = aiFeedbackOptions.firstOrNull { it.key == key } ?: return
        val draft = _uiState.value.previewDraft
        _uiState.value = _uiState.value.copy(
            selectedFeedbackKey = key,
            feedbackMessageRes = R.string.wish_preview_feedback_saved,
            qualityMessageRes = R.string.wish_preview_quality_next_regeneration,
            qualityMessageArgRes = option.labelRes,
        )
        if (draft != null) {
            viewModelScope.launch {
                feedbackRecorder.record(
                    draft = draft,
                    option = option,
                    draftText = _uiState.value.editedText,
                )
            }
        }
    }

    fun updateEditedText(text: String) {
        val draft = _uiState.value.previewDraft
        _uiState.value = _uiState.value.copy(
            editedText = text,
            draftReadiness = draft?.evaluateDraftReadiness(text, _uiState.value.selectedVariant)
                ?: WishDraftReadinessPolicy.evaluate(draftText = text, sourceText = ""),
        )
    }

    fun approve() {
        val pendingId = _uiState.value.previewDraft?.id?.value ?: return
        val draftReadiness = _uiState.value.previewDraft
            ?.evaluateDraftReadiness(_uiState.value.editedText, _uiState.value.selectedVariant)
            ?: WishDraftReadinessPolicy.evaluate(draftText = _uiState.value.editedText, sourceText = "")
        val actionReadiness = draftReadiness.toDraftActionReadiness(_uiState.value.previewDraft)
        if (actionReadiness.blocksDraftApproval) {
            _uiState.value = _uiState.value.copy(
                draftReadiness = draftReadiness,
                errorMessageRes = draftReadiness.errorMessageRes(),
            )
            return
        }
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
        val pendingId = _uiState.value.previewDraft?.id?.value ?: return
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

}
