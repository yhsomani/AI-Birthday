package com.example.ui.viewmodel

import com.example.core.resilience.SensitiveLogRedactor
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.repository.DiagnosticSnapshotRepository
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal class AutomationSetupDiagnosticSnapshotStore(
    private val diagnosticSnapshotRepository: DiagnosticSnapshotRepository,
) {
    suspend fun loadRecentPersistedHealthSnapshot(
        nowMs: Long = System.currentTimeMillis(),
    ): DiagnosticSnapshot? {
        return runCatching {
            diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
                ?.takeIf { it.status != DiagnosticSnapshotStatus.OK }
                ?.takeIf { nowMs - it.createdAtMs <= PERSISTED_HEALTH_SNAPSHOT_TTL_MS }
        }.getOrNull()
    }

    suspend fun recordAiDoctorSnapshot(report: AiDoctorReport) {
        runCatching {
            diagnosticSnapshotRepository.record(report.toDiagnosticSnapshot())
        }
    }

    internal fun AiDoctorReport.toDiagnosticSnapshot(): DiagnosticSnapshot {
        val checksJson = JSONArray().also { checksArray ->
            checks.forEach { check ->
                checksArray.put(
                    JSONObject()
                        .put("title", check.title)
                        .put("status", check.status.name)
                        .put("group", check.group.name)
                        .put("action", check.action.name)
                        .put("detail", check.detail),
                )
            }
        }
        val payload = JSONObject()
            .put("source", DiagnosticSnapshotSource.AI_DOCTOR.raw)
            .put("summaryStatus", summary.status.name)
            .put("recommendedAction", recommendedFix?.action?.name)
            .put("completedSteps", setupProgress.completedSteps)
            .put("totalSteps", setupProgress.totalSteps)
            .put("checks", checksJson)
            .toString()
        return DiagnosticSnapshot(
            id = DiagnosticSnapshotId("ai-doctor-${UUID.randomUUID()}"),
            source = DiagnosticSnapshotSource.AI_DOCTOR,
            status = summary.status.toDiagnosticSnapshotStatus(),
            summary = SensitiveLogRedactor.redact("${summary.title}: ${summary.detail}"),
            checksJson = SensitiveLogRedactor.redact(payload),
            createdAtMs = System.currentTimeMillis(),
        )
    }

    private fun ReadinessStatus.toDiagnosticSnapshotStatus(): DiagnosticSnapshotStatus = when (this) {
        ReadinessStatus.OK -> DiagnosticSnapshotStatus.OK
        ReadinessStatus.WARNING -> DiagnosticSnapshotStatus.WARNING
        ReadinessStatus.ACTION_REQUIRED -> DiagnosticSnapshotStatus.ACTION_REQUIRED
    }

    private companion object {
        const val PERSISTED_HEALTH_SNAPSHOT_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
