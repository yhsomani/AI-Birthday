package com.example.ui.screens.wish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.message.WishPreviewDeviceSetupContext
import com.example.domain.message.WishPreviewDeviceSetupReason
import com.example.domain.message.WishPreviewDeviceSetupState
import com.example.domain.message.WishPreviewDispatchContext
import com.example.domain.message.WishPreviewDispatchReason
import com.example.domain.message.WishPreviewDispatchState
import com.example.domain.message.WishPreviewRouteContext
import com.example.domain.message.WishPreviewRouteReason
import com.example.domain.message.WishPreviewRouteSelectionContext
import com.example.domain.message.WishPreviewRouteSelectionState
import com.example.domain.message.WishPreviewRouteState
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.occasion.OccasionType
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipReadinessState
import com.example.ui.viewmodel.WishPreviewSendSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WishSendSummaryCard(
    summary: WishPreviewSendSummary,
    readiness: RelationshipActionReadiness?,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()) }
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.wish_preview_send_summary_title),
                style = MaterialTheme.typography.titleSmall,
                color = readiness?.state?.sendSummaryTitleColor() ?: MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_event),
                value = eventTypeLabel(summary.eventType),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_route),
                value = channelLabel(summary.channel),
            )
            summary.routeSelectionContext?.let { routeSelectionContext ->
                SendSummaryRow(
                    label = stringResource(R.string.wish_preview_summary_route_choice),
                    value = routeSelectionContextLabel(routeSelectionContext),
                )
            }
            summary.routeContext?.let { routeContext ->
                SendSummaryRow(
                    label = stringResource(R.string.wish_preview_summary_route_setup),
                    value = routeContextLabel(routeContext),
                )
            }
            summary.deviceSetupContext?.let { deviceSetupContext ->
                SendSummaryRow(
                    label = stringResource(R.string.wish_preview_summary_device_setup),
                    value = deviceSetupContextLabel(deviceSetupContext),
                )
            }
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_schedule),
                value = dateFormat.format(Date(summary.scheduledForMs)),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_approval),
                value = approvalModeLabel(summary.approvalMode),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_dispatch),
                value = dispatchContextLabel(summary.dispatchContext),
            )
            SendSummaryRow(
                label = stringResource(R.string.wish_preview_summary_quality),
                value = if (summary.usesFallback) {
                    stringResource(R.string.wish_preview_summary_quality_fallback)
                } else {
                    stringResource(R.string.wish_preview_summary_quality_ai)
                },
            )
        }
    }
}

@Composable
private fun RelationshipReadinessState.sendSummaryTitleColor() = when (this) {
    RelationshipReadinessState.ACTION_REQUIRED -> MaterialTheme.colorScheme.error
    RelationshipReadinessState.WARNING -> MaterialTheme.colorScheme.tertiary
    RelationshipReadinessState.WAITING -> MaterialTheme.colorScheme.secondary
    RelationshipReadinessState.READY,
    RelationshipReadinessState.NEEDS_REVIEW,
    RelationshipReadinessState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
}

@Composable
private fun SendSummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(RelateFraction.metadataLabel),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(RelateFraction.metadataValue),
        )
    }
}

@Composable
private fun routeSelectionContextLabel(
    context: WishPreviewRouteSelectionContext,
): String {
    return when (context.state) {
        WishPreviewRouteSelectionState.PREFERRED_ROUTE ->
            stringResource(R.string.wish_preview_summary_route_choice_preferred)
        WishPreviewRouteSelectionState.FALLBACK_ROUTE ->
            stringResource(
                R.string.wish_preview_summary_route_choice_fallback,
                channelLabel(context.preferredChannel),
            )
    }
}

@Composable
private fun deviceSetupContextLabel(
    context: WishPreviewDeviceSetupContext,
): String {
    return when (context.state) {
        WishPreviewDeviceSetupState.READY -> when (context.reason) {
            WishPreviewDeviceSetupReason.WHATSAPP_READY ->
                stringResource(R.string.wish_preview_summary_device_whatsapp_ready)
            else -> stringResource(R.string.wish_preview_summary_device_sms_ready)
        }
        WishPreviewDeviceSetupState.NOT_REQUIRED -> when (context.reason) {
            WishPreviewDeviceSetupReason.WHATSAPP_DISABLED ->
                stringResource(R.string.wish_preview_summary_device_whatsapp_disabled)
            else -> stringResource(R.string.wish_preview_summary_device_sms_disabled)
        }
        WishPreviewDeviceSetupState.ACTION_REQUIRED -> when (context.reason) {
            WishPreviewDeviceSetupReason.SMS_PERMISSION_MISSING ->
                stringResource(R.string.wish_preview_summary_device_sms_permission_missing)
            WishPreviewDeviceSetupReason.WHATSAPP_CONSENT_REQUIRED ->
                stringResource(R.string.wish_preview_summary_device_whatsapp_consent_required)
            WishPreviewDeviceSetupReason.WHATSAPP_APP_MISSING ->
                stringResource(R.string.wish_preview_summary_device_whatsapp_app_missing)
            WishPreviewDeviceSetupReason.WHATSAPP_ACCESSIBILITY_MISSING ->
                stringResource(R.string.wish_preview_summary_device_whatsapp_accessibility_missing)
            WishPreviewDeviceSetupReason.SMS_READY,
            WishPreviewDeviceSetupReason.SMS_DISABLED,
            WishPreviewDeviceSetupReason.WHATSAPP_READY,
            WishPreviewDeviceSetupReason.WHATSAPP_DISABLED ->
                stringResource(R.string.wish_preview_summary_device_setup_required)
        }
    }
}

@Composable
private fun routeContextLabel(
    context: WishPreviewRouteContext,
): String {
    return when (context.state) {
        WishPreviewRouteState.READY -> stringResource(R.string.wish_preview_summary_route_ready)
        WishPreviewRouteState.BLOCKED -> when (context.reason) {
            WishPreviewRouteReason.READY -> stringResource(R.string.wish_preview_summary_route_ready)
            WishPreviewRouteReason.CONTACT_MISSING ->
                stringResource(R.string.wish_preview_summary_route_contact_missing)
            WishPreviewRouteReason.CHANNEL_DISABLED ->
                stringResource(R.string.wish_preview_summary_route_channel_disabled)
            WishPreviewRouteReason.MISSING_PHONE ->
                stringResource(R.string.wish_preview_summary_route_missing_phone)
            WishPreviewRouteReason.MISSING_EMAIL ->
                stringResource(R.string.wish_preview_summary_route_missing_email)
            WishPreviewRouteReason.EMAIL_SETUP_MISSING ->
                stringResource(R.string.wish_preview_summary_route_email_setup_missing)
            WishPreviewRouteReason.EMAIL_SENDER_INVALID ->
                stringResource(R.string.wish_preview_summary_route_email_sender_invalid)
            WishPreviewRouteReason.UNSUPPORTED_CHANNEL ->
                stringResource(R.string.wish_preview_summary_route_unsupported)
        }
    }
}

@Composable
private fun dispatchContextLabel(
    context: WishPreviewDispatchContext,
): String {
    return when (context.state) {
        WishPreviewDispatchState.NEEDS_APPROVAL ->
            stringResource(R.string.wish_preview_summary_dispatch_needs_approval)
        WishPreviewDispatchState.READY_TO_SEND ->
            stringResource(R.string.wish_preview_summary_dispatch_ready)
        WishPreviewDispatchState.SCHEDULED ->
            stringResource(R.string.wish_preview_summary_dispatch_scheduled)
        WishPreviewDispatchState.DEFERRED ->
            stringResource(R.string.wish_preview_summary_dispatch_deferred)
        WishPreviewDispatchState.EXPIRED ->
            stringResource(R.string.wish_preview_summary_dispatch_expired)
        WishPreviewDispatchState.BLOCKED -> when (context.reason) {
            WishPreviewDispatchReason.ALREADY_HANDLED ->
                stringResource(R.string.wish_preview_summary_dispatch_handled)
            WishPreviewDispatchReason.REJECTED ->
                stringResource(R.string.wish_preview_summary_dispatch_rejected)
            WishPreviewDispatchReason.EXPIRED,
            WishPreviewDispatchReason.APPROVAL_WINDOW_ELAPSED ->
                stringResource(R.string.wish_preview_summary_dispatch_expired)
            WishPreviewDispatchReason.FAILED ->
                stringResource(R.string.wish_preview_summary_dispatch_failed)
            WishPreviewDispatchReason.UNSUPPORTED_STATE,
            WishPreviewDispatchReason.APPROVAL_REQUIRED,
            WishPreviewDispatchReason.READY_NOW,
            WishPreviewDispatchReason.BEFORE_SCHEDULED_TIME,
            WishPreviewDispatchReason.QUIET_HOURS_OR_BLACKOUT_DATE ->
                stringResource(R.string.wish_preview_summary_dispatch_blocked)
        }
    }
}

@Composable
private fun eventTypeLabel(eventType: String): String = when (OccasionType.fromRaw(eventType)) {
    OccasionType.BIRTHDAY -> stringResource(R.string.event_type_birthday)
    OccasionType.ANNIVERSARY -> stringResource(R.string.event_type_anniversary)
    OccasionType.WORK_ANNIVERSARY -> stringResource(R.string.event_type_work_anniversary)
    OccasionType.GRADUATION -> stringResource(R.string.event_type_graduation)
    OccasionType.HOLIDAY -> stringResource(R.string.event_type_holiday)
    OccasionType.REVIVAL -> stringResource(R.string.event_type_revival)
    OccasionType.FOLLOW_UP -> stringResource(R.string.event_type_follow_up)
    OccasionType.CUSTOM -> stringResource(R.string.event_type_custom)
    OccasionType.UNKNOWN -> stringResource(R.string.event_type_custom)
}

@Composable
private fun channelLabel(channel: String): String = when (MessageChannel.fromRaw(channel)) {
    MessageChannel.SMS -> stringResource(R.string.channel_sms)
    MessageChannel.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannel.EMAIL -> stringResource(R.string.channel_email)
    MessageChannel.UNKNOWN -> channel
}

@Composable
private fun approvalModeLabel(approvalMode: String): String = when (ApprovalMode.fromRaw(approvalMode)) {
    ApprovalMode.FULLY_AUTO -> stringResource(R.string.automation_mode_fully_auto)
    ApprovalMode.SMART_APPROVE -> stringResource(R.string.automation_mode_smart_approve_default)
    ApprovalMode.VIP_APPROVE -> stringResource(R.string.automation_mode_vip_approve)
    ApprovalMode.ALWAYS_ASK -> stringResource(R.string.automation_mode_always_ask)
    ApprovalMode.DEFAULT,
    ApprovalMode.UNKNOWN -> stringResource(R.string.automation_mode_default)
}
