package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
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
            preferredChannel = request.preferredChannel.raw,
            formalityLevel = request.formalityLevel.trim().uppercase(),
            communicationStyle = request.communicationStyle.trim().uppercase(),
            automationMode = request.automationMode.raw,
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
        val preferredChannel: MessageChannel = MessageChannel.SMS,
        val formalityLevel: String = "CASUAL",
        val communicationStyle: String = "WARM",
        val automationMode: ApprovalMode = ApprovalMode.DEFAULT,
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
        data class InvalidInput(val reason: InvalidInputReason) : Outcome()
        data class Updated(val contact: ContactEntity) : Outcome()
    }

    enum class InvalidInputReason {
        MISSING_RELATIONSHIP_TYPE,
        MISSING_PREFERRED_LANGUAGE,
        UNSUPPORTED_PREFERRED_CHANNEL,
        UNSUPPORTED_AUTOMATION_MODE,
        INVALID_SEND_HOUR,
        INVALID_SEND_MINUTE,
        INCOMPLETE_CUSTOM_SEND_TIME,
        NEGATIVE_BUDGET,
    }

    private fun Request.validationError(): InvalidInputReason? {
        if (relationshipType.isBlank()) return InvalidInputReason.MISSING_RELATIONSHIP_TYPE
        if (preferredLanguage.isBlank()) return InvalidInputReason.MISSING_PREFERRED_LANGUAGE
        if (!preferredChannel.isSupportedContactPreferenceChannel()) {
            return InvalidInputReason.UNSUPPORTED_PREFERRED_CHANNEL
        }
        if (!automationMode.isSupportedContactPreferenceMode()) {
            return InvalidInputReason.UNSUPPORTED_AUTOMATION_MODE
        }
        if (customSendTimeHour != null && customSendTimeHour !in 0..23) return InvalidInputReason.INVALID_SEND_HOUR
        if (customSendTimeMinute != null && customSendTimeMinute !in 0..59) return InvalidInputReason.INVALID_SEND_MINUTE
        if ((customSendTimeHour == null) != (customSendTimeMinute == null)) {
            return InvalidInputReason.INCOMPLETE_CUSTOM_SEND_TIME
        }
        if (giftBudgetInr < 0 || annualBudgetInr < 0) return InvalidInputReason.NEGATIVE_BUDGET
        return null
    }

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotBlank() }

    private fun ApprovalMode.isSupportedContactPreferenceMode(): Boolean {
        return this == ApprovalMode.DEFAULT ||
            this == ApprovalMode.FULLY_AUTO ||
            this == ApprovalMode.SMART_APPROVE ||
            this == ApprovalMode.VIP_APPROVE ||
            this == ApprovalMode.ALWAYS_ASK
    }

    private fun MessageChannel.isSupportedContactPreferenceChannel(): Boolean {
        return this == MessageChannel.SMS ||
            this == MessageChannel.WHATSAPP ||
            this == MessageChannel.EMAIL
    }

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
