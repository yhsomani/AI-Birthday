package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactMessageContext(
    val id: ContactId,
    val displayName: String,
    val avatarUrl: String?,
    val primaryPhone: String?,
    val primaryEmail: String?,
)
