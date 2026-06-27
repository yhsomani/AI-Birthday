package com.example.core.resilience

import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.repository.DiagnosticSnapshotRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class HealthMonitorDiagnosticRecorder @Inject constructor(
    private val diagnosticSnapshotRepository: DiagnosticSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        start(scope)
    }

    internal fun start(scope: CoroutineScope) {
        HealthMonitor.registerSnapshotSink(SINK_ID) { snapshot ->
            scope.launch {
                diagnosticSnapshotRepository.record(snapshot.toDiagnosticSnapshot())
            }
        }
    }

    companion object {
        internal const val SINK_ID = "diagnostic_snapshots"

        internal fun HealthSnapshot.toDiagnosticSnapshot(
            id: String = "health-${UUID.randomUUID()}",
            createdAtMs: Long = System.currentTimeMillis(),
        ): DiagnosticSnapshot {
            val breakerSummary = circuitBreakerStates.entries.joinToString(", ") { (name, state) ->
                "$name=${state.name}"
            }.ifBlank { "none" }
            val summary = "HealthMonitor: healthy=$isHealthy; deadLetterCount=$deadLetterCount; " +
                "circuitBreakers=$breakerSummary; recentErrors=${recentErrors.size}"
            val checksJson = JSONObject()
                .put("source", DiagnosticSnapshotSource.HEALTH_MONITOR.raw)
                .put("isHealthy", isHealthy)
                .put("deadLetterCount", deadLetterCount)
                .put("circuitBreakers", JSONObject().also { json ->
                    circuitBreakerStates.forEach { (name, state) ->
                        json.put(name, state.name)
                    }
                })
                .put("recentErrors", JSONArray(recentErrors))
                .toString()
            val hasWarningEvidence = !isHealthy || recentErrors.isNotEmpty()
            return DiagnosticSnapshot(
                id = DiagnosticSnapshotId(id),
                source = DiagnosticSnapshotSource.HEALTH_MONITOR,
                status = if (hasWarningEvidence) DiagnosticSnapshotStatus.WARNING else DiagnosticSnapshotStatus.OK,
                summary = SensitiveLogRedactor.redact(summary),
                checksJson = SensitiveLogRedactor.redact(checksJson),
                createdAtMs = createdAtMs,
            )
        }
    }
}
