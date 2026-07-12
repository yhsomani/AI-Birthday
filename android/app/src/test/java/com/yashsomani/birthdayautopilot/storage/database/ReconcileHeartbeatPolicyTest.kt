package com.yashsomani.birthdayautopilot.storage.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconcileHeartbeatPolicyTest {
  @Test
  fun safeCodesAreContentFreeBoundedAndCanonical() {
    assertEquals(
      "NETWORK_OFFLINE",
      ReconcileHeartbeatPolicy.normalizeSafeCode("network-offline"),
    )
    assertEquals(
      ReconcileHeartbeatPolicy.INVALID_SAFE_CODE,
      ReconcileHeartbeatPolicy.normalizeSafeCode("unsafe code with user@example.test"),
    )
    assertEquals(
      ReconcileHeartbeatPolicy.INVALID_SAFE_CODE,
      ReconcileHeartbeatPolicy.normalizeSafeCode("A".repeat(65)),
    )
  }

  @Test
  fun onlyClosedSchedulerRowsProjectAsHeartbeats() {
    val running = ReconcileHeartbeatPolicy.initialRow("account", 1_000)
    assertEquals(
      ReconcileHeartbeatSnapshot(
        ReconcileHeartbeatStatus.RUNNING,
        ReconcileHeartbeatPolicy.RUNNING_SAFE_CODE,
        1_000,
      ),
      ReconcileHeartbeatPolicy.snapshot(running),
    )
    assertNull(ReconcileHeartbeatPolicy.snapshot(running.copy(scheduler = "UNKNOWN_STATE")))
    assertNull(ReconcileHeartbeatPolicy.snapshot(running.copy(overall = "unsafe value")))
    assertNull(ReconcileHeartbeatPolicy.snapshot(running.copy(evaluatedAtMillis = -1)))
  }
}
