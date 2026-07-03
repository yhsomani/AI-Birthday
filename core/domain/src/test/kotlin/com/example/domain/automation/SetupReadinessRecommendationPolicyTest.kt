package com.example.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupReadinessRecommendationPolicyTest {

    @Test
    fun `selectRecommendedIndex ranks action required required-group fixes first`() {
        val index = SetupReadinessRecommendationPolicy.selectRecommendedIndex(
            listOf(
                candidate(
                    status = SetupReadinessStatus.ACTION_REQUIRED,
                    group = SetupReadinessGroup.QUALITY,
                ),
                candidate(
                    status = SetupReadinessStatus.WARNING,
                    group = SetupReadinessGroup.RELIABILITY,
                ),
                candidate(
                    status = SetupReadinessStatus.ACTION_REQUIRED,
                    group = SetupReadinessGroup.REQUIRED,
                ),
            ),
        )

        assertEquals(2, index)
    }

    @Test
    fun `selectRecommendedIndex keeps original order for equal-ranked candidates`() {
        val index = SetupReadinessRecommendationPolicy.selectRecommendedIndex(
            listOf(
                candidate(
                    status = SetupReadinessStatus.WARNING,
                    group = SetupReadinessGroup.RELIABILITY,
                ),
                candidate(
                    status = SetupReadinessStatus.WARNING,
                    group = SetupReadinessGroup.RELIABILITY,
                ),
            ),
        )

        assertEquals(0, index)
    }

    @Test
    fun `selectRecommendedIndex ignores healthy and non-actionable candidates`() {
        val index = SetupReadinessRecommendationPolicy.selectRecommendedIndex(
            listOf(
                candidate(
                    status = SetupReadinessStatus.ACTION_REQUIRED,
                    group = SetupReadinessGroup.REQUIRED,
                    hasAction = false,
                ),
                candidate(
                    status = SetupReadinessStatus.OK,
                    group = SetupReadinessGroup.REQUIRED,
                ),
                candidate(
                    status = SetupReadinessStatus.WARNING,
                    group = SetupReadinessGroup.RECOVERY,
                ),
            ),
        )

        assertEquals(2, index)
    }

    @Test
    fun `selectRecommendedIndex returns null when no candidate can be recommended`() {
        val index = SetupReadinessRecommendationPolicy.selectRecommendedIndex(
            listOf(
                candidate(
                    status = SetupReadinessStatus.OK,
                    group = SetupReadinessGroup.REQUIRED,
                ),
                candidate(
                    status = SetupReadinessStatus.WARNING,
                    group = SetupReadinessGroup.RELIABILITY,
                    hasAction = false,
                ),
            ),
        )

        assertNull(index)
    }

    private fun candidate(
        status: SetupReadinessStatus,
        group: SetupReadinessGroup,
        hasAction: Boolean = true,
    ): SetupReadinessRecommendationCandidate {
        return SetupReadinessRecommendationCandidate(
            status = status,
            group = group,
            hasAction = hasAction,
        )
    }
}
