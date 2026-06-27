package com.example.core.automation.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.core.automation.notifications.EventReminderReceiver
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.notification.EventReminderScheduleRequest
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
        EventReminderScheduler.schedule(context, request())

        assertNotNull(existingPendingIntent())
    }

    @Test
    fun `schedule cancels reminder pending intent when reminders are disabled`() {
        EventReminderScheduler.schedule(context, request())
        every { anyConstructed<SecurePrefs>().isBirthdayRemindersEnabled() } returns false

        EventReminderScheduler.schedule(context, request())

        assertNull(existingPendingIntent())
    }

    private fun request(): EventReminderScheduleRequest {
        val nextWeek = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return EventReminderScheduleRequest(
            eventId = OccasionId("event_1"),
            contactId = ContactId("contact_1"),
            nextOccurrenceMs = nextWeek,
            notifyDaysBefore = 1,
            isActive = true,
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
