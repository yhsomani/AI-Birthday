package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.SentMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SentMessageDao {
    @Query("SELECT * FROM sent_messages")
    fun getAll(): Flow<List<SentMessageEntity>>

    @Query("SELECT * FROM sent_messages WHERE contactId = :contactId ORDER BY sentAtMs DESC LIMIT :limit")
    suspend fun getByContact(contactId: String, limit: Int = 100): List<SentMessageEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sent_messages WHERE contactId = :contactId AND eventType = :eventType AND (sentAtMs > :oneYearAgo))")
    suspend fun wasWishedThisYear(contactId: String, eventType: String, oneYearAgo: Long = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000): Boolean

    @Query("SELECT m.* FROM sent_messages m INNER JOIN contacts c ON c.id = m.contactId WHERE c.primaryPhone = :phone OR c.secondaryPhone = :phone ORDER BY m.sentAtMs DESC LIMIT 1")
    suspend fun getLastSentToPhone(phone: String): SentMessageEntity?

    @Query("UPDATE sent_messages SET replyReceived = 1, replyAtMs = :replyTime WHERE id = :messageId")
    suspend fun markReplyReceived(messageId: String, replyTime: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sent_messages")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sent_messages WHERE sentAtMs > :sinceMs")
    suspend fun countRecent(sinceMs: Long): Int

    @Query("SELECT * FROM sent_messages WHERE sentAtMs > :sinceMs ORDER BY sentAtMs DESC LIMIT :limit")
    suspend fun getRecentForStyleAnalysis(sinceMs: Long, limit: Int = 100): List<SentMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SentMessageEntity)

    @Query("SELECT * FROM sent_messages")
    suspend fun getAllSync(): List<SentMessageEntity>

    @Query("SELECT * FROM sent_messages WHERE sentAtMs >= :yearStartMs ORDER BY sentAtMs ASC")
    suspend fun getSentSinceYearStart(yearStartMs: Long): List<SentMessageEntity>
}
