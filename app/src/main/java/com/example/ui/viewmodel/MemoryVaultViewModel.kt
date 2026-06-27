package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MemoryNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MemoryVaultUiState(
    val contact: ContactHeader? = null,
    val notes: List<MemoryNoteRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessageRes: Int? = null
)

@HiltViewModel
class MemoryVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val memoryNoteRepository: MemoryNoteRepository
) : ViewModel() {

    private val contactId: String = savedStateHandle.get<String>("contactId") ?: ""

    private val _uiState = MutableStateFlow(MemoryVaultUiState())
    val uiState: StateFlow<MemoryVaultUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessageRes = null)
            try {
                val contact = contactRepository.getHeader(contactId)
                val notes = memoryNoteRepository.getRecordsByContact(contactId)
                _uiState.value = MemoryVaultUiState(
                    contact = contact,
                    notes = notes.sortedWith(compareByDescending<MemoryNoteRecord> { it.isPinned }.thenByDescending { it.dateMs }),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessageRes = R.string.memory_vault_error_load,
                )
            }
        }
    }

    fun addNote(text: String, category: String) {
        val cleanedText = text.trim()
        if (cleanedText.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_blank_note)
            return
        }
        if (cleanedText.length > MAX_NOTE_LENGTH) {
            _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_note_too_long)
            return
        }
        val safeCategory = category.takeIf { it in ALLOWED_CATEGORIES } ?: CATEGORY_GENERAL
        viewModelScope.launch {
            try {
                val newNote = MemoryNoteRecord(
                    id = MemoryNoteId(UUID.randomUUID().toString()),
                    contactId = ContactId(contactId),
                    noteText = cleanedText,
                    category = safeCategory,
                    dateMs = System.currentTimeMillis(),
                    isPinned = false
                )
                memoryNoteRepository.upsertRecord(newNote)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_add)
            }
        }
    }

    fun togglePin(note: MemoryNoteRecord) {
        viewModelScope.launch {
            try {
                val updatedNote = note.copy(isPinned = !note.isPinned)
                memoryNoteRepository.upsertRecord(updatedNote)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_pin)
            }
        }
    }

    fun deleteNote(note: MemoryNoteRecord) {
        viewModelScope.launch {
            try {
                memoryNoteRepository.deleteRecord(note.id)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_delete)
            }
        }
    }

    companion object {
        const val MAX_NOTE_LENGTH = 500
        const val CATEGORY_GENERAL = "GENERAL"
        private const val CATEGORY_PREFERENCE = "PREFERENCE"
        private const val CATEGORY_EVENT = "EVENT"
        private const val CATEGORY_GIFT = "GIFT"
        private const val CATEGORY_MILESTONE = "MILESTONE"
        val ALLOWED_CATEGORIES = setOf(
            CATEGORY_GENERAL,
            CATEGORY_PREFERENCE,
            CATEGORY_EVENT,
            CATEGORY_GIFT,
            CATEGORY_MILESTONE,
        )
    }
}
