package com.example.core.gemini

import android.content.Context
import com.example.core.db.dao.PendingMessageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        messageId: String? = null,
        pendingMessageDao: PendingMessageDao? = null,
        context: Context? = null,
        eventType: String = "BIRTHDAY"
    ): MessageVariants {
        return try {
            val json = JSONObject(jsonString)
            MessageVariants(
                short = json.optString("short", "Happy Birthday!"),
                standard = json.optString("standard", "Wishing you a very happy birthday!"),
                long = json.optString("long", "Wishing you a very happy birthday! Hope you have a wonderful day!"),
                formal = json.optString("formal", "Wishing you a very happy birthday!"),
                funny = json.optString("funny", "Wishing you a very happy birthday!"),
                emotional = json.optString("emotional", "Wishing you a very happy birthday!"),
                recommended = json.optString("recommended", "standard"),
                isUsingFallback = false
            )
        } catch (e: Exception) {
            val fallbackText = getFallbackTemplate(context, eventType)
            if (messageId != null && pendingMessageDao != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    pendingMessageDao.setFallbackFlag(messageId, true)
                }
            }
            if (context != null) {
                try {
                    com.example.core.automation.notifications.NotificationHelper.showSystemAlert(
                        context,
                        "AI Generation Unavailable",
                        "A template message was used because the AI generator was offline or rate-limited."
                    )
                } catch (ex: Exception) {
                    android.util.Log.e("ResponseParser", "Failed to show system alert", ex)
                }
            }
            MessageVariants.fromFallback(fallbackText)
        }
    }

    private fun getFallbackTemplate(context: Context?, eventType: String): String {
        context ?: return when (eventType) {
            "ANNIVERSARY" -> "Happy Anniversary! Wishing you both a lifetime of love and happiness."
            "WORK_ANNIVERSARY" -> "Congratulations on your work anniversary! Thank you for your hard work and dedication."
            "REVIVAL" -> "Hey! It's been a while since we caught up. Hope you're doing great! Let's connect soon."
            else -> "Wishing you a very happy birthday! Hope you have a wonderful day!"
        }
        return try {
            val resName = when (eventType) {
                "ANNIVERSARY" -> "fallback_anniversary_message"
                "WORK_ANNIVERSARY" -> "fallback_work_anniversary_message"
                "REVIVAL" -> "fallback_revival_message"
                else -> "fallback_birthday_message"
            }
            val resId = context.resources.getIdentifier(resName, "string", context.packageName)
            if (resId != 0) context.getString(resId) else when (eventType) {
                "ANNIVERSARY" -> "Happy Anniversary! Wishing you both a lifetime of love and happiness."
                "WORK_ANNIVERSARY" -> "Congratulations on your work anniversary! Thank you for your hard work and dedication."
                "REVIVAL" -> "Hey! It's been a while since we caught up. Hope you're doing great! Let's connect soon."
                else -> "Wishing you a very happy birthday! Hope you have a wonderful day!"
            }
        } catch (ex: Exception) {
            "Wishing you a very happy birthday!"
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
