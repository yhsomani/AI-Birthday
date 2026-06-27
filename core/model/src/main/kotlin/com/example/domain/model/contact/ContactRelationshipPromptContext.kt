package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactRelationshipPromptContext(
    val id: ContactId,
    val displayName: String,
    val nickname: String? = null,
    val relationshipType: String = "UNKNOWN",
    val relationshipSubtype: String? = null,
    val preferredLanguage: String = "en",
    val formalityLevel: String = "CASUAL",
    val communicationStyle: String = "WARM",
    val healthScore: Int = 50,
    val interactionFrequencyPerMonth: Float = 0f,
    val interestsJson: String = "[]",
    val hobbiesJson: String = "[]",
    val sharedHistoryJson: String = "[]",
    val sensitiveTopicsJson: String = "[]",
    val notesText: String = "",
)
