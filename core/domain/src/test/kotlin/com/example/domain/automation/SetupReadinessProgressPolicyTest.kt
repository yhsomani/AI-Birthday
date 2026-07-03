package com.example.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupReadinessProgressPolicyTest {

    @Test
    fun `summarize counts ok warnings and action required statuses`() {
        val summary = SetupReadinessProgressPolicy.summarize(
            listOf(
                SetupReadinessStatus.OK,
                SetupReadinessStatus.WARNING,
                SetupReadinessStatus.ACTION_REQUIRED,
                SetupReadinessStatus.OK,
            ),
        )

        assertEquals(2, summary.completedSteps)
        assertEquals(4, summary.totalSteps)
        assertEquals(1, summary.actionRequiredCount)
        assertEquals(1, summary.warningCount)
        assertEquals(0.5f, summary.progressFraction, 0.0001f)
    }

    @Test
    fun `summarize handles empty status lists`() {
        val summary = SetupReadinessProgressPolicy.summarize(emptyList())

        assertEquals(0, summary.completedSteps)
        assertEquals(0, summary.totalSteps)
        assertEquals(0, summary.actionRequiredCount)
        assertEquals(0, summary.warningCount)
        assertEquals(0f, summary.progressFraction, 0.0001f)
    }

    @Test
    fun `summarizeHome treats sync and ai blockers as action required and pending reviews as warning`() {
        val summary = SetupReadinessProgressPolicy.summarizeHome(
            contactCount = 0,
            syncError = "sync failed",
            aiGenerationEnabled = false,
            hasAiAccess = false,
            pendingCount = 2,
        )

        assertEquals(0, summary.completedSteps)
        assertEquals(3, summary.totalSteps)
        assertEquals(2, summary.actionRequiredCount)
        assertEquals(1, summary.warningCount)
    }

    @Test
    fun `summarizeHome reports all setup steps complete when contacts ai and review queue are ready`() {
        val summary = SetupReadinessProgressPolicy.summarizeHome(
            contactCount = 12,
            syncError = null,
            aiGenerationEnabled = true,
            hasAiAccess = true,
            pendingCount = 0,
        )

        assertEquals(3, summary.completedSteps)
        assertEquals(3, summary.totalSteps)
        assertEquals(0, summary.actionRequiredCount)
        assertEquals(0, summary.warningCount)
        assertEquals(1f, summary.progressFraction, 0.0001f)
    }
}
