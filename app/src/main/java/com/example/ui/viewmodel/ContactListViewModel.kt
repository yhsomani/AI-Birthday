package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactListItem
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ContactFilter {
    ALL,
    FAMILY,
    FRIENDS,
    WORK,
    CLOSE_FRIENDS,
    NEEDS_PERSONALIZATION,
    MISSING_RELATIONSHIP,
    MISSING_CHANNEL,
    LOW_HEALTH,
    VIP,
}

enum class ContactSort {
    NAME_ASC,
    HEALTH_DESC,
    HEALTH_ASC,
}

enum class ContactQualityStatus {
    READY,
    MISSING_EVENT,
    MISSING_CHANNEL,
    MISSING_CONTEXT,
}

data class ContactQualityState(
    val status: ContactQualityStatus,
    val hasKnownEvent: Boolean,
    val hasReachableChannel: Boolean,
    val hasPersonalizationContext: Boolean,
)

data class ContactListUiState(
    val allContacts: List<ContactListItem> = emptyList(),
    val contacts: List<ContactListItem> = emptyList(),
    val contactQuality: Map<String, ContactQualityState> = emptyMap(),
    val searchQuery: String = "",
    val selectedFilter: ContactFilter = ContactFilter.ALL,
    val selectedSort: ContactSort = ContactSort.NAME_ASC,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val syncError: String? = null,
)

@HiltViewModel
class ContactListViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val contactRepository: ContactRepository,
    private val preferencesRepository: com.example.domain.service.PreferencesRepository,
    private val syncContactsUseCase: SyncContactsUseCase,
) : ViewModel() {
    private companion object {
        const val TAG = "ContactListViewModel"
        const val PERSONALIZATION_CONFIDENCE_THRESHOLD = 0.6
        const val LOW_HEALTH_THRESHOLD = 50
    }

    private val _uiState = MutableStateFlow(ContactListUiState())
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                combine(
                    contactRepository.getContactListItems(),
                    preferencesRepository.observeChanges().onStart { emit(Unit) },
                ) { contacts, _ -> contacts }
                    .collect { contacts ->
                        val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
                        _uiState.value = _uiState.value.withContacts(
                            allContacts = contacts,
                            isLoading = false,
                            syncError = lastError,
                        )
                    }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Contact collection failed", e)
                val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
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
                
                contactRepository.getContactListItems().first().let { contacts ->
                    val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
                    _uiState.value = _uiState.value.withContacts(
                        allContacts = contacts,
                        isLoading = false,
                        isRefreshing = false,
                        syncError = lastError,
                    )
                }
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Contact refresh failed", e)
                val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    syncError = lastError ?: appContext.getString(R.string.contact_list_sync_failed)
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
        allContacts: List<ContactListItem>,
        isLoading: Boolean = this.isLoading,
        isRefreshing: Boolean = this.isRefreshing,
        syncError: String? = this.syncError,
    ): ContactListUiState {
        return copy(
            allContacts = allContacts,
            contactQuality = allContacts.associate { contact -> contact.id.value to contact.qualityState() },
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
                    contact.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    contact.nickname?.contains(normalizedQuery, ignoreCase = true) == true ||
                    contact.company?.contains(normalizedQuery, ignoreCase = true) == true ||
                    contact.relationshipType.contains(normalizedQuery, ignoreCase = true)
            }
            .filter { contact -> contact.matchesFilter(selectedFilter) }
            .let { sequence ->
                when (selectedSort) {
                    ContactSort.NAME_ASC -> sequence.sortedBy { it.displayName.lowercase() }
                    ContactSort.HEALTH_DESC -> sequence.sortedWith(
                        compareByDescending<ContactListItem> { it.healthScore }.thenBy { it.displayName.lowercase() }
                    )
                    ContactSort.HEALTH_ASC -> sequence.sortedWith(
                        compareBy<ContactListItem> { it.healthScore }.thenBy { it.displayName.lowercase() }
                    )
                }
            }
            .toList()
        return copy(contacts = filtered)
    }

    private fun ContactListItem.matchesFilter(filter: ContactFilter): Boolean {
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
            ContactFilter.NEEDS_PERSONALIZATION -> needsPersonalization()
            ContactFilter.MISSING_RELATIONSHIP -> missingRelationship()
            ContactFilter.MISSING_CHANNEL -> !hasReachablePreferredChannel()
            ContactFilter.LOW_HEALTH -> healthScore < LOW_HEALTH_THRESHOLD
            ContactFilter.VIP -> automationMode == ApprovalMode.VIP_APPROVE
        }
    }

    private fun ContactListItem.missingRelationship(): Boolean {
        return relationshipType.isBlank() || relationshipType.equals("UNKNOWN", ignoreCase = true)
    }

    private fun ContactListItem.needsPersonalization(): Boolean {
        return nickname.isNullOrBlank() &&
            notesText.isBlank() &&
            !hasJsonArrayContent(interestsJson) &&
            !hasJsonArrayContent(sharedHistoryJson) &&
            classificationConfidence < PERSONALIZATION_CONFIDENCE_THRESHOLD
    }

    private fun ContactListItem.qualityState(): ContactQualityState {
        val hasKnownEvent = hasDatedEvent()
        val hasReachableChannel = hasReachablePreferredChannel()
        val hasPersonalizationContext = !needsPersonalization()
        val status = when {
            !hasKnownEvent -> ContactQualityStatus.MISSING_EVENT
            !hasReachableChannel -> ContactQualityStatus.MISSING_CHANNEL
            !hasPersonalizationContext -> ContactQualityStatus.MISSING_CONTEXT
            else -> ContactQualityStatus.READY
        }

        return ContactQualityState(
            status = status,
            hasKnownEvent = hasKnownEvent,
            hasReachableChannel = hasReachableChannel,
            hasPersonalizationContext = hasPersonalizationContext,
        )
    }

    private fun ContactListItem.hasDatedEvent(): Boolean {
        return hasCompleteDate(birthdayDay, birthdayMonth) ||
            hasCompleteDate(anniversaryDay, anniversaryMonth) ||
            hasCompleteDate(workStartDay, workStartMonth)
    }

    private fun hasCompleteDate(day: Int?, month: Int?): Boolean {
        return day != null && month != null
    }

    private fun ContactListItem.hasReachablePreferredChannel(): Boolean {
        return when (preferredChannel) {
            MessageChannel.SMS,
            MessageChannel.WHATSAPP -> hasPhone()
            MessageChannel.EMAIL -> !primaryEmail.isNullOrBlank()
            MessageChannel.UNKNOWN -> hasPhone() || !primaryEmail.isNullOrBlank()
        }
    }

    private fun ContactListItem.hasPhone(): Boolean {
        return !primaryPhone.isNullOrBlank() || !secondaryPhone.isNullOrBlank()
    }

    private fun hasJsonArrayContent(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotBlank() && trimmed != "[]"
    }
}
