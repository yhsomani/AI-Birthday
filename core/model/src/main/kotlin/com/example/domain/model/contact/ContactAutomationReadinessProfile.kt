package com.example.domain.model.contact

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId

data class ContactAutomationReadinessProfile(
    val id: ContactId,
    val preferredChannel: MessageChannel,
    val automationMode: ApprovalMode = ApprovalMode.DEFAULT,
    val skipAutoWish: Boolean = false,
    val hasPrimaryPhone: Boolean = false,
    val hasPrimaryEmail: Boolean = false,
    val hasAutomatableOccasion: Boolean = false,
    val nickname: String?,
    val notesText: String,
    val interestsJson: String,
    val sharedHistoryJson: String,
    val classificationConfidence: Double,
) {
    val hasReviewFirstAutomationOverride: Boolean
        get() = skipAutoWish ||
            automationMode == ApprovalMode.ALWAYS_ASK ||
            automationMode == ApprovalMode.VIP_APPROVE ||
            automationMode == ApprovalMode.SMART_APPROVE

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
