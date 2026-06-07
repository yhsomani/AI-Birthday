package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
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
                    error = e.localizedMessage ?: "Failed to load memory vault"
                )
            }
        }
    }

    fun addNote(text: String, category: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val newNote = MemoryNoteEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contactId,
                    noteText = text,
                    category = category,
                    dateMs = System.currentTimeMillis(),
                    isPinned = false
                )
                memoryNoteRepository.upsert(newNote)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Failed to add note")
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
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Failed to pin/unpin note")
            }
        }
    }

    fun deleteNote(note: MemoryNoteEntity) {
        viewModelScope.launch {
            try {
                memoryNoteRepository.delete(note)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Failed to delete note")
            }
        }
    }
}
