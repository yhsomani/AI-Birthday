package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.MemoryNoteEntity
import kotlinx.coroutines.flow.Flow

data class MemoryNoteCategoryCountRow(
    val category: String,
    val count: Long,
)

@Dao
interface MemoryNoteDao {
    @Query("SELECT * FROM memory_notes WHERE contactId = :contactId ORDER BY isPinned DESC, dateMs DESC")
    suspend fun getByContact(contactId: String): List<MemoryNoteEntity>

    @Query("SELECT * FROM memory_notes WHERE contactId = :contactId ORDER BY isPinned DESC, dateMs DESC")
    fun getByContactFlow(contactId: String): Flow<List<MemoryNoteEntity>>

    @Query("SELECT category AS category, COUNT(*) AS count FROM memory_notes WHERE contactId = :contactId GROUP BY category ORDER BY count DESC, category ASC")
    suspend fun getCategoryCountsForContact(contactId: String): List<MemoryNoteCategoryCountRow>

    @Query("SELECT category AS category, COUNT(*) AS count FROM memory_notes WHERE contactId = :contactId GROUP BY category ORDER BY count DESC, category ASC")
    fun getCategoryCountsForContactFlow(contactId: String): Flow<List<MemoryNoteCategoryCountRow>>

    @Query("SELECT COUNT(*) FROM memory_notes WHERE contactId = :contactId")
    suspend fun countByContact(contactId: String): Int

    @Query("SELECT COUNT(*) FROM memory_notes WHERE contactId = :contactId")
    fun countByContactFlow(contactId: String): Flow<Int>

    @Query("SELECT * FROM memory_notes WHERE contactId = :contactId AND isPinned = 1 ORDER BY dateMs DESC")
    suspend fun getPinnedForContact(contactId: String): List<MemoryNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: MemoryNoteEntity)

    @Query("SELECT * FROM memory_notes")
    suspend fun getAllSync(): List<MemoryNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: MemoryNoteEntity)

    @Query("DELETE FROM memory_notes")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(note: MemoryNoteEntity)

    @Query("DELETE FROM memory_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
