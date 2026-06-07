package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import org.json.JSONObject

data class ClassificationResult(
    val type: String,
    val subtype: String?,
    val confidence: Double,
    val language: String,
    val formality: String,
    val communicationStyle: String
)

data class MessageVariants(
    val short: String,
    val standard: String,
    val long: String,
    val formal: String,
    val funny: String,
    val emotional: String,
    val recommended: String
) {
    fun get(variant: String): String {
        return when (variant) {
            "short" -> short
            "long" -> long
            "formal" -> formal
            "funny" -> funny
            "emotional" -> emotional
            else -> standard
        }
    }
    
    companion object {
        fun fromFallback(text: String): MessageVariants {
            return MessageVariants(text, text, text, text, text, text, "standard")
        }
    }
}

object ResponseParser {
    fun parseContactClassification(jsonString: String): ClassificationResult {
        return try {
            val json = JSONObject(jsonString)
            ClassificationResult(
                type = json.optString("type", "UNKNOWN"),
                subtype = if (json.isNull("subtype")) null else json.optString("subtype"),
                confidence = json.optDouble("confidence", 0.0),
                language = json.optString("language", "en"),
                formality = json.optString("formality", "CASUAL"),
                communicationStyle = json.optString("communication_style", "WARM")
            )
        } catch (e: Exception) {
            ClassificationResult("UNKNOWN", null, 0.0, "en", "CASUAL", "WARM")
        }
    }

    fun parseMessageVariants(jsonString: String): MessageVariants {
        return try {
            val json = JSONObject(jsonString)
            MessageVariants(
                short = json.optString("short", "Happy Birthday!"),
                standard = json.optString("standard", "Wishing you a very happy birthday!"),
                long = json.optString("long", "Wishing you a very happy birthday! Hope you have a wonderful day!"),
                formal = json.optString("formal", "Wishing you a very happy birthday!"),
                funny = json.optString("funny", "Wishing you a very happy birthday!"),
                emotional = json.optString("emotional", "Wishing you a very happy birthday!"),
                recommended = json.optString("recommended", "standard")
            )
        } catch (e: Exception) {
            MessageVariants.fromFallback("Wishing you a very happy birthday!")
        }
    }

    fun parseGiftSuggestions(jsonString: String): List<com.example.domain.service.GiftSuggestion> {
        return try {
            val cleanJson = jsonString.trim().removeSurrounding("```json", "```").trim()
            val arr = org.json.JSONArray(cleanJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                com.example.domain.service.GiftSuggestion(
                    name = obj.optString("name", "Gift"),
                    reason = obj.optString("reason", ""),
                    estimatedCostInr = obj.optInt("estimatedCostInr", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
