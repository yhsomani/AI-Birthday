package com.example.domain.model.contact

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId

data class ContactPreferences(
    val contactId: ContactId,
    val nickname: String?,
    val relationshipType: String,
    val preferredLanguage: String,
    val preferredChannel: MessageChannel,
    val formalityLevel: String,
    val communicationStyle: String,
    val automationMode: ApprovalMode,
    val customSendTimeHour: Int?,
    val customSendTimeMinute: Int?,
    val giftBudgetInr: Int,
    val annualBudgetInr: Int,
    val skipAutoWish: Boolean,
    val interestsJson: String,
    val sensitiveTopicsJson: String,
    val currentLifePhaseJson: String,
    val notesText: String,
    val updatedAtMs: Long,
)
