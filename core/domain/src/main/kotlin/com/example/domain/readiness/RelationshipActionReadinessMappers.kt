package com.example.domain.readiness

import com.example.domain.automation.MessageOperationalReadiness
import com.example.domain.home.HomeNextActionKind
import com.example.domain.message.WishPreviewDeviceSetupContext
import com.example.domain.message.WishPreviewDeviceSetupReason
import com.example.domain.message.WishPreviewDeviceSetupState
import com.example.domain.message.WishPreviewDispatchContext
import com.example.domain.message.WishPreviewDispatchReason
import com.example.domain.message.WishPreviewDispatchState
import com.example.domain.message.WishPreviewRouteContext
import com.example.domain.message.WishPreviewRouteReason
import com.example.domain.message.WishPreviewRouteState
import com.example.domain.model.notification.SetupNotificationReason

internal fun HomeNextActionKind.toRelationshipState(): RelationshipReadinessState {
    return when (this) {
        HomeNextActionKind.FIX_CONTACT_SYNC,
        HomeNextActionKind.SYNC_CONTACTS,
        HomeNextActionKind.CONNECT_AI,
        HomeNextActionKind.ENABLE_AI_GENERATION -> RelationshipReadinessState.ACTION_REQUIRED
        HomeNextActionKind.REVIEW_PENDING -> RelationshipReadinessState.NEEDS_REVIEW
        HomeNextActionKind.CREATE_BACKUP,
        HomeNextActionKind.REFRESH_BACKUP,
        HomeNextActionKind.RECONNECT_CONTACT -> RelationshipReadinessState.WARNING
    }
}

internal fun HomeNextActionKind.toRelationshipReason(): RelationshipReadinessReason {
    return when (this) {
        HomeNextActionKind.FIX_CONTACT_SYNC -> RelationshipReadinessReason.CONTACT_SYNC_FAILED
        HomeNextActionKind.SYNC_CONTACTS -> RelationshipReadinessReason.CONTACTS_MISSING
        HomeNextActionKind.CONNECT_AI -> RelationshipReadinessReason.AI_ACCESS_MISSING
        HomeNextActionKind.ENABLE_AI_GENERATION -> RelationshipReadinessReason.AI_GENERATION_DISABLED
        HomeNextActionKind.REVIEW_PENDING -> RelationshipReadinessReason.PENDING_MESSAGES
        HomeNextActionKind.CREATE_BACKUP -> RelationshipReadinessReason.BACKUP_MISSING
        HomeNextActionKind.REFRESH_BACKUP -> RelationshipReadinessReason.BACKUP_STALE
        HomeNextActionKind.RECONNECT_CONTACT -> RelationshipReadinessReason.RELATIONSHIP_HEALTH_LOW
    }
}

internal fun WishPreviewRouteContext.toRelationshipActionReadiness(): RelationshipActionReadiness {
    val relationshipReason = this.reason.toRelationshipReason()
    val action = relationshipReason.toPrimaryAction()
    val relationshipState = when (state) {
        WishPreviewRouteState.READY -> RelationshipReadinessState.READY
        WishPreviewRouteState.BLOCKED -> RelationshipReadinessState.ACTION_REQUIRED
    }
    return RelationshipActionReadiness(
        state = relationshipState,
        primaryReason = relationshipReason,
        blockers = if (relationshipState == RelationshipReadinessState.ACTION_REQUIRED) {
            listOf(RelationshipReadinessBlocker(reason = relationshipReason, action = action))
        } else {
            emptyList()
        },
        primaryAction = action,
        confidence = RelationshipReadinessConfidence.HIGH,
    )
}

internal fun WishPreviewDeviceSetupContext.toRelationshipActionReadiness(): RelationshipActionReadiness {
    val relationshipReason = this.reason.toRelationshipReason()
    val action = relationshipReason.toPrimaryAction()
    val relationshipState = when (state) {
        WishPreviewDeviceSetupState.READY,
        WishPreviewDeviceSetupState.NOT_REQUIRED -> RelationshipReadinessState.READY
        WishPreviewDeviceSetupState.ACTION_REQUIRED -> RelationshipReadinessState.ACTION_REQUIRED
    }
    return RelationshipActionReadiness(
        state = relationshipState,
        primaryReason = relationshipReason,
        blockers = if (relationshipState == RelationshipReadinessState.ACTION_REQUIRED) {
            listOf(RelationshipReadinessBlocker(reason = relationshipReason, action = action))
        } else {
            emptyList()
        },
        primaryAction = action,
        confidence = RelationshipReadinessConfidence.HIGH,
    )
}

internal fun WishPreviewDispatchContext.toRelationshipActionReadiness(): RelationshipActionReadiness {
    val relationshipReason = toRelationshipReason()
    val action = relationshipReason.toPrimaryAction()
    val relationshipState = when (state) {
        WishPreviewDispatchState.NEEDS_APPROVAL -> RelationshipReadinessState.NEEDS_REVIEW
        WishPreviewDispatchState.READY_TO_SEND -> RelationshipReadinessState.READY
        WishPreviewDispatchState.SCHEDULED,
        WishPreviewDispatchState.DEFERRED -> RelationshipReadinessState.WAITING
        WishPreviewDispatchState.EXPIRED,
        WishPreviewDispatchState.BLOCKED -> RelationshipReadinessState.WARNING
    }
    return RelationshipActionReadiness(
        state = relationshipState,
        primaryReason = relationshipReason,
        blockers = if (relationshipState == RelationshipReadinessState.WARNING) {
            listOf(RelationshipReadinessBlocker(reason = relationshipReason, action = action))
        } else {
            emptyList()
        },
        primaryAction = action,
        confidence = RelationshipReadinessConfidence.HIGH,
    )
}

internal fun MessageOperationalReadiness.toRelationshipReason(): RelationshipReadinessReason {
    return when (this) {
        MessageOperationalReadiness.READY_FOR_REVIEW -> RelationshipReadinessReason.MESSAGE_NEEDS_REVIEW
        MessageOperationalReadiness.APPROVED_SCHEDULED -> RelationshipReadinessReason.APPROVED_READY
        MessageOperationalReadiness.APPROVED_WAITING_FOR_SCHEDULE ->
            RelationshipReadinessReason.WAITING_FOR_SCHEDULE
        MessageOperationalReadiness.APPROVED_WAITING_FOR_ALLOWED_WINDOW ->
            RelationshipReadinessReason.WAITING_FOR_ALLOWED_WINDOW
        MessageOperationalReadiness.SENDING_NOW -> RelationshipReadinessReason.SENDING
        MessageOperationalReadiness.CONTACT_MISSING -> RelationshipReadinessReason.CONTACT_MISSING
        MessageOperationalReadiness.CHANNEL_DISABLED -> RelationshipReadinessReason.CHANNEL_DISABLED
        MessageOperationalReadiness.MISSING_PHONE -> RelationshipReadinessReason.MISSING_PHONE
        MessageOperationalReadiness.MISSING_EMAIL -> RelationshipReadinessReason.MISSING_EMAIL
        MessageOperationalReadiness.EMAIL_SETUP_MISSING -> RelationshipReadinessReason.EMAIL_SETUP_MISSING
        MessageOperationalReadiness.FAILED_CHECK_SETUP -> RelationshipReadinessReason.FAILED_CHECK_SETUP
    }
}

internal fun SetupNotificationReason.toRelationshipReason(): RelationshipReadinessReason {
    return when (this) {
        SetupNotificationReason.SMS_PERMISSION_MISSING -> RelationshipReadinessReason.CHANNEL_DISABLED
        SetupNotificationReason.MESSAGE_EXPIRED,
        SetupNotificationReason.DOUBLE_SEND_GUARD -> RelationshipReadinessReason.PENDING_MESSAGES
        SetupNotificationReason.AI_PROVIDER_MISSING,
        SetupNotificationReason.REVIVAL_AI_PROVIDER_MISSING -> RelationshipReadinessReason.AI_ACCESS_MISSING
        SetupNotificationReason.EXACT_ALARM_PERMISSION_MISSING -> RelationshipReadinessReason.SETUP_ACTION_REQUIRED
    }
}

internal fun RelationshipReadinessReason.toPrimaryAction(): RelationshipReadinessAction {
    return when (this) {
        RelationshipReadinessReason.MESSAGE_NEEDS_REVIEW -> RelationshipReadinessAction.REVIEW_MESSAGE
        RelationshipReadinessReason.WAITING_FOR_SCHEDULE,
        RelationshipReadinessReason.WAITING_FOR_ALLOWED_WINDOW,
        RelationshipReadinessReason.SENDING,
        RelationshipReadinessReason.APPROVED_READY,
        RelationshipReadinessReason.READY,
        RelationshipReadinessReason.DRAFT_READY,
        RelationshipReadinessReason.DRAFT_EDITED_READY -> RelationshipReadinessAction.NONE
        RelationshipReadinessReason.CONTACT_MISSING -> RelationshipReadinessAction.OPEN_CONTACT
        RelationshipReadinessReason.CHANNEL_DISABLED,
        RelationshipReadinessReason.MISSING_PHONE,
        RelationshipReadinessReason.MISSING_EMAIL -> RelationshipReadinessAction.CONFIGURE_CHANNEL
        RelationshipReadinessReason.EMAIL_SETUP_MISSING -> RelationshipReadinessAction.CONFIGURE_EMAIL
        RelationshipReadinessReason.FAILED_CHECK_SETUP -> RelationshipReadinessAction.CHECK_SETUP
        RelationshipReadinessReason.SETUP_ACTION_REQUIRED,
        RelationshipReadinessReason.SETUP_WARNING -> RelationshipReadinessAction.OPEN_SETUP
        RelationshipReadinessReason.DRAFT_TOO_SHORT,
        RelationshipReadinessReason.DRAFT_BLANK -> RelationshipReadinessAction.EDIT_DRAFT
        RelationshipReadinessReason.CONTACT_SYNC_FAILED -> RelationshipReadinessAction.FIX_CONTACT_SYNC
        RelationshipReadinessReason.CONTACTS_MISSING -> RelationshipReadinessAction.SYNC_CONTACTS
        RelationshipReadinessReason.AI_ACCESS_MISSING -> RelationshipReadinessAction.CONNECT_AI
        RelationshipReadinessReason.AI_GENERATION_DISABLED -> RelationshipReadinessAction.ENABLE_AI_GENERATION
        RelationshipReadinessReason.PENDING_MESSAGES -> RelationshipReadinessAction.REVIEW_MESSAGES
        RelationshipReadinessReason.BACKUP_MISSING -> RelationshipReadinessAction.CREATE_BACKUP
        RelationshipReadinessReason.BACKUP_STALE -> RelationshipReadinessAction.REFRESH_BACKUP
        RelationshipReadinessReason.RELATIONSHIP_HEALTH_LOW -> RelationshipReadinessAction.OPEN_CONTACT
    }
}

private fun WishPreviewRouteReason.toRelationshipReason(): RelationshipReadinessReason {
    return when (this) {
        WishPreviewRouteReason.READY -> RelationshipReadinessReason.APPROVED_READY
        WishPreviewRouteReason.CONTACT_MISSING -> RelationshipReadinessReason.CONTACT_MISSING
        WishPreviewRouteReason.CHANNEL_DISABLED -> RelationshipReadinessReason.CHANNEL_DISABLED
        WishPreviewRouteReason.MISSING_PHONE -> RelationshipReadinessReason.MISSING_PHONE
        WishPreviewRouteReason.MISSING_EMAIL -> RelationshipReadinessReason.MISSING_EMAIL
        WishPreviewRouteReason.EMAIL_SETUP_MISSING,
        WishPreviewRouteReason.EMAIL_SENDER_INVALID -> RelationshipReadinessReason.EMAIL_SETUP_MISSING
        WishPreviewRouteReason.UNSUPPORTED_CHANNEL -> RelationshipReadinessReason.CHANNEL_DISABLED
    }
}

private fun WishPreviewDeviceSetupReason.toRelationshipReason(): RelationshipReadinessReason {
    return when (this) {
        WishPreviewDeviceSetupReason.SMS_READY,
        WishPreviewDeviceSetupReason.SMS_DISABLED,
        WishPreviewDeviceSetupReason.WHATSAPP_READY,
        WishPreviewDeviceSetupReason.WHATSAPP_DISABLED -> RelationshipReadinessReason.APPROVED_READY
        WishPreviewDeviceSetupReason.SMS_PERMISSION_MISSING,
        WishPreviewDeviceSetupReason.WHATSAPP_CONSENT_REQUIRED,
        WishPreviewDeviceSetupReason.WHATSAPP_APP_MISSING,
        WishPreviewDeviceSetupReason.WHATSAPP_ACCESSIBILITY_MISSING ->
            RelationshipReadinessReason.CHANNEL_DISABLED
    }
}

private fun WishPreviewDispatchContext.toRelationshipReason(): RelationshipReadinessReason {
    return when (state) {
        WishPreviewDispatchState.NEEDS_APPROVAL -> RelationshipReadinessReason.MESSAGE_NEEDS_REVIEW
        WishPreviewDispatchState.READY_TO_SEND -> RelationshipReadinessReason.APPROVED_READY
        WishPreviewDispatchState.SCHEDULED -> RelationshipReadinessReason.WAITING_FOR_SCHEDULE
        WishPreviewDispatchState.DEFERRED -> RelationshipReadinessReason.WAITING_FOR_ALLOWED_WINDOW
        WishPreviewDispatchState.EXPIRED -> RelationshipReadinessReason.PENDING_MESSAGES
        WishPreviewDispatchState.BLOCKED -> when (reason) {
            WishPreviewDispatchReason.FAILED,
            WishPreviewDispatchReason.UNSUPPORTED_STATE -> RelationshipReadinessReason.FAILED_CHECK_SETUP
            WishPreviewDispatchReason.ALREADY_HANDLED,
            WishPreviewDispatchReason.REJECTED,
            WishPreviewDispatchReason.EXPIRED,
            WishPreviewDispatchReason.APPROVAL_WINDOW_ELAPSED,
            WishPreviewDispatchReason.APPROVAL_REQUIRED,
            WishPreviewDispatchReason.READY_NOW,
            WishPreviewDispatchReason.BEFORE_SCHEDULED_TIME,
            WishPreviewDispatchReason.QUIET_HOURS_OR_BLACKOUT_DATE ->
                RelationshipReadinessReason.PENDING_MESSAGES
        }
    }
}
