package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactClassificationPromptContext(
    val id: ContactId,
    val displayName: String,
    val notesText: String = "",
    val interactionFrequencyPerMonth: Float = 0f,
)
