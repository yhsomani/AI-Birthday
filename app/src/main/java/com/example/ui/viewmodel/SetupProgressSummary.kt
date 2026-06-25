package com.example.ui.viewmodel

data class SetupProgressSummary(
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val actionRequiredCount: Int = 0,
    val warningCount: Int = 0,
) {
    val progressFraction: Float
        get() = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps.toFloat()
}

internal fun List<ReadinessCheck>.toSetupProgressSummary(): SetupProgressSummary {
    return statusesToSetupProgressSummary(map { it.status })
}

internal fun buildHomeSetupProgressSummary(
    contactCount: Int,
    syncError: String?,
    aiGenerationEnabled: Boolean,
    hasAiAccess: Boolean,
    pendingCount: Int,
): SetupProgressSummary {
    return statusesToSetupProgressSummary(
        listOf(
            if (contactCount > 0 && syncError == null) {
                ReadinessStatus.OK
            } else {
                ReadinessStatus.ACTION_REQUIRED
            },
            if (aiGenerationEnabled && hasAiAccess) {
                ReadinessStatus.OK
            } else {
                ReadinessStatus.ACTION_REQUIRED
            },
            if (pendingCount == 0) {
                ReadinessStatus.OK
            } else {
                ReadinessStatus.WARNING
            },
        )
    )
}

private fun statusesToSetupProgressSummary(statuses: List<ReadinessStatus>): SetupProgressSummary {
    return SetupProgressSummary(
        completedSteps = statuses.count { it == ReadinessStatus.OK },
        totalSteps = statuses.size,
        actionRequiredCount = statuses.count { it == ReadinessStatus.ACTION_REQUIRED },
        warningCount = statuses.count { it == ReadinessStatus.WARNING },
    )
}
