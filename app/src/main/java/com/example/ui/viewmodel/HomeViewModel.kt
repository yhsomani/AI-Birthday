package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.auth.AuthManager
import com.example.core.resilience.StructuredLogger
import com.example.domain.home.BackupFreshnessPrompt as DomainBackupFreshnessPrompt
import com.example.domain.home.BackupFreshnessStatus as DomainBackupFreshnessStatus
import com.example.domain.home.HomeNextActionCandidate
import com.example.domain.home.HomeNextActionKind as DomainHomeNextActionKind
import com.example.domain.home.HomeNextActionPolicy
import com.example.domain.home.HomeNextActionTargetKind
import com.example.domain.home.HomeReadinessBannerCandidate
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.occasion.UpcomingEventPreview
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.readiness.RelationshipReadinessReason
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private data class HomeDashboardSnapshot(
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
                    _uiState.value = snapshot.toUiState()
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

    private fun HomeDashboardSnapshot.toUiState(): HomeUiState {
        val birthdayEvents = events.filter { it.type == OccasionType.BIRTHDAY }
            .sortedBy { it.daysUntil }
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        val birthdays = birthdayEvents.map { event ->
            UpcomingBirthday(
                name = event.label ?: event.contactId.value,
                date = dateFormat.format(Date(event.nextOccurrenceMs)),
            )
        }
        val profile = authManager.userProfile.value
        val syncError = readPreference<String?>(null) { preferencesRepository.getLastSyncError() }
        val aiGenerationEnabled = readBooleanPreference {
            preferencesRepository.isAiWishGenerationEnabled()
        }
        val hasAiAccess = readStringPreference { preferencesRepository.getGeminiApiKey() }.isNotBlank()
        val lastBackupMs = readLongPreference { preferencesRepository.getLastBackupMs() }
        val setupProgress = buildHomeSetupProgressSummary(
            contactCount = metrics.contactCount,
            syncError = syncError,
            aiGenerationEnabled = aiGenerationEnabled,
            hasAiAccess = hasAiAccess,
            pendingCount = metrics.pendingCount,
        )
        val backupPrompt = buildBackupFreshnessPrompt(lastBackupMs)
        val rankedActions = buildRankedNextActions(
            contactCount = metrics.contactCount,
            syncError = syncError,
            aiGenerationEnabled = aiGenerationEnabled,
            hasAiAccess = hasAiAccess,
            pendingCount = metrics.pendingCount,
            backupPrompt = backupPrompt,
            atRiskContacts = atRiskContacts,
        )
        return HomeUiState(
            userName = profile.displayName,
            userEmail = profile.email,
            userPhotoUrl = profile.photoUrl,
            healthScore = metrics.healthScore,
            pendingCount = metrics.pendingCount,
            upcomingEventsCount = metrics.upcomingEventsCount,
            contactCount = metrics.contactCount,
            sentCount = metrics.sentCount,
            upcomingBirthdays = birthdays,
            plannerItems = buildPlannerItems(
                atRiskContacts = atRiskContacts.drop(1),
                upcomingEvents = events,
            ),
            isLoading = false,
            syncError = syncError,
            setupProgress = setupProgress,
            backupPrompt = backupPrompt,
            primaryAction = rankedActions.firstOrNull(),
            supportingActions = rankedActions.drop(1).take(3),
        ).withReadiness()
    }

    private fun buildPlannerItems(
        atRiskContacts: List<ContactAnalyticsSummary>,
        upcomingEvents: List<UpcomingEventPreview>,
    ): List<RelationshipPlannerItem> {
        val items = mutableListOf<RelationshipPlannerItem>()
        atRiskContacts.forEach { contact ->
            items += RelationshipPlannerItem(
                title = string(R.string.home_next_action_reconnect_title, contact.displayName),
                detail = string(R.string.home_planner_reconnect_detail, contact.healthScore),
                actionTarget = HomeActionTarget.ContactDetail(contact.id.value),
            )
        }
        upcomingEvents.take(2).forEach { event ->
            items += RelationshipPlannerItem(
                title = event.label ?: event.type.toDisplayLabel(),
                detail = string(R.string.home_planner_upcoming_detail, event.daysUntil),
                actionTarget = HomeActionTarget.ContactDetail(event.contactId.value),
            )
        }
        return items.take(5)
    }

    private fun buildBackupFreshnessPrompt(lastBackupMs: Long): BackupFreshnessPrompt? {
        return HomeNextActionPolicy.backupFreshnessPrompt(
            lastBackupMs = lastBackupMs,
            nowMs = System.currentTimeMillis(),
        )
    }

    private fun buildRankedNextActions(
        contactCount: Int,
        syncError: String?,
        aiGenerationEnabled: Boolean,
        hasAiAccess: Boolean,
        pendingCount: Int,
        backupPrompt: BackupFreshnessPrompt?,
        atRiskContacts: List<ContactAnalyticsSummary>,
    ): List<HomeNextAction> {
        return HomeNextActionPolicy.rankNextActions(
            contactCount = contactCount,
            syncError = syncError,
            aiGenerationEnabled = aiGenerationEnabled,
            hasAiAccess = hasAiAccess,
            pendingCount = pendingCount,
            backupPrompt = backupPrompt,
            atRiskContacts = atRiskContacts,
        ).map { it.toUiAction() }
    }

    private fun HomeUiState.withReadiness(): HomeUiState {
        val banner = HomeNextActionPolicy.readinessBanner(
            contactCount = contactCount,
            syncError = syncError,
            pendingCount = pendingCount,
        ) ?: return copy(
            readinessTitle = null,
            readinessDetail = null,
            readinessAction = null,
            readinessActionReadiness = null,
        )
        val readiness = RelationshipActionReadinessPolicy.fromHomeReadinessBanner(banner)
        return when (readiness.primaryReason) {
            RelationshipReadinessReason.CONTACT_SYNC_FAILED -> copy(
                readinessTitle = string(R.string.home_readiness_setup_attention_title),
                readinessDetail = string(R.string.home_next_action_fix_contact_sync_detail),
                readinessAction = banner.toUiActionTarget(),
                readinessActionReadiness = readiness,
            )
            RelationshipReadinessReason.CONTACTS_MISSING -> copy(
                readinessTitle = string(R.string.home_next_action_sync_contacts_title),
                readinessDetail = string(R.string.home_next_action_sync_contacts_detail),
                readinessAction = banner.toUiActionTarget(),
                readinessActionReadiness = readiness,
            )
            RelationshipReadinessReason.PENDING_MESSAGES -> copy(
                readinessTitle = string(R.string.home_readiness_approvals_waiting_title),
                readinessDetail = string(R.string.home_next_action_review_pending_detail, banner.count),
                readinessAction = banner.toUiActionTarget(),
                readinessActionReadiness = readiness,
            )
            else -> copy(
                readinessTitle = null,
                readinessDetail = null,
                readinessAction = null,
                readinessActionReadiness = null,
            )
        }
    }

    private fun HomeReadinessBannerCandidate.toUiActionTarget(): HomeActionTarget {
        return when (targetKind) {
            HomeNextActionTargetKind.AUTOMATION_SETUP -> HomeActionTarget.AutomationSetup
            HomeNextActionTargetKind.BACKUP_RESTORE -> HomeActionTarget.BackupRestore
            HomeNextActionTargetKind.MESSAGES -> HomeActionTarget.Messages
            HomeNextActionTargetKind.CONTACT_DETAIL -> error("Home readiness banner cannot target a contact without an id")
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

    private fun <T> readPreference(fallback: T, read: () -> T): T {
        return try {
            read()
        } catch (e: Exception) {
            fallback
        }
    }

    private fun readStringPreference(read: () -> String): String = readPreference("", read)

    private fun readBooleanPreference(read: () -> Boolean): Boolean = readPreference(false, read)

    private fun readLongPreference(read: () -> Long): Long = readPreference(0L, read)

    private fun OccasionType.toDisplayLabel(): String {
        return when (this) {
            OccasionType.BIRTHDAY -> string(R.string.event_type_birthday)
            OccasionType.ANNIVERSARY -> string(R.string.event_type_anniversary)
            OccasionType.WORK_ANNIVERSARY -> string(R.string.event_type_work_anniversary)
            OccasionType.GRADUATION -> string(R.string.event_type_graduation)
            OccasionType.HOLIDAY -> string(R.string.event_type_holiday)
            OccasionType.REVIVAL -> string(R.string.event_type_revival)
            OccasionType.FOLLOW_UP -> string(R.string.event_type_follow_up)
            OccasionType.CUSTOM -> string(R.string.event_type_custom)
            else -> raw.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }

    private fun HomeNextActionCandidate.toUiAction(): HomeNextAction {
        return HomeNextAction(
            kind = kind,
            actionTarget = when (targetKind) {
                HomeNextActionTargetKind.AUTOMATION_SETUP -> HomeActionTarget.AutomationSetup
                HomeNextActionTargetKind.BACKUP_RESTORE -> HomeActionTarget.BackupRestore
                HomeNextActionTargetKind.MESSAGES -> HomeActionTarget.Messages
                HomeNextActionTargetKind.CONTACT_DETAIL -> HomeActionTarget.ContactDetail(requireNotNull(contactId))
            },
            count = count,
            daysSinceBackup = daysSinceBackup,
            contactName = contactName,
            healthScore = healthScore,
            actionReadiness = RelationshipActionReadinessPolicy.fromHomeNextActionCandidate(this),
        )
    }
}
