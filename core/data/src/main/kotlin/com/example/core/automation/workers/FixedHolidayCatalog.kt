package com.example.core.automation.workers

import java.util.Calendar

private data class FixedHoliday(
    val id: String,
    val name: String,
    val month: Int,
    val dayOfMonth: Int,
    val tone: String,
)

internal data class HolidayOccurrence(
    val id: String,
    val name: String,
    val tone: String,
    val year: Int,
    val occurrenceMs: Long,
)

internal object FixedHolidayCatalog {
    private val fixedHolidays = listOf(
        FixedHoliday("NEW_YEAR", "New Year", Calendar.JANUARY, 1, "hopeful, warm, fresh-start"),
        FixedHoliday("INDIA_REPUBLIC_DAY", "Republic Day", Calendar.JANUARY, 26, "respectful, proud, inclusive"),
        FixedHoliday("WOMENS_DAY", "International Women's Day", Calendar.MARCH, 8, "respectful, appreciative, empowering"),
        FixedHoliday("INDIA_INDEPENDENCE_DAY", "Independence Day", Calendar.AUGUST, 15, "respectful, proud, inclusive"),
        FixedHoliday("GANDHI_JAYANTI", "Gandhi Jayanti", Calendar.OCTOBER, 2, "peaceful, reflective, respectful"),
        FixedHoliday("CHRISTMAS", "Christmas", Calendar.DECEMBER, 25, "warm, festive, kind"),
    )

    fun upcoming(nowMs: Long, lookaheadDays: Int): List<HolidayOccurrence> {
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val endMs = nowMs + lookaheadDays.coerceAtLeast(0) * 24L * 60L * 60L * 1000L
        val currentYear = now.get(Calendar.YEAR)
        return listOf(currentYear, currentYear + 1)
            .flatMap { year ->
                fixedHolidays.map { holiday ->
                    val occurrenceMs = Calendar.getInstance().apply {
                        clear()
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, holiday.month)
                        set(Calendar.DAY_OF_MONTH, holiday.dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                    }.timeInMillis
                    HolidayOccurrence(
                        id = holiday.id,
                        name = holiday.name,
                        tone = holiday.tone,
                        year = year,
                        occurrenceMs = occurrenceMs,
                    )
                }
            }
            .filter { it.occurrenceMs in nowMs..endMs }
            .sortedBy { it.occurrenceMs }
    }
}
