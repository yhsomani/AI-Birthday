package com.example.domain.readiness

import com.example.domain.automation.MessageOperationalReadiness
import com.example.domain.automation.SetupReadinessRecommendationCandidate
import com.example.domain.automation.SetupReadinessRecommendationPolicy
import com.example.domain.automation.SetupReadinessStatus
import com.example.domain.automation.SetupReadinessSummaryDecision
import com.example.domain.automation.SetupReadinessSummaryPolicy
import com.example.domain.home.HomeNextActionCandidate
import com.example.domain.home.HomeNextActionKind
import com.example.domain.home.HomeReadinessBannerCandidate
import com.example.domain.message.WishDraftReadiness
import com.example.domain.message.WishPreviewDeviceSetupContext
import com.example.domain.message.WishPreviewDeviceSetupReason
import com.example.domain.message.WishPreviewDeviceSetupState
import com.example.domain.message.WishPreviewDispatchContext
import com.example.domain.message.WishPreviewDispatchReason
import com.example.domain.message.WishPreviewDispatchState
import com.example.domain.message.WishPreviewRouteContext
import com.example.domain.message.WishPreviewRouteReason
import com.example.domain.message.WishPreviewRouteState
import com.example.domain.message.WishPreviewSendSummary
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.notification.SetupNotificationRequest
import com.example.domain.model.notification.SystemAlertNotificationReason
import com.example.domain.model.notification.SystemAlertNotificationRequest

enum class RelationshipReadinessState {
    READY,
    NEEDS_REVIEW,
    WAITING,
    IN_PROGRESS,
    ACTION_REQUIRED,
    WARNING,
}

enum class RelationshipReadinessReason {
    READY,
    MESSAGE_NEEDS_REVIEW,
    APPROVED_READY,
    WAITING_FOR_SCHEDULE,
    WAITING_FOR_ALLOWED_WINDOW,
    SENDING,
    CONTACT_MISSING,
    CHANNEL_DISABLED,
    MISSING_PHONE,
    MISSING_EMAIL,
    EMAIL_SETUP_MISSING,
    FAILED_CHECK_SETUP,
    SETUP_ACTION_REQUIRED,
    SETUP_WARNING,
    DRAFT_READY,
    DRAFT_EDITED_READY,
    DRAFT_TOO_SHORT,
    DRAFT_BLANK,
    CONTACT_SYNC_FAILED,
    CONTACTS_MISSING,
    AI_ACCESS_MISSING,
    AI_GENERATION_DISABLED,
    PENDING_MESSAGES,
    BACKUP_MISSING,
    BACKUP_STALE,
    RELATIONSHIP_HEALTH_LOW,
}

enum class RelationshipReadinessAction {
    NONE,
    REVIEW_MESSAGE,
    WAIT,
    OPEN_CONTACT,
    CONFIGURE_CHANNEL,
    CONFIGURE_EMAIL,
    OPEN_SETUP,
    CHECK_SETUP,
    EDIT_DRAFT,
    FIX_CONTACT_SYNC,
    SYNC_CONTACTS,
    CONNECT_AI,
    ENABLE_AI_GENERATION,
    REVIEW_MESSAGES,
    CREATE_BACKUP,
    REFRESH_BACKUP,
}

enum class RelationshipReadinessConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class RelationshipReadinessBlocker(
    val reason: RelationshipReadinessReason,
    val action: RelationshipReadinessAction,
)

data class RelationshipActionReadiness(
    val state: RelationshipReadinessState,
    val primaryReason: RelationshipReadinessReason,
    val blockers: List<RelationshipReadinessBlocker> = emptyList(),
    val primaryAction: RelationshipReadinessAction = RelationshipReadinessAction.NONE,
    val secondaryActions: List<RelationshipReadinessAction> = emptyList(),
    val confidence: RelationshipReadinessConfidence = RelationshipReadinessConfidence.HIGH,
    val relatedMessageId: String? = null,
    val relatedContactId: String? = null,
    val relatedEventId: String? = null,
)

object RelationshipActionReadinessPolicy {
    fun fromMessageOperationalReadiness(
        readiness: MessageOperationalReadiness,
        relatedMessageId: String? = null,
        relatedContactId: String? = null,
        relatedEventId: String? = null,
    ): RelationshipActionReadiness {
        val reason = readiness.toRelationshipReason()
        val action = reason.toPrimaryAction()
        val state = when (readiness) {
            MessageOperationalReadiness.READY_FOR_REVIEW -> RelationshipReadinessState.NEEDS_REVIEW
            MessageOperationalReadiness.APPROVED_SCHEDULED -> RelationshipReadinessState.READY
            MessageOperationalReadiness.APPROVED_WAITING_FOR_SCHEDULE,
            MessageOperationalReadiness.APPROVED_WAITING_FOR_ALLOWED_WINDOW -> RelationshipReadinessState.WAITING
            MessageOperationalReadiness.SENDING_NOW -> RelationshipReadinessState.IN_PROGRESS
            MessageOperationalReadiness.FAILED_CHECK_SETUP -> RelationshipReadinessState.WARNING
            MessageOperationalReadiness.CONTACT_MISSING,
            MessageOperationalReadiness.CHANNEL_DISABLED,
            MessageOperationalReadiness.MISSING_PHONE,
            MessageOperationalReadiness.MISSING_EMAIL,
            MessageOperationalReadiness.EMAIL_SETUP_MISSING -> RelationshipReadinessState.ACTION_REQUIRED
        }
        return RelationshipActionReadiness(
            state = state,
            primaryReason = reason,
            blockers = if (state == RelationshipReadinessState.ACTION_REQUIRED) {
                listOf(RelationshipReadinessBlocker(reason = reason, action = action))
            } else {
                emptyList()
            },
            primaryAction = action,
            confidence = RelationshipReadinessConfidence.HIGH,
            relatedMessageId = relatedMessageId,
            relatedContactId = relatedContactId,
            relatedEventId = relatedEventId,
        )
    }

    fun fromWishDraftReadiness(
        readiness: WishDraftReadiness,
        relatedMessageId: String? = null,
        relatedContactId: String? = null,
        relatedEventId: String? = null,
    ): RelationshipActionReadiness {
        val reason = when (readiness) {
            WishDraftReadiness.READY -> RelationshipReadinessReason.DRAFT_READY
            WishDraftReadiness.EDITED_READY -> RelationshipReadinessReason.DRAFT_EDITED_READY
            WishDraftReadiness.TOO_SHORT -> RelationshipReadinessReason.DRAFT_TOO_SHORT
            WishDraftReadiness.BLANK -> RelationshipReadinessReason.DRAFT_BLANK
        }
        val action = if (readiness.blocksApproval) {
            RelationshipReadinessAction.EDIT_DRAFT
        } else {
            RelationshipReadinessAction.NONE
        }
        val state = if (readiness.blocksApproval) {
            RelationshipReadinessState.ACTION_REQUIRED
        } else {
            RelationshipReadinessState.READY
        }
        return RelationshipActionReadiness(
            state = state,
            primaryReason = reason,
            blockers = if (readiness.blocksApproval) {
                listOf(RelationshipReadinessBlocker(reason = reason, action = action))
            } else {
                emptyList()
            },
            primaryAction = action,
            confidence = RelationshipReadinessConfidence.HIGH,
            relatedMessageId = relatedMessageId,
            relatedContactId = relatedContactId,
            relatedEventId = relatedEventId,
        )
    }

    fun fromWishPreviewSendSummary(
        summary: WishPreviewSendSummary,
        relatedMessageId: String? = null,
        relatedContactId: String? = null,
        relatedEventId: String? = null,
    ): RelationshipActionReadiness {
        val routeBlocker = summary.routeContext
            ?.takeIf { it.state == WishPreviewRouteState.BLOCKED }
            ?.toRelationshipActionReadiness()
        val deviceBlocker = summary.deviceSetupContext
            ?.takeIf { it.state == WishPreviewDeviceSetupState.ACTION_REQUIRED }
            ?.toRelationshipActionReadiness()
        val readiness = routeBlocker
            ?: deviceBlocker
            ?: summary.dispatchContext.toRelationshipActionReadiness()
        return readiness.copy(
            relatedMessageId = relatedMessageId,
            relatedContactId = relatedContactId,
            relatedEventId = relatedEventId,
        )
    }

    fun fromSetupSummary(
        summary: SetupReadinessSummaryDecision,
        hasRecommendedAction: Boolean,
    ): RelationshipActionReadiness {
        val reason = when (summary.status) {
            SetupReadinessStatus.ACTION_REQUIRED -> RelationshipReadinessReason.SETUP_ACTION_REQUIRED
            SetupReadinessStatus.WARNING -> RelationshipReadinessReason.SETUP_WARNING
            SetupReadinessStatus.OK -> RelationshipReadinessReason.READY
        }
        val action = if (hasRecommendedAction && summary.status != SetupReadinessStatus.OK) {
            RelationshipReadinessAction.OPEN_SETUP
        } else {
            RelationshipReadinessAction.NONE
        }
        val state = when (summary.status) {
            SetupReadinessStatus.ACTION_REQUIRED -> RelationshipReadinessState.ACTION_REQUIRED
            SetupReadinessStatus.WARNING -> RelationshipReadinessState.WARNING
            SetupReadinessStatus.OK -> RelationshipReadinessState.READY
        }
        val blockerCount = when (summary.status) {
            SetupReadinessStatus.ACTION_REQUIRED -> summary.blockerCount
            SetupReadinessStatus.WARNING -> summary.warningCount
            SetupReadinessStatus.OK -> 0
        }
        return RelationshipActionReadiness(
            state = state,
            primaryReason = reason,
            blockers = List(blockerCount) {
                RelationshipReadinessBlocker(reason = reason, action = action)
            },
            primaryAction = action,
            confidence = if (summary.status == SetupReadinessStatus.OK) {
                RelationshipReadinessConfidence.HIGH
            } else {
                RelationshipReadinessConfidence.MEDIUM
            },
        )
    }

    fun fromSetupCandidates(
        candidates: List<SetupReadinessRecommendationCandidate>,
    ): RelationshipActionReadiness {
        val summary = SetupReadinessSummaryPolicy.summarize(candidates.map { it.status })
        val recommendedIndex = SetupReadinessRecommendationPolicy.selectRecommendedIndex(candidates)
        return fromSetupSummary(
            summary = summary,
            hasRecommendedAction = recommendedIndex != null,
        )
    }

    fun fromHomeNextAction(
        kind: HomeNextActionKind,
        relatedContactId: String? = null,
    ): RelationshipActionReadiness {
        val state = kind.toRelationshipState()
        val reason = kind.toRelationshipReason()
        val action = reason.toPrimaryAction()
        return RelationshipActionReadiness(
            state = state,
            primaryReason = reason,
            blockers = if (state == RelationshipReadinessState.ACTION_REQUIRED ||
                state == RelationshipReadinessState.WARNING
            ) {
                listOf(RelationshipReadinessBlocker(reason = reason, action = action))
            } else {
                emptyList()
            },
            primaryAction = action,
            secondaryActions = when (kind) {
                HomeNextActionKind.FIX_CONTACT_SYNC -> listOf(RelationshipReadinessAction.SYNC_CONTACTS)
                else -> emptyList()
            },
            confidence = RelationshipReadinessConfidence.HIGH,
            relatedContactId = relatedContactId,
        )
    }

    fun fromHomeNextActionCandidate(
        candidate: HomeNextActionCandidate,
    ): RelationshipActionReadiness {
        return fromHomeNextAction(
            kind = candidate.kind,
            relatedContactId = candidate.contactId,
        )
    }

    fun fromHomeReadinessBanner(
        candidate: HomeReadinessBannerCandidate,
    ): RelationshipActionReadiness {
        return fromHomeNextAction(
            kind = candidate.kind,
        )
    }

    fun fromSetupNotificationRequest(
        request: SetupNotificationRequest,
    ): RelationshipActionReadiness {
        val reason = request.reason.toRelationshipReason()
        val action = reason.toPrimaryAction()
        val state = when (request.reason) {
            SetupNotificationReason.MESSAGE_EXPIRED,
            SetupNotificationReason.DOUBLE_SEND_GUARD -> RelationshipReadinessState.WARNING
            SetupNotificationReason.SMS_PERMISSION_MISSING,
            SetupNotificationReason.AI_PROVIDER_MISSING,
            SetupNotificationReason.REVIVAL_AI_PROVIDER_MISSING,
            SetupNotificationReason.EXACT_ALARM_PERMISSION_MISSING -> RelationshipReadinessState.ACTION_REQUIRED
        }
        return RelationshipActionReadiness(
            state = state,
            primaryReason = reason,
            blockers = listOf(RelationshipReadinessBlocker(reason = reason, action = action)),
            primaryAction = action,
            confidence = RelationshipReadinessConfidence.MEDIUM,
        )
    }

    fun fromSystemAlertNotificationRequest(
        request: SystemAlertNotificationRequest,
    ): RelationshipActionReadiness {
        val reason = when (request.reason) {
            SystemAlertNotificationReason.AI_FALLBACK_USED -> RelationshipReadinessReason.SETUP_WARNING
            SystemAlertNotificationReason.BACKUP_STALE -> RelationshipReadinessReason.BACKUP_STALE
        }
        val action = reason.toPrimaryAction()
        return RelationshipActionReadiness(
            state = RelationshipReadinessState.WARNING,
            primaryReason = reason,
            blockers = listOf(RelationshipReadinessBlocker(reason = reason, action = action)),
            primaryAction = action,
            confidence = RelationshipReadinessConfidence.MEDIUM,
        )
    }

    private fun HomeNextActionKind.toRelationshipState(): RelationshipReadinessState {
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

    private fun HomeNextActionKind.toRelationshipReason(): RelationshipReadinessReason {
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

    private fun WishPreviewRouteContext.toRelationshipActionReadiness(): RelationshipActionReadiness {
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

    private fun WishPreviewDeviceSetupContext.toRelationshipActionReadiness(): RelationshipActionReadiness {
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

    private fun WishPreviewDispatchContext.toRelationshipActionReadiness(): RelationshipActionReadiness {
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

    private fun MessageOperationalReadiness.toRelationshipReason(): RelationshipReadinessReason {
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

    private fun SetupNotificationReason.toRelationshipReason(): RelationshipReadinessReason {
        return when (this) {
            SetupNotificationReason.SMS_PERMISSION_MISSING -> RelationshipReadinessReason.CHANNEL_DISABLED
            SetupNotificationReason.MESSAGE_EXPIRED,
            SetupNotificationReason.DOUBLE_SEND_GUARD -> RelationshipReadinessReason.PENDING_MESSAGES
            SetupNotificationReason.AI_PROVIDER_MISSING,
            SetupNotificationReason.REVIVAL_AI_PROVIDER_MISSING -> RelationshipReadinessReason.AI_ACCESS_MISSING
            SetupNotificationReason.EXACT_ALARM_PERMISSION_MISSING -> RelationshipReadinessReason.SETUP_ACTION_REQUIRED
        }
    }

    private fun RelationshipReadinessReason.toPrimaryAction(): RelationshipReadinessAction {
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
}
