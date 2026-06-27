package com.example.domain.repository

import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource

interface DiagnosticSnapshotRepository {
    suspend fun record(snapshot: DiagnosticSnapshot)

    suspend fun getRecent(limit: Int = 20): List<DiagnosticSnapshot>

    suspend fun getLatestBySource(source: DiagnosticSnapshotSource): DiagnosticSnapshot?
}
