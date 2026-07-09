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
import com.example.domain.message.WishPreviewDeviceSetupState
import com.example.domain.message.WishPreviewRouteState
import com.example.domain.message.WishPreviewSendSummary
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.notification.SetupNotificationRequest
import com.example.domain.model.notification.SystemAlertNotificationReason
import com.example.domain.model.notification.SystemAlertNotificationRequest

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
}
