package com.example.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupSystemReadinessPolicyTest {

    @Test
    fun `evaluateNotificationPermission requires action when notifications are blocked`() {
        val readiness = SetupSystemReadinessPolicy.evaluateNotificationPermission(
            notificationsAllowed = false,
        )

        assertEquals(SetupNotificationReadinessReason.PERMISSION_MISSING, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(SetupReadinessGroup.REQUIRED, readiness.group)
    }

    @Test
    fun `evaluateNotificationPermission passes when notifications are allowed`() {
        val readiness = SetupSystemReadinessPolicy.evaluateNotificationPermission(
            notificationsAllowed = true,
        )

        assertEquals(SetupNotificationReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateExactSends requires action when exact sends are blocked`() {
        val readiness = SetupSystemReadinessPolicy.evaluateExactSends(
            exactSendsAllowed = false,
        )

        assertEquals(SetupExactSendReadinessReason.PERMISSION_MISSING, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(SetupReadinessGroup.RELIABILITY, readiness.group)
    }

    @Test
    fun `evaluateExactSends passes when exact sends are allowed`() {
        val readiness = SetupSystemReadinessPolicy.evaluateExactSends(
            exactSendsAllowed = true,
        )

        assertEquals(SetupExactSendReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateDailyAutomation warns when daily worker is missing`() {
        val readiness = SetupSystemReadinessPolicy.evaluateDailyAutomation(
            dailyScheduled = false,
        )

        assertEquals(SetupDailyAutomationReadinessReason.MISSING, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(SetupReadinessGroup.RELIABILITY, readiness.group)
    }

    @Test
    fun `evaluateDailyAutomation passes when daily worker is scheduled`() {
        val readiness = SetupSystemReadinessPolicy.evaluateDailyAutomation(
            dailyScheduled = true,
        )

        assertEquals(SetupDailyAutomationReadinessReason.SCHEDULED, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateRecentHealth warns when health evidence exists`() {
        val readiness = SetupSystemReadinessPolicy.evaluateRecentHealth(
            hasRecentHealthEvidence = true,
        )

        assertEquals(SetupRecentHealthReadinessReason.RECENT_EVIDENCE, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(SetupReadinessGroup.RECOVERY, readiness.group)
    }

    @Test
    fun `evaluateRecentHealth passes when there is no health evidence`() {
        val readiness = SetupSystemReadinessPolicy.evaluateRecentHealth(
            hasRecentHealthEvidence = false,
        )

        assertEquals(SetupRecentHealthReadinessReason.CLEAR, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateDispatchRecovery warns when recovery queue has failures`() {
        val readiness = SetupSystemReadinessPolicy.evaluateDispatchRecovery(
            persistedRecoveryCount = 3,
            persistedDeadLetterCount = 2,
        )

        assertEquals(SetupDispatchRecoveryReadinessReason.RECOVERY_QUEUE_PRESENT, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(SetupReadinessGroup.RECOVERY, readiness.group)
        assertEquals(3, readiness.persistedRecoveryCount)
        assertEquals(2, readiness.persistedDeadLetterCount)
    }

    @Test
    fun `evaluateDispatchRecovery passes when recovery queue is empty`() {
        val readiness = SetupSystemReadinessPolicy.evaluateDispatchRecovery(
            persistedRecoveryCount = 0,
            persistedDeadLetterCount = 0,
        )

        assertEquals(SetupDispatchRecoveryReadinessReason.CLEAR, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }
}
