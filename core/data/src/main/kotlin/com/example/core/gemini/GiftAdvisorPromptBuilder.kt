package com.example.core.gemini

import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.common.JsonTextCodec
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.message.PromptTextFormatter

internal fun buildGiftSuggestionsPromptText(
    contact: ContactGiftAdvisorProfile,
    history: List<GiftHistoryRecord>,
): String {
    val firstName = PromptTextFormatter.firstName(contact.displayName)
    val interestsList = JsonTextCodec.parseStringArray(contact.interestsJson)
    val historyText = history.joinToString("\n") {
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
        appendLine(if (historyText.isBlank()) "None recorded" else historyText)
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
