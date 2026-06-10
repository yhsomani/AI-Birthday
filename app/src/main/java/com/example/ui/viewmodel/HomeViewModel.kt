package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.StructuredLogger
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboardMetricsUseCase: GetDashboardMetricsUseCase,
    private val authManager: AuthManager,
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
                    isLoading = false,
                    syncError = freshError ?: lastError,
                )
            } catch (e: Exception) {
                StructuredLogger.e(TAG, "Dashboard metrics load failed", e)
                val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
                _uiState.value = _uiState.value.copy(isLoading = false, syncError = lastError)
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
