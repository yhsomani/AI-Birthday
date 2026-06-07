package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.domain.usecase.SyncContactsUseCase
import javax.inject.Inject

data class ContactListUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val syncError: String? = null,
)

@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val preferencesRepository: com.example.domain.service.PreferencesRepository,
    private val syncContactsUseCase: SyncContactsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactListUiState())
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                contactRepository.getAll().collect { contacts ->
                    val lastError = try { preferencesRepository.getLastSyncError() } catch(ex: Exception) { null }
                    _uiState.value = ContactListUiState(
                        contacts = contacts,
                        isLoading = false,
                        syncError = lastError,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactListViewModel", "Error collecting contacts", e)
                val lastError = try { preferencesRepository.getLastSyncError() } catch(ex: Exception) { null }
                _uiState.value = _uiState.value.copy(isLoading = false, syncError = lastError)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.value = _uiState.value.copy(isRefreshing = true, syncError = null)
        refreshJob = viewModelScope.launch {
            try {
                // Perform contact synchronization with Google Contacts
                syncContactsUseCase(forceRefresh = true)
                
                contactRepository.getAll().first().let { contacts ->
                    val lastError = try { preferencesRepository.getLastSyncError() } catch(ex: Exception) { null }
                    _uiState.value = ContactListUiState(
                        contacts = contacts,
                        isLoading = false,
                        isRefreshing = false,
                        syncError = lastError,
                    )
                }
            } catch (e: Exception) {
                val lastError = try { preferencesRepository.getLastSyncError() } catch(ex: Exception) { null }
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    syncError = e.message ?: lastError ?: "Failed to sync contacts"
                )
            }
        }
    }

    fun dismissSyncError() {
        viewModelScope.launch {
            try {
                preferencesRepository.setLastSyncError(null)
                _uiState.value = _uiState.value.copy(syncError = null)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
