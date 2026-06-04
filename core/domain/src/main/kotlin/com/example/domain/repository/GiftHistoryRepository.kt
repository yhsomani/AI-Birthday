package com.example.domain.repository

import com.example.core.db.entities.GiftHistoryEntity

interface GiftHistoryRepository {
    suspend fun getByContact(contactId: String): List<GiftHistoryEntity>
    suspend fun upsert(gift: GiftHistoryEntity)
    suspend fun delete(gift: GiftHistoryEntity)
}
