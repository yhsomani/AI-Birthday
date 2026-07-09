package com.example.core.gemini

import com.example.domain.model.contact.ContactRelationshipPromptContext

internal fun buildReconnectPromptText(
    contact: ContactRelationshipPromptContext,
    daysSince: Int,
): String {
    val firstName = promptFirstName(contact.displayName)
    val interestsList = parsePromptStringList(contact.interestsJson)
    val hobbiesList = parsePromptStringList(contact.hobbiesJson)
    val sharedHistoryList = parsePromptStringList(contact.sharedHistoryJson)
    val sensitiveTopics = parsePromptStringList(contact.sensitiveTopicsJson)
    val sanitizedNotes = sanitizePromptNotes(contact.notesText).take(180)

    return buildString {
        appendLine("Write a short, casual reconnect message from the user to ${contact.nickname ?: firstName}.")
        appendLine()
        appendLine("Facts:")
        appendLine("- Relationship: ${contact.relationshipType}")
        contact.relationshipSubtype?.takeIf { it.isNotBlank() }?.let {
            appendLine("- Relationship detail: $it")
        }
        appendLine("- Last spoke: $daysSince days ago")
        appendLine("- Relationship health score: ${contact.healthScore}/100")
        appendLine("- Interaction frequency: ${contact.interactionFrequencyPerMonth} times/month")
        appendLine("- Interests: ${interestsList.joinToString(", ")}")
        appendLine("- Hobbies: ${hobbiesList.joinToString(", ")}")
        appendLine("- Shared history: ${sharedHistoryList.take(4).joinToString(", ")}")
        if (sanitizedNotes.isNotBlank()) {
            appendLine("- Safe notes: $sanitizedNotes")
        }
        if (sensitiveTopics.isNotEmpty()) {
            appendLine("- Avoid these topics: ${sensitiveTopics.joinToString(", ")}")
        }
        appendLine("- User style: casual, ${if (contact.formalityLevel == "CASUAL") "uses bro/yaar type language" else "professional"}")
        appendLine()
        appendLine("Requirements:")
        appendLine("- Sound spontaneous, like they just thought of them")
        appendLine("- DO NOT mention the gap in contact explicitly")
        appendLine("- Use only facts listed above; do not invent memories, problems, or life events")
        appendLine("- End with a question to start a conversation")
        appendLine("- Under 100 words")
        if (contact.preferredLanguage != "en" && contact.preferredLanguage.isNotBlank()) {
            appendLine("- Write in ${contact.preferredLanguage}")
        }
        appendLine()
        append("Return plain text only. No JSON. No quotes.")
    }
}

internal fun buildPostEventFollowUpPromptText(
    contact: ContactRelationshipPromptContext,
    originalMessage: String,
    eventType: String?,
    eventLabel: String?,
): String {
    val firstName = promptFirstName(contact.displayName)
    val interestsList = parsePromptStringList(contact.interestsJson)
    val eventName = eventLabel?.takeIf { it.isNotBlank() } ?: eventType ?: "recent occasion"

    return buildString {
        appendLine("Write a short follow-up message from the user to ${contact.nickname ?: firstName}.")
        appendLine()
        appendLine("Context:")
        appendLine("- Relationship: ${contact.relationshipType}")
        appendLine("- Event: $eventName")
        appendLine("- Preferred language: ${contact.preferredLanguage}")
        appendLine("- Formality: ${contact.formalityLevel}")
        appendLine("- Interests: ${interestsList.joinToString(", ")}")
        appendLine("- Original message already sent: ${sanitizePromptNotes(originalMessage).take(240)}")
        appendLine()
        appendLine("Requirements:")
        appendLine("- Sound natural, low-pressure, and personal")
        appendLine("- Do not repeat the original message")
        appendLine("- Do not ask why they did not reply")
        appendLine("- Ask one light question or share one warm check-in")
        appendLine("- Under 70 words")
        appendLine()
        append("Return plain text only. No JSON. No quotes.")
    }
}

internal fun buildHolidayWishPromptText(
    contact: ContactRelationshipPromptContext,
    holidayName: String,
    holidayTone: String,
): String {
    val firstName = promptFirstName(contact.displayName)
    val interestsList = parsePromptStringList(contact.interestsJson)
    val sharedHistoryList = parsePromptStringList(contact.sharedHistoryJson)

    return buildString {
        appendLine("Write a short ${holidayName} message from the user to ${contact.nickname ?: firstName}.")
        appendLine()
        appendLine("Recipient facts:")
        appendLine("- Relationship: ${contact.relationshipType}")
        appendLine("- Preferred language: ${contact.preferredLanguage}")
        appendLine("- Formality: ${contact.formalityLevel}")
        appendLine("- Communication style: ${contact.communicationStyle}")
        appendLine("- Interests: ${interestsList.joinToString(", ")}")
        appendLine("- Shared memories: ${sharedHistoryList.joinToString("; ")}")
        appendLine("- Holiday tone: $holidayTone")
        appendLine()
        appendLine("Requirements:")
        appendLine("- Sound personal and natural, not like a broadcast")
        appendLine("- Match the relationship and formality")
        appendLine("- Reference a real interest or shared memory only if one is listed")
        appendLine("- Do not invent private details")
        appendLine("- Under 80 words")
        appendLine()
        append("Return plain text only. No JSON. No quotes.")
    }
}
