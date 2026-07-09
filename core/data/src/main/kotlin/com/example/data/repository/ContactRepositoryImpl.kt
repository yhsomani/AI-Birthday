package com.example.data.repository

import com.example.core.db.dao.ContactDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.toAnalyticsProfile
import com.example.core.db.toAnalyticsSummary
import com.example.core.db.toAutomationReadinessProfile
import com.example.core.db.toClassificationProfile
import com.example.core.db.toDetailProfile
import com.example.core.db.toEventDiscoveryProfile
import com.example.core.db.toGiftAdvisorProfile
import com.example.core.db.toHealthProfile
import com.example.core.db.toHeader
import com.example.core.db.toListItem
import com.example.core.db.toMessageContext
import com.example.core.db.toMessageDispatchRecipient
import com.example.core.db.toMessageGenerationProfile
import com.example.core.db.toPickerItem
import com.example.core.db.toRelationshipAnalyticsCount
import com.example.core.db.toWishContext
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
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
import com.example.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getContactListItems(): Flow<List<ContactListItem>> {
        return contactDao.getAll().map { contacts ->
            contacts.map { it.toListItem() }
        }
    }

    override fun getContactPickerItems(): Flow<List<ContactPickerItem>> {
        return contactDao.getAll().map { contacts ->
            contacts.map { it.toPickerItem() }
        }
    }

    override fun getMessageContexts(): Flow<List<ContactMessageContext>> {
        return contactDao.getAll().map { contacts ->
            contacts.map { it.toMessageContext() }
        }
    }

    override suspend fun getAnalyticsProfiles(): List<ContactAnalyticsProfile> {
        return contactDao.getAllSync().map { it.toAnalyticsProfile() }
    }

    override fun getAnalyticsProfilesFlow(): Flow<List<ContactAnalyticsProfile>> {
        return contactDao.getAll().map { contacts ->
            contacts.map { it.toAnalyticsProfile() }
        }
    }

    override suspend fun getAutomationReadinessProfiles(): List<ContactAutomationReadinessProfile> {
        return contactDao.getAllSync().map { it.toAutomationReadinessProfile() }
    }

    override fun getAutomationReadinessProfilesFlow(): Flow<List<ContactAutomationReadinessProfile>> {
        return contactDao.getAll().map { contacts ->
            contacts.map { it.toAutomationReadinessProfile() }
        }
    }

    override suspend fun getEventDiscoveryProfiles(): List<ContactEventDiscoveryProfile> {
        return contactDao.getAllSync().map { it.toEventDiscoveryProfile() }
    }

    override suspend fun getClassificationProfile(id: String): ContactClassificationProfile? {
        return contactDao.getById(id)?.toClassificationProfile()
    }

    override suspend fun getUnclassifiedContactIds(): List<ContactId> {
        return contactDao.getAllSync()
            .filter { contact ->
                contact.relationshipType.isBlank() || contact.relationshipType == "UNKNOWN"
            }
            .map { contact -> ContactId(contact.id) }
    }

    override suspend fun getHealthProfiles(): List<ContactHealthProfile> {
        return contactDao.getAllSync().map { it.toHealthProfile() }
    }

    override fun getHealthProfilesFlow(): Flow<List<ContactHealthProfile>> {
        return contactDao.getAll().map { contacts ->
            contacts.map { it.toHealthProfile() }
        }
    }

    override suspend fun getMessageGenerationProfile(id: String): ContactMessageGenerationProfile? {
        return contactDao.getById(id)?.toMessageGenerationProfile()
    }

    override suspend fun getMessageDispatchRecipient(id: String): MessageDispatchRecipient? {
        return contactDao.getById(id)?.toMessageDispatchRecipient()
    }

    override suspend fun getDetailProfile(id: String): ContactDetailProfile? {
        return contactDao.getById(id)?.toDetailProfile()
    }

    override fun getDetailProfileFlow(id: String): Flow<ContactDetailProfile?> {
        return contactDao.getByIdFlow(id).map { contact -> contact?.toDetailProfile() }
    }

    override suspend fun getHeader(id: String): ContactHeader? {
        return contactDao.getById(id)?.toHeader()
    }

    override fun getHeaderFlow(id: String): Flow<ContactHeader?> {
        return contactDao.getByIdFlow(id).map { contact -> contact?.toHeader() }
    }

    override suspend fun getGiftAdvisorProfile(id: String): ContactGiftAdvisorProfile? {
        return contactDao.getById(id)?.toGiftAdvisorProfile()
    }

    override fun getGiftAdvisorProfileFlow(id: String): Flow<ContactGiftAdvisorProfile?> {
        return contactDao.getByIdFlow(id).map { contact -> contact?.toGiftAdvisorProfile() }
    }

    override suspend fun getWishContext(id: String): ContactWishContext? {
        return contactDao.getById(id)?.toWishContext()
    }

    override fun getWishContextFlow(id: String): Flow<ContactWishContext?> {
        return contactDao.getByIdFlow(id).map { contact -> contact?.toWishContext() }
    }

    override suspend fun contactExists(id: String): Boolean = contactDao.getById(id) != null

    override suspend fun upsertSyncedContact(contact: ContactSyncRecord) {
        contactDao.upsert(contact.toEntity())
    }

    override suspend fun updateAutomationOverride(
        id: ContactId,
        automationMode: ApprovalMode,
        skipAutoWish: Boolean,
        updatedAt: Long,
    ) = contactDao.updateAutomationOverride(
        id = id.value,
        automationMode = automationMode.raw,
        skipAutoWish = skipAutoWish,
        updatedAt = updatedAt,
    )

    override suspend fun createManualContactForEvent(
        id: ContactId,
        displayName: String,
        eventType: OccasionType,
        day: Int,
        month: Int,
        year: Int?,
        createdAt: Long,
    ) {
        val contact = ContactEntity(
            id = id.value,
            name = displayName,
            contactGroup = "Manual",
            relationshipType = "UNKNOWN",
            preferredChannel = MessageChannel.SMS.raw,
            createdAt = createdAt,
            updatedAt = createdAt,
        ).withEventDate(
            eventType = eventType,
            day = day,
            month = month,
            year = year,
            updatedAt = createdAt,
        )
        contactDao.upsert(contact)
    }

    override suspend fun updateContactEventDate(
        id: ContactId,
        eventType: OccasionType,
        day: Int,
        month: Int,
        year: Int?,
        updatedAt: Long,
    ) {
        contactDao.updateContactEventDate(
            id = id.value,
            eventType = eventType,
            day = day,
            month = month,
            year = year,
            updatedAt = updatedAt,
        )
    }

    override suspend fun updatePreferences(preferences: ContactPreferences): Boolean {
        val contact = contactDao.getById(preferences.contactId.value) ?: return false
        contactDao.update(
            contact.copy(
                nickname = preferences.nickname,
                relationshipType = preferences.relationshipType,
                preferredLanguage = preferences.preferredLanguage,
                preferredChannel = preferences.preferredChannel.raw,
                formalityLevel = preferences.formalityLevel,
                communicationStyle = preferences.communicationStyle,
                automationMode = preferences.automationMode.raw,
                giftBudgetInr = preferences.giftBudgetInr,
                annualBudgetInr = preferences.annualBudgetInr,
                skipAutoWish = preferences.skipAutoWish,
                customSendTimeHour = preferences.customSendTimeHour,
                customSendTimeMinute = preferences.customSendTimeMinute,
                interestsJson = preferences.interestsJson,
                sensitiveTopicsJson = preferences.sensitiveTopicsJson,
                currentLifePhaseJson = preferences.currentLifePhaseJson,
                notesText = preferences.notesText,
                updatedAt = preferences.updatedAtMs,
            ),
        )
        return true
    }

    override suspend fun updateClassification(
        id: String,
        type: String,
        subtype: String?,
        lang: String,
        formality: String,
        style: String,
        confidence: Double,
    ) = contactDao.updateClassification(id, type, subtype, lang, formality, style, confidence)

    override suspend fun updateHealthScore(id: String, score: Int) = contactDao.updateHealthScore(id, score)

    override suspend fun updateLastWished(id: String, timestamp: Long) = contactDao.updateLastWished(id, timestamp)

    override suspend fun incrementEngagementScore(id: String, delta: Int) = contactDao.incrementEngagementScore(id, delta)

    override suspend fun incrementConsecutiveYearsWished(id: String) = contactDao.incrementConsecutiveYearsWished(id)

    override suspend fun updateLastRevivalAttempt(id: String, timestampMs: Long) =
        contactDao.updateLastRevivalAttempt(id, timestampMs)

    override fun countAll(): Flow<Int> = contactDao.countAll()

    override fun getRelationshipAnalyticsCounts(): Flow<List<RelationshipAnalyticsCount>> {
        return contactDao.countByRelationshipType().map { counts ->
            counts.map { it.toRelationshipAnalyticsCount() }
        }
    }

    override suspend fun getTopHealthSummaries(limit: Int): List<ContactAnalyticsSummary> {
        return contactDao.getTopByHealthScore(limit).map { it.toAnalyticsSummary() }
    }

    override fun getTopHealthSummariesFlow(limit: Int): Flow<List<ContactAnalyticsSummary>> {
        return contactDao.getAll().map { contacts ->
            contacts
                .sortedByDescending { it.healthScore }
                .take(limit)
                .map { it.toAnalyticsSummary() }
        }
    }

    override suspend fun getBottomHealthSummaries(limit: Int): List<ContactAnalyticsSummary> {
        return contactDao.getBottomByHealthScore(limit).map { it.toAnalyticsSummary() }
    }

    override fun getBottomHealthSummariesFlow(limit: Int): Flow<List<ContactAnalyticsSummary>> {
        return contactDao.getAll().map { contacts ->
            contacts
                .sortedBy { it.healthScore }
                .take(limit)
                .map { it.toAnalyticsSummary() }
            }
    }
}
