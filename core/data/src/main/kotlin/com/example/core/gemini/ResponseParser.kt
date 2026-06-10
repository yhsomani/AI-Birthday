package com.example.core.gemini

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
    val recommended: String,
    val isUsingFallback: Boolean = false
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
            return MessageVariants(text, text, text, text, text, text, "standard", true)
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

    fun parseMessageVariants(
        jsonString: String,
        eventType: String = "BIRTHDAY"
    ): MessageVariants {
        return try {
            val json = JSONObject(cleanJson(jsonString))
            if (json.has("error")) {
                throw IllegalArgumentException("AI response contained an error payload")
            }
            val standard = json.optString("standard")
                .ifBlank { fallbackTextFor(eventType) }
            val short = json.optString("short").ifBlank { standard }
            val long = json.optString("long").ifBlank { standard }
            val recommended = json.optString("recommended", "standard")
                .lowercase()
                .takeIf { it in setOf("short", "standard", "long", "formal", "funny", "emotional") }
                ?: "standard"
            MessageVariants(
                short = short,
                standard = standard,
                long = long,
                formal = json.optString("formal").ifBlank { standard },
                funny = json.optString("funny").ifBlank { standard },
                emotional = json.optString("emotional").ifBlank { standard },
                recommended = recommended,
                isUsingFallback = false
            )
        } catch (e: Exception) {
            val fallbackText = fallbackTextFor(eventType)
            MessageVariants.fromFallback(fallbackText)
        }
    }

    private fun cleanJson(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun fallbackTextFor(eventType: String): String {
        return when (eventType) {
            "ANNIVERSARY" -> "Happy Anniversary! Wishing you both a lifetime of love and happiness."
            "WORK_ANNIVERSARY" -> "Congratulations on your work anniversary! Thank you for your hard work and dedication."
            "REVIVAL" -> "Hey! It's been a while since we caught up. Hope you're doing great! Let's connect soon."
            else -> "Wishing you a very happy birthday! Hope you have a wonderful day!"
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
