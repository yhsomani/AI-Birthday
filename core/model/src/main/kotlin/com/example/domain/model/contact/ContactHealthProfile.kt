package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactHealthProfile(
    val id: ContactId,
    val currentHealthScore: Int,
    val interactionFrequencyPerMonth: Float,
    val lastInteractionAtMs: Long?,
    val lastWishedAtMs: Long?,
    val consecutiveYearsWished: Int,
)
