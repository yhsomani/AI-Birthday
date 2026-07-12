package com.yashsomani.birthdayautopilot.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleHttpPolicyTest {
  @Test
  fun `success bodies require a JSON compatible content type`() {
    assertTrue(PeopleHttpResponsePolicy.isJsonMediaType("application/json"))
    assertTrue(PeopleHttpResponsePolicy.isJsonMediaType("application/json; charset=UTF-8"))
    assertTrue(PeopleHttpResponsePolicy.isJsonMediaType("application/problem+json"))
    assertFalse(PeopleHttpResponsePolicy.isJsonMediaType(null))
    assertFalse(PeopleHttpResponsePolicy.isJsonMediaType("text/html"))
    assertFalse(PeopleHttpResponsePolicy.isJsonMediaType("text/json"))
  }

  @Test
  fun `retry after accepts only a bounded delta and never an arbitrary date`() {
    assertEquals(120L, PeopleHttpResponsePolicy.parseRetryAfter("120"))
    assertNull(PeopleHttpResponsePolicy.parseRetryAfter("0"))
    assertNull(PeopleHttpResponsePolicy.parseRetryAfter("86401"))
    assertNull(PeopleHttpResponsePolicy.parseRetryAfter("Wed, 21 Oct 2030 07:28:00 GMT"))
  }

  @Test
  fun `expired sync token parser requires the typed reason`() {
    assertTrue(
      PeopleApiErrorParser.isExpiredSyncToken(
        """
        {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"EXPIRED_SYNC_TOKEN"}]}}
        """.trimIndent().toByteArray(),
      ),
    )
    assertFalse(
      PeopleApiErrorParser.isExpiredSyncToken(
        "{\"error\":{\"message\":\"EXPIRED_SYNC_TOKEN\"}}".toByteArray(),
      ),
    )
  }
}
