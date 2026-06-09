package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import org.json.JSONArray

data class ContactContextObject(
    val firstName: String,
    val nickname: String?,
    val relationshipType: String,
    val knownSince: String?,
    val ageTurning: Int?,
    val interests: List<String>,
    val sharedHistory: List<String>,
    val daysSinceLastContact: Int,
    val eventType: String,
    val eventOccurrenceNumber: Int?,
    val preferredLanguage: String,
    val userStyleSamples: List<String>,
    val usesEmoji: Boolean,
    val avgMessageLength: Int,
    val commonPhrases: List<String>,
    val previousWishes: List<String>,
    val formalityLevel: String
)

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

    fun buildClassificationPrompt(contact: ContactEntity): String {
        val firstName = getFirstName(contact.name)
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
            appendLine("  \"type\": \"FAMILY|BEST_FRIEND|CLOSE_FRIEND|FRIEND|RELATIVE|COLLEAGUE|CLIENT|MANAGER|MENTOR|ALUMNI|VENDOR|UNKNOWN\",")
            appendLine("  \"confidence\": 0.0,")
            appendLine("  \"language\": \"en|hi|mr|gu|ta|te|bn|pa\",")
            appendLine("  \"formality\": \"CASUAL|SEMI_FORMAL|FORMAL\"")
            append("}")
        }
    }

    fun buildContactContext(
        contact: ContactEntity,
        event: EventEntity,
        styleProfile: StyleProfileEntity?,
        previousMessages: List<SentMessageEntity>
    ): ContactContextObject {
        val lastInteraction = contact.lastInteractionDate
        val daysSince = if (lastInteraction != null)
            ((System.currentTimeMillis() - lastInteraction) / (1000 * 60 * 60 * 24)).toInt()
        else 0
        
        val parsedInterests = try { 
            val arr = JSONArray(contact.interestsJson); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        val parsedHistory = try { 
            val arr = JSONArray(contact.sharedHistoryJson); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        val parsedSamples = try { 
            val arr = JSONArray(styleProfile?.sampleMessagesJson ?: "[]"); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        val parsedPhrases = try { 
            val arr = JSONArray(styleProfile?.commonPhrasesJson ?: "[]"); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }

        val birthdayYear = contact.birthdayYear
        val eventYear = event.year
        val ageTurning = if (event.type == "BIRTHDAY" && birthdayYear != null && eventYear != null) {
            eventYear - birthdayYear
        } else null

        return ContactContextObject(
            firstName = getFirstName(contact.name),
            nickname = contact.nickname,
            relationshipType = contact.relationshipType,
            knownSince = null,
            ageTurning = ageTurning,
            interests = parsedInterests,
            sharedHistory = parsedHistory,
            daysSinceLastContact = daysSince,
            eventType = event.type,
            eventOccurrenceNumber = ageTurning,
            preferredLanguage = contact.preferredLanguage,
            userStyleSamples = parsedSamples,
            usesEmoji = styleProfile?.usesEmoji ?: true,
            avgMessageLength = styleProfile?.avgMessageLength ?: 120,
            commonPhrases = parsedPhrases,
            previousWishes = previousMessages.map { it.messageText },
            formalityLevel = contact.formalityLevel
        )
    }

    fun buildMessageGenerationPrompt(context: ContactContextObject): String {
        return buildString {
            appendLine("You are a personalised message writer. Write a birthday/event wish that sounds ")
            appendLine("EXACTLY like the user personally wrote it — NOT like an AI.")
            appendLine()
            appendLine("STRICT RULES:")
            appendLine("1. Never use generic phrases: \"wishing you all the best\", \"have a great day\", \"many happy returns\"")
            appendLine("2. Reference at least one specific interest or shared memory from the context")
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

    fun buildReconnectPrompt(contact: ContactEntity, daysSince: Int): String {
        val firstName = getFirstName(contact.name)
        val interestsList = try { 
            val arr = JSONArray(contact.interestsJson); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        return buildString {
            appendLine("Write a short, casual reconnect message from the user to ${contact.nickname ?: firstName}.")
            appendLine()
            appendLine("Facts:")
            appendLine("- Relationship: ${contact.relationshipType}")
            appendLine("- Last spoke: $daysSince days ago")
            appendLine("- Interests: ${interestsList.joinToString(", ")}")
            appendLine("- User style: casual, ${if (contact.formalityLevel == "CASUAL") "uses bro/yaar type language" else "professional"}")
            appendLine()
            appendLine("Requirements:")
            appendLine("- Sound spontaneous, like they just thought of them")
            appendLine("- DO NOT mention the gap in contact explicitly")
            appendLine("- End with a question to start a conversation")
            appendLine("- Under 100 words")
            appendLine()
            append("Return plain text only. No JSON. No quotes.")
        }
    }

    fun buildRegenerationPrompt(original: String, context: ContactContextObject): String = buildString {
        appendLine("The following message was rejected for being too similar to a previous wish:")
        appendLine("\"$original\"")
        appendLine()
        appendLine("Generate a COMPLETELY different message. Different tone, different references, ")
        appendLine("different structure. Same context applies:")
        append(buildMessageGenerationPrompt(context))
    }

    fun buildGiftSuggestionsPrompt(contact: ContactEntity, history: List<GiftHistoryEntity>): String {
        val firstName = getFirstName(contact.name)
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
