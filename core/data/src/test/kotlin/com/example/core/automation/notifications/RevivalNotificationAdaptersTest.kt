package com.example.core.automation.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.R
import com.example.domain.model.common.ContactId
import com.example.domain.model.notification.RevivalNotificationRequest
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
class RevivalNotificationAdaptersTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(NotificationHelper)
        every { NotificationHelper.showRevivalNotification(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun showRevivalNotification_mapsTypedRequestToLocalizedRevivalNotification() {
        val request = RevivalNotificationRequest(
            contactId = ContactId("contact_1"),
            contactDisplayName = "Priya",
            daysSinceContact = 42,
            suggestionText = "Hi Priya, want to catch up this weekend?",
        )

        context.showRevivalNotification(request)

        verify {
            NotificationHelper.showRevivalNotification(
                context,
                request,
            )
        }
        assertEquals(
            RevivalNotificationCopy(
                title = context.getString(R.string.notification_revival_title, "Priya"),
                message = context.getString(R.string.notification_revival_text, 42),
                bigText = context.getString(
                    R.string.notification_revival_big_text,
                    "Hi Priya, want to catch up this weekend?",
                ),
            ),
            request.toRevivalNotificationCopy(context),
        )
    }
}
