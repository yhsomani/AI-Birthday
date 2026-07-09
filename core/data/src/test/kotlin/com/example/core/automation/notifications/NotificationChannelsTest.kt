package com.example.core.automation.notifications

import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationChannelsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun createChannels_createsExpectedRelateChannels() {
        NotificationHelper.createChannels(context)

        val approval = requireChannel(NotificationHelper.APPROVAL)
        assertEquals(context.getString(R.string.notification_channel_approval_name), approval.name)
        assertEquals(
            context.getString(R.string.notification_channel_approval_description),
            approval.description,
        )
        assertEquals(NotificationManager.IMPORTANCE_HIGH, approval.importance)
        assertTrue(approval.shouldVibrate())

        val revival = requireChannel(NotificationHelper.REVIVAL)
        assertEquals(context.getString(R.string.notification_channel_revival_name), revival.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, revival.importance)

        val eventReminders = requireChannel(NotificationHelper.EVENT_REMINDERS)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, eventReminders.importance)
        assertTrue(eventReminders.shouldVibrate())

        val system = requireChannel(NotificationHelper.SYSTEM)
        assertEquals(context.getString(R.string.notification_channel_system_name), system.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, system.importance)

        val dispatchStatus = requireChannel(NotificationHelper.DISPATCH_STATUS)
        assertEquals(context.getString(R.string.notification_channel_dispatch_status_name), dispatchStatus.name)
        assertEquals(NotificationManager.IMPORTANCE_LOW, dispatchStatus.importance)
        assertNull(dispatchStatus.sound)
    }

    private fun requireChannel(id: String): NotificationChannel {
        val channel = manager.getNotificationChannel(id)
        assertNotNull(channel)
        return channel!!
    }
}
