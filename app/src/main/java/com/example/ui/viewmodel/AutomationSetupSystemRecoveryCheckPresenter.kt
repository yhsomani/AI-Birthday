package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.domain.automation.SetupDailyAutomationReadiness
import com.example.domain.automation.SetupDailyAutomationReadinessReason
import com.example.domain.automation.SetupDispatchRecoveryReadiness
import com.example.domain.automation.SetupDispatchRecoveryReadinessReason
import com.example.domain.automation.SetupExactSendReadiness
import com.example.domain.automation.SetupExactSendReadinessReason
import com.example.domain.automation.SetupNotificationReadiness
import com.example.domain.automation.SetupNotificationReadinessReason
import com.example.domain.automation.SetupRecentHealthReadiness
import com.example.domain.automation.SetupRecentHealthReadinessReason

internal class AutomationSetupSystemRecoveryCheckPresenter(
    private val context: Context,
) {
    fun notifications(readiness: SetupNotificationReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_notifications),
            detail = when (readiness.reason) {
                SetupNotificationReadinessReason.READY -> text(R.string.automation_setup_notifications_ok)
                SetupNotificationReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_notifications_missing)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                SetupNotificationReadinessReason.READY -> null
                SetupNotificationReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_action_app_settings)
            },
            action = when (readiness.reason) {
                SetupNotificationReadinessReason.READY -> AiDoctorAction.NONE
                SetupNotificationReadinessReason.PERMISSION_MISSING -> AiDoctorAction.OPEN_APP_SETTINGS
            },
            group = readiness.group,
        )
    }

    fun exactSends(readiness: SetupExactSendReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_exact_sends),
            detail = when (readiness.reason) {
                SetupExactSendReadinessReason.READY -> text(R.string.automation_setup_exact_sends_ok)
                SetupExactSendReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_exact_sends_missing)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                SetupExactSendReadinessReason.READY -> null
                SetupExactSendReadinessReason.PERMISSION_MISSING ->
                    text(R.string.automation_setup_action_app_settings)
            },
            action = when (readiness.reason) {
                SetupExactSendReadinessReason.READY -> AiDoctorAction.NONE
                SetupExactSendReadinessReason.PERMISSION_MISSING -> AiDoctorAction.OPEN_APP_SETTINGS
            },
            group = readiness.group,
        )
    }

    fun dailyAutomation(readiness: SetupDailyAutomationReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_daily_automation),
            detail = when (readiness.reason) {
                SetupDailyAutomationReadinessReason.SCHEDULED -> text(R.string.automation_setup_daily_ok)
                SetupDailyAutomationReadinessReason.MISSING -> text(R.string.automation_setup_daily_missing)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                SetupDailyAutomationReadinessReason.SCHEDULED -> null
                SetupDailyAutomationReadinessReason.MISSING -> text(R.string.automation_setup_action_refresh)
            },
            action = when (readiness.reason) {
                SetupDailyAutomationReadinessReason.SCHEDULED -> AiDoctorAction.NONE
                SetupDailyAutomationReadinessReason.MISSING -> AiDoctorAction.REFRESH
            },
            group = readiness.group,
        )
    }

    fun recentHealth(
        readiness: SetupRecentHealthReadiness,
        recentErrorDetail: String,
    ): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_recent_errors),
            detail = when (readiness.reason) {
                SetupRecentHealthReadinessReason.CLEAR ->
                    text(R.string.automation_setup_recent_errors_none)
                SetupRecentHealthReadinessReason.RECENT_EVIDENCE -> recentErrorDetail
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                SetupRecentHealthReadinessReason.CLEAR -> null
                SetupRecentHealthReadinessReason.RECENT_EVIDENCE ->
                    text(R.string.automation_setup_action_view_activity)
            },
            action = when (readiness.reason) {
                SetupRecentHealthReadinessReason.CLEAR -> AiDoctorAction.NONE
                SetupRecentHealthReadinessReason.RECENT_EVIDENCE -> AiDoctorAction.OPEN_ACTIVITY_HISTORY
            },
            group = readiness.group,
        )
    }

    fun dispatchRecovery(
        readiness: SetupDispatchRecoveryReadiness,
        recoveryDetail: String,
    ): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_dispatch_recovery),
            detail = recoveryDetail,
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                SetupDispatchRecoveryReadinessReason.CLEAR -> null
                SetupDispatchRecoveryReadinessReason.RECOVERY_QUEUE_PRESENT ->
                    text(R.string.automation_setup_action_view_activity)
            },
            action = when (readiness.reason) {
                SetupDispatchRecoveryReadinessReason.CLEAR -> AiDoctorAction.NONE
                SetupDispatchRecoveryReadinessReason.RECOVERY_QUEUE_PRESENT ->
                    AiDoctorAction.OPEN_ACTIVITY_HISTORY
            },
            group = readiness.group,
        )
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
