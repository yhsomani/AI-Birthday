package com.example.domain.readiness

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
