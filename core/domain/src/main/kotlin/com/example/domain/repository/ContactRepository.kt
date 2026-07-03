package com.example.domain.repository

import com.example.domain.model.ApprovalMode
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactAnalyticsProfile
import com.example.domain.model.contact.ContactAnalyticsSummary
import com.example.domain.model.contact.ContactAutomationReadinessProfile
import com.example.domain.model.contact.ContactClassificationProfile
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.contact.ContactEventDiscoveryProfile
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.contact.ContactHealthProfile
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.contact.ContactListItem
import com.example.domain.model.contact.ContactMessageGenerationProfile
import com.example.domain.model.contact.ContactMessageContext
import com.example.domain.model.contact.ContactPickerItem
import com.example.domain.model.contact.ContactPreferences
import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.model.contact.ContactWishContext
import com.example.domain.model.contact.RelationshipAnalyticsCount
import com.example.domain.model.dispatch.MessageDispatchRecipient
import com.example.domain.model.occasion.OccasionType
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getContactListItems(): Flow<List<ContactListItem>>
    fun getContactPickerItems(): Flow<List<ContactPickerItem>>
    fun getMessageContexts(): Flow<List<ContactMessageContext>>
    suspend fun getAnalyticsProfiles(): List<ContactAnalyticsProfile>
    fun getAnalyticsProfilesFlow(): Flow<List<ContactAnalyticsProfile>>
    suspend fun getAutomationReadinessProfiles(): List<ContactAutomationReadinessProfile>
    fun getAutomationReadinessProfilesFlow(): Flow<List<ContactAutomationReadinessProfile>>
    suspend fun getEventDiscoveryProfiles(): List<ContactEventDiscoveryProfile>
    suspend fun getClassificationProfile(id: String): ContactClassificationProfile?
    suspend fun getUnclassifiedContactIds(): List<ContactId>
    suspend fun getHealthProfiles(): List<ContactHealthProfile>
    fun getHealthProfilesFlow(): Flow<List<ContactHealthProfile>>
    suspend fun getMessageGenerationProfile(id: String): ContactMessageGenerationProfile?
    suspend fun getMessageDispatchRecipient(id: String): MessageDispatchRecipient?
    suspend fun getDetailProfile(id: String): ContactDetailProfile?
    fun getDetailProfileFlow(id: String): Flow<ContactDetailProfile?>
    suspend fun getHeader(id: String): ContactHeader?
    fun getHeaderFlow(id: String): Flow<ContactHeader?>
    suspend fun getGiftAdvisorProfile(id: String): ContactGiftAdvisorProfile?
    fun getGiftAdvisorProfileFlow(id: String): Flow<ContactGiftAdvisorProfile?>
    suspend fun getWishContext(id: String): ContactWishContext?
    fun getWishContextFlow(id: String): Flow<ContactWishContext?>
    suspend fun contactExists(id: String): Boolean
    suspend fun upsertSyncedContact(contact: ContactSyncRecord)
    suspend fun updateAutomationOverride(
        id: ContactId,
        automationMode: ApprovalMode,
        skipAutoWish: Boolean,
        updatedAt: Long,
    )
    suspend fun createManualContactForEvent(
        id: ContactId,
        displayName: String,
        eventType: OccasionType,
        day: Int,
        month: Int,
        year: Int?,
        createdAt: Long,
    )
    suspend fun updateContactEventDate(
        id: ContactId,
        eventType: OccasionType,
        day: Int,
        month: Int,
        year: Int?,
        updatedAt: Long,
    )
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
    suspend fun updateLastRevivalAttempt(id: String, timestampMs: Long)
    fun countAll(): Flow<Int>
    fun getRelationshipAnalyticsCounts(): Flow<List<RelationshipAnalyticsCount>>
    suspend fun getTopHealthSummaries(limit: Int): List<ContactAnalyticsSummary>
    fun getTopHealthSummariesFlow(limit: Int): Flow<List<ContactAnalyticsSummary>>
    suspend fun getBottomHealthSummaries(limit: Int): List<ContactAnalyticsSummary>
    fun getBottomHealthSummariesFlow(limit: Int): Flow<List<ContactAnalyticsSummary>>
}
