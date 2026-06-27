package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactMessagePromptContext(
    val id: ContactId,
    val displayName: String,
    val nickname: String? = null,
    val relationshipType: String = "UNKNOWN",
    val birthdayYear: Int? = null,
    val interestsJson: String = "[]",
    val sharedHistoryJson: String = "[]",
    val lastInteractionAtMs: Long? = null,
    val preferredLanguage: String = "en",
    val formalityLevel: String = "CASUAL",
    val sensitiveTopicsJson: String = "[]",
    val currentLifePhaseJson: String = "{}",
    val preferredChannel: String = "SMS",
)
