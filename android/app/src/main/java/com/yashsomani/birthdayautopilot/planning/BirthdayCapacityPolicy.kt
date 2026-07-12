package com.yashsomani.birthdayautopilot.planning

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Shared deterministic capacity primitives used by configuration admission and the send gate. */
object BirthdayCapacityPolicy {
  fun allowsArm(
    armedOnCivilDate: Int,
    dailyCap: Int,
    occurrenceAlreadyArmed: Boolean,
  ): Boolean {
    if (armedOnCivilDate < 0 || dailyCap !in 1..MAX_DAILY_CAP) return false
    return if (occurrenceAlreadyArmed) {
      armedOnCivilDate <= dailyCap
    } else {
      armedOnCivilDate < dailyCap
    }
  }

  fun exactWindowBindingMatches(
    localDate: String,
    timeZoneId: String,
    resolvedWindowStartMillis: Long,
    resolvedWindowEndMillis: Long,
    startMinute: Int,
    effectiveEndMinute: Int,
  ): Boolean {
    if (
      startMinute !in 0..1439 ||
      effectiveEndMinute !in 1..1440 ||
      startMinute >= effectiveEndMinute ||
      resolvedWindowStartMillis < 0 ||
      resolvedWindowEndMillis <= resolvedWindowStartMillis
    ) return false
    return try {
      val date = LocalDate.parse(localDate)
      val zone = ZoneId.of(timeZoneId)
      resolve(date, startMinute, zone).toEpochMilli() == resolvedWindowStartMillis &&
        resolve(date, effectiveEndMinute, zone).toEpochMilli() == resolvedWindowEndMillis
    } catch (_: DateTimeException) {
      false
    } catch (_: ArithmeticException) {
      false
    }
  }

  /** Product DST rule: gaps advance to the next valid instant; overlaps choose the first offset. */
  fun resolve(date: LocalDate, minute: Int, zoneId: ZoneId): Instant {
    require(minute in 0..1440) { "civil-minute-invalid" }
    val effectiveDate = if (minute == 1440) date.plusDays(1) else date
    val effectiveMinute = if (minute == 1440) 0 else minute
    val local = effectiveDate.atTime(LocalTime.of(effectiveMinute / 60, effectiveMinute % 60))
    val rules = zoneId.rules
    val offsets = rules.getValidOffsets(local)
    val zoned = when {
      offsets.size == 1 -> ZonedDateTime.ofLocal(local, zoneId, offsets.single())
      offsets.size > 1 -> ZonedDateTime.ofLocal(local, zoneId, offsets.first())
      else -> {
        val transition = checkNotNull(rules.getTransition(local))
        ZonedDateTime.ofLocal(transition.dateTimeAfter, zoneId, transition.offsetAfter)
      }
    }
    return zoned.toInstant()
  }

  const val MAX_DAILY_CAP = 20
}
