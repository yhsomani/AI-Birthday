package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE isActive = 1")
    fun getAll(): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events WHERE isActive = 1")
    fun countAll(): Flow<Int>

    @Query("SELECT * FROM events WHERE nextOccurrenceMs <= :timeMs AND isActive = 1")
    suspend fun getEventsBefore(timeMs: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE isActive = 1 AND daysUntil <= :days ORDER BY daysUntil ASC")
    suspend fun getUpcoming(days: Int = 30): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("SELECT * FROM events")
    suspend fun getAllSync(): List<EventEntity>

    @Delete
    suspend fun delete(event: EventEntity)
}
