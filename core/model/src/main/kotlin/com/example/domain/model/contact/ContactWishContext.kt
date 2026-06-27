package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactWishContext(
    val id: ContactId,
    val relationshipType: String,
    val preferredLanguage: String,
)
