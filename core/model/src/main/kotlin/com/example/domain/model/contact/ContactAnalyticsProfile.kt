package com.example.domain.model.contact

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.JsonTextCodec

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
            JsonTextCodec.hasStringArrayContent(interestsJson) ||
            JsonTextCodec.hasStringArrayContent(sharedHistoryJson)
}
