package com.example.ui.viewmodel

import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.style.StyleProfileRecord
import com.example.domain.readiness.RelationshipActionReadiness

internal data class AiDoctorReport(
    val summary: AiDoctorSummary,
    val checks: List<ReadinessCheck>,
    val recommendedFix: AiDoctorRecommendedFix?,
    val setupProgress: SetupProgressSummary,
    val setupActionReadiness: RelationshipActionReadiness,
)

internal data class AutomationSetupReadinessInputs(
    val contacts: List<ContactAutomationReadinessProfile>,
    val styleProfile: StyleProfileRecord?,
    val persistedRecoveryCount: Int,
)

internal data class DispatchRecoverySnapshot(
    val persistedRecoveryCount: Int,
    val latestPersistedAttempt: DispatchAttempt?,
)
