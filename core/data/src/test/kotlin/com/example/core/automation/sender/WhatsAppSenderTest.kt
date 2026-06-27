package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.accessibility.WhatsAppAccessibilityService
import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.core.accessibility.WhatsAppSendResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsAppSenderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WhatsAppAccessibilityService.instance = null
        WhatsAppAccessibilityService.pendingQueue.clear()
    }

    @After
    fun tearDown() {
        WhatsAppAccessibilityService.instance = null
        WhatsAppAccessibilityService.pendingQueue.clear()
    }

    @Test
    fun sendWithResult_returnsServiceDisabledWhenAccessibilityServiceIsNotConnected() = runTest {
        val result = WhatsAppSender(context).sendWithResult(
            phoneNumber = "+1 (555) 123-4567",
            message = "Selected",
            eventId = "event_1",
        )

        assertEquals(
            WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED),
            result,
        )
    }

    @Test
    fun send_keepsBooleanCompatibilityForUnavailableAccessibilityService() = runTest {
        val sent = WhatsAppSender(context).send(
            phoneNumber = "+1 (555) 123-4567",
            message = "Selected",
            eventId = "event_1",
        )

        assertFalse(sent)
    }
}
