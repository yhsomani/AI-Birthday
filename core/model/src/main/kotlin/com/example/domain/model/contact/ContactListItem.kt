package com.example.domain.model.contact

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId

data class ContactListItem(
    val id: ContactId,
    val displayName: String,
    val nickname: String?,
    val company: String?,
    val contactGroup: String?,
    val relationshipType: String,
    val healthScore: Int,
    val automationMode: ApprovalMode,
    val preferredChannel: MessageChannel,
    val primaryPhone: String?,
    val secondaryPhone: String?,
    val primaryEmail: String?,
    val birthdayDay: Int?,
    val birthdayMonth: Int?,
    val anniversaryDay: Int?,
    val anniversaryMonth: Int?,
    val workStartDay: Int?,
    val workStartMonth: Int?,
    val notesText: String,
    val interestsJson: String,
    val sharedHistoryJson: String,
    val classificationConfidence: Double,
)

data class ContactPickerItem(
    val id: ContactId,
    val displayName: String,
)
