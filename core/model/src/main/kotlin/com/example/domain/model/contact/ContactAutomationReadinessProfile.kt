package com.example.domain.model.contact

import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId

data class ContactAutomationReadinessProfile(
    val id: ContactId,
    val preferredChannel: MessageChannel,
    val nickname: String?,
    val notesText: String,
    val interestsJson: String,
    val sharedHistoryJson: String,
    val classificationConfidence: Double,
) {
    val hasPersonalizationData: Boolean
        get() = !nickname.isNullOrBlank() ||
            notesText.isNotBlank() ||
            hasJsonListSignal(interestsJson) ||
            hasJsonListSignal(sharedHistoryJson)

    fun hasPersonalizationContextForAi(minimumConfidence: Double): Boolean {
        return hasPersonalizationData || classificationConfidence >= minimumConfidence
    }

    private fun hasJsonListSignal(raw: String): Boolean {
        return raw.trim().let { it.isNotBlank() && it != "[]" }
    }
}
