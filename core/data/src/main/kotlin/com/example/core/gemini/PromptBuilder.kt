package com.example.core.gemini

import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.contact.ContactRelationshipPromptContext
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.message.MessagePromptContext
import com.example.domain.service.ContactClassificationContract

class PromptBuilder {
    fun buildClassificationPrompt(contact: ContactClassificationPromptContext): String {
        val firstName = promptFirstName(contact.displayName)
        val sanitizedNotes = sanitizePromptNotes(contact.notesText)
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
        return buildReconnectPromptText(contact, daysSince)
    }

    fun buildPostEventFollowUpPrompt(
        contact: ContactRelationshipPromptContext,
        originalMessage: String,
        eventType: String?,
        eventLabel: String?,
    ): String {
        return buildPostEventFollowUpPromptText(contact, originalMessage, eventType, eventLabel)
    }

    fun buildHolidayWishPrompt(
        contact: ContactRelationshipPromptContext,
        holidayName: String,
        holidayTone: String,
    ): String {
        return buildHolidayWishPromptText(contact, holidayName, holidayTone)
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
        return buildGiftSuggestionsPromptText(contact, history)
    }
}
