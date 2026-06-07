package com.example.data.repository

import com.example.core.db.dao.ContactDao
import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getAll(): Flow<List<ContactEntity>> = contactDao.getAll()

    override fun getAllPaged(): Flow<PagingData<ContactEntity>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { contactDao.getAllPaged() }
    ).flow

    override suspend fun getAllSync(): List<ContactEntity> = contactDao.getAllSync()

    override suspend fun getById(id: String): ContactEntity? = contactDao.getById(id)

    override suspend fun upsert(contact: ContactEntity) = contactDao.upsert(contact)

    override suspend fun update(contact: ContactEntity) = contactDao.update(contact)

    override suspend fun updateClassification(id: String, type: String, subtype: String?, lang: String, formality: String, style: String) =
        contactDao.updateClassification(id, type, subtype, lang, formality, style, 1.0)

    override suspend fun updateHealthScore(id: String, score: Int) = contactDao.updateHealthScore(id, score)

    override suspend fun updateLastWished(id: String, timestamp: Long) = contactDao.updateLastWished(id, timestamp)

    override suspend fun incrementEngagementScore(id: String, delta: Int) = contactDao.incrementEngagementScore(id, delta)

    override suspend fun incrementConsecutiveYearsWished(id: String) = contactDao.incrementConsecutiveYearsWished(id)

    override suspend fun getContactsForRevival(lastInteractionBeforeMs: Long): List<ContactEntity> =
        contactDao.getContactsForRevival(lastInteractionBeforeMs)

    override suspend fun updateLastRevivalAttempt(id: String, timestampMs: Long) =
        contactDao.updateLastRevivalAttempt(id, timestampMs)

    override fun countAll(): Flow<Int> = contactDao.countAll()

    override fun countByRelationshipType(): Flow<List<com.example.core.db.dao.RelationshipTypeCount>> = contactDao.countByRelationshipType()

    override suspend fun getTopByHealthScore(limit: Int): List<ContactEntity> = contactDao.getTopByHealthScore(limit)

    override suspend fun getBottomByHealthScore(limit: Int): List<ContactEntity> = contactDao.getBottomByHealthScore(limit)

    override suspend fun delete(contact: ContactEntity) = contactDao.delete(contact)
}
