package com.example.domain.automation

import com.example.domain.model.MessageChannel
import java.util.Calendar
import java.util.Locale

object AutomationSchedulePolicy {
    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    private const val MAX_BLACKOUT_SEARCH_DAYS = 366

    fun messageSendTimeMs(
        eventOccurrenceMs: Long,
        customHour: Int?,
        customMinute: Int?,
        quietHoursStart: Int,
        quietHoursEnd: Int,
        blackoutDatesJson: String,
        nowMs: Long = System.currentTimeMillis(),
        defaultHour: Int = 9,
        defaultMinute: Int = 0,
    ): Long {
        val hour = customHour ?: defaultHour
        val minute = customMinute ?: defaultMinute
        val candidate = timeOnDate(eventOccurrenceMs, hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .coerceAtLeast(nowMs)
        return nextAllowedSendMs(
            candidateMs = candidate,
            quietHoursStart = quietHoursStart,
            quietHoursEnd = quietHoursEnd,
            blackoutDatesJson = blackoutDatesJson,
            nowMs = nowMs,
        )
    }

    fun nextAllowedSendMs(
        candidateMs: Long,
        quietHoursStart: Int,
        quietHoursEnd: Int,
        blackoutDatesJson: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        val blackoutDates = blackoutDatesJson.toDateSet()
        var candidate = candidateMs.coerceAtLeast(nowMs)
        repeat(MAX_BLACKOUT_SEARCH_DAYS) {
            if (dateKey(candidate) in blackoutDates) {
                candidate = startOfNextDay(candidate)
                return@repeat
            }

            val quietAdjusted = adjustForQuietHours(
                candidateMs = candidate,
                quietHoursStart = quietHoursStart.coerceIn(0, 23),
                quietHoursEnd = quietHoursEnd.coerceIn(0, 23),
            )
            if (quietAdjusted != candidate) {
                candidate = quietAdjusted
                return@repeat
            }
            return candidate
        }
        return candidate
    }

    fun reminderTimeMs(
        eventOccurrenceMs: Long,
        notifyDaysBefore: Int,
        nowMs: Long = System.currentTimeMillis(),
        defaultHour: Int = 9,
        defaultMinute: Int = 0,
    ): Long {
        val reminderDate = eventOccurrenceMs - notifyDaysBefore.coerceAtLeast(0) * ONE_DAY_MS
        return timeOnDate(reminderDate, defaultHour.coerceIn(0, 23), defaultMinute.coerceIn(0, 59))
            .coerceAtLeast(nowMs)
    }

    fun isChannelBlocked(channel: MessageChannel, channelBlackoutJson: String): Boolean {
        return channel != MessageChannel.UNKNOWN && channel in channelBlackoutJson.toChannelSet()
    }

    private fun adjustForQuietHours(
        candidateMs: Long,
        quietHoursStart: Int,
        quietHoursEnd: Int,
    ): Long {
        if (quietHoursStart == quietHoursEnd) return candidateMs

        val calendar = Calendar.getInstance().apply { timeInMillis = candidateMs }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val inQuietHours = if (quietHoursStart < quietHoursEnd) {
            hour in quietHoursStart until quietHoursEnd
        } else {
            hour >= quietHoursStart || hour < quietHoursEnd
        }
        if (!inQuietHours) return candidateMs

        val adjusted = Calendar.getInstance().apply {
            timeInMillis = candidateMs
            set(Calendar.HOUR_OF_DAY, quietHoursEnd)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (quietHoursStart > quietHoursEnd && hour >= quietHoursStart) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return adjusted.timeInMillis
    }

    private fun timeOnDate(sourceMs: Long, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = sourceMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfNextDay(sourceMs: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = sourceMs
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dateKey(sourceMs: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = sourceMs }
        return "%04d-%02d-%02d".format(
            Locale.US,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun String.toDateSet(): Set<String> {
        return DATE_PATTERN.findAll(this)
            .map { it.value }
            .toSet()
    }

    private fun String.toChannelSet(): Set<MessageChannel> {
        return TOKEN_PATTERN.findAll(this)
            .map { MessageChannel.fromRaw(it.groupValues[1]) }
            .filter { it != MessageChannel.UNKNOWN }
            .toSet()
    }

    private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val TOKEN_PATTERN = Regex("\"([A-Za-z_]+)\"")
}
