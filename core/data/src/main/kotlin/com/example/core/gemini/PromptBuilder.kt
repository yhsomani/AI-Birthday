package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import org.json.JSONArray

data class ContactContextObject(
    val name: String,
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
    fun buildClassificationPrompt(contact: ContactEntity): String = """
        You are a contact classification engine. Based on the contact data below, 
        determine their relationship to the phone owner.

        Contact data:
        - Name: ${contact.name}
        - Company: ${contact.company ?: "unknown"}
        - Notes: ${contact.notesText.take(200)}
        - Interaction frequency: ${contact.interactionFrequencyPerMonth} times/month

        Return ONLY valid JSON, no explanation, no markdown:
        {
          "type": "FAMILY|BEST_FRIEND|CLOSE_FRIEND|FRIEND|RELATIVE|COLLEAGUE|CLIENT|MANAGER|MENTOR|ALUMNI|VENDOR|UNKNOWN",
          "confidence": 0.0,
          "language": "en|hi|mr|gu|ta|te|bn|pa",
          "formality": "CASUAL|SEMI_FORMAL|FORMAL"
        }
    """.trimIndent()

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
            name = contact.name,
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

    fun buildMessageGenerationPrompt(context: ContactContextObject): String = """
        You are a personalised message writer. Write a birthday/event wish that sounds 
        EXACTLY like the user personally wrote it — NOT like an AI.

        STRICT RULES:
        1. Never use generic phrases: "wishing you all the best", "have a great day", "many happy returns"
        2. Reference at least one specific interest or shared memory from the context
        3. Match the user's exact writing style (tone, emojis, sentence length)
        4. Never repeat or paraphrase any previous wish listed below
        5. Write in language: ${context.preferredLanguage}

        RECIPIENT:
        - Name: ${context.name} (call them: ${context.nickname ?: context.name})
        - Relationship: ${context.relationshipType}
        - Age turning: ${context.ageTurning ?: "unknown"}
        - Interests: ${context.interests.joinToString(", ")}
        - Shared memories: ${context.sharedHistory.joinToString("; ")}
        - Last spoke: ${context.daysSinceLastContact} days ago

        EVENT: ${context.eventType} (${context.eventOccurrenceNumber?.let { "turning ${'$'}it" } ?: ""})

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

    fun buildReconnectPrompt(contact: ContactEntity, daysSince: Int): String = """
        Write a short, casual reconnect message from the user to ${contact.nickname ?: contact.name}.

        Facts:
        - Relationship: ${contact.relationshipType}
        - Last spoke: $daysSince days ago
        - Interests: ${contact.interestsJson}
        - User style: casual, ${if (contact.formalityLevel == "CASUAL") "uses bro/yaar type language" else "professional"}

        Requirements:
        - Sound spontaneous, like they just thought of them
        - DO NOT mention the gap in contact explicitly
        - End with a question to start a conversation
        - Under 100 words

        Return plain text only. No JSON. No quotes.
    """.trimIndent()

    fun buildRegenerationPrompt(original: String, context: ContactContextObject): String = """
        The following message was rejected for being too similar to a previous wish:
        "$original"

        Generate a COMPLETELY different message. Different tone, different references, 
        different structure. Same context applies:
        ${buildMessageGenerationPrompt(context)}
    """.trimIndent()
}
