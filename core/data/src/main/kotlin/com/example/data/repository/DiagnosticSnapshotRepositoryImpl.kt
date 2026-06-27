package com.example.data.repository

import com.example.core.db.dao.DiagnosticSnapshotDao
import com.example.domain.diagnostic.toDiagnosticSnapshot
import com.example.domain.diagnostic.toEntity
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.repository.DiagnosticSnapshotRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticSnapshotRepositoryImpl @Inject constructor(
    private val diagnosticSnapshotDao: DiagnosticSnapshotDao,
) : DiagnosticSnapshotRepository {
    override suspend fun record(snapshot: DiagnosticSnapshot) {
        diagnosticSnapshotDao.upsert(snapshot.toEntity())
        diagnosticSnapshotDao.deleteOlderThan(snapshot.createdAtMs - SNAPSHOT_RETENTION_MS)
    }

    override suspend fun getRecent(limit: Int): List<DiagnosticSnapshot> {
        return diagnosticSnapshotDao.getRecent(limit).map { it.toDiagnosticSnapshot() }
    }

    override suspend fun getLatestBySource(source: DiagnosticSnapshotSource): DiagnosticSnapshot? {
        return diagnosticSnapshotDao.getLatestBySource(source.raw)?.toDiagnosticSnapshot()
    }

    private companion object {
        const val SNAPSHOT_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
