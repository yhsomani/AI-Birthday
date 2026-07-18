package com.yashsomani.birthdayautopilot.automation.workers

import com.yashsomani.birthdayautopilot.automation.orchestration.IOSComposerReservationRecheckPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkerAttentionPolicyTest {
  @Test
  fun idleReconciliationIsSilentButAnObservedMissIsEmitted() {
    assertEquals(emptyList<String>(), WorkerAttentionPolicy.successful("RECONCILE_IDLE"))
    assertEquals(
      listOf("BIRTHDAY_MISSED"),
      WorkerAttentionPolicy.successful("RECONCILE_IDLE", listOf("BIRTHDAY_MISSED")),
    )
  }

  @Test
  fun onlyClassifiedTerminalFailuresAreEmitted() {
    assertNull(WorkerAttentionPolicy.failure("RECONCILE_ATTEMPTS_EXHAUSTED", terminal = false))
    assertEquals(
      "RECONCILE_ATTEMPTS_EXHAUSTED",
      WorkerAttentionPolicy.failure("RECONCILE_ATTEMPTS_EXHAUSTED", terminal = true),
    )
    assertNull(WorkerAttentionPolicy.failure("UNREVIEWED_FAILURE", terminal = true))
  }

  @Test
  fun iOSComposerReservationUsesOneHourlySuccessorInsteadOfTheNetworkRetryFloor() {
    val now = 1_000_000L
    val hold = IOSComposerReservationRecheckPolicy.result(now)
    assertEquals("IOS_COMPOSER_RESERVED", hold.safeCode)
    assertEquals(false, hold.retryRecommended)
    assertEquals(now + 60L * 60L * 1_000L, hold.nextWakeAtMillis)
    assertEquals(hold.nextWakeAtMillis, ReconcileSuccessorPolicy.nextRunAtMillis(hold, now))

    val ordinaryRetry = hold.copy(retryRecommended = true, nextWakeAtMillis = null)
    assertEquals(
      now + ReconcileSuccessorPolicy.MIN_RETRY_DELAY_MILLIS,
      ReconcileSuccessorPolicy.nextRunAtMillis(ordinaryRetry, now),
    )
  }

  @Test
  fun repeatedReservationAttentionIsCollapsedBeforeTheDailyCategoryDedupe() {
    assertEquals(
      listOf("IOS_COMPOSER_RESERVED"),
      WorkerAttentionPolicy.successful(
        "IOS_COMPOSER_RESERVED",
        listOf("IOS_COMPOSER_RESERVED"),
      ),
    )
  }
}
