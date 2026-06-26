package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.MemoryNoteEntity

@Dao
interface MemoryNoteDao {
    @Query("SELECT * FROM memory_notes WHERE contactId = :contactId ORDER BY isPinned DESC, dateMs DESC")
    suspend fun getByContact(contactId: String): List<MemoryNoteEntity>

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
}
