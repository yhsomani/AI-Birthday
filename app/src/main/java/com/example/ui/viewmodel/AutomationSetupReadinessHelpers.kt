package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.core.resilience.CircuitState
import com.example.core.resilience.HealthSnapshot
import com.example.core.resilience.LogEntry
import com.example.core.resilience.SensitiveLogRedactor
import com.example.domain.automation.SetupProviderCircuitState
import com.example.domain.model.MessageChannel
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.repository.DispatchAttemptRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray

internal const val AUTOMATION_CHANNEL_VERIFICATION_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

internal suspend fun DispatchAttemptRepository.loadDispatchRecoverySnapshot(
    persistedRecoveryCount: Int? = null,
): DispatchRecoverySnapshot {
    val resolvedPersistedRecoveryCount = persistedRecoveryCount ?: runCatching {
        countFailureRecoveryQueue().first()
    }.getOrDefault(0)
    val latestPersistedAttempt = runCatching {
        getFailureRecoveryQueue(limit = 1).firstOrNull()
    }.getOrNull()
    return DispatchRecoverySnapshot(
        persistedRecoveryCount = resolvedPersistedRecoveryCount,
        latestPersistedAttempt = latestPersistedAttempt,
    )
}

internal fun DispatchRecoverySnapshot.toReadinessDetail(context: Context): String {
    val summary = when {
        persistedRecoveryCount == 0 -> context.text(R.string.automation_setup_dispatch_recovery_none)
        else -> context.text(
            R.string.automation_setup_dispatch_recovery_count,
            persistedRecoveryCount,
        )
    }
    val latest = latestPersistedAttempt ?: return summary
    return "$summary " + context.text(
        R.string.automation_setup_dispatch_recovery_latest,
        latest.channel.raw,
        latest.result.raw,
        latest.messageDraftId.value,
    )
}

internal fun List<LogEntry>.toRecentErrorDetail(
    context: Context,
    health: HealthSnapshot,
    persistedHealthSnapshot: DiagnosticSnapshot?,
    aiFailureDiagnoser: AutomationSetupAiFailureDiagnoser,
): String {
    val liveError = lastOrNull()?.message ?: health.recentErrors.lastOrNull()
    return when {
        liveError != null -> aiFailureDiagnoser.diagnose(liveError)
        persistedHealthSnapshot != null -> context.text(
            R.string.automation_setup_ai_error_recent,
            SensitiveLogRedactor.redact(persistedHealthSnapshot.summary).take(160),
        )
        else -> context.text(R.string.automation_setup_recent_errors_none)
    }
}

internal fun CircuitState?.toSetupProviderCircuitState(): SetupProviderCircuitState {
    return when (this) {
        null -> SetupProviderCircuitState.NONE
        CircuitState.CLOSED -> SetupProviderCircuitState.CLOSED
        CircuitState.HALF_OPEN -> SetupProviderCircuitState.HALF_OPEN
        CircuitState.OPEN -> SetupProviderCircuitState.OPEN
    }
}

internal fun countJsonArrayItems(raw: String?): Int {
    if (raw.isNullOrBlank()) return 0
    return try {
        JSONArray(raw).length()
    } catch (_: Exception) {
        0
    }
}

internal fun String.toAutomationChannelSet(): Set<MessageChannel> {
    return CHANNEL_TOKEN_PATTERN.findAll(this)
        .map { MessageChannel.fromRaw(it.groupValues[1]) }
        .filter { it != MessageChannel.UNKNOWN }
        .toSet()
}

private fun Context.text(@StringRes resId: Int, vararg args: Any): String {
    return getString(resId, *args)
}

private val CHANNEL_TOKEN_PATTERN = Regex("\"([A-Za-z_]+)\"")
