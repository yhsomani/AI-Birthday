package com.yashsomani.birthdayautopilot.automation.workers

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
}
