package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recalculates health scores for all contacts based on interaction frequency,
 * last interaction date, and consecutive years wished.
 *
 * Health score formula (0-100):
 * - Base: 50
 * - +min(40, interactionFrequencyPerMonth * 8)        (max +40 for 5+ interactions/month)
 * - +(daysSinceLastContact < 30 ? 20 : 0)            (recent contact bonus)
 * - +(daysSinceLastContact < 7 ? 10 : 0)             (very recent contact bonus)
 * - +(consecutiveYearsWished * 5)                    (consistency bonus, max +25)
 * - +(health_score penalty if lastWishedDate is null and daysSinceLastContact > 180)
 */
@Singleton
class RefreshHealthScoresUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(): RefreshOutcome {
        val contacts = contactRepository.getAllSync()
        var updated = 0
        contacts.forEach { contact ->
            val newScore = computeHealthScore(contact)
            if (newScore != contact.healthScore) {
                contactRepository.updateHealthScore(contact.id, newScore)
                updated++
            }
        }
        return RefreshOutcome(scanned = contacts.size, updated = updated)
    }

    private fun computeHealthScore(contact: ContactEntity): Int {
        var score = 50
        score += (contact.interactionFrequencyPerMonth * 8).toInt().coerceAtMost(40)

        val now = System.currentTimeMillis()
        val daysSinceLastContact = if (contact.lastInteractionDate != null) {
            ((now - contact.lastInteractionDate!!) / (1000L * 60 * 60 * 24)).toInt()
        } else {
            Int.MAX_VALUE
        }

        if (daysSinceLastContact < 30) score += 20
        if (daysSinceLastContact < 7) score += 10
        score += (contact.consecutiveYearsWished * 5).coerceAtMost(25)

        if (contact.lastWishedDate == null && daysSinceLastContact > 180) {
            score -= 20
        }

        return score.coerceIn(0, 100)
    }

    data class RefreshOutcome(val scanned: Int, val updated: Int)
}
