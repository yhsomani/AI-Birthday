package com.example.domain.model.contact

import com.example.domain.model.common.ContactId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactAnalyticsProfileTest {
    @Test
    fun `hasPersonalizationSignals detects contact enrichment fields`() {
        assertTrue(profile(nickname = "Ash").hasPersonalizationSignals)
        assertTrue(profile(notesText = "College friend").hasPersonalizationSignals)
        assertTrue(profile(interestsJson = "[\"music\"]").hasPersonalizationSignals)
        assertTrue(profile(sharedHistoryJson = "[\"met in 2018\"]").hasPersonalizationSignals)
    }

    @Test
    fun `hasPersonalizationSignals ignores blank fields and empty json lists`() {
        assertFalse(
            profile(
                nickname = " ",
                notesText = " ",
                interestsJson = " [] ",
                sharedHistoryJson = "",
            ).hasPersonalizationSignals,
        )
    }

    private fun profile(
        nickname: String? = null,
        notesText: String = "",
        interestsJson: String = "[]",
        sharedHistoryJson: String = "[]",
    ): ContactAnalyticsProfile {
        return ContactAnalyticsProfile(
            id = ContactId("contact_1"),
            healthScore = 80,
            nickname = nickname,
            notesText = notesText,
            interestsJson = interestsJson,
            sharedHistoryJson = sharedHistoryJson,
        )
    }
}
