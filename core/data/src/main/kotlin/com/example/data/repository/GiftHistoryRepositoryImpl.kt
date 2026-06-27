package com.example.data.repository

import com.example.core.db.dao.GiftHistoryDao
import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.gift.toEntity
import com.example.domain.gift.toRecord
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.repository.GiftHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GiftHistoryRepositoryImpl @Inject constructor(
    private val giftHistoryDao: GiftHistoryDao
) : GiftHistoryRepository {
    override suspend fun getByContact(contactId: String): List<GiftHistoryEntity> =
        giftHistoryDao.getByContact(contactId)

    override suspend fun getRecordsByContact(contactId: String): List<GiftHistoryRecord> =
        giftHistoryDao.getByContact(contactId).map { it.toRecord() }

    override suspend fun countByContact(contactId: String): Int =
        giftHistoryDao.countByContact(contactId)

    override suspend fun upsert(gift: GiftHistoryEntity) =
        giftHistoryDao.upsert(gift)

    override suspend fun upsertRecord(gift: GiftHistoryRecord) =
        giftHistoryDao.upsert(gift.toEntity())

    override suspend fun delete(gift: GiftHistoryEntity) =
        giftHistoryDao.delete(gift)

    override suspend fun deleteRecord(id: GiftHistoryId) =
        giftHistoryDao.deleteById(id.value)
}
