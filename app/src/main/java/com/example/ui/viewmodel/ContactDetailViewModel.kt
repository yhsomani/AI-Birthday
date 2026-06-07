package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GenerateMessageUseCase
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
    val generationResult: String? = null,
    val generationError: String? = null,
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val generateMessageUseCase: GenerateMessageUseCase,
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

    fun generateWish() {
        val event = _uiState.value.upcomingEvent ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, generationError = null)
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
                        generationError = "A wish has already been generated for this event.",
                    )
                }
                is GenerateMessageUseCase.GenerationOutcome.EventNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationError = "Event not found.",
                    )
                }
                is GenerateMessageUseCase.GenerationOutcome.ContactNotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generationError = "Contact not found.",
                    )
                }
            }
        }
    }

    fun clearGenerationResult() {
        _uiState.value = _uiState.value.copy(generationResult = null, generationError = null)
    }
}
