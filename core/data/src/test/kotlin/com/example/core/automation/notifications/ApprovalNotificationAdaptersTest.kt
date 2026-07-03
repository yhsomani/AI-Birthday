package com.example.core.automation.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.R
import com.example.core.gemini.MessageVariants
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.service.MessageVariantsResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApprovalNotificationAdaptersTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(NotificationHelper)
        every { NotificationHelper.showApprovalNotification(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun showApprovalNotification_mapsTypedRequestToLocalizedApprovalNotification() {
        val request = request()
        val variants = variants()

        context.showApprovalNotification(request, variants)

        verify {
            NotificationHelper.showApprovalNotification(
                context,
                request,
                variants,
            )
        }
        assertEquals(
            ApprovalNotificationCopy(
                title = context.getString(R.string.notification_approval_title, "Amit"),
                message = "Standard wish",
            ),
            request.toApprovalNotificationCopy(context, variants),
        )
    }

    @Test
    fun messageVariantsResult_mapsToApprovalMessageVariants() {
        val mapped = MessageVariantsResult(
            short = "Short",
            standard = "Standard",
            long = "Long",
            formal = "Formal",
            funny = "Funny",
            emotional = "Emotional",
            recommended = "formal",
            isUsingFallback = true,
        ).toApprovalMessageVariants()

        assertEquals("Short", mapped.short)
        assertEquals("Standard", mapped.standard)
        assertEquals("Long", mapped.long)
        assertEquals("Formal", mapped.formal)
        assertEquals("Funny", mapped.funny)
        assertEquals("Emotional", mapped.emotional)
        assertEquals("formal", mapped.recommended)
        assertTrue(mapped.isUsingFallback)
    }

    private fun request(): ApprovalNotificationRequest {
        return ApprovalNotificationRequest(
            contactId = ContactId("contact_1"),
            contactDisplayName = "Amit",
            eventId = OccasionId("event_1"),
            messageId = MessageDraftId("message_1"),
        )
    }

    private fun variants(): MessageVariants {
        return MessageVariants(
            short = "Short wish",
            standard = "Standard wish",
            long = "Long wish",
            formal = "Formal wish",
            funny = "Funny wish",
            emotional = "Emotional wish",
            recommended = "standard",
        )
    }
}
