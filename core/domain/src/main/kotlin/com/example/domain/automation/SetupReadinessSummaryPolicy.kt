package com.example.domain.automation

data class SetupReadinessSummaryDecision(
    val status: SetupReadinessStatus,
    val blockerCount: Int,
    val warningCount: Int,
    val firstProblemIndex: Int?,
)

object SetupReadinessSummaryPolicy {
    fun summarize(statuses: List<SetupReadinessStatus>): SetupReadinessSummaryDecision {
        val blockerCount = statuses.count { it == SetupReadinessStatus.ACTION_REQUIRED }
        val warningCount = statuses.count { it == SetupReadinessStatus.WARNING }
        val firstBlockerIndex = statuses.indexOfFirst { it == SetupReadinessStatus.ACTION_REQUIRED }
            .takeUnless { it == -1 }
        val firstWarningIndex = statuses.indexOfFirst { it == SetupReadinessStatus.WARNING }
            .takeUnless { it == -1 }
        return SetupReadinessSummaryDecision(
            status = when {
                blockerCount > 0 -> SetupReadinessStatus.ACTION_REQUIRED
                warningCount > 0 -> SetupReadinessStatus.WARNING
                else -> SetupReadinessStatus.OK
            },
            blockerCount = blockerCount,
            warningCount = warningCount,
            firstProblemIndex = firstBlockerIndex ?: firstWarningIndex,
        )
    }
}
