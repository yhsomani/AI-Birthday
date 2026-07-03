package com.example.domain.automation

import com.example.domain.model.message.ExactSendScheduleState
import com.example.domain.model.message.ExactSendScheduleUpdate

sealed interface ExactSendScheduleDecision {
    val scheduledForMs: Long
    val scheduleUpdate: ExactSendScheduleUpdate?

    data class EnqueueNow(
        override val scheduledForMs: Long,
        override val scheduleUpdate: ExactSendScheduleUpdate?,
    ) : ExactSendScheduleDecision

    data class ScheduleExactAlarm(
        override val scheduledForMs: Long,
        override val scheduleUpdate: ExactSendScheduleUpdate?,
    ) : ExactSendScheduleDecision

    data class ScheduleWorkFallback(
        override val scheduledForMs: Long,
        val initialDelayMs: Long,
        override val scheduleUpdate: ExactSendScheduleUpdate?,
    ) : ExactSendScheduleDecision
}

object ExactSendSchedulePolicy {
    fun decide(
        scheduleState: ExactSendScheduleState,
        quietHoursStart: Int,
        quietHoursEnd: Int,
        blackoutDatesJson: String,
        canScheduleExactAlarm: Boolean,
        nowMs: Long,
    ): ExactSendScheduleDecision {
        val allowedScheduledForMs = AutomationSchedulePolicy.nextAllowedSendMs(
            candidateMs = scheduleState.scheduledForMs,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
            blackoutDatesJson = blackoutDatesJson,
            nowMs = nowMs,
        )
        val scheduleUpdate = if (allowedScheduledForMs != scheduleState.scheduledForMs) {
            scheduleState.scheduleUpdate(allowedScheduledForMs)
        } else {
            null
        }

        if (allowedScheduledForMs <= nowMs) {
            return ExactSendScheduleDecision.EnqueueNow(
                scheduledForMs = allowedScheduledForMs,
                scheduleUpdate = scheduleUpdate,
            )
        }

        return if (canScheduleExactAlarm) {
            ExactSendScheduleDecision.ScheduleExactAlarm(
                scheduledForMs = allowedScheduledForMs,
                scheduleUpdate = scheduleUpdate,
            )
        } else {
            ExactSendScheduleDecision.ScheduleWorkFallback(
                scheduledForMs = allowedScheduledForMs,
                initialDelayMs = allowedScheduledForMs - nowMs,
                scheduleUpdate = scheduleUpdate,
            )
        }
    }
}
