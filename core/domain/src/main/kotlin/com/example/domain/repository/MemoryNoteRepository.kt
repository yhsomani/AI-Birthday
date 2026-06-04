package com.example.domain.repository

import com.example.core.db.entities.MemoryNoteEntity

interface MemoryNoteRepository {
    suspend fun getByContact(contactId: String): List<MemoryNoteEntity>
    suspend fun upsert(note: MemoryNoteEntity)
    suspend fun delete(note: MemoryNoteEntity)
}
