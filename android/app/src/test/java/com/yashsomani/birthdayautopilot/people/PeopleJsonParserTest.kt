package com.yashsomani.birthdayautopilot.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleJsonParserTest {
  private val parser = PeopleJsonParser(maxPagePeople = 1_000)

  @Test
  fun `parses only contact-backed names birthdays phones and metadata`() {
    val result = parser.parse(validPage().toByteArray()) as PeoplePageParseResult.Success
    val page = result.page
    val person = page.contacts.single()

    assertEquals("people/c123", person.resourceName)
    assertEquals("contacts/source-1", person.contactSourceId)
    assertFalse(person.deleted)
    assertEquals("Ada Lovelace", person.names.single().displayName)
    assertEquals("Ada", person.names.single().givenName)
    assertEquals(PeopleBirthday(1815, 12, 10), person.birthdays.single())
    assertEquals("+91 98765 43210", person.phoneNumbers.single().value)
    assertEquals("mobile", person.phoneNumbers.single().type)
    assertEquals("next-page", page.nextPageToken)
    assertEquals(null, page.nextSyncToken)
    assertEquals(2, page.totalItems)
  }

  @Test
  fun `incremental tombstone is retained without inventing contact values`() {
    val result = parser.parse(
      """
      {
        "connections": [{
          "resourceName": "people/deleted-1",
          "metadata": {
            "deleted": true,
            "sources": [{"type": "CONTACT", "id": "contacts/deleted-1"}]
          }
        }],
        "nextSyncToken": "next-sync"
      }
      """.trimIndent().toByteArray(),
    ) as PeoplePageParseResult.Success

    val tombstone = result.page.contacts.single()
    assertTrue(tombstone.deleted)
    assertTrue(tombstone.names.isEmpty())
    assertTrue(tombstone.birthdays.isEmpty())
    assertTrue(tombstone.phoneNumbers.isEmpty())
    assertEquals("next-sync", result.page.nextSyncToken)
  }

  @Test
  fun `profile or mismatched field sources fail the entire page as partial merge`() {
    val profilePerson = validPage().replace(
      "{\"type\": \"CONTACT\", \"id\": \"contacts/source-1\"}",
      "{\"type\": \"PROFILE\", \"id\": \"profiles/source-1\"}",
    )
    assertEquals(
      PeopleMalformedReason.PARTIAL_SOURCE_MERGE,
      (parser.parse(profilePerson.toByteArray()) as PeoplePageParseResult.Failure).reason,
    )

    val mismatchedField = validPage().replaceFirst(
      "\"metadata\": {\"source\": {\"type\": \"CONTACT\", \"id\": \"contacts/source-1\"}}",
      "\"metadata\": {\"source\": {\"type\": \"CONTACT\", \"id\": \"contacts/other\"}}",
    )
    assertEquals(
      PeopleMalformedReason.PARTIAL_SOURCE_MERGE,
      (parser.parse(mismatchedField.toByteArray()) as PeoplePageParseResult.Failure).reason,
    )
  }

  @Test
  fun `unicode bidi controls and malformed people never enter staging`() {
    val bidi = validPage().replace("Ada Lovelace", "Ada\\u202ELovelace")
    assertEquals(
      PeopleMalformedReason.MALFORMED_PERSON,
      (parser.parse(bidi.toByteArray()) as PeoplePageParseResult.Failure).reason,
    )

    val missingPhoneValue = validPage().replace("\"value\": \"+91 98765 43210\",", "")
    assertEquals(
      PeopleMalformedReason.MALFORMED_PERSON,
      (parser.parse(missingPhoneValue.toByteArray()) as PeoplePageParseResult.Failure).reason,
    )

    val invalidUtf8 = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte())
    assertEquals(
      PeopleMalformedReason.INVALID_JSON,
      (parser.parse(invalidUtf8) as PeoplePageParseResult.Failure).reason,
    )
    assertTrue(parser.parse("{} {}".toByteArray()) is PeoplePageParseResult.Failure)
  }

  @Test
  fun `duplicates and impossible pagination metadata fail all-or-nothing parsing`() {
    val personJson = validPerson()
    val duplicate = """
      {"connections": [$personJson, $personJson], "nextSyncToken": "sync"}
    """.trimIndent()
    assertEquals(
      PeopleMalformedReason.DUPLICATE_PERSON,
      (parser.parse(duplicate.toByteArray()) as PeoplePageParseResult.Failure).reason,
    )

    val twoTerminalTokens = validPage()
      .replace("\"nextPageToken\": \"next-page\"", "\"nextPageToken\": \"next-page\", \"nextSyncToken\": \"sync\"")
    assertEquals(
      PeopleMalformedReason.INVALID_PAGE,
      (parser.parse(twoTerminalTokens.toByteArray()) as PeoplePageParseResult.Failure).reason,
    )
  }

  private fun validPage(): String = """
    {
      "connections": [${validPerson()}],
      "nextPageToken": "next-page",
      "totalItems": 2
    }
  """.trimIndent()

  private fun validPerson(): String = """
    {
      "resourceName": "people/c123",
      "metadata": {
        "sources": [{"type": "CONTACT", "id": "contacts/source-1"}]
      },
      "names": [{
        "metadata": {"source": {"type": "CONTACT", "id": "contacts/source-1"}},
        "displayName": "Ada Lovelace",
        "givenName": "Ada"
      }],
      "birthdays": [{
        "metadata": {"source": {"type": "CONTACT", "id": "contacts/source-1"}},
        "date": {"year": 1815, "month": 12, "day": 10}
      }],
      "phoneNumbers": [{
        "metadata": {"source": {"type": "CONTACT", "id": "contacts/source-1"}},
        "value": "+91 98765 43210",
        "type": "mobile"
      }]
    }
  """.trimIndent()
}
