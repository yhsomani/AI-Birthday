package com.yashsomani.birthdayautopilot.bridge

import com.yashsomani.birthdayautopilot.storage.database.ContactSyncStateEntity
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSetupStepResolverTest {
  @Test
  fun `authoritative first import always enters shell despite recoverable refresh state`() {
    listOf(
      SyncFreshness.FRESH,
      SyncFreshness.STALE_WARNING,
      SyncFreshness.SAFETY_PAUSED,
      SyncFreshness.AUTH_ACTION_REQUIRED,
    ).forEach { freshness ->
      assertEquals(
        freshness.name,
        "complete",
        AndroidSetupStepResolver.resolve(
          eligibilitySupported = true,
          identityReady = true,
          syncState = syncState(
            activeGeneration = "authoritative-generation",
            stagingGeneration = if (freshness == SyncFreshness.STALE_WARNING) {
              "replacement-in-progress"
            } else {
              null
            },
            freshness = freshness,
            lastAttemptMillis = 2_000,
          ),
        ),
      )
    }
  }

  @Test
  fun `setup remains blocking until first authoritative import`() {
    assertEquals(
      "contacts-disclosure",
      AndroidSetupStepResolver.resolve(true, true, syncState()),
    )
    assertEquals(
      "sync-summary",
      AndroidSetupStepResolver.resolve(
        true,
        true,
        syncState(
          stagingGeneration = "first-import-in-progress",
          lastAttemptMillis = 1_000,
        ),
      ),
    )
    assertEquals(
      "contacts-disclosure",
      AndroidSetupStepResolver.resolve(
        true,
        true,
        syncState(
          freshness = SyncFreshness.AUTH_ACTION_REQUIRED,
          lastAttemptMillis = 1_000,
        ),
      ),
    )
  }

  private fun syncState(
    activeGeneration: String? = null,
    stagingGeneration: String? = null,
    freshness: SyncFreshness = SyncFreshness.NEVER_SYNCED,
    lastAttemptMillis: Long? = null,
  ) = ContactSyncStateEntity(
    accountId = "account",
    activeGeneration = activeGeneration,
    stagingGeneration = stagingGeneration,
    syncToken = null,
    parametersHash = "parameters",
    freshness = freshness,
    lastFullSuccessMillis = activeGeneration?.let { 1_000 },
    lastIncrementalSuccessMillis = null,
    lastAttemptMillis = lastAttemptMillis,
    lastErrorCode = null,
    revision = 0,
  )
}
