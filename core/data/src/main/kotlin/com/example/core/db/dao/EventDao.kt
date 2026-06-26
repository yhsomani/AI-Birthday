package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE isActive = 1")
    fun getAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EventEntity?

    @Query("SELECT COUNT(*) FROM events WHERE isActive = 1")
    fun countAll(): Flow<Int>

    @Query("SELECT * FROM events WHERE nextOccurrenceMs <= :timeMs AND isActive = 1")
    suspend fun getEventsBefore(timeMs: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE isActive = 1 AND nextOccurrenceMs >= :nowMs AND CAST((nextOccurrenceMs - :nowMs) / 86400000 AS INTEGER) <= :days ORDER BY nextOccurrenceMs ASC")
    suspend fun getUpcoming(days: Int, nowMs: Long): List<EventEntity>

    @Query("UPDATE events SET isActive = 0 WHERE contactId = :contactId AND type = :type")
    suspend fun deactivateEventsForContact(contactId: String, type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("SELECT * FROM events")
    suspend fun getAllSync(): List<EventEntity>

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(event: EventEntity)
}
