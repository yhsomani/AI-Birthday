package com.example.domain.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishDraftReadinessPolicyTest {

    @Test
    fun `blank drafts block approval`() {
        val result = WishDraftReadinessPolicy.evaluate(
            draftText = "   ",
            sourceText = "Original warm birthday draft",
        )

        assertEquals(WishDraftReadiness.BLANK, result)
        assertTrue(result.blocksApproval)
    }

    @Test
    fun `short drafts block approval`() {
        val result = WishDraftReadinessPolicy.evaluate(
            draftText = "Too short",
            sourceText = "Original warm birthday draft",
        )

        assertEquals(WishDraftReadiness.TOO_SHORT, result)
        assertTrue(result.blocksApproval)
    }

    @Test
    fun `edited valid drafts are ready without blocking approval`() {
        val result = WishDraftReadinessPolicy.evaluate(
            draftText = "A warmer edited birthday draft",
            sourceText = "Original warm birthday draft",
        )

        assertEquals(WishDraftReadiness.EDITED_READY, result)
        assertFalse(result.blocksApproval)
    }

    @Test
    fun `unchanged valid drafts are ready without blocking approval`() {
        val result = WishDraftReadinessPolicy.evaluate(
            draftText = "Original warm birthday draft",
            sourceText = "Original warm birthday draft",
        )

        assertEquals(WishDraftReadiness.READY, result)
        assertFalse(result.blocksApproval)
    }
}
