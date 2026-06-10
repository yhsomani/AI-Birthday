package com.example.core.automation.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.core.automation.notifications.EventReminderReceiver
import com.example.core.db.entities.EventEntity
import com.example.core.prefs.SecurePrefs
import io.mockk.*
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventReminderSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkConstructor(SecurePrefs::class)
        every { anyConstructed<SecurePrefs>().isBirthdayRemindersEnabled() } returns true
    }

    @After
    fun tearDown() {
        EventReminderScheduler.cancel(context, "event_1")
        unmockkAll()
    }

    @Test
    fun `schedule creates reminder pending intent when reminders are enabled`() {
        EventReminderScheduler.schedule(context, event())

        assertNotNull(existingPendingIntent())
    }

    @Test
    fun `schedule cancels reminder pending intent when reminders are disabled`() {
        EventReminderScheduler.schedule(context, event())
        every { anyConstructed<SecurePrefs>().isBirthdayRemindersEnabled() } returns false

        EventReminderScheduler.schedule(context, event())

        assertNull(existingPendingIntent())
    }

    private fun event(): EventEntity {
        val nextWeek = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = "BIRTHDAY",
            dayOfMonth = 1,
            month = 1,
            nextOccurrenceMs = nextWeek,
            notifyDaysBefore = 1,
        )
    }

    private fun existingPendingIntent(): PendingIntent? {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra(EventReminderScheduler.EXTRA_EVENT_ID, "event_1")
        }
        return PendingIntent.getBroadcast(
            context,
            "event_1".hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
