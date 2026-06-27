package com.example.domain.repository

import com.example.core.db.entities.ContactEntity
import com.example.domain.model.contact.ContactAnalyticsProfile
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.contact.ContactEventDiscoveryProfile
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.contact.ContactHealthProfile
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.contact.ContactListItem
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.contact.ContactPreferences
import com.example.domain.model.contact.ContactWishContext
import com.example.domain.model.contact.RelationshipAnalyticsCount
import com.example.domain.model.dispatch.MessageDispatchRecipient
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAll(): Flow<List<ContactEntity>>
    fun getContactListItems(): Flow<List<ContactListItem>>
    fun getContactPickerItems(): Flow<List<ContactPickerItem>>
    fun getMessageContexts(): Flow<List<ContactMessageContext>>
    fun getAllPaged(): Flow<PagingData<ContactEntity>>
    suspend fun getAllSync(): List<ContactEntity>
    suspend fun getAnalyticsProfiles(): List<ContactAnalyticsProfile>
    suspend fun getAutomationReadinessProfiles(): List<ContactAutomationReadinessProfile>
    suspend fun getEventDiscoveryProfiles(): List<ContactEventDiscoveryProfile>
    suspend fun getHealthProfiles(): List<ContactHealthProfile>
    suspend fun getById(id: String): ContactEntity?
    suspend fun getMessageDispatchRecipient(id: String): MessageDispatchRecipient?
    suspend fun getDetailProfile(id: String): ContactDetailProfile?
    suspend fun getHeader(id: String): ContactHeader?
    suspend fun getGiftAdvisorProfile(id: String): ContactGiftAdvisorProfile?
    suspend fun getWishContext(id: String): ContactWishContext?
    suspend fun contactExists(id: String): Boolean
    suspend fun upsert(contact: ContactEntity)
    suspend fun update(contact: ContactEntity)
    suspend fun updatePreferences(preferences: ContactPreferences): Boolean
    suspend fun updateClassification(
        id: String,
        type: String,
        subtype: String?,
        lang: String,
        formality: String,
        style: String,
        confidence: Double,
    )
    suspend fun updateHealthScore(id: String, score: Int)
    suspend fun updateLastWished(id: String, timestamp: Long)
    suspend fun incrementEngagementScore(id: String, delta: Int)
    suspend fun incrementConsecutiveYearsWished(id: String)
    suspend fun getContactsForRevival(lastInteractionBeforeMs: Long): List<ContactEntity>
    suspend fun updateLastRevivalAttempt(id: String, timestampMs: Long)
    fun countAll(): Flow<Int>
    fun countByRelationshipType(): Flow<List<com.example.core.db.dao.RelationshipTypeCount>>
    fun getRelationshipAnalyticsCounts(): Flow<List<RelationshipAnalyticsCount>>
    suspend fun getTopHealthSummaries(limit: Int): List<ContactAnalyticsSummary>
    suspend fun getBottomHealthSummaries(limit: Int): List<ContactAnalyticsSummary>
    suspend fun getTopByHealthScore(limit: Int): List<ContactEntity>
    suspend fun getBottomByHealthScore(limit: Int): List<ContactEntity>
    suspend fun delete(contact: ContactEntity)
}
