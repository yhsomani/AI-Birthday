package com.example.core.gemini

import com.example.core.resilience.StructuredLogger
import com.example.domain.model.occasion.OccasionType
import com.example.domain.service.ContactClassificationContract
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
    val isUsingFallback: Boolean = false,
    val parseMetadata: MessageVariantParseMetadata = MessageVariantParseMetadata.success()
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
        fun fromFallback(
            text: String,
            reason: MessageVariantFallbackReason = MessageVariantFallbackReason.MALFORMED_JSON
        ): MessageVariants {
            return MessageVariants(
                short = text,
                standard = text,
                long = text,
                formal = text,
                funny = text,
                emotional = text,
                recommended = "standard",
                isUsingFallback = true,
                parseMetadata = MessageVariantParseMetadata.fallback(reason)
            )
        }
    }
}

data class MessageVariantParseMetadata(
    val status: MessageVariantParseStatus,
    val fallbackReason: MessageVariantFallbackReason
) {
    companion object {
        fun success() = MessageVariantParseMetadata(
            status = MessageVariantParseStatus.SUCCESS,
            fallbackReason = MessageVariantFallbackReason.NONE
        )

        fun fallback(reason: MessageVariantFallbackReason) = MessageVariantParseMetadata(
            status = MessageVariantParseStatus.FALLBACK,
            fallbackReason = reason
        )
    }
}

enum class MessageVariantParseStatus {
    SUCCESS,
    FALLBACK
}

enum class MessageVariantFallbackReason(val code: String) {
    NONE("none"),
    MALFORMED_JSON("malformed_json"),
    ERROR_PAYLOAD("error_payload")
}

object ResponseParser {
    fun parseContactClassification(jsonString: String): ClassificationResult {
        return try {
            val json = JSONObject(cleanJson(jsonString))
            val styleRaw = json.firstNonBlank(
                ContactClassificationContract.Fields.COMMUNICATION_STYLE,
                ContactClassificationContract.Fields.LEGACY_COMMUNICATION_STYLE,
                ContactClassificationContract.Fields.LEGACY_STYLE,
            )
            val style = ContactClassificationContract.communicationStyleOrNull(styleRaw)
            logCommunicationStyleDefault(styleRaw, style)
            ClassificationResult(
                type = ContactClassificationContract.normalizeRelationshipType(
                    json.firstNonBlank(
                        ContactClassificationContract.Fields.RELATIONSHIP_TYPE,
                        ContactClassificationContract.Fields.LEGACY_RELATIONSHIP_TYPE,
                    )
                ),
                subtype = json.firstNonBlank(
                    ContactClassificationContract.Fields.RELATIONSHIP_SUBTYPE,
                    ContactClassificationContract.Fields.LEGACY_RELATIONSHIP_SUBTYPE,
                ),
                confidence = json.optDouble(ContactClassificationContract.Fields.CONFIDENCE, 0.0),
                language = ContactClassificationContract.normalizeLanguage(
                    json.firstNonBlank(ContactClassificationContract.Fields.LANGUAGE)
                ),
                formality = ContactClassificationContract.normalizeFormality(
                    json.firstNonBlank(ContactClassificationContract.Fields.FORMALITY)
                ),
                communicationStyle = style ?: ContactClassificationContract.DEFAULT_COMMUNICATION_STYLE
            )
        } catch (e: Exception) {
            ClassificationResult(
                ContactClassificationContract.DEFAULT_RELATIONSHIP_TYPE,
                null,
                0.0,
                ContactClassificationContract.DEFAULT_LANGUAGE,
                ContactClassificationContract.DEFAULT_FORMALITY,
                ContactClassificationContract.DEFAULT_COMMUNICATION_STYLE
            )
        }
    }

    fun parseMessageVariants(
        jsonString: String,
        eventType: String = OccasionType.BIRTHDAY.raw
    ): MessageVariants {
        return try {
            val json = JSONObject(cleanJson(jsonString))
            if (json.has("error")) {
                return fallbackMessageVariants(eventType, MessageVariantFallbackReason.ERROR_PAYLOAD)
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
                isUsingFallback = false,
                parseMetadata = MessageVariantParseMetadata.success()
            )
        } catch (e: Exception) {
            fallbackMessageVariants(eventType, MessageVariantFallbackReason.MALFORMED_JSON)
        }
    }

    private fun fallbackMessageVariants(
        eventType: String,
        reason: MessageVariantFallbackReason
    ): MessageVariants {
        val fallbackText = fallbackTextFor(eventType)
        return MessageVariants.fromFallback(fallbackText, reason)
    }

    private fun cleanJson(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("```")) return text

        text = text.removePrefix("```").trimStart()
        val firstLineEnd = text.indexOf('\n')
        if (firstLineEnd >= 0) {
            val firstLine = text.substring(0, firstLineEnd).trim()
            if (firstLine.equals("json", ignoreCase = true)) {
                text = text.substring(firstLineEnd + 1)
            }
        }
        return text.removeSuffix("```").trim()
    }

    private fun fallbackTextFor(eventType: String): String {
        if (eventType.equals("REVIVAL", ignoreCase = true)) {
            return "Hey! It's been a while since we caught up. Hope you're doing great! Let's connect soon."
        }
        return when (OccasionType.fromRaw(eventType)) {
            OccasionType.ANNIVERSARY -> "Happy Anniversary! Wishing you both a lifetime of love and happiness."
            OccasionType.WORK_ANNIVERSARY -> "Congratulations on your work anniversary! Thank you for your hard work and dedication."
            else -> "Wishing you a very happy birthday! Hope you have a wonderful day!"
        }
    }

    private fun JSONObject.firstNonBlank(vararg names: String): String? {
        names.forEach { name ->
            optString(name)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private fun logCommunicationStyleDefault(rawValue: String?, normalized: String?) {
        when {
            rawValue.isNullOrBlank() -> StructuredLogger.i(
                TAG,
                "Classification response missing communication style; using default",
                mapOf("default" to ContactClassificationContract.DEFAULT_COMMUNICATION_STYLE),
            )
            normalized == null -> StructuredLogger.w(
                TAG,
                "Classification response had unsupported communication style; using default",
                extras = mapOf("default" to ContactClassificationContract.DEFAULT_COMMUNICATION_STYLE),
            )
        }
    }

    fun parseGiftSuggestions(jsonString: String): List<com.example.domain.service.GiftSuggestion> {
        return try {
            val cleaned = cleanJson(jsonString)
            val arr = if (cleaned.trim().startsWith("[")) {
                org.json.JSONArray(cleaned)
            } else {
                JSONObject(cleaned).optJSONArray("suggestions") ?: org.json.JSONArray()
            }
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

    private const val TAG = "ResponseParser"
}
