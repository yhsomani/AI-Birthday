package com.example.domain.automation

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageStatus
import java.util.Locale

object RevivalCadencePolicy {
    private val blockingStatuses = setOf(
        MessageStatus.PENDING,
        MessageStatus.APPROVED,
        MessageStatus.DISPATCHING,
        MessageStatus.SENT,
    )

    fun eventId(contactId: String): String = "REVIVAL_$contactId"

    fun evaluate(
        contact: ContactEntity,
        existingSameYearRevival: PendingMessageEntity?,
        nowMs: Long,
    ): Decision {
        if (contact.skipAutoWish) {
            return Decision(false, "skip_auto_wish", cadenceDays(contact))
        }

        val existingStatus = MessageStatus.fromRaw(existingSameYearRevival?.status)
        if (existingStatus in blockingStatuses) {
            return Decision(false, "existing_same_year_revival", cadenceDays(contact))
        }

        val cadenceDays = cadenceDays(contact)
        val lastAttemptMs = contact.lastRevivalAttemptMs
        if (lastAttemptMs > 0L) {
            val elapsedMs = (nowMs - lastAttemptMs).coerceAtLeast(0L)
            if (elapsedMs < cadenceDays.daysToMs()) {
                return Decision(false, "cadence_not_elapsed", cadenceDays)
            }
        }

        return Decision(true, "eligible", cadenceDays)
    }

    fun cadenceDays(contact: ContactEntity): Int {
        val relationshipDays = when (contact.relationshipType.trim().uppercase(Locale.US)) {
            "FAMILY", "BEST_FRIEND", "CLOSE_FRIEND", "PARTNER" -> 30
            "FRIEND", "RELATIVE", "MENTOR", "ALUMNI" -> 45
            "COLLEAGUE", "COWORKER", "CLIENT", "MANAGER", "VENDOR", "PROFESSIONAL" -> 90
            else -> 60
        }
        val frequencyDays = when {
            contact.interactionFrequencyPerMonth >= 4f -> 30
            contact.interactionFrequencyPerMonth >= 1f -> 45
            contact.interactionFrequencyPerMonth > 0f -> 60
            else -> relationshipDays
        }
        val healthAdjustment = when {
            contact.healthScore < 20 -> -15
            contact.healthScore < 30 -> -7
            contact.healthScore >= 70 -> 30
            else -> 0
        }
        return (minOf(relationshipDays, frequencyDays) + healthAdjustment).coerceIn(30, 120)
    }

    private fun Int.daysToMs(): Long = this * 24L * 60L * 60L * 1000L

    data class Decision(
        val shouldCreate: Boolean,
        val reason: String,
        val cadenceDays: Int,
    )
}
