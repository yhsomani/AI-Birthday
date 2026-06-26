package com.example.domain.service

object ContactClassificationContract {
    const val DEFAULT_RELATIONSHIP_TYPE = "UNKNOWN"
    const val DEFAULT_LANGUAGE = "en"
    const val DEFAULT_FORMALITY = "CASUAL"
    const val DEFAULT_COMMUNICATION_STYLE = "WARM"

    object Fields {
        const val RELATIONSHIP_TYPE = "relationship_type"
        const val LEGACY_RELATIONSHIP_TYPE = "type"
        const val RELATIONSHIP_SUBTYPE = "relationship_subtype"
        const val LEGACY_RELATIONSHIP_SUBTYPE = "subtype"
        const val CONFIDENCE = "confidence"
        const val LANGUAGE = "language"
        const val FORMALITY = "formality"
        const val COMMUNICATION_STYLE = "communication_style"
        const val LEGACY_COMMUNICATION_STYLE = "communicationStyle"
        const val LEGACY_STYLE = "style"
    }

    val relationshipTypes = listOf(
        "FAMILY",
        "BEST_FRIEND",
        "CLOSE_FRIEND",
        "FRIEND",
        "RELATIVE",
        "COLLEAGUE",
        "COWORKER",
        "CLIENT",
        "MANAGER",
        "MENTOR",
        "ALUMNI",
        "VENDOR",
        "ACQUAINTANCE",
        "WORK",
        "PARTNER",
        "UNKNOWN",
    )

    val languageCodes = listOf("en", "hi", "mr", "gu", "ta", "te", "bn", "pa")
    val formalityLevels = listOf("CASUAL", "SEMI_FORMAL", "FORMAL")
    val communicationStyles = listOf("WARM", "FUNNY", "PROFESSIONAL", "EMOTIONAL")

    fun normalizeRelationshipType(value: String?): String =
        normalizeUpperEnum(value, relationshipTypes, DEFAULT_RELATIONSHIP_TYPE)

    fun normalizeLanguage(value: String?): String {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        return normalized?.takeIf { it in languageCodes } ?: DEFAULT_LANGUAGE
    }

    fun normalizeFormality(value: String?): String =
        normalizeUpperEnum(value, formalityLevels, DEFAULT_FORMALITY)

    fun communicationStyleOrNull(value: String?): String? =
        normalizeUpperEnumOrNull(value, communicationStyles)

    fun normalizeCommunicationStyle(value: String?): String =
        communicationStyleOrNull(value) ?: DEFAULT_COMMUNICATION_STYLE

    fun promptValues(values: List<String>): String = values.joinToString("|")

    private fun normalizeUpperEnum(
        value: String?,
        allowedValues: List<String>,
        defaultValue: String,
    ): String = normalizeUpperEnumOrNull(value, allowedValues) ?: defaultValue

    private fun normalizeUpperEnumOrNull(value: String?, allowedValues: List<String>): String? {
        val normalized = value
            ?.trim()
            ?.replace(Regex("[\\s-]+"), "_")
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }

        return normalized?.takeIf { it in allowedValues }
    }
}
