package com.example.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.db.entities.MessageFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageFeedbackDao {
    @Query("SELECT * FROM message_feedback ORDER BY createdAtMs DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<MessageFeedbackEntity>>

    @Query("SELECT * FROM message_feedback WHERE pendingMessageId = :pendingMessageId ORDER BY createdAtMs DESC")
    suspend fun getByPendingMessage(pendingMessageId: String): List<MessageFeedbackEntity>

    @Query("SELECT * FROM message_feedback WHERE pendingMessageId = :pendingMessageId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getLatestForPendingMessage(pendingMessageId: String): MessageFeedbackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: MessageFeedbackEntity)

    @Query("UPDATE message_feedback SET appliedToRegeneration = 1 WHERE id = :id")
    suspend fun markApplied(id: String)
}
