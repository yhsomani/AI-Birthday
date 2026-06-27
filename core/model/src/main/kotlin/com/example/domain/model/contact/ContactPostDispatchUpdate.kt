package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactPostDispatchUpdate(
    val contactId: ContactId,
    val wishedAtMs: Long,
    val healthScoreDelta: Int,
)
