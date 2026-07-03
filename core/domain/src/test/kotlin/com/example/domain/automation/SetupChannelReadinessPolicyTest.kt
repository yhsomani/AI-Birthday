package com.example.domain.automation

import com.example.domain.model.MessageChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupChannelReadinessPolicyTest {

    @Test
    fun `evaluateSms treats disabled and unused SMS as ready`() {
        assertEquals(
            SmsSetupReadiness(
                reason = SmsSetupReadinessReason.DISABLED,
                status = SetupReadinessStatus.OK,
            ),
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = false,
                selectedSmsContactCount = 4,
                smsDisabled = true,
            ),
        )
        assertEquals(
            SmsSetupReadiness(
                reason = SmsSetupReadinessReason.NOT_USED,
                status = SetupReadinessStatus.OK,
            ),
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = false,
                selectedSmsContactCount = 0,
                smsDisabled = false,
            ),
        )
    }

    @Test
    fun `evaluateSms requires permission only when SMS is selected`() {
        assertEquals(
            SmsSetupReadiness(
                reason = SmsSetupReadinessReason.PERMISSION_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                selectedContactCount = 2,
            ),
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = false,
                selectedSmsContactCount = 2,
                smsDisabled = false,
            ),
        )
        assertEquals(
            SmsSetupReadiness(
                reason = SmsSetupReadinessReason.READY,
                status = SetupReadinessStatus.OK,
                selectedContactCount = 2,
            ),
            SetupChannelReadinessPolicy.evaluateSms(
                smsAllowed = true,
                selectedSmsContactCount = 2,
                smsDisabled = false,
            ),
        )
    }

    @Test
    fun `evaluateWhatsApp handles disabled unused and ready states`() {
        assertEquals(
            WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.DISABLED,
                status = SetupReadinessStatus.OK,
            ),
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = false,
                accessibilityEnabled = false,
                whatsAppInstalled = false,
                selectedWhatsAppContactCount = 3,
                whatsAppDisabled = true,
            ),
        )
        assertEquals(
            WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.NOT_USED,
                status = SetupReadinessStatus.OK,
            ),
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = false,
                accessibilityEnabled = false,
                whatsAppInstalled = false,
                selectedWhatsAppContactCount = 0,
                whatsAppDisabled = false,
            ),
        )
        assertEquals(
            WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.READY,
                status = SetupReadinessStatus.OK,
                selectedContactCount = 3,
            ),
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = true,
                accessibilityEnabled = true,
                whatsAppInstalled = true,
                selectedWhatsAppContactCount = 3,
                whatsAppDisabled = false,
            ),
        )
    }

    @Test
    fun `evaluateWhatsApp reports the first missing automation prerequisite`() {
        assertEquals(
            WhatsAppSetupReadinessReason.CONSENT_REQUIRED,
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = false,
                accessibilityEnabled = false,
                whatsAppInstalled = false,
                selectedWhatsAppContactCount = 1,
                whatsAppDisabled = false,
            ).reason,
        )
        assertEquals(
            WhatsAppSetupReadinessReason.APP_MISSING,
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = true,
                accessibilityEnabled = false,
                whatsAppInstalled = false,
                selectedWhatsAppContactCount = 1,
                whatsAppDisabled = false,
            ).reason,
        )
        assertEquals(
            WhatsAppSetupReadinessReason.ACCESSIBILITY_MISSING,
            SetupChannelReadinessPolicy.evaluateWhatsApp(
                consentGranted = true,
                accessibilityEnabled = false,
                whatsAppInstalled = true,
                selectedWhatsAppContactCount = 1,
                whatsAppDisabled = false,
            ).reason,
        )
    }

    @Test
    fun `evaluateChannelVerification warns when no selected route exists`() {
        val readiness = SetupChannelReadinessPolicy.evaluateChannelVerification(
            selectedChannels = emptySet(),
            successfulChannels = emptySet(),
        )

        assertEquals(ChannelVerificationReadinessReason.NO_ROUTES, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(SetupReadinessGroup.RELIABILITY, readiness.group)
    }

    @Test
    fun `evaluateChannelVerification passes when every selected channel has success evidence`() {
        val readiness = SetupChannelReadinessPolicy.evaluateChannelVerification(
            selectedChannels = setOf(MessageChannel.EMAIL, MessageChannel.SMS),
            successfulChannels = setOf(MessageChannel.EMAIL, MessageChannel.SMS),
        )

        assertEquals(ChannelVerificationReadinessReason.VERIFIED, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(setOf(MessageChannel.EMAIL, MessageChannel.SMS), readiness.selectedChannels)
        assertEquals(emptySet<MessageChannel>(), readiness.unverifiedChannels)
    }

    @Test
    fun `evaluateChannelVerification routes email-only gaps to email self test`() {
        val readiness = SetupChannelReadinessPolicy.evaluateChannelVerification(
            selectedChannels = setOf(MessageChannel.EMAIL, MessageChannel.SMS),
            successfulChannels = setOf(MessageChannel.SMS),
        )

        assertEquals(ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(setOf(MessageChannel.EMAIL), readiness.unverifiedChannels)
    }

    @Test
    fun `evaluateChannelVerification routes SMS and WhatsApp gaps to filtered messages first`() {
        assertEquals(
            ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES,
            SetupChannelReadinessPolicy.evaluateChannelVerification(
                selectedChannels = setOf(MessageChannel.SMS, MessageChannel.EMAIL),
                successfulChannels = emptySet(),
            ).reason,
        )
        assertEquals(
            ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES,
            SetupChannelReadinessPolicy.evaluateChannelVerification(
                selectedChannels = setOf(MessageChannel.WHATSAPP, MessageChannel.EMAIL),
                successfulChannels = emptySet(),
            ).reason,
        )
    }

    @Test
    fun `evaluateChannelVerification falls back to activity for unsupported gaps`() {
        val readiness = SetupChannelReadinessPolicy.evaluateChannelVerification(
            selectedChannels = setOf(MessageChannel.UNKNOWN),
            successfulChannels = emptySet(),
        )

        assertEquals(ChannelVerificationReadinessReason.VIEW_ACTIVITY, readiness.reason)
        assertEquals(setOf(MessageChannel.UNKNOWN), readiness.unverifiedChannels)
    }
}
