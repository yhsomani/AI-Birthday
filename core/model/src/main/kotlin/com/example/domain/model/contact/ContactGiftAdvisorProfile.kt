package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactGiftAdvisorProfile(
    val id: ContactId,
    val displayName: String,
    val nickname: String?,
    val relationshipType: String,
    val interestsJson: String,
    val giftBudgetInr: Int,
)
