package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactAnalyticsSummary(
    val id: ContactId,
    val displayName: String,
    val healthScore: Int,
    val relationshipType: String,
)
