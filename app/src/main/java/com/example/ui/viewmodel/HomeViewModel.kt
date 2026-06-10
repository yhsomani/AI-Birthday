package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GetDashboardMetricsUseCase
import com.example.domain.usecase.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class UpcomingBirthday(
    val name: String,
    val date: String,
)

data class RelationshipPlannerItem(
    val title: String,
    val detail: String,
    val contactId: String? = null,
)

data class HomeUiState(
    val userName: String = "User",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val healthScore: Int = 0,
    val pendingCount: Int = 0,
    val upcomingEventsCount: Int = 0,
    val contactCount: Int = 0,
    val sentCount: Int = 0,
    val upcomingBirthdays: List<UpcomingBirthday> = emptyList(),
    val isLoading: Boolean = true,
    val syncError: String? = null,
    val readinessTitle: String? = null,
    val readinessDetail: String? = null,
    val plannerItems: List<RelationshipPlannerItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboardMetricsUseCase: GetDashboardMetricsUseCase,
    private val authManager: AuthManager,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val preferencesRepository: com.example.domain.service.PreferencesRepository,
) : ViewModel() {
    private companion object {
        const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.userProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userName = profile.displayName,
                    userEmail = profile.email,
                    userPhotoUrl = profile.photoUrl,
                )
            }
        }
        loadMetrics()
    }

    fun loadMetrics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val lastError = preferencesRepository.getLastSyncError()
                var metrics = getDashboardMetricsUseCase()
                if (metrics.contactCount == 0) {
                    try {
                        syncContactsUseCase()
                        metrics = getDashboardMetricsUseCase()
                    } catch (e: Exception) {
                        // Ignore sync failures during automatic launch
                    }
                }
                val events = eventRepository.getUpcoming(30)
                val atRiskContacts = contactRepository.getBottomByHealthScore(3)
                    .filter { it.healthScore < 50 }
                val birthdayEvents = events.filter { it.type == "BIRTHDAY" }
                    .sortedBy { it.daysUntil }
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                val birthdays = birthdayEvents.map { event ->
                    UpcomingBirthday(
                        name = event.label ?: event.contactId,
                        date = dateFormat.format(Date(event.nextOccurrenceMs)),
                    )
                }
                val profile = authManager.userProfile.value
                val freshError = preferencesRepository.getLastSyncError()
                _uiState.value = HomeUiState(
                    userName = profile.displayName,
                    userEmail = profile.email,
                    userPhotoUrl = profile.photoUrl,
                    healthScore = metrics.healthScore,
                    pendingCount = metrics.pendingCount,
                    upcomingEventsCount = metrics.upcomingEventsCount,
                    contactCount = metrics.contactCount,
                    sentCount = metrics.sentCount,
                    upcomingBirthdays = birthdays,
                    plannerItems = buildPlannerItems(atRiskContacts, metrics.pendingCount, events),
                    isLoading = false,
                    syncError = freshError ?: lastError,
                ).withReadiness()
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Dashboard metrics load failed", e)
                val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
                _uiState.value = _uiState.value.copy(isLoading = false, syncError = lastError)
            }
        }
    }

    private fun buildPlannerItems(
        atRiskContacts: List<com.example.core.db.entities.ContactEntity>,
        pendingCount: Int,
        upcomingEvents: List<EventEntity>,
    ): List<RelationshipPlannerItem> {
        val items = mutableListOf<RelationshipPlannerItem>()
        if (pendingCount > 0) {
            items += RelationshipPlannerItem(
                title = "Review pending wishes",
                detail = "$pendingCount approval(s) are waiting before send time.",
            )
        }
        atRiskContacts.forEach { contact ->
            items += RelationshipPlannerItem(
                title = "Reconnect with ${contact.name}",
                detail = "Relationship health is ${contact.healthScore}. Add a memory or generate a warm check-in.",
                contactId = contact.id,
            )
        }
        upcomingEvents.take(2).forEach { event ->
            items += RelationshipPlannerItem(
                title = event.label ?: event.type.replace("_", " "),
                detail = "Upcoming in ${event.daysUntil} day(s). Check personalization before the wish is generated.",
                contactId = event.contactId,
            )
        }
        return items.take(5)
    }

    private fun HomeUiState.withReadiness(): HomeUiState {
        return when {
            syncError != null -> copy(
                readinessTitle = "Setup needs attention",
                readinessDetail = "Contact sync is reporting an issue. Open AI Doctor for the exact fix.",
            )
            contactCount == 0 -> copy(
                readinessTitle = "Sync contacts to start",
                readinessDetail = "RelateAI needs contacts before it can discover events or personalize wishes.",
            )
            pendingCount > 0 -> copy(
                readinessTitle = "Approvals waiting",
                readinessDetail = "$pendingCount message(s) need review before they can send.",
            )
            else -> copy(readinessTitle = null, readinessDetail = null)
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
