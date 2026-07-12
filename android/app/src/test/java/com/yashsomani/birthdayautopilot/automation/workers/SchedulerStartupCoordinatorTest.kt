package com.yashsomani.birthdayautopilot.automation.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerStartupCoordinatorTest {
  @Test
  fun schedulerFailureIsRecordedWithoutEscapingApplicationStartup() {
    var recorded: SchedulerStartupStatus? = null
    val result = SchedulerStartupCoordinator.initialize(
      recorder = SchedulerStartupStateRecorder { status ->
        recorded = status
        true
      },
      schedule = { error("scheduler unavailable") },
    )

    assertEquals(SchedulerStartupStatus.FAILED, result)
    assertEquals(SchedulerStartupStatus.FAILED, recorded)
  }

  @Test
  fun missingSchedulerLinkageIsRecordedWithoutEscapingApplicationStartup() {
    var recorded: SchedulerStartupStatus? = null
    val result = SchedulerStartupCoordinator.initialize(
      recorder = SchedulerStartupStateRecorder { status ->
        recorded = status
        true
      },
      schedule = { throw NoClassDefFoundError("work manager unavailable") },
    )

    assertEquals(SchedulerStartupStatus.FAILED, result)
    assertEquals(SchedulerStartupStatus.FAILED, recorded)
  }

  @Test
  fun recorderFailureAlsoFailsClosedWithoutEscaping() {
    var scheduled = false
    val result = SchedulerStartupCoordinator.initialize(
      recorder = SchedulerStartupStateRecorder { throw IllegalStateException("disk unavailable") },
      schedule = { scheduled = true },
    )

    assertFalse(scheduled)
    assertEquals(SchedulerStartupStatus.FAILED, result)
  }

  @Test
  fun readyRequiresBothSchedulingAndDurableEvidence() {
    var recordedReady = false
    val recordedStatuses = mutableListOf<SchedulerStartupStatus>()
    val ready = SchedulerStartupCoordinator.initialize(
      recorder = SchedulerStartupStateRecorder { status ->
        recordedStatuses += status
        recordedReady = status == SchedulerStartupStatus.READY
        true
      },
      schedule = {},
    )
    var unpersistedScheduled = false
    val unpersisted = SchedulerStartupCoordinator.initialize(
      recorder = SchedulerStartupStateRecorder { status ->
        status == SchedulerStartupStatus.FAILED
      },
      schedule = { unpersistedScheduled = true },
    )

    assertTrue(recordedReady)
    assertEquals(
      listOf(SchedulerStartupStatus.FAILED, SchedulerStartupStatus.READY),
      recordedStatuses,
    )
    assertEquals(SchedulerStartupStatus.READY, ready)
    assertTrue(unpersistedScheduled)
    assertEquals(SchedulerStartupStatus.FAILED, unpersisted)
    assertFalse(unpersisted == SchedulerStartupStatus.READY)
  }
}
