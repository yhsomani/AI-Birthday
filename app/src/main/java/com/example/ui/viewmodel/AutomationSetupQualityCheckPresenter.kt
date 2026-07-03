package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.domain.automation.GenericMessageReadiness
import com.example.domain.automation.GenericMessageReadinessReason
import com.example.domain.automation.PersonalizationReadiness
import com.example.domain.automation.PersonalizationReadinessReason
import com.example.domain.automation.StyleCoachReadiness
import com.example.domain.automation.StyleCoachReadinessReason

internal class AutomationSetupQualityCheckPresenter(
    private val context: Context,
) {
    fun styleCoach(readiness: StyleCoachReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_style_coach),
            detail = when (readiness.reason) {
                StyleCoachReadinessReason.TRAINED ->
                    text(R.string.automation_setup_style_trained, readiness.sampleCount)
                StyleCoachReadinessReason.NEEDS_MORE ->
                    text(R.string.automation_setup_style_needs_more, readiness.samplesNeeded)
                StyleCoachReadinessReason.EMPTY ->
                    text(R.string.automation_setup_style_empty)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                StyleCoachReadinessReason.TRAINED -> null
                StyleCoachReadinessReason.NEEDS_MORE,
                StyleCoachReadinessReason.EMPTY -> text(R.string.automation_setup_action_open_style_coach)
            },
            action = when (readiness.reason) {
                StyleCoachReadinessReason.TRAINED -> AiDoctorAction.NONE
                StyleCoachReadinessReason.NEEDS_MORE,
                StyleCoachReadinessReason.EMPTY -> AiDoctorAction.OPEN_STYLE_COACH
            },
            group = readiness.group,
        )
    }

    fun personalization(readiness: PersonalizationReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_personalization),
            detail = when (readiness.reason) {
                PersonalizationReadinessReason.EMPTY ->
                    text(R.string.automation_setup_personalization_empty)
                PersonalizationReadinessReason.READY -> text(
                    R.string.automation_setup_personalization_ok,
                    readiness.enrichedContactCount,
                    readiness.totalContactCount,
                )
                PersonalizationReadinessReason.LOW -> text(
                    R.string.automation_setup_personalization_low,
                    readiness.enrichedContactCount,
                    readiness.totalContactCount,
                )
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                PersonalizationReadinessReason.EMPTY ->
                    text(R.string.automation_setup_action_sync_contacts)
                PersonalizationReadinessReason.READY -> null
                PersonalizationReadinessReason.LOW ->
                    text(R.string.automation_setup_action_review_contacts)
            },
            action = when (readiness.reason) {
                PersonalizationReadinessReason.EMPTY -> AiDoctorAction.SYNC_CONTACTS
                PersonalizationReadinessReason.READY -> AiDoctorAction.NONE
                PersonalizationReadinessReason.LOW -> AiDoctorAction.OPEN_CONTACTS
            },
            group = readiness.group,
        )
    }

    fun genericMessages(readiness: GenericMessageReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_generic_messages),
            detail = when (readiness.reason) {
                GenericMessageReadinessReason.EMPTY ->
                    text(R.string.automation_setup_generic_messages_empty)
                GenericMessageReadinessReason.READY ->
                    text(R.string.automation_setup_generic_messages_ok)
                GenericMessageReadinessReason.RISK -> text(
                    R.string.automation_setup_generic_messages_low,
                    readiness.genericRiskCount,
                    readiness.totalContactCount,
                )
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                GenericMessageReadinessReason.EMPTY ->
                    text(R.string.automation_setup_action_sync_contacts)
                GenericMessageReadinessReason.READY -> null
                GenericMessageReadinessReason.RISK ->
                    text(R.string.automation_setup_action_review_contacts)
            },
            action = when (readiness.reason) {
                GenericMessageReadinessReason.EMPTY -> AiDoctorAction.SYNC_CONTACTS
                GenericMessageReadinessReason.READY -> AiDoctorAction.NONE
                GenericMessageReadinessReason.RISK -> AiDoctorAction.OPEN_CONTACTS
            },
            group = readiness.group,
        )
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
