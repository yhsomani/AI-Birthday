package com.example.ui.viewmodel

import com.example.domain.automation.SetupProgressSummary as DomainSetupProgressSummary
import com.example.domain.automation.SetupReadinessGroup
import com.example.domain.automation.SetupReadinessProgressPolicy
import com.example.domain.automation.SetupReadinessStatus

typealias ReadinessGroup = SetupReadinessGroup
typealias ReadinessStatus = SetupReadinessStatus
typealias SetupProgressSummary = DomainSetupProgressSummary

internal fun List<ReadinessCheck>.toSetupProgressSummary(): SetupProgressSummary {
    return SetupReadinessProgressPolicy.summarize(map { it.status })
}

internal fun buildHomeSetupProgressSummary(
    contactCount: Int,
    syncError: String?,
    aiGenerationEnabled: Boolean,
    hasAiAccess: Boolean,
    pendingCount: Int,
): SetupProgressSummary {
    return SetupReadinessProgressPolicy.summarizeHome(
        contactCount = contactCount,
        syncError = syncError,
        aiGenerationEnabled = aiGenerationEnabled,
        hasAiAccess = hasAiAccess,
        pendingCount = pendingCount,
    )
}
