package com.example.domain.repository

import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.memory.MemoryNoteSummary
import kotlinx.coroutines.flow.Flow

interface MemoryNoteRepository {
    suspend fun getByContact(contactId: String): List<MemoryNoteEntity>
    suspend fun getRecordsByContact(contactId: String): List<MemoryNoteRecord>
    fun getRecordsByContactFlow(contactId: String): Flow<List<MemoryNoteRecord>>
    suspend fun getSummaryForContact(contactId: String): MemoryNoteSummary
    fun getSummaryForContactFlow(contactId: String): Flow<MemoryNoteSummary>
    suspend fun countByContact(contactId: String): Int
    fun countByContactFlow(contactId: String): Flow<Int>
    suspend fun upsert(note: MemoryNoteEntity)
    suspend fun upsertRecord(note: MemoryNoteRecord)
    suspend fun delete(note: MemoryNoteEntity)
    suspend fun deleteRecord(id: MemoryNoteId)
}
