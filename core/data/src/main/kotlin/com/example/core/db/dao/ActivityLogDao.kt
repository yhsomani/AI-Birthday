package com.example.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.db.entities.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY createdAtMs DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE type = :type ORDER BY createdAtMs DESC LIMIT :limit")
    fun getByType(type: String, limit: Int): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE createdAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}
