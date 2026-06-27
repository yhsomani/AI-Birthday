package com.example.data.repository

import com.example.core.db.dao.DiagnosticSnapshotDao
import com.example.core.db.entities.DiagnosticSnapshotEntity
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticSnapshotRepositoryImplTest {

    @Test
    fun `record and latest map diagnostic snapshots through dao`() = runTest {
        val dao = RecordingDiagnosticSnapshotDao()
        val repository = DiagnosticSnapshotRepositoryImpl(dao)
        val snapshot = DiagnosticSnapshot(
            id = DiagnosticSnapshotId("snapshot_1"),
            source = DiagnosticSnapshotSource.HEALTH_MONITOR,
            status = DiagnosticSnapshotStatus.WARNING,
            summary = "HealthMonitor warning",
            checksJson = """{"recentErrors":1}""",
            createdAtMs = 1_700_000_000_000,
        )

        repository.record(snapshot)

        assertEquals(snapshot, repository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR))
        assertEquals(listOf(snapshot), repository.getRecent())
    }

    @Test
    fun `record prunes snapshots older than retention window`() = runTest {
        val dao = RecordingDiagnosticSnapshotDao()
        val repository = DiagnosticSnapshotRepositoryImpl(dao)
        val oldSnapshot = snapshot(
            id = "old_snapshot",
            createdAtMs = 1_700_000_000_000,
        )
        val newSnapshot = snapshot(
            id = "new_snapshot",
            createdAtMs = 1_700_000_000_000 + THIRTY_ONE_DAYS_MS,
        )

        repository.record(oldSnapshot)
        repository.record(newSnapshot)

        assertEquals(listOf(newSnapshot), repository.getRecent())
    }

    private fun snapshot(id: String, createdAtMs: Long): DiagnosticSnapshot {
        return DiagnosticSnapshot(
            id = DiagnosticSnapshotId(id),
            source = DiagnosticSnapshotSource.HEALTH_MONITOR,
            status = DiagnosticSnapshotStatus.WARNING,
            summary = "HealthMonitor warning",
            checksJson = "{}",
            createdAtMs = createdAtMs,
        )
    }

    private class RecordingDiagnosticSnapshotDao : DiagnosticSnapshotDao {
        private val snapshots = mutableListOf<DiagnosticSnapshotEntity>()

        override suspend fun upsert(snapshot: DiagnosticSnapshotEntity) {
            snapshots.removeAll { it.id == snapshot.id }
            snapshots += snapshot
        }

        override suspend fun getRecent(limit: Int): List<DiagnosticSnapshotEntity> {
            return snapshots.sortedByDescending { it.createdAtMs }.take(limit)
        }

        override suspend fun getLatestBySource(source: String): DiagnosticSnapshotEntity? {
            return snapshots
                .filter { it.source == source }
                .maxByOrNull { it.createdAtMs }
        }

        override suspend fun getAllSync(): List<DiagnosticSnapshotEntity> {
            return snapshots.sortedByDescending { it.createdAtMs }
        }

        override suspend fun deleteOlderThan(cutoffMs: Long) {
            snapshots.removeAll { it.createdAtMs < cutoffMs }
        }

        override suspend fun deleteAll() {
            snapshots.clear()
        }
    }

    private companion object {
        const val THIRTY_ONE_DAYS_MS = 31L * 24 * 60 * 60 * 1000
    }
}
