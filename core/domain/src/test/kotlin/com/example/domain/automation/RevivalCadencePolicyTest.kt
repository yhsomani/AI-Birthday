package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevivalCadencePolicyTest {

    @Test
    fun `evaluate blocks active same-year revival drafts`() {
        val decision = RevivalCadencePolicy.evaluate(
            contact = contact(lastRevivalAttemptMs = 0L),
            existingSameYearRevival = pending(status = "PENDING"),
            nowMs = NOW,
        )

        assertFalse(decision.shouldCreate)
        assertEquals("existing_same_year_revival", decision.reason)
    }

    @Test
    fun `evaluate blocks professional contacts until cadence elapses`() {
        val decision = RevivalCadencePolicy.evaluate(
            contact = contact(
                relationshipType = "COLLEAGUE",
                healthScore = 40,
                lastRevivalAttemptMs = NOW - 45.daysToMs(),
            ),
            existingSameYearRevival = null,
            nowMs = NOW,
        )

        assertFalse(decision.shouldCreate)
        assertEquals("cadence_not_elapsed", decision.reason)
        assertEquals(90, decision.cadenceDays)
    }

    @Test
    fun `evaluate allows professional contacts after cadence elapses`() {
        val decision = RevivalCadencePolicy.evaluate(
            contact = contact(
                relationshipType = "COLLEAGUE",
                healthScore = 40,
                lastRevivalAttemptMs = NOW - 91.daysToMs(),
            ),
            existingSameYearRevival = null,
            nowMs = NOW,
        )

        assertTrue(decision.shouldCreate)
        assertEquals("eligible", decision.reason)
    }

    @Test
    fun `cadence uses interaction frequency and health score`() {
        val frequentLowHealth = RevivalCadencePolicy.cadenceDays(
            contact(
                relationshipType = "UNKNOWN",
                healthScore = 15,
                interactionFrequencyPerMonth = 5f,
            )
        )
        val unknownNormal = RevivalCadencePolicy.cadenceDays(contact(relationshipType = "UNKNOWN", healthScore = 40))

        assertEquals(30, frequentLowHealth)
        assertEquals(60, unknownNormal)
    }

    @Test
    fun `evaluate allows failed existing revival after cadence`() {
        val decision = RevivalCadencePolicy.evaluate(
            contact = contact(lastRevivalAttemptMs = NOW - 61.daysToMs()),
            existingSameYearRevival = pending(status = "FAILED"),
            nowMs = NOW,
        )

        assertTrue(decision.shouldCreate)
    }

    private fun contact(
        relationshipType: String = "FRIEND",
        healthScore: Int = 30,
        interactionFrequencyPerMonth: Float = 0f,
        lastRevivalAttemptMs: Long = 0L,
    ): ContactEntity {
        return ContactEntity(
            id = "c1",
            name = "Priya",
            relationshipType = relationshipType,
            healthScore = healthScore,
            interactionFrequencyPerMonth = interactionFrequencyPerMonth,
            lastRevivalAttemptMs = lastRevivalAttemptMs,
        )
    }

    private fun pending(status: String): PendingMessageEntity {
        return PendingMessageEntity(
            id = "p1",
            contactId = "c1",
            eventId = RevivalCadencePolicy.eventId("c1"),
            shortVariant = "Hi",
            standardVariant = "Hi",
            longVariant = "Hi",
            formalVariant = "Hi",
            funnyVariant = "Hi",
            emotionalVariant = "Hi",
            channel = "SMS",
            scheduledForMs = NOW,
            approvalMode = "SMART_APPROVE",
            status = status,
            scheduledYear = 2026,
        )
    }

    private fun Int.daysToMs(): Long = this * 24L * 60L * 60L * 1000L

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
