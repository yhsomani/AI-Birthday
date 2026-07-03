package com.example.core.automation.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.R
import com.example.domain.model.notification.SystemAlertNotificationReason
import com.example.domain.model.notification.SystemAlertNotificationRequest
import com.example.domain.navigation.RelateDeepLinks
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemAlertNotificationAdaptersTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(NotificationHelper)
        every { NotificationHelper.showSystemAlert(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun showSystemAlert_mapsAiFallbackRequestToLocalizedSystemAlert() {
        val request = SystemAlertNotificationRequest(
            reason = SystemAlertNotificationReason.AI_FALLBACK_USED,
        )

        context.showSystemAlert(request)

        verify {
            NotificationHelper.showSystemAlert(
                context,
                request,
            )
        }
        assertEquals(
            SystemAlertNotificationCopy(
                title = context.getString(R.string.notification_ai_fallback_title),
                message = context.getString(R.string.notification_ai_fallback_message),
            ),
            request.toSystemAlertNotificationCopy(context),
        )
        assertEquals(RelateDeepLinks.AutomationSetup.uri, request.toSystemAlertNotificationContentUri())
    }

    @Test
    fun showSystemAlert_mapsBackupStaleRequestToLocalizedSystemAlert() {
        val request = SystemAlertNotificationRequest(
            reason = SystemAlertNotificationReason.BACKUP_STALE,
        )

        context.showSystemAlert(request)

        verify {
            NotificationHelper.showSystemAlert(
                context,
                request,
            )
        }
        assertEquals(
            SystemAlertNotificationCopy(
                title = context.getString(R.string.notification_backup_reminder_title),
                message = context.getString(R.string.notification_backup_reminder_message),
            ),
            request.toSystemAlertNotificationCopy(context),
        )
        assertEquals(RelateDeepLinks.BackupRestore.uri, request.toSystemAlertNotificationContentUri())
    }
}
