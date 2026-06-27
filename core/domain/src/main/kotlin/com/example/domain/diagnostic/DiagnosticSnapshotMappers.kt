package com.example.domain.diagnostic

import com.example.core.db.entities.DiagnosticSnapshotEntity
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus

fun DiagnosticSnapshotEntity.toDiagnosticSnapshot(): DiagnosticSnapshot {
    return DiagnosticSnapshot(
        id = DiagnosticSnapshotId(id),
        source = DiagnosticSnapshotSource.fromRaw(source),
        status = DiagnosticSnapshotStatus.fromRaw(status),
        summary = summary,
        checksJson = checksJson,
        createdAtMs = createdAtMs,
    )
}

fun DiagnosticSnapshot.toEntity(): DiagnosticSnapshotEntity {
    return DiagnosticSnapshotEntity(
        id = id.value,
        source = source.raw,
        status = status.raw,
        summary = summary,
        checksJson = checksJson,
        createdAtMs = createdAtMs,
    )
}
