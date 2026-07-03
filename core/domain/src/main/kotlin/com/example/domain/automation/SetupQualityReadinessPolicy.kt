package com.example.domain.automation

import com.example.domain.model.contact.ContactAutomationReadinessProfile

enum class StyleCoachReadinessReason {
    TRAINED,
    NEEDS_MORE,
    EMPTY,
}

enum class PersonalizationReadinessReason {
    EMPTY,
    READY,
    LOW,
}

enum class GenericMessageReadinessReason {
    EMPTY,
    READY,
    RISK,
}

data class StyleCoachReadiness(
    val reason: StyleCoachReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.QUALITY,
    val sampleCount: Int = 0,
    val samplesNeeded: Int = 0,
)

data class PersonalizationReadiness(
    val reason: PersonalizationReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.QUALITY,
    val enrichedContactCount: Int = 0,
    val totalContactCount: Int = 0,
)

data class GenericMessageReadiness(
    val reason: GenericMessageReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.QUALITY,
    val genericRiskCount: Int = 0,
    val totalContactCount: Int = 0,
)

object SetupQualityReadinessPolicy {
    const val REQUIRED_STYLE_SAMPLE_COUNT = 3
    const val MIN_PERSONALIZATION_PERCENT = 50
    const val PERSONALIZATION_CONFIDENCE_THRESHOLD = 0.6

    fun evaluateStyleCoach(
        styleSampleCount: Int,
    ): StyleCoachReadiness {
        return when {
            styleSampleCount >= REQUIRED_STYLE_SAMPLE_COUNT -> StyleCoachReadiness(
                reason = StyleCoachReadinessReason.TRAINED,
                status = SetupReadinessStatus.OK,
                sampleCount = styleSampleCount,
            )
            styleSampleCount > 0 -> StyleCoachReadiness(
                reason = StyleCoachReadinessReason.NEEDS_MORE,
                status = SetupReadinessStatus.WARNING,
                sampleCount = styleSampleCount,
                samplesNeeded = REQUIRED_STYLE_SAMPLE_COUNT - styleSampleCount,
            )
            else -> StyleCoachReadiness(
                reason = StyleCoachReadinessReason.EMPTY,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
        }
    }

    fun evaluatePersonalization(
        contacts: List<ContactAutomationReadinessProfile>,
    ): PersonalizationReadiness {
        if (contacts.isEmpty()) {
            return PersonalizationReadiness(
                reason = PersonalizationReadinessReason.EMPTY,
                status = SetupReadinessStatus.WARNING,
            )
        }

        val enrichedCount = contacts.count { it.hasPersonalizationData }
        val percentage = (enrichedCount * 100) / contacts.size
        return PersonalizationReadiness(
            reason = if (percentage >= MIN_PERSONALIZATION_PERCENT) {
                PersonalizationReadinessReason.READY
            } else {
                PersonalizationReadinessReason.LOW
            },
            status = if (percentage >= MIN_PERSONALIZATION_PERCENT) {
                SetupReadinessStatus.OK
            } else {
                SetupReadinessStatus.WARNING
            },
            enrichedContactCount = enrichedCount,
            totalContactCount = contacts.size,
        )
    }

    fun evaluateGenericMessages(
        contacts: List<ContactAutomationReadinessProfile>,
    ): GenericMessageReadiness {
        if (contacts.isEmpty()) {
            return GenericMessageReadiness(
                reason = GenericMessageReadinessReason.EMPTY,
                status = SetupReadinessStatus.WARNING,
            )
        }

        val genericRiskCount = contacts.count {
            !it.hasPersonalizationContextForAi(PERSONALIZATION_CONFIDENCE_THRESHOLD)
        }
        return GenericMessageReadiness(
            reason = if (genericRiskCount == 0) {
                GenericMessageReadinessReason.READY
            } else {
                GenericMessageReadinessReason.RISK
            },
            status = if (genericRiskCount == 0) {
                SetupReadinessStatus.OK
            } else {
                SetupReadinessStatus.WARNING
            },
            genericRiskCount = genericRiskCount,
            totalContactCount = contacts.size,
        )
    }
}
