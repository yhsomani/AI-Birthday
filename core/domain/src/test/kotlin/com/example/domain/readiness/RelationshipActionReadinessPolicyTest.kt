package com.example.domain.readiness

import com.example.domain.automation.MessageOperationalReadiness
import com.example.domain.automation.SetupReadinessGroup
import com.example.domain.automation.SetupReadinessRecommendationCandidate
import com.example.domain.automation.SetupReadinessStatus
import com.example.domain.home.HomeNextActionCandidate
import com.example.domain.home.HomeNextActionKind
import com.example.domain.home.HomeNextActionTargetKind
import com.example.domain.message.WishDraftReadiness
import com.example.domain.message.WishPreviewDeviceSetupContext
import com.example.domain.message.WishPreviewDeviceSetupReason
import com.example.domain.message.WishPreviewDeviceSetupState
import com.example.domain.message.WishPreviewRouteContext
import com.example.domain.message.WishPreviewRouteReason
import com.example.domain.message.WishPreviewRouteState
import com.example.domain.message.WishPreviewSendSummary
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.notification.SetupNotificationRequest
import com.example.domain.model.notification.SystemAlertNotificationReason
import com.example.domain.model.notification.SystemAlertNotificationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipActionReadinessPolicyTest {

    @Test
    fun `message route blocker maps to canonical action required readiness`() {
        val readiness = RelationshipActionReadinessPolicy.fromMessageOperationalReadiness(
            readiness = MessageOperationalReadiness.MISSING_PHONE,
            relatedMessageId = "message_1",
            relatedContactId = "contact_1",
            relatedEventId = "event_1",
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.MISSING_PHONE, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.primaryAction)
        assertEquals("message_1", readiness.relatedMessageId)
        assertEquals("contact_1", readiness.relatedContactId)
        assertEquals("event_1", readiness.relatedEventId)
        assertEquals(
            RelationshipReadinessBlocker(
                reason = RelationshipReadinessReason.MISSING_PHONE,
                action = RelationshipReadinessAction.CONFIGURE_CHANNEL,
            ),
            readiness.blockers.single(),
        )
    }

    @Test
    fun `approved message waiting for schedule maps to canonical waiting readiness`() {
        val readiness = RelationshipActionReadinessPolicy.fromMessageOperationalReadiness(
            readiness = MessageOperationalReadiness.APPROVED_WAITING_FOR_SCHEDULE,
        )

        assertEquals(RelationshipReadinessState.WAITING, readiness.state)
        assertEquals(RelationshipReadinessReason.WAITING_FOR_SCHEDULE, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.NONE, readiness.primaryAction)
        assertTrue(readiness.blockers.isEmpty())
    }

    @Test
    fun `blank wish draft maps to edit draft blocker`() {
        val readiness = RelationshipActionReadinessPolicy.fromWishDraftReadiness(
            readiness = WishDraftReadiness.BLANK,
            relatedMessageId = "message_2",
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.DRAFT_BLANK, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.EDIT_DRAFT, readiness.primaryAction)
        assertEquals("message_2", readiness.relatedMessageId)
        assertEquals(RelationshipReadinessAction.EDIT_DRAFT, readiness.blockers.single().action)
    }

    @Test
    fun `edited ready wish draft maps to ready without blocker`() {
        val readiness = RelationshipActionReadinessPolicy.fromWishDraftReadiness(
            readiness = WishDraftReadiness.EDITED_READY,
        )

        assertEquals(RelationshipReadinessState.READY, readiness.state)
        assertEquals(RelationshipReadinessReason.DRAFT_EDITED_READY, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.NONE, readiness.primaryAction)
        assertTrue(readiness.blockers.isEmpty())
    }

    @Test
    fun `wish preview send summary maps route blocker to canonical channel action`() {
        val readiness = RelationshipActionReadinessPolicy.fromWishPreviewSendSummary(
            summary = wishPreviewSummary(
                routeContext = WishPreviewRouteContext(
                    state = WishPreviewRouteState.BLOCKED,
                    reason = WishPreviewRouteReason.MISSING_PHONE,
                ),
            ),
            relatedMessageId = "message_3",
            relatedContactId = "contact_3",
            relatedEventId = "event_3",
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.MISSING_PHONE, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.primaryAction)
        assertEquals("message_3", readiness.relatedMessageId)
        assertEquals("contact_3", readiness.relatedContactId)
        assertEquals("event_3", readiness.relatedEventId)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.blockers.single().action)
    }

    @Test
    fun `wish preview send summary maps device setup blocker to canonical channel action`() {
        val readiness = RelationshipActionReadinessPolicy.fromWishPreviewSendSummary(
            summary = wishPreviewSummary(
                deviceSetupContext = WishPreviewDeviceSetupContext(
                    state = WishPreviewDeviceSetupState.ACTION_REQUIRED,
                    reason = WishPreviewDeviceSetupReason.WHATSAPP_ACCESSIBILITY_MISSING,
                ),
            ),
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.CHANNEL_DISABLED, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.primaryAction)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.blockers.single().action)
    }

    @Test
    fun `wish preview send summary maps approval-needed dispatch to canonical review action`() {
        val readiness = RelationshipActionReadinessPolicy.fromWishPreviewSendSummary(
            summary = wishPreviewSummary(),
        )

        assertEquals(RelationshipReadinessState.NEEDS_REVIEW, readiness.state)
        assertEquals(RelationshipReadinessReason.MESSAGE_NEEDS_REVIEW, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.REVIEW_MESSAGE, readiness.primaryAction)
        assertTrue(readiness.blockers.isEmpty())
    }

    @Test
    fun `setup candidates map ranked action required checks to setup readiness`() {
        val readiness = RelationshipActionReadinessPolicy.fromSetupCandidates(
            listOf(
                SetupReadinessRecommendationCandidate(
                    status = SetupReadinessStatus.WARNING,
                    group = SetupReadinessGroup.QUALITY,
                    hasAction = true,
                ),
                SetupReadinessRecommendationCandidate(
                    status = SetupReadinessStatus.ACTION_REQUIRED,
                    group = SetupReadinessGroup.REQUIRED,
                    hasAction = true,
                ),
                SetupReadinessRecommendationCandidate(
                    status = SetupReadinessStatus.OK,
                    group = SetupReadinessGroup.RECOVERY,
                    hasAction = false,
                ),
            )
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.SETUP_ACTION_REQUIRED, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, readiness.primaryAction)
        assertEquals(RelationshipReadinessConfidence.MEDIUM, readiness.confidence)
        assertEquals(1, readiness.blockers.size)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, readiness.blockers.single().action)
    }

    @Test
    fun `home pending review action maps to canonical review readiness`() {
        val readiness = RelationshipActionReadinessPolicy.fromHomeNextActionCandidate(
            HomeNextActionCandidate(
                kind = HomeNextActionKind.REVIEW_PENDING,
                targetKind = HomeNextActionTargetKind.MESSAGES,
                count = 3,
            )
        )

        assertEquals(RelationshipReadinessState.NEEDS_REVIEW, readiness.state)
        assertEquals(RelationshipReadinessReason.PENDING_MESSAGES, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.REVIEW_MESSAGES, readiness.primaryAction)
        assertTrue(readiness.blockers.isEmpty())
    }

    @Test
    fun `home contact sync failure maps to canonical setup blocker`() {
        val readiness = RelationshipActionReadinessPolicy.fromHomeNextAction(
            kind = HomeNextActionKind.FIX_CONTACT_SYNC,
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.CONTACT_SYNC_FAILED, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.FIX_CONTACT_SYNC, readiness.primaryAction)
        assertEquals(RelationshipReadinessAction.SYNC_CONTACTS, readiness.secondaryActions.single())
        assertEquals(RelationshipReadinessAction.FIX_CONTACT_SYNC, readiness.blockers.single().action)
    }

    @Test
    fun `home backup freshness action maps to canonical warning readiness`() {
        val readiness = RelationshipActionReadinessPolicy.fromHomeNextAction(
            kind = HomeNextActionKind.REFRESH_BACKUP,
        )

        assertEquals(RelationshipReadinessState.WARNING, readiness.state)
        assertEquals(RelationshipReadinessReason.BACKUP_STALE, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.REFRESH_BACKUP, readiness.primaryAction)
        assertEquals(RelationshipReadinessAction.REFRESH_BACKUP, readiness.blockers.single().action)
    }

    @Test
    fun `setup notification maps sms permission blocker to canonical setup route action`() {
        val readiness = RelationshipActionReadinessPolicy.fromSetupNotificationRequest(
            SetupNotificationRequest(
                reason = SetupNotificationReason.SMS_PERMISSION_MISSING,
                contactDisplayName = "Amit",
            )
        )

        assertEquals(RelationshipReadinessState.ACTION_REQUIRED, readiness.state)
        assertEquals(RelationshipReadinessReason.CHANNEL_DISABLED, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.primaryAction)
        assertEquals(RelationshipReadinessConfidence.MEDIUM, readiness.confidence)
        assertEquals(RelationshipReadinessAction.CONFIGURE_CHANNEL, readiness.blockers.single().action)
    }

    @Test
    fun `setup notification maps expired message to canonical messages review warning`() {
        val readiness = RelationshipActionReadinessPolicy.fromSetupNotificationRequest(
            SetupNotificationRequest(
                reason = SetupNotificationReason.MESSAGE_EXPIRED,
                contactDisplayName = "Amit",
            )
        )

        assertEquals(RelationshipReadinessState.WARNING, readiness.state)
        assertEquals(RelationshipReadinessReason.PENDING_MESSAGES, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.REVIEW_MESSAGES, readiness.primaryAction)
        assertEquals(RelationshipReadinessAction.REVIEW_MESSAGES, readiness.blockers.single().action)
    }

    @Test
    fun `system alert notification maps ai fallback to canonical setup warning`() {
        val readiness = RelationshipActionReadinessPolicy.fromSystemAlertNotificationRequest(
            SystemAlertNotificationRequest(
                reason = SystemAlertNotificationReason.AI_FALLBACK_USED,
            )
        )

        assertEquals(RelationshipReadinessState.WARNING, readiness.state)
        assertEquals(RelationshipReadinessReason.SETUP_WARNING, readiness.primaryReason)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, readiness.primaryAction)
        assertEquals(RelationshipReadinessAction.OPEN_SETUP, readiness.blockers.single().action)
    }

    private fun wishPreviewSummary(
        routeContext: WishPreviewRouteContext? = null,
        deviceSetupContext: WishPreviewDeviceSetupContext? = null,
    ): WishPreviewSendSummary {
        return WishPreviewSendSummary(
            eventType = "BIRTHDAY",
            channel = "SMS",
            scheduledForMs = 1_700_000_000_000L,
            approvalMode = "VIP_APPROVE",
            usesFallback = false,
            routeContext = routeContext,
            deviceSetupContext = deviceSetupContext,
        )
    }
}
