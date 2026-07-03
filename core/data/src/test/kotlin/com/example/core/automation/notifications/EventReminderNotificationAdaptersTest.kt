package com.example.core.automation.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.R
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.notification.EventReminderNotificationRequest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventReminderNotificationAdaptersTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(NotificationHelper)
        every { NotificationHelper.showEventReminderNotification(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun showEventReminderNotification_mapsTypedRequestToLocalizedReminderNotification() {
        val request = request()

        context.showEventReminderNotification(request)

        verify {
            NotificationHelper.showEventReminderNotification(
                context,
                request,
            )
        }
        assertEquals(
            EventReminderNotificationCopy(
                title = context.getString(R.string.notification_event_reminder_title, "Amit"),
                message = context.getString(
                    R.string.notification_event_reminder_text,
                    "Amit",
                    "work anniversary",
                ),
            ),
            request.toEventReminderNotificationCopy(context),
        )
    }

    private fun request(): EventReminderNotificationRequest {
        return EventReminderNotificationRequest(
            contactId = ContactId("contact_1"),
            contactDisplayName = "Amit",
            eventId = OccasionId("event_1"),
            eventType = "WORK_ANNIVERSARY",
        )
    }
}
