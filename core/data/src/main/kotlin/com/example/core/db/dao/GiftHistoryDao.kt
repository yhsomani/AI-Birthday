package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.GiftHistoryEntity

@Dao
interface GiftHistoryDao {
    @Query("SELECT * FROM gift_history WHERE contactId = :contactId ORDER BY year DESC")
    suspend fun getByContact(contactId: String): List<GiftHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gift: GiftHistoryEntity)

    @Query("SELECT * FROM gift_history")
    suspend fun getAllSync(): List<GiftHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(gift: GiftHistoryEntity)

    @Delete
    suspend fun delete(gift: GiftHistoryEntity)
}
