package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.db.entities.ContactEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.domain.usecase.SyncContactsUseCase
import javax.inject.Inject

enum class ContactFilter {
    ALL,
    FAMILY,
    FRIENDS,
    WORK,
    CLOSE_FRIENDS,
}

enum class ContactSort {
    NAME_ASC,
    HEALTH_DESC,
    HEALTH_ASC,
}

data class ContactListUiState(
    val allContacts: List<ContactEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ContactFilter = ContactFilter.ALL,
    val selectedSort: ContactSort = ContactSort.NAME_ASC,
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
    private companion object {
        const val TAG = "ContactListViewModel"
        const val SYNC_FAILED_MESSAGE = "Unable to sync contacts. Please try again."
    }

    private val _uiState = MutableStateFlow(ContactListUiState())
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                contactRepository.getAll().collect { contacts ->
                    val lastError = try { preferencesRepository.getLastSyncError() } catch(ex: Exception) { null }
                    _uiState.value = _uiState.value.withContacts(
                        allContacts = contacts,
                        isLoading = false,
                        syncError = lastError,
                    )
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Contact collection failed", e)
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
                    _uiState.value = _uiState.value.withContacts(
                        allContacts = contacts,
                        isLoading = false,
                        isRefreshing = false,
                        syncError = lastError,
                    )
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Contact refresh failed", e)
                val lastError = try { preferencesRepository.getLastSyncError() } catch(ex: Exception) { null }
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    syncError = lastError ?: SYNC_FAILED_MESSAGE
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

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query).withFilteredContacts()
    }

    fun selectFilter(filter: ContactFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter).withFilteredContacts()
    }

    fun selectSort(sort: ContactSort) {
        _uiState.value = _uiState.value.copy(selectedSort = sort).withFilteredContacts()
    }

    private fun ContactListUiState.withContacts(
        allContacts: List<ContactEntity>,
        isLoading: Boolean = this.isLoading,
        isRefreshing: Boolean = this.isRefreshing,
        syncError: String? = this.syncError,
    ): ContactListUiState {
        return copy(
            allContacts = allContacts,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            syncError = syncError,
        ).withFilteredContacts()
    }

    private fun ContactListUiState.withFilteredContacts(): ContactListUiState {
        val normalizedQuery = searchQuery.trim()
        val filtered = allContacts
            .asSequence()
            .filter { contact ->
                normalizedQuery.isBlank() ||
                    contact.name.contains(normalizedQuery, ignoreCase = true) ||
                    contact.nickname?.contains(normalizedQuery, ignoreCase = true) == true ||
                    contact.company?.contains(normalizedQuery, ignoreCase = true) == true ||
                    contact.relationshipType.contains(normalizedQuery, ignoreCase = true)
            }
            .filter { contact -> contact.matchesFilter(selectedFilter) }
            .let { sequence ->
                when (selectedSort) {
                    ContactSort.NAME_ASC -> sequence.sortedBy { it.name.lowercase() }
                    ContactSort.HEALTH_DESC -> sequence.sortedWith(
                        compareByDescending<ContactEntity> { it.healthScore }.thenBy { it.name.lowercase() }
                    )
                    ContactSort.HEALTH_ASC -> sequence.sortedWith(
                        compareBy<ContactEntity> { it.healthScore }.thenBy { it.name.lowercase() }
                    )
                }
            }
            .toList()
        return copy(contacts = filtered)
    }

    private fun ContactEntity.matchesFilter(filter: ContactFilter): Boolean {
        return when (filter) {
            ContactFilter.ALL -> true
            ContactFilter.FAMILY -> contactGroup.equals("Family", ignoreCase = true) ||
                relationshipType.equals("FAMILY", ignoreCase = true)
            ContactFilter.FRIENDS -> contactGroup.equals("Friends", ignoreCase = true) ||
                relationshipType.equals("FRIEND", ignoreCase = true)
            ContactFilter.WORK -> contactGroup.equals("Work", ignoreCase = true) ||
                relationshipType.equals("WORK", ignoreCase = true)
            ContactFilter.CLOSE_FRIENDS -> contactGroup.equals("Close Friends", ignoreCase = true) ||
                relationshipType.equals("CLOSE_FRIEND", ignoreCase = true)
        }
    }
}
