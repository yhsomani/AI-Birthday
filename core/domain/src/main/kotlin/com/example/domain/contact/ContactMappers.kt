package com.example.domain.contact

import com.example.core.db.entities.ContactEntity
import com.example.core.db.dao.RelationshipTypeCount
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAnalyticsProfile
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.contact.ContactAutomationProfile
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.contact.ContactEventDiscoveryProfile
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.contact.ContactHealthProfile
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.contact.ContactListItem
import com.example.domain.model.contact.ContactMessagePromptContext
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.contact.ContactRelationshipPromptContext
import com.example.domain.model.contact.ContactWishContext
import com.example.domain.model.contact.RelationshipAnalyticsCount
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.dispatch.MessageDispatchRecipient

fun ContactEntity.toAutomationProfile(): ContactAutomationProfile {
    return ContactAutomationProfile(
        id = ContactId(id),
        relationshipType = relationshipType,
        healthScore = healthScore,
        interactionFrequencyPerMonth = interactionFrequencyPerMonth,
        lastRevivalAttemptMs = lastRevivalAttemptMs,
        skipAutoWish = skipAutoWish,
    )
}

fun ContactEntity.toAutomationReadinessProfile(): ContactAutomationReadinessProfile {
    return ContactAutomationReadinessProfile(
        id = ContactId(id),
        preferredChannel = MessageChannel.fromRaw(preferredChannel),
        nickname = nickname,
        notesText = notesText,
        interestsJson = interestsJson,
        sharedHistoryJson = sharedHistoryJson,
        classificationConfidence = classificationConfidence,
    )
}

fun ContactEntity.toDeliveryRouteProfile(): ContactDeliveryRouteProfile {
    return ContactDeliveryRouteProfile(
        preferredChannel = MessageChannel.fromRaw(preferredChannel),
        hasPrimaryPhone = !primaryPhone.isNullOrBlank(),
        hasPrimaryEmail = !primaryEmail.isNullOrBlank(),
    )
}

fun ContactEntity.toClassificationPromptContext(): ContactClassificationPromptContext {
    return ContactClassificationPromptContext(
        id = ContactId(id),
        displayName = name,
        notesText = notesText,
        interactionFrequencyPerMonth = interactionFrequencyPerMonth,
    )
}

fun ContactEntity.toHealthProfile(): ContactHealthProfile {
    return ContactHealthProfile(
        id = ContactId(id),
        currentHealthScore = healthScore,
        interactionFrequencyPerMonth = interactionFrequencyPerMonth,
        lastInteractionAtMs = lastInteractionDate,
        lastWishedAtMs = lastWishedDate,
        consecutiveYearsWished = consecutiveYearsWished,
    )
}

fun ContactEntity.toEventDiscoveryProfile(): ContactEventDiscoveryProfile {
    return ContactEventDiscoveryProfile(
        id = ContactId(id),
        displayName = name,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        birthdayYear = birthdayYear,
        anniversaryDay = anniversaryDay,
        anniversaryMonth = anniversaryMonth,
        anniversaryYear = anniversaryYear,
        workStartDay = workStartDay,
        workStartMonth = workStartMonth,
        workStartYear = workStartYear,
    )
}

fun ContactEntity.toAnalyticsSummary(): ContactAnalyticsSummary {
    return ContactAnalyticsSummary(
        id = ContactId(id),
        displayName = name,
        healthScore = healthScore,
        relationshipType = relationshipType,
    )
}

fun ContactEntity.toAnalyticsProfile(): ContactAnalyticsProfile {
    return ContactAnalyticsProfile(
        id = ContactId(id),
        healthScore = healthScore,
        nickname = nickname,
        notesText = notesText,
        interestsJson = interestsJson,
        sharedHistoryJson = sharedHistoryJson,
    )
}

fun ContactEntity.toDetailProfile(): ContactDetailProfile {
    return ContactDetailProfile(
        id = ContactId(id),
        displayName = name,
        contactGroup = contactGroup,
        healthScore = healthScore,
        nickname = nickname,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        primaryPhone = primaryPhone,
        primaryEmail = primaryEmail,
        relationshipType = relationshipType,
        preferredLanguage = preferredLanguage,
        preferredChannel = MessageChannel.fromRaw(preferredChannel),
        formalityLevel = formalityLevel,
        communicationStyle = communicationStyle,
        automationMode = ApprovalMode.fromRaw(automationMode),
        customSendTimeHour = customSendTimeHour,
        customSendTimeMinute = customSendTimeMinute,
        giftBudgetInr = giftBudgetInr,
        annualBudgetInr = annualBudgetInr,
        skipAutoWish = skipAutoWish,
        interestsJson = interestsJson,
        sensitiveTopicsJson = sensitiveTopicsJson,
        currentLifePhaseJson = currentLifePhaseJson,
        notesText = notesText,
    )
}

fun ContactEntity.toWishContext(): ContactWishContext {
    return ContactWishContext(
        id = ContactId(id),
        relationshipType = relationshipType,
        preferredLanguage = preferredLanguage,
    )
}

fun ContactEntity.toHeader(): ContactHeader {
    return ContactHeader(
        id = ContactId(id),
        displayName = name,
    )
}

fun ContactEntity.toGiftAdvisorProfile(): ContactGiftAdvisorProfile {
    return ContactGiftAdvisorProfile(
        id = ContactId(id),
        displayName = name,
        nickname = nickname,
        relationshipType = relationshipType,
        interestsJson = interestsJson,
        giftBudgetInr = giftBudgetInr,
    )
}

fun ContactEntity.toRelationshipPromptContext(): ContactRelationshipPromptContext {
    return ContactRelationshipPromptContext(
        id = ContactId(id),
        displayName = name,
        nickname = nickname,
        relationshipType = relationshipType,
        relationshipSubtype = relationshipSubtype,
        preferredLanguage = preferredLanguage,
        formalityLevel = formalityLevel,
        communicationStyle = communicationStyle,
        healthScore = healthScore,
        interactionFrequencyPerMonth = interactionFrequencyPerMonth,
        interestsJson = interestsJson,
        hobbiesJson = hobbiesJson,
        sharedHistoryJson = sharedHistoryJson,
        sensitiveTopicsJson = sensitiveTopicsJson,
        notesText = notesText,
    )
}

fun ContactEntity.toMessagePromptContact(): ContactMessagePromptContext {
    return ContactMessagePromptContext(
        id = ContactId(id),
        displayName = name,
        nickname = nickname,
        relationshipType = relationshipType,
        birthdayYear = birthdayYear,
        interestsJson = interestsJson,
        sharedHistoryJson = sharedHistoryJson,
        lastInteractionAtMs = lastInteractionDate,
        preferredLanguage = preferredLanguage,
        formalityLevel = formalityLevel,
        sensitiveTopicsJson = sensitiveTopicsJson,
        currentLifePhaseJson = currentLifePhaseJson,
        preferredChannel = preferredChannel,
    )
}

fun ContactEntity.toListItem(): ContactListItem {
    return ContactListItem(
        id = ContactId(id),
        displayName = name,
        nickname = nickname,
        company = company,
        contactGroup = contactGroup,
        relationshipType = relationshipType,
        healthScore = healthScore,
        automationMode = ApprovalMode.fromRaw(automationMode),
        preferredChannel = MessageChannel.fromRaw(preferredChannel),
        primaryPhone = primaryPhone,
        secondaryPhone = secondaryPhone,
        primaryEmail = primaryEmail,
        birthdayDay = birthdayDay,
        birthdayMonth = birthdayMonth,
        anniversaryDay = anniversaryDay,
        anniversaryMonth = anniversaryMonth,
        workStartDay = workStartDay,
        workStartMonth = workStartMonth,
        notesText = notesText,
        interestsJson = interestsJson,
        sharedHistoryJson = sharedHistoryJson,
        classificationConfidence = classificationConfidence,
    )
}

fun ContactEntity.toPickerItem(): ContactPickerItem {
    return ContactPickerItem(
        id = ContactId(id),
        displayName = name,
    )
}

fun ContactEntity.toMessageContext(): ContactMessageContext {
    return ContactMessageContext(
        id = ContactId(id),
        displayName = name,
        avatarUrl = profilePhotoUri,
        primaryPhone = primaryPhone,
        primaryEmail = primaryEmail,
    )
}

fun ContactEntity.toMessageDispatchRecipient(): MessageDispatchRecipient {
    return MessageDispatchRecipient(
        id = ContactId(id),
        displayName = name,
        primaryPhone = primaryPhone,
        primaryEmail = primaryEmail,
    )
}

fun RelationshipTypeCount.toRelationshipAnalyticsCount(): RelationshipAnalyticsCount {
    return RelationshipAnalyticsCount(
        relationshipType = relationshipType,
        count = count,
    )
}
