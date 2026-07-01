package com.example.data.repository

import com.example.core.db.dao.GiftHistoryDao
import com.example.domain.gift.toEntity
import com.example.domain.gift.toRecord
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.repository.GiftHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GiftHistoryRepositoryImpl @Inject constructor(
    private val giftHistoryDao: GiftHistoryDao
) : GiftHistoryRepository {
    override suspend fun getRecordsByContact(contactId: String): List<GiftHistoryRecord> =
        giftHistoryDao.getByContact(contactId).map { it.toRecord() }

    override fun getRecordsByContactFlow(contactId: String): Flow<List<GiftHistoryRecord>> {
        return giftHistoryDao.getByContactFlow(contactId).map { gifts ->
            gifts.map { it.toRecord() }
        }
    }

    override suspend fun countByContact(contactId: String): Int =
        giftHistoryDao.countByContact(contactId)

    override fun countByContactFlow(contactId: String): Flow<Int> =
        giftHistoryDao.countByContactFlow(contactId)

    override suspend fun upsertRecord(gift: GiftHistoryRecord) =
        giftHistoryDao.upsert(gift.toEntity())

    override suspend fun deleteRecord(id: GiftHistoryId) =
        giftHistoryDao.deleteById(id.value)
}
