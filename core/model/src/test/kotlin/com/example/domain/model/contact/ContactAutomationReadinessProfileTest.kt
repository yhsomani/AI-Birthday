package com.example.domain.model.contact

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactAutomationReadinessProfileTest {
    @Test
    fun `hasPersonalizationData detects enrichment fields`() {
        assertTrue(profile(nickname = "Ash").hasPersonalizationData)
        assertTrue(profile(notesText = "College friend").hasPersonalizationData)
        assertTrue(profile(interestsJson = "[\"music\"]").hasPersonalizationData)
        assertTrue(profile(sharedHistoryJson = "[\"met in 2018\"]").hasPersonalizationData)
    }

    @Test
    fun `hasPersonalizationData ignores blank fields and empty json lists`() {
        assertFalse(
            profile(
                nickname = " ",
                notesText = " ",
                interestsJson = " [] ",
                sharedHistoryJson = "",
            ).hasPersonalizationData,
        )
    }

    @Test
    fun `hasPersonalizationContextForAi accepts either enrichment or confidence`() {
        assertTrue(profile(notesText = "College friend").hasPersonalizationContextForAi(0.6))
        assertTrue(profile(classificationConfidence = 0.7).hasPersonalizationContextForAi(0.6))
        assertFalse(profile(classificationConfidence = 0.59).hasPersonalizationContextForAi(0.6))
    }

    @Test
    fun `hasReviewFirstAutomationOverride detects manual automation blockers`() {
        assertTrue(profile(automationMode = ApprovalMode.ALWAYS_ASK).hasReviewFirstAutomationOverride)
        assertTrue(profile(automationMode = ApprovalMode.VIP_APPROVE).hasReviewFirstAutomationOverride)
        assertTrue(profile(automationMode = ApprovalMode.SMART_APPROVE).hasReviewFirstAutomationOverride)
        assertTrue(profile(skipAutoWish = true).hasReviewFirstAutomationOverride)
        assertFalse(profile(automationMode = ApprovalMode.DEFAULT).hasReviewFirstAutomationOverride)
        assertFalse(profile(automationMode = ApprovalMode.FULLY_AUTO).hasReviewFirstAutomationOverride)
    }

    private fun profile(
        automationMode: ApprovalMode = ApprovalMode.DEFAULT,
        skipAutoWish: Boolean = false,
        nickname: String? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
        classificationConfidence: Double = 0.0,
    ): ContactAutomationReadinessProfile {
        return ContactAutomationReadinessProfile(
            id = ContactId("contact_1"),
            preferredChannel = MessageChannel.SMS,
            automationMode = automationMode,
            skipAutoWish = skipAutoWish,
            nickname = nickname,
            notesText = notesText,
            interestsJson = interestsJson,
            sharedHistoryJson = sharedHistoryJson,
            classificationConfidence = classificationConfidence,
        )
    }
}
