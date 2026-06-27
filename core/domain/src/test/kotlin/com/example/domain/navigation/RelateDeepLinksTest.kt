package com.example.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RelateDeepLinksTest {

    @Test
    fun uriBuilders_encodePathSensitiveIds() {
        val contactId = "people/c123 + 100%"
        val messageRef = "pending/message #1"

        assertEquals(
            "relateai://contact/people%2Fc123%20%2B%20100%25",
            RelateDeepLinks.Contact.uri(contactId),
        )
        assertEquals(
            "relateai://wish/people%2Fc123%20%2B%20100%25/pending%2Fmessage%20%231",
            RelateDeepLinks.Wish.uri(contactId, messageRef),
        )
    }

    @Test
    fun staticUrisMatchManifestHosts() {
        assertEquals("relateai://settings", RelateDeepLinks.Settings.uri)
        assertEquals("relateai://backup-restore", RelateDeepLinks.BackupRestore.uri)
    }

    @Test
    fun pathSegmentEncodingDoesNotLeaveSlashSeparators() {
        val encoded = RelateDeepLinks.encodePathSegment("family/friend")

        assertFalse(encoded.contains("/"))
        assertEquals("family%2Ffriend", encoded)
    }
}
