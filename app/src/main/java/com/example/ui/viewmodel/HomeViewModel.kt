package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.resilience.StructuredLogger
import com.example.domain.home.BackupFreshnessPrompt as DomainBackupFreshnessPrompt
import com.example.domain.home.BackupFreshnessStatus as DomainBackupFreshnessStatus
import com.example.domain.home.HomeNextActionKind as DomainHomeNextActionKind
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.occasion.UpcomingEventPreview
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GetDashboardMetricsUseCase
import com.example.domain.usecase.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias BackupFreshnessStatus = DomainBackupFreshnessStatus
typealias BackupFreshnessPrompt = DomainBackupFreshnessPrompt
typealias HomeNextActionKind = DomainHomeNextActionKind

data class UpcomingBirthday(
    val name: String,
    val date: String,
)

data class RelationshipPlannerItem(
    val title: String,
    val detail: String,
    val actionTarget: HomeActionTarget,
)

sealed interface HomeActionTarget {
    data object AutomationSetup : HomeActionTarget
    data object BackupRestore : HomeActionTarget
    data object Messages : HomeActionTarget
    data class ContactDetail(val contactId: String) : HomeActionTarget
}

data class HomeNextAction(
    val kind: HomeNextActionKind,
    val actionTarget: HomeActionTarget,
    val count: Int = 0,
    val daysSinceBackup: Long? = null,
    val contactName: String? = null,
    val healthScore: Int? = null,
    val actionReadiness: RelationshipActionReadiness = RelationshipActionReadinessPolicy.fromHomeNextAction(
        kind = kind,
        relatedContactId = (actionTarget as? HomeActionTarget.ContactDetail)?.contactId,
    ),
)

data class HomeUiState(
    val userName: String = "",
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
    val readinessAction: HomeActionTarget? = null,
    val readinessActionReadiness: RelationshipActionReadiness? = null,
    val setupProgress: SetupProgressSummary = SetupProgressSummary(),
    val plannerItems: List<RelationshipPlannerItem> = emptyList(),
    val backupPrompt: BackupFreshnessPrompt? = null,
    val primaryAction: HomeNextAction? = null,
    val supportingActions: List<HomeNextAction> = emptyList(),
)

internal data class HomeDashboardSnapshot(
    val metrics: GetDashboardMetricsUseCase.DashboardMetrics,
    val events: List<UpcomingEventPreview>,
    val atRiskContacts: List<ContactAnalyticsSummary>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
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
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasAttemptedInitialSync = false

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
        observeMetrics()
    }

    fun loadMetrics() {
        hasAttemptedInitialSync = false
        _uiState.value = _uiState.value.copy(isLoading = true)
        manualRefresh.tryEmit(Unit)
    }

    private fun observeMetrics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            combine(
                getDashboardMetricsUseCase.observe(),
                eventRepository.getUpcomingPreviewsFlow(30),
                contactRepository.getBottomHealthSummariesFlow(3),
                merge(
                    preferencesRepository.observeChanges(),
                    manualRefresh,
                ).onStart { emit(Unit) },
            ) { metrics, events, atRiskContacts, _ ->
                HomeDashboardSnapshot(
                    metrics = metrics,
                    events = events,
                    atRiskContacts = atRiskContacts.filter { it.healthScore < 50 },
                )
            }
                .catch { e ->
                    StructuredLogger.e(TAG, "Dashboard metrics load failed", e)
                    val lastError = try { preferencesRepository.getLastSyncError() } catch (ex: Exception) { null }
                    _uiState.value = _uiState.value.copy(isLoading = false, syncError = lastError)
                }
                .collect { snapshot ->
                    maybeRunInitialSync(snapshot.metrics)
                    _uiState.value = snapshot.toHomeUiState(
                        appContext = appContext,
                        preferencesRepository = preferencesRepository,
                        profile = authManager.userProfile.value,
                    )
                }
        }
    }

    private fun maybeRunInitialSync(metrics: GetDashboardMetricsUseCase.DashboardMetrics) {
        if (metrics.contactCount != 0 || hasAttemptedInitialSync) return
        hasAttemptedInitialSync = true
        viewModelScope.launch {
            try {
                syncContactsUseCase()
            } catch (e: Exception) {
                // Sync failures are surfaced through the persisted sync-error state.
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
