package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.data.R
import com.example.domain.model.notification.SmsPermissionSetupNotificationRequest
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
class MessageDispatcherNotificationAdaptersTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(NotificationHelper)
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun smsPermissionSetupNotificationRequest_mapsContactDisplayNameToTypedRequest() {
        assertEquals(
            SmsPermissionSetupNotificationRequest(contactDisplayName = "Amit"),
            smsPermissionSetupNotificationRequest(contactDisplayName = "Amit"),
        )
    }

    @Test
    fun showSmsPermissionSetupNotification_mapsTypedRequestToLocalizedSetupNotification() {
        context.showSmsPermissionSetupNotification(
            SmsPermissionSetupNotificationRequest(contactDisplayName = "Amit")
        )

        verify {
            NotificationHelper.showSetupNotification(
                context,
                context.getString(R.string.notification_setup_sms_permission_title),
                context.getString(R.string.notification_setup_sms_permission_message, "Amit"),
            )
        }
    }
}
