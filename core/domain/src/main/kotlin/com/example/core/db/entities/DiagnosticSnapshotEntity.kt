package com.example.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus

@Entity(
    tableName = "diagnostic_snapshots",
    indices = [
        Index(value = ["source", "createdAtMs"], name = "idx_diagnostic_snapshots_source_createdAtMs"),
        Index(value = ["createdAtMs"], name = "idx_diagnostic_snapshots_createdAtMs"),
    ],
)
data class DiagnosticSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "'AI_DOCTOR'") val source: String = DiagnosticSnapshotSource.AI_DOCTOR.raw,
    @ColumnInfo(defaultValue = "'OK'") val status: String = DiagnosticSnapshotStatus.OK.raw,
    val summary: String,
    @ColumnInfo(defaultValue = "'{}'") val checksJson: String = "{}",
    val createdAtMs: Long = System.currentTimeMillis(),
)
