package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.MoodLogEntity

@Dao
interface MoodLogDao {
    @Query("SELECT * FROM mood_logs WHERE contactId = :contactId ORDER BY timestamp DESC")
    suspend fun getLogsForContact(contactId: String): List<MoodLogEntity>

    @Query("SELECT * FROM mood_logs WHERE contactId = :contactId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMood(contactId: String): MoodLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoodLogEntity)

    @Query("DELETE FROM mood_logs WHERE contactId = :contactId")
    suspend fun deleteLogsForContact(contactId: String)
}
