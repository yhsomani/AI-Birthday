package com.example.domain.home

import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAnalyticsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNextActionPolicyTest {

    @Test
    fun `backup freshness maps never backed up stale and recent backups`() {
        val now = 1_800_000_000_000L

        assertEquals(
            BackupFreshnessPrompt(status = BackupFreshnessStatus.NEVER_BACKED_UP),
            HomeNextActionPolicy.backupFreshnessPrompt(lastBackupMs = 0L, nowMs = now),
        )
        assertEquals(
            BackupFreshnessPrompt(status = BackupFreshnessStatus.STALE, daysSinceBackup = 31),
            HomeNextActionPolicy.backupFreshnessPrompt(
                lastBackupMs = now - 31L * HomeNextActionPolicy.DAY_MS,
                nowMs = now,
            ),
        )
        assertNull(
            HomeNextActionPolicy.backupFreshnessPrompt(
                lastBackupMs = now - 2L * HomeNextActionPolicy.DAY_MS,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `ranked actions preserve operational priorities`() {
        val actions = HomeNextActionPolicy.rankNextActions(
            contactCount = 10,
            syncError = null,
            aiGenerationEnabled = true,
            hasAiAccess = true,
            pendingCount = 2,
            backupPrompt = BackupFreshnessPrompt(
                status = BackupFreshnessStatus.STALE,
                daysSinceBackup = 31,
            ),
            atRiskContacts = listOf(contactSummary(id = "c_low", displayName = "Asha", healthScore = 32)),
        )

        assertEquals(HomeNextActionKind.REVIEW_PENDING, actions[0].kind)
        assertEquals(HomeNextActionTargetKind.MESSAGES, actions[0].targetKind)
        assertEquals(2, actions[0].count)
        assertEquals(HomeNextActionKind.REFRESH_BACKUP, actions[1].kind)
        assertEquals(HomeNextActionTargetKind.BACKUP_RESTORE, actions[1].targetKind)
        assertEquals(31L, actions[1].daysSinceBackup)
        assertEquals(HomeNextActionKind.RECONNECT_CONTACT, actions[2].kind)
        assertEquals(HomeNextActionTargetKind.CONTACT_DETAIL, actions[2].targetKind)
        assertEquals("c_low", actions[2].contactId)
    }

    @Test
    fun `setup blockers outrank other next actions`() {
        val actions = HomeNextActionPolicy.rankNextActions(
            contactCount = 0,
            syncError = "Sync failed",
            aiGenerationEnabled = false,
            hasAiAccess = false,
            pendingCount = 3,
            backupPrompt = BackupFreshnessPrompt(status = BackupFreshnessStatus.NEVER_BACKED_UP),
            atRiskContacts = emptyList(),
        )

        assertEquals(HomeNextActionKind.FIX_CONTACT_SYNC, actions.first().kind)
        assertEquals(HomeNextActionTargetKind.AUTOMATION_SETUP, actions.first().targetKind)
        assertEquals(
            listOf(
                HomeNextActionKind.FIX_CONTACT_SYNC,
                HomeNextActionKind.REVIEW_PENDING,
                HomeNextActionKind.CONNECT_AI,
                HomeNextActionKind.ENABLE_AI_GENERATION,
                HomeNextActionKind.CREATE_BACKUP,
            ),
            actions.map { it.kind },
        )
    }

    @Test
    fun `readiness banner preserves setup and review priority`() {
        assertEquals(
            HomeReadinessBannerCandidate(
                kind = HomeNextActionKind.FIX_CONTACT_SYNC,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            ),
            HomeNextActionPolicy.readinessBanner(
                contactCount = 0,
                syncError = "Sync failed",
                pendingCount = 3,
            ),
        )
        assertEquals(
            HomeReadinessBannerCandidate(
                kind = HomeNextActionKind.SYNC_CONTACTS,
                targetKind = HomeNextActionTargetKind.AUTOMATION_SETUP,
            ),
            HomeNextActionPolicy.readinessBanner(
                contactCount = 0,
                syncError = null,
                pendingCount = 3,
            ),
        )
        assertEquals(
            HomeReadinessBannerCandidate(
                kind = HomeNextActionKind.REVIEW_PENDING,
                targetKind = HomeNextActionTargetKind.MESSAGES,
                count = 3,
            ),
            HomeNextActionPolicy.readinessBanner(
                contactCount = 12,
                syncError = null,
                pendingCount = 3,
            ),
        )
        assertNull(
            HomeNextActionPolicy.readinessBanner(
                contactCount = 12,
                syncError = null,
                pendingCount = 0,
            ),
        )
    }

    private fun contactSummary(
        id: String,
        displayName: String,
        healthScore: Int,
    ): ContactAnalyticsSummary {
        return ContactAnalyticsSummary(
            id = ContactId(id),
            displayName = displayName,
            healthScore = healthScore,
            relationshipType = "FRIEND",
        )
    }
}
