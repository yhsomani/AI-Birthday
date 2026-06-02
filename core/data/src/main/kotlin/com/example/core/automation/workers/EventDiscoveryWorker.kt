package com.example.automation.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.entities.EventEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

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
            val now = System.currentTimeMillis()

            contacts.forEach { contact ->
                val bDay = contact.birthdayDay
                val bMonth = contact.birthdayMonth
                if (bDay != null && bMonth != null) {
                    val nextBirthday = computeNextOccurrence(bDay, bMonth)
                    val daysUntil = TimeUnit.MILLISECONDS.toDays(nextBirthday - now).toInt()
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_birthday",
                        contactId = contact.id,
                        type = "BIRTHDAY",
                        dayOfMonth = bDay,
                        month = bMonth,
                        nextOccurrenceMs = nextBirthday,
                        daysUntil = daysUntil.coerceAtLeast(0)
                    ))
                }

                val aDay = contact.anniversaryDay
                val aMonth = contact.anniversaryMonth
                if (aDay != null && aMonth != null) {
                    val nextAnniv = computeNextOccurrence(aDay, aMonth)
                    val daysUntil = TimeUnit.MILLISECONDS.toDays(nextAnniv - now).toInt()
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_anniversary",
                        contactId = contact.id,
                        type = "ANNIVERSARY",
                        dayOfMonth = aDay,
                        month = aMonth,
                        nextOccurrenceMs = nextAnniv,
                        daysUntil = daysUntil.coerceAtLeast(0)
                    ))
                }

                val wDay = contact.workStartDay
                val wMonth = contact.workStartMonth
                if (wDay != null && wMonth != null) {
                    val nextWorkAnniv = computeNextOccurrence(wDay, wMonth)
                    val daysUntil = TimeUnit.MILLISECONDS.toDays(nextWorkAnniv - now).toInt()
                    eventDao.upsert(EventEntity(
                        id = "${contact.id}_work_anniversary",
                        contactId = contact.id,
                        type = "WORK_ANNIVERSARY",
                        dayOfMonth = wDay,
                        month = wMonth,
                        nextOccurrenceMs = nextWorkAnniv,
                        daysUntil = daysUntil.coerceAtLeast(0)
                    ))
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private fun computeNextOccurrence(day: Int, month: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private companion object {
        const val TAG = "EventDiscoveryWorker"
    }
}
