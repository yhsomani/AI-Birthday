package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.StyleProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StyleProfileDao {
    @Query("SELECT * FROM style_profile WHERE id = 1")
    fun getFlow(): Flow<StyleProfileEntity?>

    @Query("SELECT * FROM style_profile WHERE id = 1")
    suspend fun get(): StyleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: StyleProfileEntity)
}
