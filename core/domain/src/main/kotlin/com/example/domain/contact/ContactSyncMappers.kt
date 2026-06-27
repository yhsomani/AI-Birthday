package com.example.domain.contact

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.contact.ContactSyncRecord

fun ContactSyncRecord.toEntity(): ContactEntity {
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

fun Iterable<ContactSyncRecord>.toEntities(): List<ContactEntity> {
    return map { it.toEntity() }
}
