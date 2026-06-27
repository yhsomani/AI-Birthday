package com.example.core.gemini

import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.contact.ContactRelationshipPromptContext
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.message.MessagePromptContext
import com.example.domain.service.ContactClassificationContract
import org.json.JSONArray

class PromptBuilder {
    private fun getFirstName(fullName: String): String {
        val trimmed = fullName.trim()
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx == -1) trimmed else trimmed.substring(0, spaceIdx)
    }

    private fun sanitizeNotes(notes: String): String {
        var sanitized = notes
        sanitized = sanitized.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL]")
        sanitized = sanitized.replace(Regex("\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}"), "[PHONE]")
        return sanitized
    }

    fun buildClassificationPrompt(contact: ContactClassificationPromptContext): String {
        val firstName = getFirstName(contact.displayName)
        val sanitizedNotes = sanitizeNotes(contact.notesText)
        return buildString {
            appendLine("You are a contact classification engine. Based on the contact data below, ")
            appendLine("determine their relationship to the phone owner.")
            appendLine()
            appendLine("Contact data:")
            appendLine("- First Name: $firstName")
            appendLine("- Notes: ${sanitizedNotes.take(200)}")
            appendLine("- Interaction frequency: ${contact.interactionFrequencyPerMonth} times/month")
            appendLine()
            appendLine("Return ONLY valid JSON, no explanation, no markdown:")
            appendLine("{")
            appendLine("  \"${ContactClassificationContract.Fields.RELATIONSHIP_TYPE}\": \"${ContactClassificationContract.promptValues(ContactClassificationContract.relationshipTypes)}\",")
            appendLine("  \"${ContactClassificationContract.Fields.RELATIONSHIP_SUBTYPE}\": null,")
            appendLine("  \"${ContactClassificationContract.Fields.CONFIDENCE}\": 0.0,")
            appendLine("  \"${ContactClassificationContract.Fields.LANGUAGE}\": \"${ContactClassificationContract.promptValues(ContactClassificationContract.languageCodes)}\",")
            appendLine("  \"${ContactClassificationContract.Fields.FORMALITY}\": \"${ContactClassificationContract.promptValues(ContactClassificationContract.formalityLevels)}\",")
            appendLine("  \"${ContactClassificationContract.Fields.COMMUNICATION_STYLE}\": \"${ContactClassificationContract.promptValues(ContactClassificationContract.communicationStyles)}\"")
            append("}")
        }
    }

    fun buildMessageGenerationPrompt(context: MessagePromptContext): String {
        val hasSpecificContext = context.interests.isNotEmpty() ||
            context.sharedHistory.isNotEmpty() ||
            context.memoryNotes.isNotEmpty() ||
            context.giftHistory.isNotEmpty()

        return buildString {
            appendLine("You are a personalised message writer. Write a birthday/event wish that sounds ")
            appendLine("EXACTLY like the user personally wrote it — NOT like an AI.")
            appendLine()
            appendLine("STRICT RULES:")
            appendLine("1. Never use generic phrases: \"wishing you all the best\", \"have a great day\", \"many happy returns\"")
            if (hasSpecificContext) {
                appendLine("2. Reference at least one real specific interest, shared memory, Memory Vault note, or gift-history detail from the context")
            } else {
                appendLine("2. Do not invent interests, memories, life events, or private details that are not in the context")
            }
            appendLine("3. Match the user's exact writing style (tone, emojis, sentence length)")
            appendLine("4. Never repeat or paraphrase any previous wish listed below")
            appendLine("5. Write in language: ${context.preferredLanguage}")
            appendLine()
            appendLine("RECIPIENT:")
            appendLine("- Name: ${context.firstName} (call them: ${context.nickname ?: context.firstName})")
            appendLine("- Relationship: ${context.relationshipType}")
            appendLine("- Age turning: ${context.ageTurning ?: "unknown"}")
            appendLine("- Interests: ${context.interests.joinToString(", ")}")
            appendLine("- Shared memories: ${context.sharedHistory.joinToString("; ")}")
            appendLine("- Last spoke: ${context.daysSinceLastContact} days ago")
            appendLine("- Preferred send channel: ${context.preferredChannel.raw}")
            if (!context.currentLifePhase.isNullOrBlank()) {
                appendLine("- Current life phase: ${context.currentLifePhase}")
            }
            if (context.memoryNotes.isNotEmpty()) {
                appendLine("- Memory Vault notes: ${context.memoryNotes.joinToString("; ")}")
            }
            if (context.giftHistory.isNotEmpty()) {
                appendLine("- Gift history: ${context.giftHistory.joinToString("; ")}")
            }
            if (context.sensitiveTopics.isNotEmpty()) {
                appendLine("- Avoid these topics: ${context.sensitiveTopics.joinToString(", ")}")
            }
            appendLine()
            appendLine("EVENT: ${context.eventType} (${context.eventOccurrenceNumber?.let { "turning $it" } ?: ""})")
            appendLine()
            appendLine("USER'S WRITING STYLE:")
            appendLine(context.userStyleSamples.take(3).joinToString("\n") { "  - \"$it\"" })
            appendLine("Uses emojis: ${context.usesEmoji}")
            appendLine("Typical length: ~${context.avgMessageLength} characters")
            appendLine("Common phrases: ${context.commonPhrases.joinToString(", ")}")
            appendLine()
            appendLine("PREVIOUS WISHES SENT TO THIS PERSON (DO NOT REPEAT):")
            appendLine(context.previousWishes.take(5).joinToString("\n") { "  - \"$it\"" })
            appendLine()
            appendLine("Return ONLY valid JSON:")
            appendLine("{")
            appendLine("  \"short\": \"message under 160 chars\",")
            appendLine("  \"standard\": \"message 150-250 chars\",")
            appendLine("  \"long\": \"message 300-450 chars\",")
            appendLine("  \"formal\": \"polished respectful version, 150-250 chars\",")
            appendLine("  \"funny\": \"light funny version that still feels personal, 150-250 chars\",")
            appendLine("  \"emotional\": \"warm heartfelt version, 150-250 chars\",")
            appendLine("  \"recommended\": \"short|standard|long|formal|funny|emotional\",")
            appendLine("  \"reasoning\": \"one sentence\"")
            append("}")

            if (context.preferredLanguage != "en" && context.preferredLanguage.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine("SYSTEM INSTRUCTION:")
                appendLine("Generate ALL message variants in ${context.preferredLanguage}. Use native script and culturally appropriate expressions.")
                appendLine("For Hindi: use natural Hinglish (Hindi-English mix) if the contact's style suggests it.")
                append("For formal contexts in Indian languages: use respectful honorifics appropriate to the relationship.")
            }
        }
    }

    fun buildReconnectPrompt(contact: ContactRelationshipPromptContext, daysSince: Int): String {
        val firstName = getFirstName(contact.displayName)
        val interestsList = try {
            val arr = JSONArray(contact.interestsJson); List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }
        val hobbiesList = try {
            val arr = JSONArray(contact.hobbiesJson); List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }
        val sharedHistoryList = try {
            val arr = JSONArray(contact.sharedHistoryJson); List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }
        val sensitiveTopics = try {
            val arr = JSONArray(contact.sensitiveTopicsJson); List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }
        val sanitizedNotes = sanitizeNotes(contact.notesText).take(180)
        
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

    fun buildPostEventFollowUpPrompt(
        contact: ContactRelationshipPromptContext,
        originalMessage: String,
        eventType: String?,
        eventLabel: String?,
    ): String {
        val firstName = getFirstName(contact.displayName)
        val interestsList = try {
            val arr = JSONArray(contact.interestsJson)
            List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }
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
            appendLine("- Original message already sent: ${sanitizeNotes(originalMessage).take(240)}")
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

    fun buildHolidayWishPrompt(
        contact: ContactRelationshipPromptContext,
        holidayName: String,
        holidayTone: String,
    ): String {
        val firstName = getFirstName(contact.displayName)
        val interestsList = try {
            val arr = JSONArray(contact.interestsJson)
            List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }
        val sharedHistoryList = try {
            val arr = JSONArray(contact.sharedHistoryJson)
            List(arr.length()) { arr.getString(it) }
        } catch(e: Exception) { emptyList() }

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

    fun buildRegenerationPrompt(
        original: String,
        context: MessagePromptContext,
        feedbackInstruction: String? = null
    ): String = buildString {
        if (feedbackInstruction.isNullOrBlank()) {
            appendLine("The following message was rejected for being too similar to a previous wish:")
        } else {
            appendLine("The following message was rejected by the user:")
        }
        appendLine("\"$original\"")
        appendLine()
        if (!feedbackInstruction.isNullOrBlank()) {
            appendLine("Fix this specific issue: $feedbackInstruction")
            appendLine()
        }
        appendLine("Generate a COMPLETELY different message. Different tone, different references,")
        appendLine("different structure. Same context applies:")
        append(buildMessageGenerationPrompt(context))
    }

    fun buildGiftSuggestionsPrompt(contact: ContactGiftAdvisorProfile, history: List<GiftHistoryRecord>): String {
        val firstName = getFirstName(contact.displayName)
        val interestsList = try { 
            val arr = JSONArray(contact.interestsJson); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        val historyStr = history.joinToString("\n") { 
            "  - ${it.giftName} (Category: ${it.giftCategory}, Cost: \u20b9${it.approxCostInr}, Liked: ${it.receivedWell ?: "Unknown"})"
        }
        
        return buildString {
            appendLine("You are a personalized gift advisor. Recommend 3 unique gift ideas for ${contact.nickname ?: firstName}.")
            appendLine()
            appendLine("Recipient Facts:")
            appendLine("- Relationship: ${contact.relationshipType}")
            appendLine("- Interests: ${interestsList.joinToString(", ")}")
            appendLine("- Annual Gift Budget: \u20b9${contact.giftBudgetInr}")
            appendLine()
            appendLine("Previous Gift History:")
            appendLine(if (historyStr.isBlank()) "None recorded" else historyStr)
            appendLine()
            appendLine("Requirements:")
            appendLine("- Provide exactly 3 diverse recommendations.")
            appendLine("- Ensure ideas fit within the annual budget (\u20b9${contact.giftBudgetInr}) and align with the interests.")
            appendLine("- Avoid repeat/similar items to their previous gifts.")
            appendLine("- Give a specific, compelling reason for each.")
            appendLine()
            appendLine("Return ONLY a valid JSON array, no explanation, no markdown:")
            appendLine("[")
            appendLine("  {")
            appendLine("    \"name\": \"Gift Name\",")
            appendLine("    \"reason\": \"Specific reason why they will love it based on their interests\",")
            appendLine("    \"estimatedCostInr\": 500")
            appendLine("  }")
            append("]")
        }
    }
}
