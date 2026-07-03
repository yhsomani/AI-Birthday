package com.example.domain.automation

import com.example.domain.model.MessageChannel

enum class SmsSetupReadinessReason {
    DISABLED,
    NOT_USED,
    READY,
    PERMISSION_MISSING,
}

enum class WhatsAppSetupReadinessReason {
    DISABLED,
    NOT_USED,
    CONSENT_REQUIRED,
    APP_MISSING,
    ACCESSIBILITY_MISSING,
    READY,
}

enum class ChannelVerificationReadinessReason {
    NO_ROUTES,
    VERIFIED,
    EMAIL_TEST_REQUIRED,
    REVIEW_SMS_MESSAGES,
    REVIEW_WHATSAPP_MESSAGES,
    VIEW_ACTIVITY,
}

data class SmsSetupReadiness(
    val reason: SmsSetupReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
    val selectedContactCount: Int = 0,
)

data class WhatsAppSetupReadiness(
    val reason: WhatsAppSetupReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RELIABILITY,
    val selectedContactCount: Int = 0,
)

data class ChannelVerificationReadiness(
    val reason: ChannelVerificationReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RELIABILITY,
    val selectedChannels: Set<MessageChannel> = emptySet(),
    val unverifiedChannels: Set<MessageChannel> = emptySet(),
)

object SetupChannelReadinessPolicy {
    fun evaluateSms(
        smsAllowed: Boolean,
        selectedSmsContactCount: Int,
        smsDisabled: Boolean,
    ): SmsSetupReadiness {
        return when {
            smsDisabled -> SmsSetupReadiness(
                reason = SmsSetupReadinessReason.DISABLED,
                status = SetupReadinessStatus.OK,
            )
            selectedSmsContactCount == 0 -> SmsSetupReadiness(
                reason = SmsSetupReadinessReason.NOT_USED,
                status = SetupReadinessStatus.OK,
            )
            smsAllowed -> SmsSetupReadiness(
                reason = SmsSetupReadinessReason.READY,
                status = SetupReadinessStatus.OK,
                selectedContactCount = selectedSmsContactCount,
            )
            else -> SmsSetupReadiness(
                reason = SmsSetupReadinessReason.PERMISSION_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                selectedContactCount = selectedSmsContactCount,
            )
        }
    }

    fun evaluateWhatsApp(
        consentGranted: Boolean,
        accessibilityEnabled: Boolean,
        whatsAppInstalled: Boolean,
        selectedWhatsAppContactCount: Int,
        whatsAppDisabled: Boolean,
    ): WhatsAppSetupReadiness {
        return when {
            whatsAppDisabled -> WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.DISABLED,
                status = SetupReadinessStatus.OK,
            )
            selectedWhatsAppContactCount == 0 -> WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.NOT_USED,
                status = SetupReadinessStatus.OK,
            )
            !consentGranted -> WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.CONSENT_REQUIRED,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                selectedContactCount = selectedWhatsAppContactCount,
            )
            !whatsAppInstalled -> WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.APP_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                selectedContactCount = selectedWhatsAppContactCount,
            )
            !accessibilityEnabled -> WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.ACCESSIBILITY_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                selectedContactCount = selectedWhatsAppContactCount,
            )
            else -> WhatsAppSetupReadiness(
                reason = WhatsAppSetupReadinessReason.READY,
                status = SetupReadinessStatus.OK,
                selectedContactCount = selectedWhatsAppContactCount,
            )
        }
    }

    fun evaluateChannelVerification(
        selectedChannels: Set<MessageChannel>,
        successfulChannels: Set<MessageChannel>,
    ): ChannelVerificationReadiness {
        if (selectedChannels.isEmpty()) {
            return ChannelVerificationReadiness(
                reason = ChannelVerificationReadinessReason.NO_ROUTES,
                status = SetupReadinessStatus.WARNING,
            )
        }

        val unverifiedChannels = selectedChannels - successfulChannels
        if (unverifiedChannels.isEmpty()) {
            return ChannelVerificationReadiness(
                reason = ChannelVerificationReadinessReason.VERIFIED,
                status = SetupReadinessStatus.OK,
                selectedChannels = selectedChannels,
            )
        }

        return ChannelVerificationReadiness(
            reason = when {
                unverifiedChannels == setOf(MessageChannel.EMAIL) ->
                    ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED
                MessageChannel.SMS in unverifiedChannels ->
                    ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES
                MessageChannel.WHATSAPP in unverifiedChannels ->
                    ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES
                else -> ChannelVerificationReadinessReason.VIEW_ACTIVITY
            },
            status = SetupReadinessStatus.WARNING,
            selectedChannels = selectedChannels,
            unverifiedChannels = unverifiedChannels,
        )
    }
}
