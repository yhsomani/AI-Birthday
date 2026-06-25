package com.example.domain.automation

import com.example.domain.model.ApprovalMode

object ApprovalModeResolver {
    fun resolve(
        relationship: String?,
        contactOverride: String?,
        globalMode: String?,
        skipAutoWish: Boolean = false,
    ): ApprovalMode {
        if (skipAutoWish) return ApprovalMode.ALWAYS_ASK

        val overrideMode = ApprovalMode.fromRaw(contactOverride)
        if (overrideMode.isExplicitAutomationMode()) {
            return overrideMode
        }

        val global = ApprovalMode.fromRaw(globalMode)
            .takeIf { it.isExplicitAutomationMode() }
            ?: ApprovalMode.SMART_APPROVE

        return when (relationship?.trim()?.uppercase()) {
            "FAMILY", "BEST_FRIEND" -> ApprovalMode.VIP_APPROVE
            "CLOSE_FRIEND", "RELATIVE" -> {
                if (global == ApprovalMode.ALWAYS_ASK) ApprovalMode.ALWAYS_ASK else ApprovalMode.SMART_APPROVE
            }
            else -> global
        }
    }

    fun schedulesAutomaticDispatch(mode: ApprovalMode): Boolean {
        return mode == ApprovalMode.FULLY_AUTO || mode == ApprovalMode.SMART_APPROVE
    }

    fun needsReviewNotification(mode: ApprovalMode): Boolean {
        return mode != ApprovalMode.FULLY_AUTO
    }

    private fun ApprovalMode.isExplicitAutomationMode(): Boolean {
        return this == ApprovalMode.FULLY_AUTO ||
            this == ApprovalMode.SMART_APPROVE ||
            this == ApprovalMode.VIP_APPROVE ||
            this == ApprovalMode.ALWAYS_ASK
    }
}
