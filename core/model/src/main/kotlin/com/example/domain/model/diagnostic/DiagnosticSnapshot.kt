package com.example.domain.model.diagnostic

import com.example.domain.model.common.DiagnosticSnapshotId

data class DiagnosticSnapshot(
    val id: DiagnosticSnapshotId,
    val source: DiagnosticSnapshotSource,
    val status: DiagnosticSnapshotStatus,
    val summary: String,
    val checksJson: String,
    val createdAtMs: Long,
)

enum class DiagnosticSnapshotSource(val raw: String) {
    AI_DOCTOR("AI_DOCTOR"),
    HEALTH_MONITOR("HEALTH_MONITOR"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): DiagnosticSnapshotSource {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}

enum class DiagnosticSnapshotStatus(val raw: String) {
    OK("OK"),
    WARNING("WARNING"),
    ACTION_REQUIRED("ACTION_REQUIRED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): DiagnosticSnapshotStatus {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.raw == normalized } ?: UNKNOWN
        }
    }
}
