package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.domain.automation.SetupEmailReadiness
import com.example.domain.automation.SetupEmailReadinessReason

internal class AutomationSetupEmailCheckPresenter(
    private val context: Context,
) {
    fun email(readiness: SetupEmailReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_email),
            detail = when (readiness.reason) {
                SetupEmailReadinessReason.INVALID_SENDER ->
                    text(R.string.automation_setup_email_invalid)
                SetupEmailReadinessReason.VERIFIED ->
                    text(R.string.automation_setup_email_ok)
                SetupEmailReadinessReason.UNVERIFIED ->
                    text(R.string.automation_setup_email_unverified)
                SetupEmailReadinessReason.MISSING_FOR_CONTACTS -> text(
                    R.string.automation_setup_email_missing_for_contacts,
                    readiness.emailPreferredContactCount,
                )
                SetupEmailReadinessReason.OPTIONAL ->
                    text(R.string.automation_setup_email_optional)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                SetupEmailReadinessReason.INVALID_SENDER,
                SetupEmailReadinessReason.MISSING_FOR_CONTACTS,
                SetupEmailReadinessReason.OPTIONAL -> text(R.string.automation_setup_action_open_settings)
                SetupEmailReadinessReason.UNVERIFIED -> text(R.string.automation_setup_action_test_email)
                SetupEmailReadinessReason.VERIFIED -> null
            },
            action = when (readiness.reason) {
                SetupEmailReadinessReason.INVALID_SENDER,
                SetupEmailReadinessReason.MISSING_FOR_CONTACTS,
                SetupEmailReadinessReason.OPTIONAL -> AiDoctorAction.OPEN_SETTINGS
                SetupEmailReadinessReason.UNVERIFIED -> AiDoctorAction.TEST_EMAIL
                SetupEmailReadinessReason.VERIFIED -> AiDoctorAction.NONE
            },
            group = readiness.group,
        )
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
