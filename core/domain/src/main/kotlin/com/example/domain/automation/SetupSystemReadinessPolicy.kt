package com.example.domain.automation

enum class SetupNotificationReadinessReason {
    READY,
    PERMISSION_MISSING,
}

enum class SetupExactSendReadinessReason {
    READY,
    PERMISSION_MISSING,
}

enum class SetupDailyAutomationReadinessReason {
    SCHEDULED,
    MISSING,
}

enum class SetupRecentHealthReadinessReason {
    CLEAR,
    RECENT_EVIDENCE,
}

enum class SetupDispatchRecoveryReadinessReason {
    CLEAR,
    RECOVERY_QUEUE_PRESENT,
}

data class SetupNotificationReadiness(
    val reason: SetupNotificationReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
)

data class SetupExactSendReadiness(
    val reason: SetupExactSendReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RELIABILITY,
)

data class SetupDailyAutomationReadiness(
    val reason: SetupDailyAutomationReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RELIABILITY,
)

data class SetupRecentHealthReadiness(
    val reason: SetupRecentHealthReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RECOVERY,
)

data class SetupDispatchRecoveryReadiness(
    val reason: SetupDispatchRecoveryReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RECOVERY,
    val persistedRecoveryCount: Int = 0,
    val persistedDeadLetterCount: Int = 0,
)

object SetupSystemReadinessPolicy {
    fun evaluateNotificationPermission(
        notificationsAllowed: Boolean,
    ): SetupNotificationReadiness {
        return if (notificationsAllowed) {
            SetupNotificationReadiness(
                reason = SetupNotificationReadinessReason.READY,
                status = SetupReadinessStatus.OK,
            )
        } else {
            SetupNotificationReadiness(
                reason = SetupNotificationReadinessReason.PERMISSION_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
        }
    }

    fun evaluateExactSends(
        exactSendsAllowed: Boolean,
    ): SetupExactSendReadiness {
        return if (exactSendsAllowed) {
            SetupExactSendReadiness(
                reason = SetupExactSendReadinessReason.READY,
                status = SetupReadinessStatus.OK,
            )
        } else {
            SetupExactSendReadiness(
                reason = SetupExactSendReadinessReason.PERMISSION_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
        }
    }

    fun evaluateDailyAutomation(
        dailyScheduled: Boolean,
    ): SetupDailyAutomationReadiness {
        return if (dailyScheduled) {
            SetupDailyAutomationReadiness(
                reason = SetupDailyAutomationReadinessReason.SCHEDULED,
                status = SetupReadinessStatus.OK,
            )
        } else {
            SetupDailyAutomationReadiness(
                reason = SetupDailyAutomationReadinessReason.MISSING,
                status = SetupReadinessStatus.WARNING,
            )
        }
    }

    fun evaluateRecentHealth(
        hasRecentHealthEvidence: Boolean,
    ): SetupRecentHealthReadiness {
        return if (hasRecentHealthEvidence) {
            SetupRecentHealthReadiness(
                reason = SetupRecentHealthReadinessReason.RECENT_EVIDENCE,
                status = SetupReadinessStatus.WARNING,
            )
        } else {
            SetupRecentHealthReadiness(
                reason = SetupRecentHealthReadinessReason.CLEAR,
                status = SetupReadinessStatus.OK,
            )
        }
    }

    fun evaluateDispatchRecovery(
        persistedRecoveryCount: Int,
        persistedDeadLetterCount: Int,
    ): SetupDispatchRecoveryReadiness {
        return if (persistedRecoveryCount == 0) {
            SetupDispatchRecoveryReadiness(
                reason = SetupDispatchRecoveryReadinessReason.CLEAR,
                status = SetupReadinessStatus.OK,
                persistedRecoveryCount = persistedRecoveryCount,
                persistedDeadLetterCount = persistedDeadLetterCount,
            )
        } else {
            SetupDispatchRecoveryReadiness(
                reason = SetupDispatchRecoveryReadinessReason.RECOVERY_QUEUE_PRESENT,
                status = SetupReadinessStatus.WARNING,
                persistedRecoveryCount = persistedRecoveryCount,
                persistedDeadLetterCount = persistedDeadLetterCount,
            )
        }
    }
}
