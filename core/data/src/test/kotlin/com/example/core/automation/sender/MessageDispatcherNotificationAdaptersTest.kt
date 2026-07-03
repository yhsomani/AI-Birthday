package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.notifications.SetupNotificationCopy
import com.example.core.automation.notifications.toSetupNotificationContentUri
import com.example.core.automation.notifications.toSetupNotificationCopy
import com.example.core.data.R
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.notification.SetupNotificationRequest
import com.example.domain.model.notification.SmsPermissionSetupNotificationRequest
import com.example.domain.navigation.RelateDeepLinks
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
        every { NotificationHelper.showSetupNotification(any(), any()) } just Runs
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
    fun setupNotificationRequest_mapsReasonAndContactDisplayNameToTypedRequest() {
        assertEquals(
            SetupNotificationRequest(
                reason = SetupNotificationReason.MESSAGE_EXPIRED,
                contactDisplayName = "Amit",
            ),
            setupNotificationRequest(
                reason = SetupNotificationReason.MESSAGE_EXPIRED,
                contactDisplayName = "Amit",
            ),
        )
    }

    @Test
    fun showSmsPermissionSetupNotification_mapsTypedRequestToLocalizedSetupNotification() {
        val request = SetupNotificationRequest(
            reason = SetupNotificationReason.SMS_PERMISSION_MISSING,
            contactDisplayName = "Amit",
        )

        context.showSmsPermissionSetupNotification(
            SmsPermissionSetupNotificationRequest(contactDisplayName = "Amit")
        )

        verify {
            NotificationHelper.showSetupNotification(
                context,
                request,
            )
        }
        assertEquals(
            SetupNotificationCopy(
                title = context.getString(R.string.notification_setup_sms_permission_title),
                message = context.getString(R.string.notification_setup_sms_permission_message, "Amit"),
            ),
            request.toSetupNotificationCopy(context),
        )
    }

    @Test
    fun showSetupNotification_mapsExpiredMessageRequestToLocalizedSetupNotification() {
        val request = SetupNotificationRequest(
            reason = SetupNotificationReason.MESSAGE_EXPIRED,
            contactDisplayName = "Amit",
        )

        context.showSetupNotification(request)

        verify {
            NotificationHelper.showSetupNotification(
                context,
                request,
            )
        }
        assertEquals(
            SetupNotificationCopy(
                title = context.getString(R.string.notification_setup_message_expired_title),
                message = context.getString(R.string.notification_setup_message_expired_message, "Amit"),
            ),
            request.toSetupNotificationCopy(context),
        )
    }

    @Test
    fun showSetupNotification_mapsDoubleSendRequestToLocalizedSetupNotification() {
        val request = SetupNotificationRequest(
            reason = SetupNotificationReason.DOUBLE_SEND_GUARD,
            contactDisplayName = "Amit",
        )

        context.showSetupNotification(request)

        verify {
            NotificationHelper.showSetupNotification(
                context,
                request,
            )
        }
        assertEquals(
            SetupNotificationCopy(
                title = context.getString(R.string.notification_setup_double_send_title),
                message = context.getString(R.string.notification_setup_double_send_message, "Amit"),
            ),
            request.toSetupNotificationCopy(context),
        )
    }

    @Test
    fun showSetupNotification_mapsAiProviderMissingRequestToLocalizedSetupNotification() {
        val request = SetupNotificationRequest(
            reason = SetupNotificationReason.AI_PROVIDER_MISSING,
        )

        context.showSetupNotification(request)

        verify {
            NotificationHelper.showSetupNotification(
                context,
                request,
            )
        }
        assertEquals(
            SetupNotificationCopy(
                title = context.getString(R.string.notification_setup_ai_title),
                message = context.getString(R.string.notification_setup_ai_message),
            ),
            request.toSetupNotificationCopy(context),
        )
    }

    @Test
    fun showSetupNotification_mapsRevivalAiProviderMissingRequestToLocalizedSetupNotification() {
        val request = SetupNotificationRequest(
            reason = SetupNotificationReason.REVIVAL_AI_PROVIDER_MISSING,
        )

        context.showSetupNotification(request)

        verify {
            NotificationHelper.showSetupNotification(
                context,
                request,
            )
        }
        assertEquals(
            SetupNotificationCopy(
                title = context.getString(R.string.notification_setup_ai_title),
                message = context.getString(R.string.notification_setup_revival_ai_message),
            ),
            request.toSetupNotificationCopy(context),
        )
    }

    @Test
    fun showSetupNotification_mapsExactAlarmPermissionMissingRequestToLocalizedSetupNotification() {
        val request = SetupNotificationRequest(
            reason = SetupNotificationReason.EXACT_ALARM_PERMISSION_MISSING,
        )

        context.showSetupNotification(request)

        verify {
            NotificationHelper.showSetupNotification(
                context,
                request,
            )
        }
        assertEquals(
            SetupNotificationCopy(
                title = context.getString(R.string.notification_setup_exact_alarm_title),
                message = context.getString(R.string.notification_setup_exact_alarm_message),
            ),
            request.toSetupNotificationCopy(context),
        )
    }

    @Test
    fun setupNotificationContentUri_usesCanonicalReadinessAction() {
        assertEquals(
            RelateDeepLinks.AutomationSetup.uri,
            SetupNotificationRequest(
                reason = SetupNotificationReason.SMS_PERMISSION_MISSING,
                contactDisplayName = "Amit",
            ).toSetupNotificationContentUri(),
        )
        assertEquals(
            RelateDeepLinks.Messages.uri,
            SetupNotificationRequest(
                reason = SetupNotificationReason.MESSAGE_EXPIRED,
                contactDisplayName = "Amit",
            ).toSetupNotificationContentUri(),
        )
        assertEquals(
            RelateDeepLinks.AutomationSetup.uri,
            SetupNotificationRequest(
                reason = SetupNotificationReason.AI_PROVIDER_MISSING,
            ).toSetupNotificationContentUri(),
        )
        assertEquals(
            RelateDeepLinks.AutomationSetup.uri,
            SetupNotificationRequest(
                reason = SetupNotificationReason.EXACT_ALARM_PERMISSION_MISSING,
            ).toSetupNotificationContentUri(),
        )
    }
}
