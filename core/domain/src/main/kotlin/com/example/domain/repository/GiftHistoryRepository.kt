package com.example.domain.repository

import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.gift.GiftHistoryRecord
import kotlinx.coroutines.flow.Flow

interface GiftHistoryRepository {
    suspend fun getByContact(contactId: String): List<GiftHistoryEntity>
    suspend fun getRecordsByContact(contactId: String): List<GiftHistoryRecord>
    fun getRecordsByContactFlow(contactId: String): Flow<List<GiftHistoryRecord>>
    suspend fun countByContact(contactId: String): Int
    fun countByContactFlow(contactId: String): Flow<Int>
    suspend fun upsert(gift: GiftHistoryEntity)
    suspend fun upsertRecord(gift: GiftHistoryRecord)
    suspend fun delete(gift: GiftHistoryEntity)
    suspend fun deleteRecord(id: GiftHistoryId)
}
