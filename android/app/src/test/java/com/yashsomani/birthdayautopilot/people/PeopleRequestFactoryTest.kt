package com.yashsomani.birthdayautopilot.people

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleRequestFactoryTest {
  private val factory = PeopleRequestFactory(pageSize = 1_000)

  @Test
  fun `full request is pinned to contacts source and minimum field mask`() {
    val result = factory.build(PeopleSyncMode.Full, pageToken = null) as PeopleRequestBuildResult.Success
    val uri = result.request.uri

    assertEquals("https", uri.scheme)
    assertEquals("people.googleapis.com", uri.host)
    assertEquals("/v1/people/me/connections", uri.path)
    assertEquals(
      mapOf(
        "personFields" to PEOPLE_PERSON_FIELDS,
        "sources" to PEOPLE_CONTACT_SOURCE,
        "pageSize" to "1000",
        "requestSyncToken" to "true",
        "sortOrder" to "LAST_MODIFIED_ASCENDING",
      ),
      query(uri.rawQuery),
    )
    assertFalse(uri.toString().contains("photos"))
    assertFalse(uri.toString().contains("emailAddresses"))
  }

  @Test
  fun `incremental pages retain the same parameters and redact tokens from diagnostics`() {
    val syncToken = "sync/token+private"
    val pageToken = "page/token+private"
    val mode = PeopleSyncMode.Incremental(syncToken, factory.parameterFingerprint)
    val result = factory.build(mode, pageToken) as PeopleRequestBuildResult.Success
    val query = query(result.request.uri.rawQuery)

    assertEquals(syncToken, query["syncToken"])
    assertEquals(pageToken, query["pageToken"])
    assertEquals(PEOPLE_PERSON_FIELDS, query["personFields"])
    assertFalse(result.request.toString().contains(syncToken))
    assertFalse(mode.toString().contains(syncToken))
  }

  @Test
  fun `changed parameter fingerprint and malformed tokens fail before network`() {
    assertEquals(
      PeopleMalformedReason.PARAMETER_MISMATCH,
      (
        factory.build(
          PeopleSyncMode.Incremental("sync", "wrong-fingerprint"),
          pageToken = null,
        ) as PeopleRequestBuildResult.Failure
        ).reason,
    )
    assertEquals(
      PeopleMalformedReason.INVALID_PAGE,
      (factory.build(PeopleSyncMode.Full, "bad token") as PeopleRequestBuildResult.Failure).reason,
    )
    assertTrue(factory.parameterFingerprint.matches(Regex("^[0-9a-f]{64}$")))
  }

  private fun query(rawQuery: String): Map<String, String> = rawQuery
    .split('&')
    .associate { pair ->
      val (key, value) = pair.split('=', limit = 2)
      URLDecoder.decode(key, StandardCharsets.UTF_8) to
        URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}
