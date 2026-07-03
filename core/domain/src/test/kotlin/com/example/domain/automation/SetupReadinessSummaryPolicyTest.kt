package com.example.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupReadinessSummaryPolicyTest {

    @Test
    fun `summarize reports action required when blockers exist`() {
        val decision = SetupReadinessSummaryPolicy.summarize(
            listOf(
                SetupReadinessStatus.WARNING,
                SetupReadinessStatus.ACTION_REQUIRED,
                SetupReadinessStatus.OK,
            ),
        )

        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, decision.status)
        assertEquals(1, decision.blockerCount)
        assertEquals(1, decision.warningCount)
        assertEquals(1, decision.firstProblemIndex)
    }

    @Test
    fun `summarize reports warning when only warnings exist`() {
        val decision = SetupReadinessSummaryPolicy.summarize(
            listOf(
                SetupReadinessStatus.OK,
                SetupReadinessStatus.WARNING,
                SetupReadinessStatus.WARNING,
            ),
        )

        assertEquals(SetupReadinessStatus.WARNING, decision.status)
        assertEquals(0, decision.blockerCount)
        assertEquals(2, decision.warningCount)
        assertEquals(1, decision.firstProblemIndex)
    }

    @Test
    fun `summarize reports ok when no problems exist`() {
        val decision = SetupReadinessSummaryPolicy.summarize(
            listOf(
                SetupReadinessStatus.OK,
                SetupReadinessStatus.OK,
            ),
        )

        assertEquals(SetupReadinessStatus.OK, decision.status)
        assertEquals(0, decision.blockerCount)
        assertEquals(0, decision.warningCount)
        assertNull(decision.firstProblemIndex)
    }
}
