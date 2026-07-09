package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE isArchived = 0 AND isDeleted = 0")
    fun getAll(): Flow<List<ContactEntity>>
    
    @Query("SELECT * FROM contacts WHERE isArchived = 0 AND isDeleted = 0")
    suspend fun getAllSync(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id IN (:ids) AND isArchived = 0 AND isDeleted = 0")
    suspend fun getByIds(ids: List<String>): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id AND isDeleted = 0")
    fun getByIdFlow(id: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)
    
    @Query("UPDATE contacts SET relationshipType = :type, relationshipSubtype = :subtype, preferredLanguage = :lang, formalityLevel = :formality, communicationStyle = :style, classificationConfidence = :confidence WHERE id = :id")
    suspend fun updateClassification(id: String, type: String, subtype: String?, lang: String, formality: String, style: String, confidence: Double)

    @Query("UPDATE contacts SET classificationConfidence = :confidence WHERE id = :id")
    suspend fun updateClassificationConfidence(id: String, confidence: Double)

    @Query("UPDATE contacts SET automationMode = :automationMode, skipAutoWish = :skipAutoWish, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAutomationOverride(
        id: String,
        automationMode: String,
        skipAutoWish: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE contacts SET birthdayDay = :day, birthdayMonth = :month, birthdayYear = :year, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBirthdayDate(id: String, day: Int, month: Int, year: Int?, updatedAt: Long)

    @Query("UPDATE contacts SET anniversaryDay = :day, anniversaryMonth = :month, anniversaryYear = :year, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAnniversaryDate(id: String, day: Int, month: Int, year: Int?, updatedAt: Long)

    @Query("UPDATE contacts SET workStartDay = :day, workStartMonth = :month, workStartYear = :year, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWorkStartDate(id: String, day: Int, month: Int, year: Int?, updatedAt: Long)

    @Query("UPDATE contacts SET healthScore = :score WHERE id = :id")
    suspend fun updateHealthScore(id: String, score: Int)

    @Query("UPDATE contacts SET healthScore = MIN(100, MAX(0, healthScore + :delta)) WHERE id = :id")
    suspend fun updateHealthScoreDelta(id: String, delta: Int)

    @Query("UPDATE contacts SET lastWishedDate = :timestamp WHERE id = :id")
    suspend fun updateLastWished(id: String, timestamp: Long)

    @Query("UPDATE contacts SET engagementScore = engagementScore + :delta WHERE id = :id")
    suspend fun incrementEngagementScore(id: String, delta: Int)

    @Query("UPDATE contacts SET consecutiveYearsWished = consecutiveYearsWished + 1 WHERE id = :id")
    suspend fun incrementConsecutiveYearsWished(id: String)

    @Query("SELECT COUNT(*) FROM contacts WHERE isArchived = 0 AND isDeleted = 0")
    fun countAll(): Flow<Int>

    @Query("SELECT relationshipType, COUNT(*) as count FROM contacts WHERE isArchived = 0 AND isDeleted = 0 GROUP BY relationshipType")
    fun countByRelationshipType(): Flow<List<RelationshipTypeCount>>

    @Query("SELECT * FROM contacts WHERE isArchived = 0 AND isDeleted = 0 ORDER BY healthScore DESC LIMIT :limit")
    suspend fun getTopByHealthScore(limit: Int): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE isArchived = 0 AND isDeleted = 0 ORDER BY healthScore ASC LIMIT :limit")
    suspend fun getBottomByHealthScore(limit: Int): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE isArchived = 0 AND isDeleted = 0 AND healthScore < 40 AND lastRevivalAttemptMs < :thirtyDaysAgoMs ORDER BY healthScore ASC LIMIT 5")
    suspend fun getContactsForRevival(thirtyDaysAgoMs: Long): List<ContactEntity>

    @Query("UPDATE contacts SET lastRevivalAttemptMs = :timestamp WHERE id = :id")
    suspend fun updateLastRevivalAttempt(id: String, timestamp: Long)

    @Query("UPDATE contacts SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(contact: ContactEntity)
}
