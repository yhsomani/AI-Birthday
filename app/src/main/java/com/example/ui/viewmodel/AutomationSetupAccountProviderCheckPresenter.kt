package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.domain.automation.AiWishGenerationReadiness
import com.example.domain.automation.AiWishGenerationReadinessReason
import com.example.domain.automation.GeminiAccessReadiness
import com.example.domain.automation.GeminiAccessReadinessReason
import com.example.domain.automation.GeminiCircuitReadiness
import com.example.domain.automation.GeminiCircuitReadinessReason
import com.example.domain.automation.GoogleContactsReadiness
import com.example.domain.automation.GoogleContactsReadinessReason

internal class AutomationSetupAccountProviderCheckPresenter(
    private val context: Context,
) {
    fun googleContacts(readiness: GoogleContactsReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_google_contacts),
            detail = when (readiness.reason) {
                GoogleContactsReadinessReason.READY ->
                    text(R.string.automation_setup_google_contacts_ok)
                GoogleContactsReadinessReason.ACCESS_MISSING ->
                    text(R.string.automation_setup_google_contacts_missing)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                GoogleContactsReadinessReason.READY -> null
                GoogleContactsReadinessReason.ACCESS_MISSING ->
                    text(R.string.automation_setup_action_sync_contacts)
            },
            action = when (readiness.reason) {
                GoogleContactsReadinessReason.READY -> AiDoctorAction.NONE
                GoogleContactsReadinessReason.ACCESS_MISSING -> AiDoctorAction.SYNC_CONTACTS
            },
            group = readiness.group,
        )
    }

    fun geminiAccess(readiness: GeminiAccessReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_gemini),
            detail = when (readiness.reason) {
                GeminiAccessReadinessReason.API_KEY_CONFIGURED ->
                    text(R.string.automation_setup_gemini_key_ok)
                GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE ->
                    text(R.string.automation_setup_gemini_auth_ok)
                GeminiAccessReadinessReason.MISSING_ACCESS ->
                    text(R.string.automation_setup_gemini_auth_missing)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                GeminiAccessReadinessReason.API_KEY_CONFIGURED,
                GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE ->
                    text(R.string.automation_setup_action_test_ai)
                GeminiAccessReadinessReason.MISSING_ACCESS ->
                    text(R.string.automation_setup_action_open_settings)
            },
            action = when (readiness.reason) {
                GeminiAccessReadinessReason.API_KEY_CONFIGURED,
                GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE -> AiDoctorAction.TEST_AI
                GeminiAccessReadinessReason.MISSING_ACCESS -> AiDoctorAction.OPEN_SETTINGS
            },
            group = readiness.group,
        )
    }

    fun aiWishGeneration(readiness: AiWishGenerationReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_ai_wish_generation),
            detail = when (readiness.reason) {
                AiWishGenerationReadinessReason.ENABLED ->
                    text(R.string.automation_setup_ai_wish_enabled)
                AiWishGenerationReadinessReason.DISABLED ->
                    text(R.string.automation_setup_ai_wish_disabled)
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                AiWishGenerationReadinessReason.ENABLED -> null
                AiWishGenerationReadinessReason.DISABLED ->
                    text(R.string.automation_setup_action_open_settings)
            },
            action = when (readiness.reason) {
                AiWishGenerationReadinessReason.ENABLED -> AiDoctorAction.NONE
                AiWishGenerationReadinessReason.DISABLED -> AiDoctorAction.OPEN_SETTINGS
            },
            group = readiness.group,
        )
    }

    fun geminiCircuit(readiness: GeminiCircuitReadiness): ReadinessCheck {
        return ReadinessCheck(
            title = text(R.string.automation_setup_check_gemini_circuit),
            detail = when (readiness.reason) {
                GeminiCircuitReadinessReason.NO_STATE ->
                    text(R.string.automation_setup_gemini_circuit_none)
                GeminiCircuitReadinessReason.CLOSED ->
                    text(R.string.automation_setup_gemini_circuit_ok)
                GeminiCircuitReadinessReason.HALF_OPEN,
                GeminiCircuitReadinessReason.OPEN -> text(
                    R.string.automation_setup_gemini_circuit_state,
                    readiness.circuitState.name,
                )
            },
            status = readiness.status,
            actionLabel = when (readiness.reason) {
                GeminiCircuitReadinessReason.OPEN ->
                    text(R.string.automation_setup_action_test_ai)
                GeminiCircuitReadinessReason.NO_STATE,
                GeminiCircuitReadinessReason.CLOSED,
                GeminiCircuitReadinessReason.HALF_OPEN -> null
            },
            action = when (readiness.reason) {
                GeminiCircuitReadinessReason.OPEN -> AiDoctorAction.TEST_AI
                GeminiCircuitReadinessReason.NO_STATE,
                GeminiCircuitReadinessReason.CLOSED,
                GeminiCircuitReadinessReason.HALF_OPEN -> AiDoctorAction.NONE
            },
            group = readiness.group,
        )
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
