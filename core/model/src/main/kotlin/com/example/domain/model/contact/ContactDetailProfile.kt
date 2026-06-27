package com.example.domain.model.contact

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId

data class ContactDetailProfile(
    val id: ContactId,
    val displayName: String,
    val contactGroup: String?,
    val healthScore: Int,
    val nickname: String?,
    val birthdayDay: Int?,
    val birthdayMonth: Int?,
    val primaryPhone: String?,
    val primaryEmail: String?,
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
)
