package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactHeader(
    val id: ContactId,
    val displayName: String,
)
