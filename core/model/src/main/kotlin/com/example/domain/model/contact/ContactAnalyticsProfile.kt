package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactAnalyticsProfile(
    val id: ContactId,
    val healthScore: Int,
    val nickname: String?,
    val notesText: String,
    val interestsJson: String,
    val sharedHistoryJson: String,
) {
    val hasPersonalizationSignals: Boolean
        get() = !nickname.isNullOrBlank() ||
            notesText.isNotBlank() ||
            hasJsonListSignal(interestsJson) ||
            hasJsonListSignal(sharedHistoryJson)

    private fun hasJsonListSignal(raw: String): Boolean {
        return raw.trim().let { it.isNotBlank() && it != "[]" }
    }
}
