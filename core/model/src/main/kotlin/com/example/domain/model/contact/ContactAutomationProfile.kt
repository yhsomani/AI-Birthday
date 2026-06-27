package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactAutomationProfile(
    val id: ContactId,
    val relationshipType: String,
    val healthScore: Int,
    val interactionFrequencyPerMonth: Float,
    val lastRevivalAttemptMs: Long,
    val skipAutoWish: Boolean,
)

