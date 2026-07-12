package com.yashsomani.birthdayautopilot.storage.database

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleDataFreshnessPolicyTest {
  @Test
  fun freshAndStaleWarningRemainEligibleOnlyThroughTheThirtyDayTrustedBoundary() {
    val now = 4_000_000_000L
    val boundary = now - PeopleDataFreshnessPolicy.MAXIMUM_UNATTENDED_AGE_MILLIS

    assertTrue(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.FRESH, fullSuccess = boundary),
        now,
      ),
    )
    assertTrue(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(
          freshness = SyncFreshness.STALE_WARNING,
          fullSuccess = boundary - 10_000,
          incrementalSuccess = boundary,
        ),
        now,
      ),
    )
    assertFalse(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.STALE_WARNING, fullSuccess = boundary - 1),
        now,
      ),
    )
  }

  @Test
  fun missingUntrustedFutureAndNonAutomatableStatesFailClosed() {
    val now = 4_000_000_000L
    assertFalse(PeopleDataFreshnessPolicy.allowsUnattendedAutomation(null, now))
    assertFalse(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.FRESH, fullSuccess = now),
        null,
      ),
    )
    assertFalse(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.FRESH, fullSuccess = now + 1),
        now,
      ),
    )
    assertFalse(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.AUTH_ACTION_REQUIRED, fullSuccess = now),
        now,
      ),
    )
    assertFalse(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.FRESH, fullSuccess = null),
        now,
      ),
    )
    assertFalse(
      PeopleDataFreshnessPolicy.allowsUnattendedAutomation(
        state(freshness = SyncFreshness.FRESH, fullSuccess = 0),
        Long.MAX_VALUE,
      ),
    )
  }

  @Test
  fun sharedFreshnessCorpusPreservesExactSevenAndThirtyDayBoundaries() {
    val contract = JSONObject(
      findRepositoryFile("contracts/contacts-freshness-policy-v1.json").readText(),
    )
    assertEquals("contacts-freshness-v1", contract.getString("version"))
    assertEquals(
      PeopleDataFreshnessPolicy.NORMAL_MAXIMUM_AGE_MILLIS,
      contract.getLong("normalMaximumAgeMillis"),
    )
    assertEquals(
      PeopleDataFreshnessPolicy.MAXIMUM_UNATTENDED_AGE_MILLIS,
      contract.getLong("automationMaximumAgeMillis"),
    )
    val cases = contract.getJSONArray("cases")
    val ids = linkedSetOf<String>()
    repeat(cases.length()) { index ->
      val case = cases.getJSONObject(index)
      val id = case.getString("id")
      assertTrue("duplicate fixture id: $id", ids.add(id))
      val state = state(
        freshness = SyncFreshness.valueOf(case.getString("storedState")),
        fullSuccess = case.optLongOrNull("lastSuccessMillis"),
        lastErrorCode = case.optStringOrNull("lastErrorCode"),
      )
      val trustedNow = case.optLongOrNull("trustedNowMillis")
      assertEquals(
        id,
        PeopleDataFreshnessBand.valueOf(case.getString("expectedBand")),
        PeopleDataFreshnessPolicy.assess(state, trustedNow).band,
      )
      assertEquals(
        id,
        case.getBoolean("allowsAutomation"),
        PeopleDataFreshnessPolicy.allowsUnattendedAutomation(state, trustedNow),
      )
    }
  }

  private fun state(
    freshness: SyncFreshness,
    fullSuccess: Long?,
    incrementalSuccess: Long? = null,
    lastErrorCode: String? = null,
  ) = ContactSyncStateEntity(
    accountId = "account",
    activeGeneration = "generation",
    stagingGeneration = null,
    syncToken = "token",
    parametersHash = "a".repeat(64),
    freshness = freshness,
    lastFullSuccessMillis = fullSuccess,
    lastIncrementalSuccessMillis = incrementalSuccess,
    lastAttemptMillis = fullSuccess,
    lastErrorCode = lastErrorCode,
    revision = 1,
  )

  private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else getLong(key)

  private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else getString(key)

  private fun findRepositoryFile(relativePath: String): File {
    var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    while (current != null) {
      val candidate = File(current, relativePath)
      if (candidate.isFile) return candidate
      current = current.parentFile
    }
    error("Repository fixture not found: $relativePath")
  }
}
