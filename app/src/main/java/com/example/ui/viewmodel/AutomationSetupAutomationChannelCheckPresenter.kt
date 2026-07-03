package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.domain.automation.AutomatableEventsReadiness
import com.example.domain.automation.AutomatableEventsReadinessReason
import com.example.domain.automation.AutomaticDeliveryRoutesReadiness
import com.example.domain.automation.AutomaticDeliveryRoutesReadinessReason
import com.example.domain.automation.ChannelVerificationReadiness
import com.example.domain.automation.ChannelVerificationReadinessReason
import com.example.domain.automation.FullAutomationReadiness
import com.example.domain.automation.FullAutomationReadinessReason
import com.example.domain.automation.SmsSetupReadiness
import com.example.domain.automation.SmsSetupReadinessReason
import com.example.domain.automation.WhatsAppSetupReadiness
import com.example.domain.automation.WhatsAppSetupReadinessReason
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel

internal class AutomationSetupAutomationChannelCheckPresenter(
    private val context: Context,
) {
    private val defaultRouteOrder = listOf(
        MessageChannel.SMS,
        MessageChannel.WHATSAPP,
        MessageChannel.EMAIL,
    )

    fun fullAutomation(readiness: FullAutomationReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_full_automation),
            detail = when (readiness.reason) {
                FullAutomationReadinessReason.MODE_DISABLED -> text(
                    R.string.automation_setup_full_automation_disabled,
                    readiness.globalAutomationMode.label(),
                )
                FullAutomationReadinessReason.CONTACT_OVERRIDES -> text(
                    R.string.automation_setup_full_automation_contact_overrides,
                    readiness.reviewFirstOverrideCount,
                )
                FullAutomationReadinessReason.READY ->
                    text(R.string.automation_setup_full_automation_ok)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                FullAutomationReadinessReason.MODE_DISABLED ->
                    text(R.string.automation_setup_action_open_settings)
                FullAutomationReadinessReason.CONTACT_OVERRIDES ->
                    text(R.string.automation_setup_action_review_contacts)
                FullAutomationReadinessReason.READY -> null
            },
            action = when (readiness.reason) {
                FullAutomationReadinessReason.MODE_DISABLED -> AiDoctorAction.OPEN_SETTINGS
                FullAutomationReadinessReason.CONTACT_OVERRIDES -> AiDoctorAction.OPEN_CONTACTS
                FullAutomationReadinessReason.READY -> AiDoctorAction.NONE
            },
            group = readiness.group,
        )
    }

    fun automatableEvents(readiness: AutomatableEventsReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_automatable_events),
            detail = when (readiness.reason) {
                AutomatableEventsReadinessReason.NO_CONTACTS ->
                    text(R.string.automation_setup_automatable_events_empty)
                AutomatableEventsReadinessReason.READY ->
                    text(R.string.automation_setup_automatable_events_ok, readiness.eventReadyCount)
                AutomatableEventsReadinessReason.MISSING_EVENTS -> text(
                    R.string.automation_setup_automatable_events_missing,
                    readiness.totalContactCount - readiness.eventReadyCount,
                    readiness.totalContactCount,
                )
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                AutomatableEventsReadinessReason.NO_CONTACTS ->
                    text(R.string.automation_setup_action_sync_contacts)
                AutomatableEventsReadinessReason.READY -> null
                AutomatableEventsReadinessReason.MISSING_EVENTS ->
                    text(R.string.automation_setup_action_review_contacts)
            },
            action = when (readiness.reason) {
                AutomatableEventsReadinessReason.NO_CONTACTS -> AiDoctorAction.SYNC_CONTACTS
                AutomatableEventsReadinessReason.READY -> AiDoctorAction.NONE
                AutomatableEventsReadinessReason.MISSING_EVENTS -> AiDoctorAction.OPEN_CONTACTS
            },
            group = readiness.group,
        )
    }

    fun deliveryRoutes(readiness: AutomaticDeliveryRoutesReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_delivery_routes),
            detail = when (readiness.reason) {
                AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS ->
                    text(R.string.automation_setup_delivery_routes_no_events)
                AutomaticDeliveryRoutesReadinessReason.READY ->
                    text(R.string.automation_setup_delivery_routes_ok, readiness.routableContactCount)
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES -> text(
                    R.string.automation_setup_delivery_routes_missing,
                    readiness.eventContactCount - readiness.routableContactCount,
                    readiness.eventContactCount,
                )
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS,
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES ->
                    text(R.string.automation_setup_action_review_contacts)
                AutomaticDeliveryRoutesReadinessReason.READY -> null
            },
            action = when (readiness.reason) {
                AutomaticDeliveryRoutesReadinessReason.NO_EVENT_CONTACTS,
                AutomaticDeliveryRoutesReadinessReason.MISSING_ROUTES -> AiDoctorAction.OPEN_CONTACTS
                AutomaticDeliveryRoutesReadinessReason.READY -> AiDoctorAction.NONE
            },
            group = readiness.group,
        )
    }

    fun channelVerification(readiness: ChannelVerificationReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_channel_verification),
            detail = when (readiness.reason) {
                ChannelVerificationReadinessReason.NO_ROUTES ->
                    text(R.string.automation_setup_channel_verification_no_routes)
                ChannelVerificationReadinessReason.VERIFIED -> text(
                    R.string.automation_setup_channel_verification_ok,
                    readiness.selectedChannels.toChannelLabelList(),
                )
                ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED,
                ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES,
                ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES,
                ChannelVerificationReadinessReason.VIEW_ACTIVITY -> text(
                    R.string.automation_setup_channel_verification_missing,
                    readiness.unverifiedChannels.toChannelLabelList(),
                )
            },
            status = readiness.status,
            actionLabel = readiness.channelVerificationActionLabel(),
            action = readiness.channelVerificationAction(),
            group = readiness.group,
        )
    }

    fun sms(readiness: SmsSetupReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_sms),
            detail = when (readiness.reason) {
                SmsSetupReadinessReason.DISABLED -> text(R.string.automation_setup_sms_disabled)
                SmsSetupReadinessReason.NOT_USED -> text(R.string.automation_setup_sms_not_used)
                SmsSetupReadinessReason.READY -> text(
                    R.string.automation_setup_sms_ok_for_contacts,
                    readiness.selectedContactCount,
                )
                SmsSetupReadinessReason.PERMISSION_MISSING -> text(
                    R.string.automation_setup_sms_missing_for_contacts,
                    readiness.selectedContactCount,
                )
            },
            status = readiness.status,
            actionLabel = if (readiness.reason == SmsSetupReadinessReason.PERMISSION_MISSING) {
                text(R.string.automation_setup_action_app_settings)
            } else {
                null
            },
            action = if (readiness.reason == SmsSetupReadinessReason.PERMISSION_MISSING) {
                AiDoctorAction.OPEN_APP_SETTINGS
            } else {
                AiDoctorAction.NONE
            },
            group = readiness.group,
        )
    }

    fun whatsApp(readiness: WhatsAppSetupReadiness): ReadinessCheck {
        val requiresFix = readiness.status == ReadinessStatus.ACTION_REQUIRED
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_whatsapp),
            detail = when (readiness.reason) {
                WhatsAppSetupReadinessReason.DISABLED -> text(R.string.automation_setup_whatsapp_disabled)
                WhatsAppSetupReadinessReason.NOT_USED -> text(R.string.automation_setup_whatsapp_not_used)
                WhatsAppSetupReadinessReason.CONSENT_REQUIRED -> text(
                    R.string.automation_setup_whatsapp_consent_needed_for_contacts,
                    readiness.selectedContactCount,
                )
                WhatsAppSetupReadinessReason.APP_MISSING -> text(
                    R.string.automation_setup_whatsapp_app_missing_for_contacts,
                    readiness.selectedContactCount,
                )
                WhatsAppSetupReadinessReason.ACCESSIBILITY_MISSING -> text(
                    R.string.automation_setup_whatsapp_accessibility_missing_for_contacts,
                    readiness.selectedContactCount,
                )
                WhatsAppSetupReadinessReason.READY -> text(
                    R.string.automation_setup_whatsapp_ok_for_contacts,
                    readiness.selectedContactCount,
                )
            },
            status = readiness.status,
            actionLabel = if (requiresFix) {
                text(R.string.automation_setup_action_open_accessibility)
            } else {
                null
            },
            action = if (requiresFix) {
                AiDoctorAction.OPEN_ACCESSIBILITY_SETTINGS
            } else {
                AiDoctorAction.NONE
            },
            group = readiness.group,
        )
    }

    private fun ChannelVerificationReadiness.channelVerificationActionLabel(): String? {
        return when (reason) {
            ChannelVerificationReadinessReason.NO_ROUTES -> text(R.string.automation_setup_action_review_contacts)
            ChannelVerificationReadinessReason.VERIFIED -> null
            ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED -> text(R.string.automation_setup_action_test_email)
            ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES ->
                text(R.string.automation_setup_action_review_sms_messages)
            ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES ->
                text(R.string.automation_setup_action_review_whatsapp_messages)
            ChannelVerificationReadinessReason.VIEW_ACTIVITY -> text(R.string.automation_setup_action_view_activity)
        }
    }

    private fun ChannelVerificationReadiness.channelVerificationAction(): AiDoctorAction {
        return when (reason) {
            ChannelVerificationReadinessReason.NO_ROUTES -> AiDoctorAction.OPEN_CONTACTS
            ChannelVerificationReadinessReason.VERIFIED -> AiDoctorAction.NONE
            ChannelVerificationReadinessReason.EMAIL_TEST_REQUIRED -> AiDoctorAction.TEST_EMAIL
            ChannelVerificationReadinessReason.REVIEW_SMS_MESSAGES -> AiDoctorAction.OPEN_SMS_MESSAGES
            ChannelVerificationReadinessReason.REVIEW_WHATSAPP_MESSAGES -> AiDoctorAction.OPEN_WHATSAPP_MESSAGES
            ChannelVerificationReadinessReason.VIEW_ACTIVITY -> AiDoctorAction.OPEN_ACTIVITY_HISTORY
        }
    }

    private fun Set<MessageChannel>.toChannelLabelList(): String {
        return sortedBy {
            defaultRouteOrder.indexOf(it).takeIf { index -> index >= 0 } ?: defaultRouteOrder.size
        }
            .joinToString(", ") { it.label() }
    }

    private fun MessageChannel.label(): String {
        return when (this) {
            MessageChannel.SMS -> text(R.string.channel_sms)
            MessageChannel.WHATSAPP -> text(R.string.channel_whatsapp)
            MessageChannel.EMAIL -> text(R.string.channel_email)
            MessageChannel.UNKNOWN -> MessageChannel.UNKNOWN.raw
        }
    }

    private fun ApprovalMode.label(): String {
        return when (this) {
            ApprovalMode.FULLY_AUTO -> text(R.string.automation_mode_fully_auto)
            ApprovalMode.SMART_APPROVE -> text(R.string.automation_mode_smart_approve_default)
            ApprovalMode.VIP_APPROVE -> text(R.string.automation_mode_vip_approve)
            ApprovalMode.ALWAYS_ASK -> text(R.string.automation_mode_always_ask)
            ApprovalMode.DEFAULT,
            ApprovalMode.UNKNOWN -> text(R.string.automation_mode_default)
        }
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
