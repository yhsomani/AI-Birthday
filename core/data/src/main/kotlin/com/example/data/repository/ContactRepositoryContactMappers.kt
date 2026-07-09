package com.example.data.repository

import com.example.core.db.dao.ContactDao
import com.example.core.db.entities.ContactEntity
import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.model.occasion.OccasionType

internal suspend fun ContactDao.updateContactEventDate(
    id: String,
    eventType: OccasionType,
    day: Int,
    month: Int,
    year: Int?,
    updatedAt: Long,
) {
    when (eventType) {
        OccasionType.BIRTHDAY -> updateBirthdayDate(id, day, month, year, updatedAt)
        OccasionType.ANNIVERSARY -> updateAnniversaryDate(id, day, month, year, updatedAt)
        OccasionType.WORK_ANNIVERSARY -> updateWorkStartDate(id, day, month, year, updatedAt)
        else -> Unit
    }
}

internal fun ContactEntity.withEventDate(
    eventType: OccasionType,
    day: Int,
    month: Int,
    year: Int?,
    updatedAt: Long,
): ContactEntity {
    return when (eventType) {
        OccasionType.BIRTHDAY -> copy(
            birthdayDay = day,
            birthdayMonth = month,
            birthdayYear = year,
            updatedAt = updatedAt,
        )
        OccasionType.ANNIVERSARY -> copy(
            anniversaryDay = day,
            anniversaryMonth = month,
            anniversaryYear = year,
            updatedAt = updatedAt,
        )
        OccasionType.WORK_ANNIVERSARY -> copy(
            workStartDay = day,
            workStartMonth = month,
            workStartYear = year,
            updatedAt = updatedAt,
        )
        else -> copy(updatedAt = updatedAt)
    }
}

internal fun ContactSyncRecord.toEntity(): ContactEntity {
    return ContactEntity(
        id = id,
        googleContactId = googleContactId,
        name = displayName,
        nickname = nickname,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        birthdayYear = birthdayYear,
        anniversaryDay = anniversaryDay,
        anniversaryMonth = anniversaryMonth,
        anniversaryYear = anniversaryYear,
        workStartDay = workStartDay,
        workStartMonth = workStartMonth,
        workStartYear = workStartYear,
        primaryPhone = primaryPhone,
        secondaryPhone = secondaryPhone,
        primaryEmail = primaryEmail,
        company = company,
        jobTitle = jobTitle,
        address = address,
        profilePhotoUri = profilePhotoUri,
        contactGroup = contactGroup,
        relationshipType = relationshipType,
        relationsJson = relationsJson,
        notesText = notesText,
        isDeleted = isDeleted,
    )
}
