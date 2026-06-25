package com.example.domain.event

import java.util.Calendar

object EventDatePolicy {
    fun isValidDate(day: Int, month: Int, year: Int?): Boolean {
        if (month !in 1..12 || day !in 1..31) return false
        val validationYear = year ?: 2024
        return tryCreateDate(validationYear, month, day) != null
    }

    fun nextOccurrenceMs(
        day: Int,
        month: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var candidateYear = calendar.get(Calendar.YEAR)
        repeat(16) {
            val candidate = tryCreateDate(candidateYear, month, day)
            if (candidate != null && candidate.timeInMillis >= calendar.timeInMillis) {
                return candidate.timeInMillis
            }
            candidateYear++
        }
        return null
    }

    private fun tryCreateDate(year: Int, month: Int, day: Int): Calendar? {
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
