package com.example.domain.repository

import com.example.core.db.entities.ContactEntity
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAll(): Flow<List<ContactEntity>>
    fun getAllPaged(): Flow<PagingData<ContactEntity>>
    suspend fun getAllSync(): List<ContactEntity>
    suspend fun getById(id: String): ContactEntity?
    suspend fun upsert(contact: ContactEntity)
    suspend fun update(contact: ContactEntity)
    suspend fun updateClassification(id: String, type: String, subtype: String?, lang: String, formality: String, style: String)
    suspend fun updateHealthScore(id: String, score: Int)
    suspend fun updateLastWished(id: String, timestamp: Long)
    suspend fun incrementEngagementScore(id: String, delta: Int)
    suspend fun incrementConsecutiveYearsWished(id: String)
    fun countAll(): Flow<Int>
    fun countByRelationshipType(): Flow<List<com.example.core.db.dao.RelationshipTypeCount>>
    suspend fun getTopByHealthScore(limit: Int): List<ContactEntity>
    suspend fun getBottomByHealthScore(limit: Int): List<ContactEntity>
    suspend fun delete(contact: ContactEntity)
}
