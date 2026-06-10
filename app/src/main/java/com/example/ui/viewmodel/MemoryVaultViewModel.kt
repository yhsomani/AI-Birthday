package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.MemoryNoteEntity
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
    val contact: ContactEntity? = null,
    val notes: List<MemoryNoteEntity> = emptyList(),
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
                val contact = contactRepository.getById(contactId)
                val notes = memoryNoteRepository.getByContact(contactId)
                _uiState.value = MemoryVaultUiState(
                    contact = contact,
                    notes = notes.sortedWith(compareByDescending<MemoryNoteEntity> { it.isPinned }.thenByDescending { it.dateMs }),
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
        if (cleanedText.isBlank()) return
        if (cleanedText.length > MAX_NOTE_LENGTH) {
            _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_note_too_long)
            return
        }
        val safeCategory = category.takeIf { it in ALLOWED_CATEGORIES } ?: CATEGORY_GENERAL
        viewModelScope.launch {
            try {
                val newNote = MemoryNoteEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contactId,
                    noteText = cleanedText,
                    category = safeCategory,
                    dateMs = System.currentTimeMillis(),
                    isPinned = false
                )
                memoryNoteRepository.upsert(newNote)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_add)
            }
        }
    }

    fun togglePin(note: MemoryNoteEntity) {
        viewModelScope.launch {
            try {
                val updatedNote = note.copy(isPinned = !note.isPinned)
                memoryNoteRepository.upsert(updatedNote)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessageRes = R.string.memory_vault_error_pin)
            }
        }
    }

    fun deleteNote(note: MemoryNoteEntity) {
        viewModelScope.launch {
            try {
                memoryNoteRepository.delete(note)
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
