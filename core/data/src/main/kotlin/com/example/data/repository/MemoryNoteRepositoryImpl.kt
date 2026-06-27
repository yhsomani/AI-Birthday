package com.example.data.repository

import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.memory.toEntity
import com.example.domain.memory.toRecord
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.domain.model.memory.MemoryNoteSummary
import com.example.domain.repository.MemoryNoteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryNoteRepositoryImpl @Inject constructor(
    private val memoryNoteDao: MemoryNoteDao
) : MemoryNoteRepository {
    override suspend fun getByContact(contactId: String): List<MemoryNoteEntity> =
        memoryNoteDao.getByContact(contactId)

    override suspend fun getRecordsByContact(contactId: String): List<MemoryNoteRecord> =
        memoryNoteDao.getByContact(contactId).map { it.toRecord() }

    override suspend fun getSummaryForContact(contactId: String): MemoryNoteSummary {
        val categoryCounts = memoryNoteDao.getCategoryCountsForContact(contactId).map { row ->
            MemoryNoteCategoryCount(
                category = row.category,
                count = row.count.toInt(),
            )
        }
        return MemoryNoteSummary(
            totalCount = categoryCounts.sumOf { it.count },
            categoryCounts = categoryCounts,
        )
    }

    override suspend fun countByContact(contactId: String): Int =
        memoryNoteDao.countByContact(contactId)

    override suspend fun upsert(note: MemoryNoteEntity) =
        memoryNoteDao.upsert(note)

    override suspend fun upsertRecord(note: MemoryNoteRecord) =
        memoryNoteDao.upsert(note.toEntity())

    override suspend fun delete(note: MemoryNoteEntity) =
        memoryNoteDao.delete(note)

    override suspend fun deleteRecord(id: MemoryNoteId) =
        memoryNoteDao.deleteById(id.value)
}
