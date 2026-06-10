package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GenerateMessageUseCase
import com.example.domain.usecase.UpdateContactPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetailUiState(
    val contact: ContactEntity? = null,
    val upcomingBirthdayDaysLeft: Int? = null,
    val upcomingEvent: EventEntity? = null,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val isSavingPreferences: Boolean = false,
    val generationResult: String? = null,
    val generationErrorRes: Int? = null,
    val preferenceMessageRes: Int? = null,
    val preferenceError: String? = null,
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val generateMessageUseCase: GenerateMessageUseCase,
    private val updateContactPreferencesUseCase: UpdateContactPreferencesUseCase,
) : ViewModel() {

    private val contactId: String = savedStateHandle.get<String>("contactId") ?: ""

    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    init {
        loadContact()
    }

    private fun loadContact() {
        viewModelScope.launch {
            try {
                val contact = contactRepository.getById(contactId)
                val events = eventRepository.getUpcoming(365)
                val birthdayEvent = events.find {
                    contact?.let { c -> it.contactId == c.id } == true
                }
                val daysLeft = if (birthdayEvent != null) {
                    val days = (birthdayEvent.nextOccurrenceMs - System.currentTimeMillis()) / 86400000
                    days.toInt().coerceAtLeast(0)
                } else null

                _uiState.value = ContactDetailUiState(
                    contact = contact,
                    upcomingBirthdayDaysLeft = daysLeft,
                    upcomingEvent = birthdayEvent,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun savePreferences(request: UpdateContactPreferencesUseCase.Request) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingPreferences = true,
                preferenceMessageRes = null,
                preferenceError = null,
            )
            when (val outcome = updateContactPreferencesUseCase(request.copy(contactId = contactId))) {
                UpdateContactPreferencesUseCase.Outcome.ContactNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingPreferences = false,
                        preferenceError = "Contact not found.",
                    )
                }
                is UpdateContactPreferencesUseCase.Outcome.InvalidInput -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingPreferences = false,
                        preferenceError = outcome.message,
                    )
                }
                is UpdateContactPreferencesUseCase.Outcome.Updated -> {
                    _uiState.value = _uiState.value.copy(
                        contact = outcome.contact,
                        isSavingPreferences = false,
                        preferenceMessageRes = R.string.contact_detail_preferences_saved,
                        preferenceError = null,
                    )
                    loadContact()
                }
            }
        }
    }

    fun generateWish() {
        val event = _uiState.value.upcomingEvent
        if (event == null) {
            _uiState.value = _uiState.value.copy(generationErrorRes = R.string.contact_detail_error_no_upcoming_event)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, generationErrorRes = null)
            when (val result = generateMessageUseCase(event.id)) {
                is GenerateMessageUseCase.GenerationOutcome.Generated -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationResult = result.pendingId,
                    )
                }
                is GenerateMessageUseCase.GenerationOutcome.AlreadyExists -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationErrorRes = R.string.contact_detail_error_already_generated,
                    )
                }
                is GenerateMessageUseCase.GenerationOutcome.EventNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationErrorRes = R.string.contact_detail_error_event_not_found,
                    )
                }
                is GenerateMessageUseCase.GenerationOutcome.ContactNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationErrorRes = R.string.contact_detail_error_contact_not_found,
                    )
                }
                is GenerateMessageUseCase.GenerationOutcome.AiDisabled -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationErrorRes = R.string.contact_detail_error_ai_disabled,
                    )
                }
            }
        }
    }

    fun clearGenerationResult() {
        _uiState.value = _uiState.value.copy(
            generationResult = null,
            generationErrorRes = null,
            preferenceMessageRes = null,
            preferenceError = null,
        )
    }
}
