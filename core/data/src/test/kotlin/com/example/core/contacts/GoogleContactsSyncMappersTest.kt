package com.example.core.contacts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GoogleContactsSyncMappersTest {

    @Test
    fun `toContactSyncRecord maps person fields into sync record`() {
        val record = JSONObject(
            """
            {
              "resourceName": "people/c123",
              "names": [{"displayName": "Asha Rao"}],
              "nicknames": [{"value": "Ash"}],
              "phoneNumbers": [{"value": "+15551234567"}],
              "emailAddresses": [{"value": "asha@example.com"}],
              "organizations": [{"name": "Relate", "title": "Designer"}],
              "birthdays": [{"date": {"day": 4, "month": 7, "year": 1994}}],
              "events": [
                {"type": "anniversary", "date": {"day": 14, "month": 2}},
                {"type": "work_anniversary", "date": {"day": 8, "month": 9, "year": 2021}}
              ],
              "memberships": [
                {"contactGroupMembership": {"contactGroupResourceName": "contactGroups/friends"}}
              ],
              "relations": [
                {"person": "Kiran", "type": "sibling"},
                {"person": "", "type": "ignored"}
              ],
              "addresses": [{"formattedValue": "123 Main St"}],
              "photos": [{"url": "https://example.com/photo.jpg"}],
              "biographies": [{"value": "Met at design school"}]
            }
            """.trimIndent(),
        ).toContactSyncRecord()

        requireNotNull(record)
        assertEquals("people/c123", record.id)
        assertEquals("people/c123", record.googleContactId)
        assertEquals("Asha Rao", record.displayName)
        assertEquals("Ash", record.nickname)
        assertEquals("+15551234567", record.primaryPhone)
        assertEquals("asha@example.com", record.primaryEmail)
        assertEquals("Relate", record.company)
        assertEquals("Designer", record.jobTitle)
        assertEquals(4, record.birthdayDay)
        assertEquals(7, record.birthdayMonth)
        assertEquals(1994, record.birthdayYear)
        assertEquals(14, record.anniversaryDay)
        assertEquals(2, record.anniversaryMonth)
        assertEquals(8, record.workStartDay)
        assertEquals(9, record.workStartMonth)
        assertEquals(2021, record.workStartYear)
        assertEquals("friends", record.contactGroup)
        assertEquals("""[{"person":"Kiran","type":"sibling"}]""", record.relationsJson)
        assertEquals("123 Main St", record.address)
        assertEquals("https://example.com/photo.jpg", record.profilePhotoUri)
        assertEquals("Met at design school", record.notesText)
        assertFalse(record.isDeleted)
    }

    @Test
    fun `toContactSyncRecord falls back display name and handles deleted contacts`() {
        val deleted = JSONObject(
            """
            {
              "resourceName": "people/deleted",
              "metadata": {"deleted": true}
            }
            """.trimIndent(),
        ).toContactSyncRecord()

        requireNotNull(deleted)
        assertEquals("people/deleted", deleted.id)
        assertEquals("", deleted.displayName)
        assertTrue(deleted.isDeleted)

        val phoneNamed = JSONObject(
            """
            {
              "resourceName": "people/phone",
              "phoneNumbers": [{"value": "+15550001111"}]
            }
            """.trimIndent(),
        ).toContactSyncRecord()

        requireNotNull(phoneNamed)
        assertEquals("+15550001111", phoneNamed.displayName)
        assertEquals("[]", phoneNamed.relationsJson)
        assertEquals("", phoneNamed.notesText)
        assertFalse(phoneNamed.isDeleted)

        assertNull(JSONObject("""{"names": [{"displayName": "No resource"}]}""").toContactSyncRecord())
    }
}
