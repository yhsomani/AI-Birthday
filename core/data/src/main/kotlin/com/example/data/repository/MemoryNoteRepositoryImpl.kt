package com.example.data.repository

import com.example.core.db.dao.MemoryNoteDao
import com.example.domain.memory.toEntity
import com.example.domain.memory.toRecord
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.domain.model.memory.MemoryNoteSummary
import com.example.domain.repository.MemoryNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryNoteRepositoryImpl @Inject constructor(
    private val memoryNoteDao: MemoryNoteDao
) : MemoryNoteRepository {
    override suspend fun getRecordsByContact(contactId: String): List<MemoryNoteRecord> =
        memoryNoteDao.getByContact(contactId).map { it.toRecord() }

    override fun getRecordsByContactFlow(contactId: String): Flow<List<MemoryNoteRecord>> {
        return memoryNoteDao.getByContactFlow(contactId).map { notes ->
            notes.map { it.toRecord() }
        }
    }

    override suspend fun getSummaryForContact(contactId: String): MemoryNoteSummary {
        val categoryCounts = memoryNoteDao.getCategoryCountsForContact(contactId).map { row ->
            MemoryNoteCategoryCount(
                category = row.category,
                count = row.count.toInt(),
            )
        }
        return categoryCounts.toSummary()
    }

    override fun getSummaryForContactFlow(contactId: String): Flow<MemoryNoteSummary> {
        return memoryNoteDao.getCategoryCountsForContactFlow(contactId).map { rows ->
            rows.map { row ->
                MemoryNoteCategoryCount(
                    category = row.category,
                    count = row.count.toInt(),
                )
            }.toSummary()
        }
    }

    override suspend fun countByContact(contactId: String): Int =
        memoryNoteDao.countByContact(contactId)

    override fun countByContactFlow(contactId: String): Flow<Int> =
        memoryNoteDao.countByContactFlow(contactId)

    override suspend fun upsertRecord(note: MemoryNoteRecord) =
        memoryNoteDao.upsert(note.toEntity())

    override suspend fun deleteRecord(id: MemoryNoteId) =
        memoryNoteDao.deleteById(id.value)

    private fun List<MemoryNoteCategoryCount>.toSummary(): MemoryNoteSummary {
        return MemoryNoteSummary(
            totalCount = sumOf { it.count },
            categoryCounts = this,
        )
    }
}
