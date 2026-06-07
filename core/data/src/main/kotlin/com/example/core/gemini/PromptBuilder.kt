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
        return """
            You are a contact classification engine. Based on the contact data below, 
            determine their relationship to the phone owner.

            Contact data:
            - First Name: $firstName
            - Notes: ${sanitizedNotes.take(200)}
            - Interaction frequency: ${contact.interactionFrequencyPerMonth} times/month

            Return ONLY valid JSON, no explanation, no markdown:
            {
              "type": "FAMILY|BEST_FRIEND|CLOSE_FRIEND|FRIEND|RELATIVE|COLLEAGUE|CLIENT|MANAGER|MENTOR|ALUMNI|VENDOR|UNKNOWN",
              "confidence": 0.0,
              "language": "en|hi|mr|gu|ta|te|bn|pa",
              "formality": "CASUAL|SEMI_FORMAL|FORMAL"
            }
        """.trimIndent()
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
        val basePrompt = """
            You are a personalised message writer. Write a birthday/event wish that sounds 
            EXACTLY like the user personally wrote it — NOT like an AI.
    
            STRICT RULES:
            1. Never use generic phrases: "wishing you all the best", "have a great day", "many happy returns"
            2. Reference at least one specific interest or shared memory from the context
            3. Match the user's exact writing style (tone, emojis, sentence length)
            4. Never repeat or paraphrase any previous wish listed below
            5. Write in language: ${context.preferredLanguage}
    
            RECIPIENT:
            - Name: ${context.firstName} (call them: ${context.nickname ?: context.firstName})
            - Relationship: ${context.relationshipType}
            - Age turning: ${context.ageTurning ?: "unknown"}
            - Interests: ${context.interests.joinToString(", ")}
            - Shared memories: ${context.sharedHistory.joinToString("; ")}
            - Last spoke: ${context.daysSinceLastContact} days ago
    
            EVENT: ${context.eventType} (${context.eventOccurrenceNumber?.let { "turning $it" } ?: ""})
    
            USER'S WRITING STYLE:
            ${context.userStyleSamples.take(3).joinToString("\n") { "  - \"$it\"" }}
            Uses emojis: ${context.usesEmoji}
            Typical length: ~${context.avgMessageLength} characters
            Common phrases: ${context.commonPhrases.joinToString(", ")}
    
            PREVIOUS WISHES SENT TO THIS PERSON (DO NOT REPEAT):
            ${context.previousWishes.take(5).joinToString("\n") { "  - \"$it\"" }}
    
            Return ONLY valid JSON:
            {
              "short": "message under 160 chars",
              "standard": "message 150-250 chars",
              "long": "message 300-450 chars",
              "recommended": "short|standard|long",
              "reasoning": "one sentence"
            }
        """.trimIndent()

        return if (context.preferredLanguage != "en" && context.preferredLanguage.isNotBlank()) {
            basePrompt + "\n\nSYSTEM INSTRUCTION:\nGenerate ALL message variants in ${context.preferredLanguage}. Use native script and culturally appropriate expressions.\nFor Hindi: use natural Hinglish (Hindi-English mix) if the contact's style suggests it.\nFor formal contexts in Indian languages: use respectful honorifics appropriate to the relationship."
        } else {
            basePrompt
        }
    }

    fun buildReconnectPrompt(contact: ContactEntity, daysSince: Int): String {
        val firstName = getFirstName(contact.name)
        val interestsList = try { 
            val arr = JSONArray(contact.interestsJson); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        return """
            Write a short, casual reconnect message from the user to ${contact.nickname ?: firstName}.
    
            Facts:
            - Relationship: ${contact.relationshipType}
            - Last spoke: $daysSince days ago
            - Interests: ${interestsList.joinToString(", ")}
            - User style: casual, ${if (contact.formalityLevel == "CASUAL") "uses bro/yaar type language" else "professional"}
    
            Requirements:
            - Sound spontaneous, like they just thought of them
            - DO NOT mention the gap in contact explicitly
            - End with a question to start a conversation
            - Under 100 words
    
            Return plain text only. No JSON. No quotes.
        """.trimIndent()
    }

    fun buildRegenerationPrompt(original: String, context: ContactContextObject): String = """
        The following message was rejected for being too similar to a previous wish:
        "$original"

        Generate a COMPLETELY different message. Different tone, different references, 
        different structure. Same context applies:
        ${buildMessageGenerationPrompt(context)}
    """.trimIndent()

    fun buildGiftSuggestionsPrompt(contact: ContactEntity, history: List<GiftHistoryEntity>): String {
        val firstName = getFirstName(contact.name)
        val interestsList = try { 
            val arr = JSONArray(contact.interestsJson); List(arr.length()) { arr.getString(it) } 
        } catch(e: Exception) { emptyList() }
        
        val historyStr = history.joinToString("\n") { 
            "  - ${it.giftName} (Category: ${it.giftCategory}, Cost: \u20b9${it.approxCostInr}, Liked: ${it.receivedWell ?: "Unknown"})"
        }
        
        return """
            You are a personalized gift advisor. Recommend 3 unique gift ideas for ${contact.nickname ?: firstName}.
            
            Recipient Facts:
            - Relationship: ${contact.relationshipType}
            - Interests: ${interestsList.joinToString(", ")}
            - Annual Gift Budget: \u20b9${contact.giftBudgetInr}
            
            Previous Gift History:
            ${if (historyStr.isBlank()) "None recorded" else historyStr}
            
            Requirements:
            - Provide exactly 3 diverse recommendations.
            - Ensure ideas fit within the annual budget (\u20b9${contact.giftBudgetInr}) and align with the interests.
            - Avoid repeat/similar items to their previous gifts.
            - Give a specific, compelling reason for each.
            
            Return ONLY a valid JSON array, no explanation, no markdown:
            [
              {
                "name": "Gift Name",
                "reason": "Specific reason why they will love it based on their interests",
                "estimatedCostInr": 500
              }
            ]
        """.trimIndent()
    }
}
