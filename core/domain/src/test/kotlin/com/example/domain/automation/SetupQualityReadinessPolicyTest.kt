package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupQualityReadinessPolicyTest {

    @Test
    fun `evaluateStyleCoach passes after required samples`() {
        val readiness = SetupQualityReadinessPolicy.evaluateStyleCoach(
            styleSampleCount = 3,
        )

        assertEquals(StyleCoachReadinessReason.TRAINED, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(3, readiness.sampleCount)
        assertEquals(SetupReadinessGroup.QUALITY, readiness.group)
    }

    @Test
    fun `evaluateStyleCoach warns when some samples exist`() {
        val readiness = SetupQualityReadinessPolicy.evaluateStyleCoach(
            styleSampleCount = 1,
        )

        assertEquals(StyleCoachReadinessReason.NEEDS_MORE, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(2, readiness.samplesNeeded)
    }

    @Test
    fun `evaluateStyleCoach requires action when there are no samples`() {
        val readiness = SetupQualityReadinessPolicy.evaluateStyleCoach(
            styleSampleCount = 0,
        )

        assertEquals(StyleCoachReadinessReason.EMPTY, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
    }

    @Test
    fun `evaluatePersonalization warns when there are no contacts`() {
        val readiness = SetupQualityReadinessPolicy.evaluatePersonalization(emptyList())

        assertEquals(PersonalizationReadinessReason.EMPTY, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
    }

    @Test
    fun `evaluatePersonalization passes when at least half of contacts are enriched`() {
        val readiness = SetupQualityReadinessPolicy.evaluatePersonalization(
            listOf(
                contact(id = "enriched", nickname = "Asha"),
                contact(id = "empty"),
            ),
        )

        assertEquals(PersonalizationReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(1, readiness.enrichedContactCount)
        assertEquals(2, readiness.totalContactCount)
    }

    @Test
    fun `evaluatePersonalization warns when fewer than half of contacts are enriched`() {
        val readiness = SetupQualityReadinessPolicy.evaluatePersonalization(
            listOf(
                contact(id = "empty_1"),
                contact(id = "empty_2"),
            ),
        )

        assertEquals(PersonalizationReadinessReason.LOW, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(0, readiness.enrichedContactCount)
        assertEquals(2, readiness.totalContactCount)
    }

    @Test
    fun `evaluateGenericMessages warns when there are no contacts`() {
        val readiness = SetupQualityReadinessPolicy.evaluateGenericMessages(emptyList())

        assertEquals(GenericMessageReadinessReason.EMPTY, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
    }

    @Test
    fun `evaluateGenericMessages passes when every contact has AI context`() {
        val readiness = SetupQualityReadinessPolicy.evaluateGenericMessages(
            listOf(
                contact(id = "notes", notesText = "Met during college."),
                contact(id = "confidence", classificationConfidence = 0.6),
            ),
        )

        assertEquals(GenericMessageReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(0, readiness.genericRiskCount)
    }

    @Test
    fun `evaluateGenericMessages warns when contacts lack AI context`() {
        val readiness = SetupQualityReadinessPolicy.evaluateGenericMessages(
            listOf(
                contact(id = "empty"),
                contact(id = "notes", notesText = "Likes hiking."),
            ),
        )

        assertEquals(GenericMessageReadinessReason.RISK, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(1, readiness.genericRiskCount)
        assertEquals(2, readiness.totalContactCount)
    }

    private fun contact(
        id: String,
        nickname: String? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
        classificationConfidence: Double = 0.0,
    ): ContactAutomationReadinessProfile {
        return ContactAutomationReadinessProfile(
            id = ContactId(id),
            preferredChannel = MessageChannel.SMS,
            automationMode = ApprovalMode.DEFAULT,
            skipAutoWish = false,
            hasPrimaryPhone = false,
            hasPrimaryEmail = false,
            hasAutomatableOccasion = false,
            nickname = nickname,
            notesText = notesText,
            interestsJson = interestsJson,
            sharedHistoryJson = sharedHistoryJson,
            classificationConfidence = classificationConfidence,
        )
    }
}
