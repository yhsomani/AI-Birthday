package com.example.ui.viewmodel

import com.example.domain.automation.SetupReadinessRecommendationCandidate
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy

enum class AiDoctorAction {
    NONE,
    REFRESH,
    TEST_AI,
    TEST_EMAIL,
    SYNC_CONTACTS,
    OPEN_SETTINGS,
    OPEN_STYLE_COACH,
    OPEN_CONTACTS,
    OPEN_MESSAGES,
    OPEN_SMS_MESSAGES,
    OPEN_WHATSAPP_MESSAGES,
    OPEN_ACTIVITY_HISTORY,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_APP_SETTINGS,
}

data class AiDoctorSummary(
    val title: String = "",
    val detail: String = "",
    val status: ReadinessStatus = ReadinessStatus.WARNING,
)

data class ReadinessCheck(
    val title: String,
    val detail: String,
    val status: ReadinessStatus,
    val actionLabel: String? = null,
    val action: AiDoctorAction = AiDoctorAction.NONE,
    val group: ReadinessGroup = ReadinessGroup.REQUIRED,
    val actionReadiness: RelationshipActionReadiness = RelationshipActionReadinessPolicy.fromSetupCandidates(
        listOf(
            SetupReadinessRecommendationCandidate(
                status = status,
                group = group,
                hasAction = action != AiDoctorAction.NONE && !actionLabel.isNullOrBlank(),
            ),
        ),
    ),
)

data class AiDoctorRecommendedFix(
    val title: String,
    val detail: String,
    val actionLabel: String,
    val action: AiDoctorAction,
    val status: ReadinessStatus,
    val group: ReadinessGroup,
    val actionReadiness: RelationshipActionReadiness = RelationshipActionReadinessPolicy.fromSetupCandidates(
        listOf(
            SetupReadinessRecommendationCandidate(
                status = status,
                group = group,
                hasAction = action != AiDoctorAction.NONE && actionLabel.isNotBlank(),
            ),
        ),
    ),
)

data class AutomationSetupUiState(
    val checks: List<ReadinessCheck> = emptyList(),
    val summary: AiDoctorSummary = AiDoctorSummary(),
    val recommendedFix: AiDoctorRecommendedFix? = null,
    val setupProgress: SetupProgressSummary = SetupProgressSummary(),
    val setupActionReadiness: RelationshipActionReadiness =
        RelationshipActionReadinessPolicy.fromSetupCandidates(emptyList()),
    val isRefreshing: Boolean = false,
    val isSyncingContacts: Boolean = false,
    val isTestingAi: Boolean = false,
    val isTestingEmail: Boolean = false,
    val whatsAppAutomationConsentGranted: Boolean = false,
    val operationMessage: String? = null,
)
