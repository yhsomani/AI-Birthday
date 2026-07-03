package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactClassificationProfile(
    val id: ContactId,
    val relationshipType: String,
    val promptContext: ContactClassificationPromptContext,
)
