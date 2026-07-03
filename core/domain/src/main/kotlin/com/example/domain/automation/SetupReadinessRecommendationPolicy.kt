package com.example.domain.automation

enum class SetupReadinessGroup {
    REQUIRED,
    QUALITY,
    RELIABILITY,
    RECOVERY,
}

data class SetupReadinessRecommendationCandidate(
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup,
    val hasAction: Boolean,
)

object SetupReadinessRecommendationPolicy {
    fun selectRecommendedIndex(
        candidates: List<SetupReadinessRecommendationCandidate>,
    ): Int? {
        return candidates
            .withIndex()
            .filter { (_, candidate) ->
                candidate.status != SetupReadinessStatus.OK && candidate.hasAction
            }
            .minWithOrNull(
                compareBy<IndexedValue<SetupReadinessRecommendationCandidate>> {
                    it.value.status.recommendedFixRank()
                }.thenBy {
                    it.value.group.recommendedFixRank()
                }.thenBy {
                    it.index
                },
            )
            ?.index
    }

    private fun SetupReadinessStatus.recommendedFixRank(): Int = when (this) {
        SetupReadinessStatus.ACTION_REQUIRED -> 0
        SetupReadinessStatus.WARNING -> 1
        SetupReadinessStatus.OK -> 2
    }

    private fun SetupReadinessGroup.recommendedFixRank(): Int = when (this) {
        SetupReadinessGroup.REQUIRED -> 0
        SetupReadinessGroup.RELIABILITY -> 1
        SetupReadinessGroup.QUALITY -> 2
        SetupReadinessGroup.RECOVERY -> 3
    }
}
