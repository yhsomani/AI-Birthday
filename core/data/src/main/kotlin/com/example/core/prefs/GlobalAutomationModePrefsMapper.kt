package com.example.core.prefs

import com.example.domain.model.ApprovalMode

internal object GlobalAutomationModePrefsMapper {
    fun toSupportedApprovalMode(raw: String?): ApprovalMode {
        return ApprovalMode.fromRaw(raw)
            .takeIf { it.isSupportedGlobalMode() }
            ?: DEFAULT_GLOBAL_AUTOMATION_MODE
    }

    fun toSupportedRaw(mode: ApprovalMode): String {
        return mode
            .takeIf { it.isSupportedGlobalMode() }
            ?.raw
            ?: DEFAULT_GLOBAL_AUTOMATION_MODE.raw
    }

    val DEFAULT_GLOBAL_AUTOMATION_MODE: ApprovalMode = ApprovalMode.ALWAYS_ASK

    private fun ApprovalMode.isSupportedGlobalMode(): Boolean {
        return this == ApprovalMode.FULLY_AUTO ||
            this == ApprovalMode.SMART_APPROVE ||
            this == ApprovalMode.VIP_APPROVE ||
            this == ApprovalMode.ALWAYS_ASK
    }
}
