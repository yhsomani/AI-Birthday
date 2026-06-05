package com.example.domain.usecase

import com.example.domain.repository.ContactRepository
import com.example.domain.service.AiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies a contact's relationship to the user using Gemini AI.
 * Falls back to UNKNOWN if Gemini is unavailable.
 *
 * Business rules:
 * - Only classifies if contact.relationshipType is UNKNOWN (idempotent)
 * - Respects rate limiter to avoid Gemini API throttling
 * - Updates contact with subtype, language, formality, and communication style
 */
@Singleton
class ClassifyContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val aiService: AiService
) {
    suspend operator fun invoke(contactId: String): ClassificationOutcome {
        val contact = contactRepository.getById(contactId) ?: return ClassificationOutcome.ContactNotFound

        if (contact.relationshipType != "UNKNOWN" && contact.relationshipType.isNotEmpty()) {
            return ClassificationOutcome.AlreadyClassified(contact.relationshipType)
        }

        val result = aiService.classifyContact(contact)

        contactRepository.updateClassification(
            id = contact.id,
            type = result.type,
            subtype = result.subtype,
            lang = result.language,
            formality = result.formality,
            style = result.communicationStyle
        )

        return ClassificationOutcome.Classified(result.type, result.confidence)
    }

    sealed class ClassificationOutcome {
        data object ContactNotFound : ClassificationOutcome()
        data class AlreadyClassified(val type: String) : ClassificationOutcome()
        data class Classified(val type: String, val confidence: Double) : ClassificationOutcome()
    }
}
