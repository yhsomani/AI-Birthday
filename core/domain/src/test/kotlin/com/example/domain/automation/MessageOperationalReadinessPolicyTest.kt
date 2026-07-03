package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.message.PendingMessageListItem
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageOperationalReadinessPolicyTest {

    @Test
    fun `route blocker wins before status readiness`() {
        val result = MessageOperationalReadinessPolicy.evaluate(
            message = message(status = MessageStatus.APPROVED, channel = MessageChannel.EMAIL),
            contact = contact(primaryEmail = null),
            channelBlackoutJson = "[]",
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
        )

        assertEquals(MessageOperationalReadiness.MISSING_EMAIL, result)
        assertTrue(result.blocksTaskFlow)
        assertTrue(result.requiresContactOrChannelFix)
    }

    @Test
    fun `ready route maps message status into operational readiness`() {
        val contact = contact(primaryPhone = "+919999900000")

        assertEquals(
            MessageOperationalReadiness.READY_FOR_REVIEW,
            evaluate(status = MessageStatus.PENDING, contact = contact),
        )
        assertEquals(
            MessageOperationalReadiness.APPROVED_SCHEDULED,
            evaluate(status = MessageStatus.APPROVED, contact = contact),
        )
        assertEquals(
            MessageOperationalReadiness.SENDING_NOW,
            evaluate(status = MessageStatus.DISPATCHING, contact = contact),
        )
        assertEquals(
            MessageOperationalReadiness.FAILED_CHECK_SETUP,
            evaluate(status = MessageStatus.FAILED, contact = contact),
        )
    }

    @Test
    fun `approved future message waits for scheduled time`() {
        val nowMs = 1_000L

        val result = MessageOperationalReadinessPolicy.evaluate(
            message = message(
                status = MessageStatus.APPROVED,
                channel = MessageChannel.SMS,
                scheduledForMs = nowMs + 60_000L,
            ),
            contact = contact(primaryPhone = "+919999900000"),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
            nowMs = nowMs,
            quietHoursStart = 0,
            quietHoursEnd = 0,
            blackoutDatesJson = "[]",
        )

        assertEquals(MessageOperationalReadiness.APPROVED_WAITING_FOR_SCHEDULE, result)
    }

    @Test
    fun `approved due message waits for allowed send window during quiet hours`() {
        val quietNowMs = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = MessageOperationalReadinessPolicy.evaluate(
            message = message(
                status = MessageStatus.APPROVED,
                channel = MessageChannel.SMS,
                scheduledForMs = quietNowMs - 1_000L,
            ),
            contact = contact(primaryPhone = "+919999900000"),
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
            nowMs = quietNowMs,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            blackoutDatesJson = "[]",
        )

        assertEquals(MessageOperationalReadiness.APPROVED_WAITING_FOR_ALLOWED_WINDOW, result)
    }

    @Test
    fun `route blockers expose shared task-flow flags`() {
        val routeBlockers = listOf(
            MessageOperationalReadiness.CONTACT_MISSING,
            MessageOperationalReadiness.CHANNEL_DISABLED,
            MessageOperationalReadiness.MISSING_PHONE,
            MessageOperationalReadiness.MISSING_EMAIL,
            MessageOperationalReadiness.EMAIL_SETUP_MISSING,
        )
        val nonRouteBlockers = MessageOperationalReadiness.entries - routeBlockers.toSet()

        routeBlockers.forEach { readiness ->
            assertTrue("$readiness should block task flow", readiness.blocksTaskFlow)
            assertTrue("$readiness should route to setup recovery", readiness.requiresContactOrChannelFix)
        }
        nonRouteBlockers.forEach { readiness ->
            assertFalse("$readiness should not block task flow", readiness.blocksTaskFlow)
            assertFalse("$readiness should not route to setup recovery", readiness.requiresContactOrChannelFix)
        }
    }

    private fun evaluate(
        status: MessageStatus,
        contact: ContactMessageContext,
    ): MessageOperationalReadiness {
        return MessageOperationalReadinessPolicy.evaluate(
            message = message(status = status, channel = MessageChannel.SMS),
            contact = contact,
            channelBlackoutJson = "[]",
            senderEmail = "",
            senderEmailPassword = "",
        )
    }

    private fun message(
        status: MessageStatus,
        channel: MessageChannel,
        scheduledForMs: Long = 1_700_000_000_000L,
    ) = PendingMessageListItem(
        id = MessageDraftId("pm_1"),
        contactId = ContactId("c_1"),
        occasionId = OccasionId("e_1"),
        selectedVariantText = "Happy birthday",
        standardVariant = "Happy birthday",
        channel = channel,
        scheduledForMs = scheduledForMs,
        approvalMode = ApprovalMode.UNKNOWN,
        status = status,
        editedByUser = false,
        userEditedText = null,
    )

    private fun contact(
        primaryPhone: String? = null,
        primaryEmail: String? = null,
    ) = ContactMessageContext(
        id = ContactId("c_1"),
        displayName = "Asha",
        avatarUrl = null,
        primaryPhone = primaryPhone,
        primaryEmail = primaryEmail,
    )
}
