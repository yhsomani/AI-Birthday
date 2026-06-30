package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageFeedbackRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ActivityLogType
import com.example.domain.model.MessageStatus
import com.example.domain.model.contact.ContactWishContext
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
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

data class ReviewNextTarget(
    val contactId: String,
    val messageRef: String,
)

data class WishPreviewSendSummary(
    val eventType: String,
    val channel: String,
    val scheduledForMs: Long,
    val approvalMode: String,
    val usesFallback: Boolean,
)

enum class WishDraftReadiness {
    READY,
    EDITED_READY,
    TOO_SHORT,
    BLANK,
}

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
    val previewDraft: WishPreviewDraft? = null,
    val selectedVariant: String = "standard",
    val editedText: String = "",
    val isLoading: Boolean = true,
    val isApproving: Boolean = false,
    val isRejecting: Boolean = false,
    val isRegenerating: Boolean = false,
    val isTestingSend: Boolean = false,
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
    val feedbackEvent: FeedbackEvent? = null,
    val whySignals: List<WhySignal> = emptyList(),
    val nextReviewTarget: ReviewNextTarget? = null,
    val remainingReviewCount: Int = 0,
    val sendSummary: WishPreviewSendSummary? = null,
    val draftReadiness: WishDraftReadiness = WishDraftReadiness.READY,
)

private data class WishPreviewLiveData(
    val draft: WishPreviewDraft?,
    val contact: ContactWishContext? = null,
    val memoryCount: Int = 0,
    val giftCount: Int = 0,
    val previousWishes: Int = 0,
    val eventType: OccasionType? = null,
    val reviewQueue: List<WishPreviewReviewItem> = emptyList(),
)

private data class WishPreviewContextData(
    val contact: ContactWishContext?,
    val memoryCount: Int,
    val giftCount: Int,
    val previousWishes: Int,
    val eventType: OccasionType?,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class WishPreviewViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val messageFeedbackRepository: MessageFeedbackRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val memoryNoteRepository: MemoryNoteRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val approvePendingMessageUseCase: ApprovePendingMessageUseCase,
    private val rejectPendingMessageUseCase: RejectPendingMessageUseCase,
    private val regeneratePendingMessageUseCase: RegeneratePendingMessageUseCase,
    private val testSendUseCase: TestSendUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishPreviewUiState())
    val uiState: StateFlow<WishPreviewUiState> = _uiState.asStateFlow()
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
                            ) { context, reviewQueue ->
                                WishPreviewLiveData(
                                    draft = draft,
                                    contact = context.contact,
                                    memoryCount = context.memoryCount,
                                    giftCount = context.giftCount,
                                    previousWishes = context.previousWishes,
                                    eventType = context.eventType,
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

    private fun WishPreviewLiveData.toUiState(current: WishPreviewUiState): WishPreviewUiState {
        val draft = draft ?: return current.copy(
            previewDraft = null,
            isLoading = false,
            errorMessageRes = R.string.wish_preview_error_message_not_found,
        )
        val reviewQueueState = buildReviewQueueState(draft, reviewQueue)
        val preserveEditorState = !current.isLoading && current.previewDraft == draft
        val selectedVariant = if (preserveEditorState) current.selectedVariant else draft.selectedVariant
        val editedText = if (preserveEditorState) current.editedText else draft.selectedVariantText
        val qualityMessageRes = current.qualityMessageRes
            ?: if (draft.isUsingFallback) R.string.wish_preview_quality_template_used else null
        return current.copy(
            previewDraft = draft,
            selectedVariant = selectedVariant,
            editedText = editedText,
            isLoading = false,
            errorMessageRes = null,
            usedFallback = draft.isUsingFallback,
            whySignals = buildWhySignals(
                draft = draft,
                contact = contact,
                memoryCount = memoryCount,
                giftCount = giftCount,
                previousWishes = previousWishes,
            ),
            sendSummary = buildSendSummary(draft, eventType),
            draftReadiness = draft.evaluateDraftReadiness(editedText, selectedVariant),
            nextReviewTarget = reviewQueueState.nextTarget,
            remainingReviewCount = reviewQueueState.remainingReviewCount,
            qualityMessageRes = qualityMessageRes,
        )
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
                        .getLatestForPendingMessage(pendingId)
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
                messageFeedbackRepository.record(
                    MessageFeedbackEntity(
                        id = UUID.randomUUID().toString(),
                        pendingMessageId = draft.id.value,
                        contactId = draft.contactId.value,
                        eventId = draft.occasionId.value,
                        reasonKey = option.key,
                        instruction = option.instruction,
                        draftText = _uiState.value.editedText,
                    )
                )
                activityLogRepository.record(
                    ActivityLogEntity(
                        id = UUID.randomUUID().toString(),
                        type = ActivityLogType.AI.raw,
                        title = "AI feedback saved",
                        detail = option.instruction,
                        contactId = draft.contactId.value,
                        eventId = draft.occasionId.value,
                        messageId = draft.id.value,
                        severity = ActivityLogSeverity.INFO.raw,
                        status = ActivityLogStatus.OPEN.raw,
                        actionRoute = "wish/${draft.contactId.value}/${draft.id.value}",
                        metadataJson = "{\"feedback\":\"${option.key}\"}",
                    )
                )
            }
        }
    }

    fun updateEditedText(text: String) {
        val draft = _uiState.value.previewDraft
        _uiState.value = _uiState.value.copy(
            editedText = text,
            draftReadiness = draft?.evaluateDraftReadiness(text, _uiState.value.selectedVariant)
                ?: text.evaluateDraftReadinessAgainst(""),
        )
    }

    fun approve() {
        val pendingId = _uiState.value.previewDraft?.id?.value ?: return
        val draftReadiness = _uiState.value.previewDraft
            ?.evaluateDraftReadiness(_uiState.value.editedText, _uiState.value.selectedVariant)
            ?: _uiState.value.editedText.evaluateDraftReadinessAgainst("")
        if (draftReadiness == WishDraftReadiness.BLANK || draftReadiness == WishDraftReadiness.TOO_SHORT) {
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

    private fun buildReviewQueueState(
        current: WishPreviewDraft,
        reviewQueue: List<WishPreviewReviewItem>,
    ): ReviewQueueState {
        val reviewableMessages = reviewQueue
            .filter { it.status == MessageStatus.PENDING }
            .sortedWith(compareBy<WishPreviewReviewItem> { it.scheduledForMs }.thenBy { it.id.value })
        val remainingReviewCount = reviewableMessages.count { it.id != current.id }
        val currentIndex = reviewableMessages.indexOfFirst { it.id == current.id }
        val nextMessage = when {
            remainingReviewCount == 0 -> null
            currentIndex == -1 -> reviewableMessages.firstOrNull { it.id != current.id }
            else -> reviewableMessages
                .drop(currentIndex + 1)
                .firstOrNull { it.id != current.id }
                ?: reviewableMessages.firstOrNull { it.id != current.id }
        }
        return ReviewQueueState(
            nextTarget = nextMessage?.let {
                ReviewNextTarget(contactId = it.contactId.value, messageRef = it.id.value)
            },
            remainingReviewCount = remainingReviewCount,
        )
    }

    private fun buildWhySignals(
        draft: WishPreviewDraft,
        contact: ContactWishContext?,
        memoryCount: Int,
        giftCount: Int,
        previousWishes: Int,
    ): List<WhySignal> {
        return listOf(
            WhySignal(R.string.wish_why_relationship, contact?.relationshipType ?: "UNKNOWN"),
            WhySignal(R.string.wish_why_language, contact?.preferredLanguage ?: "en"),
            WhySignal(R.string.wish_why_channel, draft.channel.raw),
            WhySignal(R.string.wish_why_tone, draft.selectedVariant),
            WhySignal(R.string.wish_why_memories, memoryCount.toString()),
            WhySignal(R.string.wish_why_gifts, giftCount.toString()),
            WhySignal(R.string.wish_why_previous, previousWishes.toString()),
        )
    }

    private fun buildSendSummary(
        draft: WishPreviewDraft,
        eventType: OccasionType?,
    ): WishPreviewSendSummary {
        return WishPreviewSendSummary(
            eventType = eventType?.raw ?: OccasionType.BIRTHDAY.raw,
            channel = draft.channel.raw,
            scheduledForMs = draft.scheduledForMs,
            approvalMode = draft.approvalMode.raw,
            usesFallback = draft.isUsingFallback,
        )
    }

    private fun WishPreviewDraft.evaluateDraftReadiness(
        draft: String,
        variant: String,
    ): WishDraftReadiness {
        return draft.evaluateDraftReadinessAgainst(variantText(variant))
    }

    private fun String.evaluateDraftReadinessAgainst(sourceText: String): WishDraftReadiness {
        val trimmed = trim()
        return when {
            trimmed.isBlank() -> WishDraftReadiness.BLANK
            trimmed.length < MIN_REVIEWED_DRAFT_LENGTH -> WishDraftReadiness.TOO_SHORT
            this != sourceText -> WishDraftReadiness.EDITED_READY
            else -> WishDraftReadiness.READY
        }
    }

    private fun WishDraftReadiness.errorMessageRes(): Int {
        return when (this) {
            WishDraftReadiness.TOO_SHORT -> R.string.wish_preview_readiness_short
            WishDraftReadiness.BLANK -> R.string.wish_preview_readiness_blank
            WishDraftReadiness.READY,
            WishDraftReadiness.EDITED_READY -> R.string.wish_preview_readiness_ready
        }
    }

    private data class ReviewQueueState(
        val nextTarget: ReviewNextTarget? = null,
        val remainingReviewCount: Int = 0,
    )

    private companion object {
        const val MIN_REVIEWED_DRAFT_LENGTH = 12
    }
}
