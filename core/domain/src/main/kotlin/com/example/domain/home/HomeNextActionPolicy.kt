package com.example.domain.home

import com.example.domain.model.contact.ContactAnalyticsSummary

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

enum class HomeNextActionTargetKind {
    AUTOMATION_SETUP,
    BACKUP_RESTORE,
    MESSAGES,
    CONTACT_DETAIL,
}

data class HomeNextActionCandidate(
    val kind: HomeNextActionKind,
    val targetKind: HomeNextActionTargetKind,
    val count: Int = 0,
    val daysSinceBackup: Long? = null,
    val contactId: String? = null,
    val contactName: String? = null,
    val healthScore: Int? = null,
)

data class HomeReadinessBannerCandidate(
    val kind: HomeNextActionKind,
    val targetKind: HomeNextActionTargetKind,
    val count: Int = 0,
)

object HomeNextActionPolicy {
    const val STALE_BACKUP_DAYS = 30L
    const val DAY_MS = 24L * 60 * 60 * 1000L

    fun backupFreshnessPrompt(
        lastBackupMs: Long,
        nowMs: Long,
    ): BackupFreshnessPrompt? {
        if (lastBackupMs <= 0L) {
            return BackupFreshnessPrompt(status = BackupFreshnessStatus.NEVER_BACKED_UP)
        }
        val daysSinceBackup = ((nowMs - lastBackupMs).coerceAtLeast(0L)) / DAY_MS
        return if (daysSinceBackup >= STALE_BACKUP_DAYS) {
            BackupFreshnessPrompt(
                status = BackupFreshnessStatus.STALE,
                daysSinceBackup = daysSinceBackup,
            )
        } else {
            null
        }
    }

    fun rankNextActions(
        contactCount: Int,
        syncError: String?,
        aiGenerationEnabled: Boolean,
        hasAiAccess: Boolean,
        pendingCount: Int,
        backupPrompt: BackupFreshnessPrompt?,
        atRiskContacts: List<ContactAnalyticsSummary>,
    ): List<HomeNextActionCandidate> {
        val rankedActions = mutableListOf<Pair<Int, HomeNextActionCandidate>>()
        val contactSetupAction = when {
            syncError != null -> HomeNextActionCandidate(
                kind = HomeNextActionKind.FIX_CONTACT_SYNC,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            )
            contactCount == 0 -> HomeNextActionCandidate(
                kind = HomeNextActionKind.SYNC_CONTACTS,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            )
            else -> null
        }
        contactSetupAction?.let { rankedActions += 100 to it }
        if (pendingCount > 0) {
            rankedActions += 90 to HomeNextActionCandidate(
                kind = HomeNextActionKind.REVIEW_PENDING,
                targetKind = HomeNextActionTargetKind.MESSAGES,
                count = pendingCount,
            )
        }
        if (!hasAiAccess) {
            rankedActions += 80 to HomeNextActionCandidate(
                kind = HomeNextActionKind.CONNECT_AI,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            )
        }
        if (!aiGenerationEnabled) {
            rankedActions += 75 to HomeNextActionCandidate(
                kind = HomeNextActionKind.ENABLE_AI_GENERATION,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            )
        }
        when (backupPrompt?.status) {
            BackupFreshnessStatus.NEVER_BACKED_UP -> {
                rankedActions += 70 to HomeNextActionCandidate(
                    kind = HomeNextActionKind.CREATE_BACKUP,
                    targetKind = HomeNextActionTargetKind.BACKUP_RESTORE,
                )
            }
            BackupFreshnessStatus.STALE -> {
                rankedActions += 60 to HomeNextActionCandidate(
                    kind = HomeNextActionKind.REFRESH_BACKUP,
                    targetKind = HomeNextActionTargetKind.BACKUP_RESTORE,
                    daysSinceBackup = backupPrompt.daysSinceBackup,
                )
            }
            null -> Unit
        }
        atRiskContacts.firstOrNull()?.let { contact ->
            rankedActions += 50 to HomeNextActionCandidate(
                kind = HomeNextActionKind.RECONNECT_CONTACT,
                targetKind = HomeNextActionTargetKind.CONTACT_DETAIL,
                contactId = contact.id.value,
                contactName = contact.displayName,
                healthScore = contact.healthScore,
            )
        }
        return rankedActions
            .sortedByDescending { it.first }
            .map { it.second }
    }

    fun readinessBanner(
        contactCount: Int,
        syncError: String?,
        pendingCount: Int,
    ): HomeReadinessBannerCandidate? {
        return when {
            syncError != null -> HomeReadinessBannerCandidate(
                kind = HomeNextActionKind.FIX_CONTACT_SYNC,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            )
            contactCount == 0 -> HomeReadinessBannerCandidate(
                kind = HomeNextActionKind.SYNC_CONTACTS,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            )
            pendingCount > 0 -> HomeReadinessBannerCandidate(
                kind = HomeNextActionKind.REVIEW_PENDING,
                targetKind = HomeNextActionTargetKind.MESSAGES,
                count = pendingCount,
            )
            else -> null
        }
    }
}
