package com.example.domain.model.dispatch

import com.example.domain.model.common.ContactId

data class MessageDispatchRecipient(
    val id: ContactId,
    val displayName: String,
    val primaryPhone: String?,
    val primaryEmail: String?,
)
