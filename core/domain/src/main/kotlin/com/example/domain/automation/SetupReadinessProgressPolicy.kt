package com.example.domain.automation

enum class SetupReadinessStatus {
    OK,
    WARNING,
    ACTION_REQUIRED,
}

data class SetupProgressSummary(
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val actionRequiredCount: Int = 0,
    val warningCount: Int = 0,
) {
    val progressFraction: Float
        get() = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps.toFloat()
}

object SetupReadinessProgressPolicy {
    fun summarize(statuses: List<SetupReadinessStatus>): SetupProgressSummary {
        return SetupProgressSummary(
            completedSteps = statuses.count { it == SetupReadinessStatus.OK },
            totalSteps = statuses.size,
            actionRequiredCount = statuses.count { it == SetupReadinessStatus.ACTION_REQUIRED },
            warningCount = statuses.count { it == SetupReadinessStatus.WARNING },
        )
    }

    fun summarizeHome(
        contactCount: Int,
        syncError: String?,
        aiGenerationEnabled: Boolean,
        hasAiAccess: Boolean,
        pendingCount: Int,
    ): SetupProgressSummary {
        return summarize(
            listOf(
                if (contactCount > 0 && syncError == null) {
                    SetupReadinessStatus.OK
                } else {
                    SetupReadinessStatus.ACTION_REQUIRED
                },
                if (aiGenerationEnabled && hasAiAccess) {
                    SetupReadinessStatus.OK
                } else {
                    SetupReadinessStatus.ACTION_REQUIRED
                },
                if (pendingCount == 0) {
                    SetupReadinessStatus.OK
                } else {
                    SetupReadinessStatus.WARNING
                },
            ),
        )
    }
}
