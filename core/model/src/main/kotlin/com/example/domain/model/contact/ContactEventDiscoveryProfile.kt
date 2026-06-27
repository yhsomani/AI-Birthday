package com.example.domain.model.contact

import com.example.domain.model.common.ContactId

data class ContactEventDiscoveryProfile(
    val id: ContactId,
    val displayName: String,
    val birthdayDay: Int?,
    val birthdayMonth: Int?,
    val birthdayYear: Int?,
    val anniversaryDay: Int?,
    val anniversaryMonth: Int?,
    val anniversaryYear: Int?,
    val workStartDay: Int?,
    val workStartMonth: Int?,
    val workStartYear: Int?,
)
