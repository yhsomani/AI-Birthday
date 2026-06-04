package com.example.data.repository

import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.repository.MemoryNoteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryNoteRepositoryImpl @Inject constructor(
    private val memoryNoteDao: MemoryNoteDao
) : MemoryNoteRepository {
    override suspend fun getByContact(contactId: String): List<MemoryNoteEntity> =
        memoryNoteDao.getByContact(contactId)

    override suspend fun upsert(note: MemoryNoteEntity) =
        memoryNoteDao.upsert(note)

    override suspend fun delete(note: MemoryNoteEntity) =
        memoryNoteDao.delete(note)
}
