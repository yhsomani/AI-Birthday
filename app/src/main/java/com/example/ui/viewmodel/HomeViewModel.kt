package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.auth.AuthManager
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.EventType
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.GetDashboardMetricsUseCase
import com.example.domain.usecase.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val actionTarget: HomeActionTarget,
)

enum class BackupFreshnessStatus {
    NEVER_BACKED_UP,
    STALE,
}

data class BackupFreshnessPrompt(
    val status: BackupFreshnessStatus,
    val daysSinceBackup: Long? = null,
)

enum class HomeNextActionKind {
    SYNC_CONTACTS,
    FIX_CONTACT_SYNC,
    CONNECT_AI,
    ENABLE_AI_GENERATION,
    REVIEW_PENDING,
    CREATE_BACKUP,
    REFRESH_BACKUP,
    RECONNECT_CONTACT,
}

data class HomeNextAction(
    val kind: HomeNextActionKind,
    val actionTarget: HomeActionTarget,
    val count: Int = 0,
    val daysSinceBackup: Long? = null,
    val contactName: String? = null,
    val healthScore: Int? = null,
)

sealed interface HomeActionTarget {
    data object AutomationSetup : HomeActionTarget
    data object BackupRestore : HomeActionTarget
    data object Messages : HomeActionTarget
    data class ContactDetail(val contactId: String) : HomeActionTarget
}

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
    val setupProgress: SetupProgressSummary = SetupProgressSummary(),
    val plannerItems: List<RelationshipPlannerItem> = emptyList(),
    val backupPrompt: BackupFreshnessPrompt? = null,
    val primaryAction: HomeNextAction? = null,
    val supportingActions: List<HomeNextAction> = emptyList(),
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
        const val STALE_BACKUP_DAYS = 30L
        const val DAY_MS = 24L * 60 * 60 * 1000L
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
                val birthdayEvents = events.filter { EventType.fromRaw(it.type) == EventType.BIRTHDAY }
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
                val aiGenerationEnabled = readBooleanPreference {
                    preferencesRepository.isAiWishGenerationEnabled()
                }
                val hasAiAccess = readStringPreference { preferencesRepository.getGeminiApiKey() }.isNotBlank()
                val lastBackupMs = readLongPreference { preferencesRepository.getLastBackupMs() }
                val setupProgress = buildHomeSetupProgressSummary(
                    contactCount = metrics.contactCount,
                    syncError = freshError ?: lastError,
                    aiGenerationEnabled = aiGenerationEnabled,
                    hasAiAccess = hasAiAccess,
                    pendingCount = metrics.pendingCount,
                )
                val backupPrompt = buildBackupFreshnessPrompt(lastBackupMs)
                val rankedActions = buildRankedNextActions(
                    contactCount = metrics.contactCount,
                    syncError = freshError ?: lastError,
                    aiGenerationEnabled = aiGenerationEnabled,
                    hasAiAccess = hasAiAccess,
                    pendingCount = metrics.pendingCount,
                    backupPrompt = backupPrompt,
                    atRiskContacts = atRiskContacts,
                )
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
                    plannerItems = buildPlannerItems(
                        atRiskContacts = atRiskContacts.drop(1),
                        upcomingEvents = events,
                    ),
                    isLoading = false,
                    syncError = freshError ?: lastError,
                    setupProgress = setupProgress,
                    backupPrompt = backupPrompt,
                    primaryAction = rankedActions.firstOrNull(),
                    supportingActions = rankedActions.drop(1).take(3),
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
        upcomingEvents: List<EventEntity>,
    ): List<RelationshipPlannerItem> {
        val items = mutableListOf<RelationshipPlannerItem>()
        atRiskContacts.forEach { contact ->
            items += RelationshipPlannerItem(
                title = string(R.string.home_next_action_reconnect_title, contact.name),
                detail = string(R.string.home_planner_reconnect_detail, contact.healthScore),
                actionTarget = HomeActionTarget.ContactDetail(contact.id),
            )
        }
        upcomingEvents.take(2).forEach { event ->
            items += RelationshipPlannerItem(
                title = event.label ?: event.type.toDisplayLabel(),
                detail = string(R.string.home_planner_upcoming_detail, event.daysUntil),
                actionTarget = HomeActionTarget.ContactDetail(event.contactId),
            )
        }
        return items.take(5)
    }

    private fun buildBackupFreshnessPrompt(lastBackupMs: Long): BackupFreshnessPrompt? {
        if (lastBackupMs <= 0L) {
            return BackupFreshnessPrompt(status = BackupFreshnessStatus.NEVER_BACKED_UP)
        }
        val daysSinceBackup = ((System.currentTimeMillis() - lastBackupMs).coerceAtLeast(0L)) / DAY_MS
        return if (daysSinceBackup >= STALE_BACKUP_DAYS) {
            BackupFreshnessPrompt(
                status = BackupFreshnessStatus.STALE,
                daysSinceBackup = daysSinceBackup,
            )
        } else {
            null
        }
    }

    private fun buildRankedNextActions(
        contactCount: Int,
        syncError: String?,
        aiGenerationEnabled: Boolean,
        hasAiAccess: Boolean,
        pendingCount: Int,
        backupPrompt: BackupFreshnessPrompt?,
        atRiskContacts: List<com.example.core.db.entities.ContactEntity>,
    ): List<HomeNextAction> {
        val rankedActions = mutableListOf<Pair<Int, HomeNextAction>>()
        val contactSetupAction = when {
            syncError != null -> HomeNextAction(
                kind = HomeNextActionKind.FIX_CONTACT_SYNC,
                actionTarget = HomeActionTarget.AutomationSetup,
            )
            contactCount == 0 -> HomeNextAction(
                kind = HomeNextActionKind.SYNC_CONTACTS,
                actionTarget = HomeActionTarget.AutomationSetup,
            )
            else -> null
        }
        contactSetupAction?.let { rankedActions += 100 to it }
        if (pendingCount > 0) {
            rankedActions += 90 to HomeNextAction(
                kind = HomeNextActionKind.REVIEW_PENDING,
                actionTarget = HomeActionTarget.Messages,
                count = pendingCount,
            )
        }
        if (!hasAiAccess) {
            rankedActions += 80 to HomeNextAction(
                kind = HomeNextActionKind.CONNECT_AI,
                actionTarget = HomeActionTarget.AutomationSetup,
            )
        }
        if (!aiGenerationEnabled) {
            rankedActions += 75 to HomeNextAction(
                kind = HomeNextActionKind.ENABLE_AI_GENERATION,
                actionTarget = HomeActionTarget.AutomationSetup,
            )
        }
        when (backupPrompt?.status) {
            BackupFreshnessStatus.NEVER_BACKED_UP -> {
                rankedActions += 70 to HomeNextAction(
                    kind = HomeNextActionKind.CREATE_BACKUP,
                    actionTarget = HomeActionTarget.BackupRestore,
                )
            }
            BackupFreshnessStatus.STALE -> {
                rankedActions += 60 to HomeNextAction(
                    kind = HomeNextActionKind.REFRESH_BACKUP,
                    actionTarget = HomeActionTarget.BackupRestore,
                    daysSinceBackup = backupPrompt.daysSinceBackup,
                )
            }
            null -> Unit
        }
        atRiskContacts.firstOrNull()?.let { contact ->
            rankedActions += 50 to HomeNextAction(
                kind = HomeNextActionKind.RECONNECT_CONTACT,
                actionTarget = HomeActionTarget.ContactDetail(contact.id),
                contactName = contact.name,
                healthScore = contact.healthScore,
            )
        }
        return rankedActions
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun HomeUiState.withReadiness(): HomeUiState {
        return when {
            syncError != null -> copy(
                readinessTitle = string(R.string.home_readiness_setup_attention_title),
                readinessDetail = string(R.string.home_next_action_fix_contact_sync_detail),
                readinessAction = HomeActionTarget.AutomationSetup,
            )
            contactCount == 0 -> copy(
                readinessTitle = string(R.string.home_next_action_sync_contacts_title),
                readinessDetail = string(R.string.home_next_action_sync_contacts_detail),
                readinessAction = HomeActionTarget.AutomationSetup,
            )
            pendingCount > 0 -> copy(
                readinessTitle = string(R.string.home_readiness_approvals_waiting_title),
                readinessDetail = string(R.string.home_next_action_review_pending_detail, pendingCount),
                readinessAction = HomeActionTarget.Messages,
            )
            else -> copy(
                readinessTitle = null,
                readinessDetail = null,
                readinessAction = null,
            )
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

    private fun String.toDisplayLabel(): String {
        return when (EventType.fromRaw(this)) {
            EventType.BIRTHDAY -> string(R.string.event_type_birthday)
            EventType.ANNIVERSARY -> string(R.string.event_type_anniversary)
            EventType.WORK_ANNIVERSARY -> string(R.string.event_type_work_anniversary)
            EventType.CUSTOM -> string(R.string.event_type_custom)
            else -> replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }
}
