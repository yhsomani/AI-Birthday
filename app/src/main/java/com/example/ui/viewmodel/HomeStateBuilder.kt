package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.core.auth.UserProfile
import com.example.domain.home.HomeNextActionCandidate
import com.example.domain.home.HomeNextActionPolicy
import com.example.domain.home.HomeNextActionTargetKind
import com.example.domain.home.HomeReadinessBannerCandidate
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.occasion.UpcomingEventPreview
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.readiness.RelationshipReadinessReason
import com.example.domain.service.PreferencesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun HomeDashboardSnapshot.toHomeUiState(
    appContext: Context,
    preferencesRepository: PreferencesRepository,
    profile: UserProfile,
): HomeUiState {
    val birthdayEvents = events.filter { it.type == OccasionType.BIRTHDAY }
        .sortedBy { it.daysUntil }
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    val birthdays = birthdayEvents.map { event ->
        UpcomingBirthday(
            name = event.label ?: event.contactId.value,
            date = dateFormat.format(Date(event.nextOccurrenceMs)),
        )
    }
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
            appContext = appContext,
            atRiskContacts = atRiskContacts.drop(1),
            upcomingEvents = events,
        ),
        isLoading = false,
        syncError = syncError,
        setupProgress = setupProgress,
        backupPrompt = backupPrompt,
        primaryAction = rankedActions.firstOrNull(),
        supportingActions = rankedActions.drop(1).take(3),
    ).withReadiness(appContext)
}

private fun buildPlannerItems(
    appContext: Context,
    atRiskContacts: List<ContactAnalyticsSummary>,
    upcomingEvents: List<UpcomingEventPreview>,
): List<RelationshipPlannerItem> {
    val items = mutableListOf<RelationshipPlannerItem>()
    atRiskContacts.forEach { contact ->
        items += RelationshipPlannerItem(
            title = appContext.string(R.string.home_next_action_reconnect_title, contact.displayName),
            detail = appContext.string(R.string.home_planner_reconnect_detail, contact.healthScore),
            actionTarget = HomeActionTarget.ContactDetail(contact.id.value),
        )
    }
    upcomingEvents.take(2).forEach { event ->
        items += RelationshipPlannerItem(
            title = event.label ?: event.type.toDisplayLabel(appContext),
            detail = appContext.string(R.string.home_planner_upcoming_detail, event.daysUntil),
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

private fun HomeUiState.withReadiness(appContext: Context): HomeUiState {
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
            readinessTitle = appContext.string(R.string.home_readiness_setup_attention_title),
            readinessDetail = appContext.string(R.string.home_next_action_fix_contact_sync_detail),
            readinessAction = banner.toUiActionTarget(),
            readinessActionReadiness = readiness,
        )
        RelationshipReadinessReason.CONTACTS_MISSING -> copy(
            readinessTitle = appContext.string(R.string.home_next_action_sync_contacts_title),
            readinessDetail = appContext.string(R.string.home_next_action_sync_contacts_detail),
            readinessAction = banner.toUiActionTarget(),
            readinessActionReadiness = readiness,
        )
        RelationshipReadinessReason.PENDING_MESSAGES -> copy(
            readinessTitle = appContext.string(R.string.home_readiness_approvals_waiting_title),
            readinessDetail = appContext.string(R.string.home_next_action_review_pending_detail, banner.count),
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

private fun OccasionType.toDisplayLabel(appContext: Context): String {
    return when (this) {
        OccasionType.BIRTHDAY -> appContext.string(R.string.event_type_birthday)
        OccasionType.ANNIVERSARY -> appContext.string(R.string.event_type_anniversary)
        OccasionType.WORK_ANNIVERSARY -> appContext.string(R.string.event_type_work_anniversary)
        OccasionType.GRADUATION -> appContext.string(R.string.event_type_graduation)
        OccasionType.HOLIDAY -> appContext.string(R.string.event_type_holiday)
        OccasionType.REVIVAL -> appContext.string(R.string.event_type_revival)
        OccasionType.FOLLOW_UP -> appContext.string(R.string.event_type_follow_up)
        OccasionType.CUSTOM -> appContext.string(R.string.event_type_custom)
        else -> raw.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
    }
}

private fun Context.string(@StringRes resId: Int, vararg args: Any): String {
    return getString(resId, *args)
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
