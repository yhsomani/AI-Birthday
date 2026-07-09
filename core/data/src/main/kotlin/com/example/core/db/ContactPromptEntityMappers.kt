package com.example.core.db

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactClassificationProfile
import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.contact.ContactMessageGenerationProfile
import com.example.domain.model.contact.ContactMessagePromptContext
import com.example.domain.model.contact.ContactRelationshipPromptContext

fun ContactEntity.toClassificationPromptContext(): ContactClassificationPromptContext {
    return ContactClassificationPromptContext(
        id = ContactId(id),
        displayName = name,
        notesText = notesText,
        interactionFrequencyPerMonth = interactionFrequencyPerMonth,
    )
}

fun ContactEntity.toClassificationProfile(): ContactClassificationProfile {
    return ContactClassificationProfile(
        id = ContactId(id),
        relationshipType = relationshipType,
        promptContext = toClassificationPromptContext(),
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

fun ContactEntity.toMessageGenerationProfile(): ContactMessageGenerationProfile {
    return ContactMessageGenerationProfile(
        id = ContactId(id),
        relationshipType = relationshipType,
        automationMode = ApprovalMode.fromRaw(automationMode),
        skipAutoWish = skipAutoWish,
        deliveryRouteProfile = toDeliveryRouteProfile(),
        promptContext = toMessagePromptContact(),
        header = toHeader(),
        customSendTimeHour = customSendTimeHour,
        customSendTimeMinute = customSendTimeMinute,
    )
}
