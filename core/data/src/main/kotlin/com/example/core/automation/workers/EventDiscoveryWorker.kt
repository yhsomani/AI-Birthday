package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.StructuredLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId

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
                        type = "BIRTHDAY",
                        label = contact.name,
                        dayOfMonth = bDay,
                        month = bMonth,
                        year = contact.birthdayYear,
                        nextOccurrenceMs = nextBirthday,
                        isActive = true
                    ))
                    eventCount++
                } else {
                    eventDao.deactivateEventsForContact(contact.id, "BIRTHDAY")
                }

                // Anniversary
                val aDay = contact.anniversaryDay
                val aMonth = contact.anniversaryMonth
                if (aDay != null && aMonth != null) {
                    val nextAnniv = computeNextOccurrence(aMonth, aDay, contact.anniversaryYear)
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_anniversary",
                        contactId = contact.id,
                        type = "ANNIVERSARY",
                        label = contact.name,
                        dayOfMonth = aDay,
                        month = aMonth,
                        year = contact.anniversaryYear,
                        nextOccurrenceMs = nextAnniv,
                        isActive = true
                    ))
                    eventCount++
                } else {
                    eventDao.deactivateEventsForContact(contact.id, "ANNIVERSARY")
                }

                // Work Anniversary
                val wDay = contact.workStartDay
                val wMonth = contact.workStartMonth
                if (wDay != null && wMonth != null) {
                    val nextWorkAnniv = computeNextOccurrence(wMonth, wDay, contact.workStartYear)
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_work_anniversary",
                        contactId = contact.id,
                        type = "WORK_ANNIVERSARY",
                        label = contact.name,
                        dayOfMonth = wDay,
                        month = wMonth,
                        year = contact.workStartYear,
                        nextOccurrenceMs = nextWorkAnniv,
                        isActive = true
                    ))
                    eventCount++
                } else {
                    eventDao.deactivateEventsForContact(contact.id, "WORK_ANNIVERSARY")
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
        val today = LocalDate.now()
        
        // Handle Feb 29 in non-leap years
        val effectiveDay = if (month == 2 && day == 29 && !Year.of(today.year).isLeap) 28 else day
        
        var candidate = LocalDate.of(today.year, month, effectiveDay)
        if (candidate.isBefore(today) || candidate.isEqual(today)) {
            // Roll forward to next year
            val nextYear = today.year + 1
            val nextEffectiveDay = if (month == 2 && day == 29 && !Year.of(nextYear).isLeap) 28 else day
            candidate = LocalDate.of(nextYear, month, nextEffectiveDay)
        }
        
        return candidate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private companion object {
        const val TAG = "EventDiscoveryWorker"
    }
}
