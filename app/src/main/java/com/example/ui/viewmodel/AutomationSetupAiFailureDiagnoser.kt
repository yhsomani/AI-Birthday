package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.core.resilience.SensitiveLogRedactor

internal class AutomationSetupAiFailureDiagnoser(
    private val context: Context,
) {
    fun diagnose(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("429") || lower.contains("quota") || lower.contains("exhausted") ->
                text(R.string.automation_setup_ai_error_quota)
            lower.contains("api key") ||
                lower.contains("apikey") ||
                lower.contains("permission") ||
                lower.contains("403") ||
                lower.contains("unauthenticated") ->
                text(R.string.automation_setup_ai_error_auth)
            lower.contains("network") ||
                lower.contains("timeout") ||
                lower.contains("unavailable") ||
                lower.contains("unable to resolve") ->
                text(R.string.automation_setup_ai_error_network)
            lower.contains("json") || lower.contains("parse") || lower.contains("empty response") ->
                text(R.string.automation_setup_ai_error_json)
            lower.contains("circuit breaker") ->
                text(R.string.automation_setup_ai_error_circuit)
            else -> text(
                R.string.automation_setup_ai_error_recent,
                SensitiveLogRedactor.redact(raw).take(160),
            )
        }
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
