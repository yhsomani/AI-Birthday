package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateContactPreferencesUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {
    suspend operator fun invoke(request: Request): Outcome {
        val contact = contactRepository.getById(request.contactId) ?: return Outcome.ContactNotFound

        val validationError = request.validationError()
        if (validationError != null) return Outcome.InvalidInput(validationError)

        val updated = contact.copy(
            nickname = request.nickname.trimToNull(),
            relationshipType = request.relationshipType.trim().uppercase(),
            preferredLanguage = request.preferredLanguage.trim().lowercase().ifBlank { "en" },
            preferredChannel = request.preferredChannel.trim().uppercase(),
            formalityLevel = request.formalityLevel.trim().uppercase(),
            communicationStyle = request.communicationStyle.trim().uppercase(),
            automationMode = request.automationMode.trim().uppercase(),
            giftBudgetInr = request.giftBudgetInr.coerceAtLeast(0),
            annualBudgetInr = request.annualBudgetInr.coerceAtLeast(0),
            skipAutoWish = request.skipAutoWish,
            customSendTimeHour = request.customSendTimeHour,
            customSendTimeMinute = request.customSendTimeMinute,
            interestsJson = request.interests.toJsonArray(),
            sensitiveTopicsJson = request.sensitiveTopics.toJsonArray(),
            currentLifePhaseJson = request.currentLifePhase.trim().toLifePhaseJson(),
            notesText = request.notes.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        contactRepository.update(updated)
        return Outcome.Updated(updated)
    }

    data class Request(
        val contactId: String,
        val nickname: String = "",
        val relationshipType: String = "UNKNOWN",
        val preferredLanguage: String = "en",
        val preferredChannel: String = "SMS",
        val formalityLevel: String = "CASUAL",
        val communicationStyle: String = "WARM",
        val automationMode: String = "DEFAULT",
        val customSendTimeHour: Int? = null,
        val customSendTimeMinute: Int? = null,
        val giftBudgetInr: Int = 500,
        val annualBudgetInr: Int = 0,
        val skipAutoWish: Boolean = false,
        val interests: String = "",
        val sensitiveTopics: String = "",
        val currentLifePhase: String = "",
        val notes: String = "",
    )

    sealed class Outcome {
        data object ContactNotFound : Outcome()
        data class InvalidInput(val message: String) : Outcome()
        data class Updated(val contact: ContactEntity) : Outcome()
    }

    private fun Request.validationError(): String? {
        if (relationshipType.isBlank()) return "Relationship type is required."
        if (preferredLanguage.isBlank()) return "Preferred language is required."
        if (preferredChannel.uppercase() !in setOf("SMS", "WHATSAPP", "EMAIL")) {
            return "Choose SMS, WhatsApp, or Email as the preferred channel."
        }
        if (automationMode.uppercase() !in setOf("DEFAULT", "FULLY_AUTO", "SMART_APPROVE", "VIP_APPROVE", "ALWAYS_ASK")) {
            return "Choose a supported automation mode."
        }
        if (customSendTimeHour != null && customSendTimeHour !in 0..23) return "Send hour must be 0-23."
        if (customSendTimeMinute != null && customSendTimeMinute !in 0..59) return "Send minute must be 0-59."
        if ((customSendTimeHour == null) != (customSendTimeMinute == null)) {
            return "Set both hour and minute for a custom send time."
        }
        if (giftBudgetInr < 0 || annualBudgetInr < 0) return "Budgets cannot be negative."
        return null
    }

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotBlank() }

    private fun String.toJsonArray(): String {
        val values = split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return values.joinToString(prefix = "[", postfix = "]") { "\"${it.jsonEscape()}\"" }
    }

    private fun String.toLifePhaseJson(): String {
        val value = trim()
        return if (value.isBlank()) "{}" else "{\"phase\":\"${value.jsonEscape()}\"}"
    }

    private fun String.jsonEscape(): String = buildString {
        this@jsonEscape.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
