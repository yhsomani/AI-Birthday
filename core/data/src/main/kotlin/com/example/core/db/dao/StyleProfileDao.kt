package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StyleProfileDao {
    @Query("SELECT * FROM style_profile WHERE id = 1")
    fun getFlow(): Flow<StyleProfileEntity?>

    @Query("SELECT * FROM style_profile WHERE id = 1")
    suspend fun get(): StyleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: StyleProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StyleProfileHistoryEntity)

    @Query("SELECT * FROM style_profile_history ORDER BY savedAtMs DESC")
    suspend fun getHistory(): List<StyleProfileHistoryEntity>

    @Query("DELETE FROM style_profile_history WHERE id NOT IN (SELECT id FROM style_profile_history ORDER BY savedAtMs DESC LIMIT 3)")
    suspend fun deleteOldHistory()

    @Transaction
    suspend fun upsertWithHistory(profile: StyleProfileEntity, history: StyleProfileHistoryEntity) {
        upsert(profile)
        insertHistory(history)
        deleteOldHistory()
    }
}
