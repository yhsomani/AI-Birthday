package com.example.domain.automation

import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.ExactSendScheduleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactSendSchedulePolicyTest {
    @Test
    fun `past schedule enqueues now and records schedule update`() {
        val decision = ExactSendSchedulePolicy.decide(
            scheduleState = scheduleState(scheduledForMs = NOW_MS - 60_000L),
            quietHoursStart = 0,
            quietHoursEnd = 0,
            blackoutDatesJson = "[]",
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertTrue(decision is ExactSendScheduleDecision.EnqueueNow)
        decision as ExactSendScheduleDecision.EnqueueNow
        assertEquals(NOW_MS, decision.scheduledForMs)
        assertEquals(MessageDraftId("message_1"), decision.scheduleUpdate?.messageId)
        assertEquals(NOW_MS, decision.scheduleUpdate?.scheduledForMs)
    }

    @Test
    fun `future schedule uses exact alarm when permission is available`() {
        val scheduledForMs = NOW_MS + 60_000L
        val decision = ExactSendSchedulePolicy.decide(
            scheduleState = scheduleState(scheduledForMs = scheduledForMs),
            quietHoursStart = 0,
            quietHoursEnd = 0,
            blackoutDatesJson = "[]",
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertTrue(decision is ExactSendScheduleDecision.ScheduleExactAlarm)
        decision as ExactSendScheduleDecision.ScheduleExactAlarm
        assertEquals(scheduledForMs, decision.scheduledForMs)
        assertNull(decision.scheduleUpdate)
    }

    @Test
    fun `future schedule uses work fallback when exact alarm permission is unavailable`() {
        val scheduledForMs = NOW_MS + 90_000L
        val decision = ExactSendSchedulePolicy.decide(
            scheduleState = scheduleState(scheduledForMs = scheduledForMs),
            quietHoursStart = 0,
            quietHoursEnd = 0,
            blackoutDatesJson = "[]",
            canScheduleExactAlarm = false,
            nowMs = NOW_MS,
        )

        assertTrue(decision is ExactSendScheduleDecision.ScheduleWorkFallback)
        decision as ExactSendScheduleDecision.ScheduleWorkFallback
        assertEquals(scheduledForMs, decision.scheduledForMs)
        assertEquals(90_000L, decision.initialDelayMs)
        assertNull(decision.scheduleUpdate)
    }

    @Test
    fun `quiet hours adjustment is included in exact alarm decision`() {
        val scheduledForMs = NOW_MS + 60_000L
        val expectedScheduledForMs = AutomationSchedulePolicy.nextAllowedSendMs(
            candidateMs = scheduledForMs,
            quietHoursStart = 0,
            quietHoursEnd = 23,
            blackoutDatesJson = "[]",
            nowMs = NOW_MS,
        )

        val decision = ExactSendSchedulePolicy.decide(
            scheduleState = scheduleState(scheduledForMs = scheduledForMs),
            quietHoursStart = 0,
            quietHoursEnd = 23,
            blackoutDatesJson = "[]",
            canScheduleExactAlarm = true,
            nowMs = NOW_MS,
        )

        assertTrue(decision is ExactSendScheduleDecision.ScheduleExactAlarm)
        decision as ExactSendScheduleDecision.ScheduleExactAlarm
        assertEquals(expectedScheduledForMs, decision.scheduledForMs)
        assertEquals(expectedScheduledForMs, decision.scheduleUpdate?.scheduledForMs)
    }

    private fun scheduleState(scheduledForMs: Long): ExactSendScheduleState {
        return ExactSendScheduleState(
            messageId = MessageDraftId("message_1"),
            occasionId = OccasionId("event_1"),
            scheduledForMs = scheduledForMs,
        )
    }

    private companion object {
        const val NOW_MS = 1_800_000_000_000L
    }
}
