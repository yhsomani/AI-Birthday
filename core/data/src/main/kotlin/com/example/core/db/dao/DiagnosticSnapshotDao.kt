package com.example.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.db.entities.DiagnosticSnapshotEntity

@Dao
interface DiagnosticSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DiagnosticSnapshotEntity)

    @Query("SELECT * FROM diagnostic_snapshots ORDER BY createdAtMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<DiagnosticSnapshotEntity>

    @Query("SELECT * FROM diagnostic_snapshots WHERE source = :source ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getLatestBySource(source: String): DiagnosticSnapshotEntity?

    @Query("SELECT * FROM diagnostic_snapshots ORDER BY createdAtMs DESC")
    suspend fun getAllSync(): List<DiagnosticSnapshotEntity>

    @Query("DELETE FROM diagnostic_snapshots WHERE createdAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM diagnostic_snapshots")
    suspend fun deleteAll()
}
