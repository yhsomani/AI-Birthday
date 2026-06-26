package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.EventType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class EventDiscoveryWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val contactDao: ContactDao,
    private val eventDao: EventDao
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val contacts = contactDao.getAllSync()
            StructuredLogger.i(TAG, "Discovering events for ${contacts.size} contacts")
            var eventCount = 0

            contacts.forEach { contact ->
                // Birthday
                val bDay = contact.birthdayDay
                val bMonth = contact.birthdayMonth
                if (bDay != null && bMonth != null) {
                    val nextBirthday = computeNextOccurrence(bMonth, bDay, contact.birthdayYear)
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_birthday",
                        contactId = contact.id,
                        type = EventType.BIRTHDAY.raw,
                        label = contact.name,
                        dayOfMonth = bDay,
                        month = bMonth,
                        year = contact.birthdayYear,
                        nextOccurrenceMs = nextBirthday,
                        isActive = true
                    ))
                    eventCount++
                } else {
                    eventDao.deactivateEventsForContact(contact.id, EventType.BIRTHDAY.raw)
                }

                // Anniversary
                val aDay = contact.anniversaryDay
                val aMonth = contact.anniversaryMonth
                if (aDay != null && aMonth != null) {
                    val nextAnniv = computeNextOccurrence(aMonth, aDay, contact.anniversaryYear)
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_anniversary",
                        contactId = contact.id,
                        type = EventType.ANNIVERSARY.raw,
                        label = contact.name,
                        dayOfMonth = aDay,
                        month = aMonth,
                        year = contact.anniversaryYear,
                        nextOccurrenceMs = nextAnniv,
                        isActive = true
                    ))
                    eventCount++
                } else {
                    eventDao.deactivateEventsForContact(contact.id, EventType.ANNIVERSARY.raw)
                }

                // Work Anniversary
                val wDay = contact.workStartDay
                val wMonth = contact.workStartMonth
                if (wDay != null && wMonth != null) {
                    val nextWorkAnniv = computeNextOccurrence(wMonth, wDay, contact.workStartYear)
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_work_anniversary",
                        contactId = contact.id,
                        type = EventType.WORK_ANNIVERSARY.raw,
                        label = contact.name,
                        dayOfMonth = wDay,
                        month = wMonth,
                        year = contact.workStartYear,
                        nextOccurrenceMs = nextWorkAnniv,
                        isActive = true
                    ))
                    eventCount++
                } else {
                    eventDao.deactivateEventsForContact(contact.id, EventType.WORK_ANNIVERSARY.raw)
                }
            }

            StructuredLogger.i(TAG, "Discovered $eventCount events")
            Result.success()
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private fun computeNextOccurrence(month: Int, day: Int, year: Int?): Long {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        val candidate = Calendar.getInstance()
        candidate.set(Calendar.HOUR_OF_DAY, 9)
        candidate.set(Calendar.MINUTE, 0)
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)

        // Handle Feb 29 for non-leap years (calculate for current year first)
        var effectiveDay = day
        if (month == 2 && day == 29 && !isLeapYear(currentYear)) {
            effectiveDay = 28
        }

        var targetYear = currentYear
        if (month < currentMonth || (month == currentMonth && effectiveDay <= currentDay)) {
            targetYear = currentYear + 1
            // Recalculate for the new target year
            effectiveDay = day
            if (month == 2 && day == 29 && !isLeapYear(targetYear)) {
                effectiveDay = 28
            }
        }

        candidate.set(Calendar.YEAR, targetYear)
        candidate.set(Calendar.MONTH, month - 1)
        candidate.set(Calendar.DAY_OF_MONTH, effectiveDay)

        return candidate.timeInMillis
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private companion object {
        const val TAG = "EventDiscoveryWorker"
    }
}
