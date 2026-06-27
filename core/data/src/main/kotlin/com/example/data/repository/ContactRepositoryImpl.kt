package com.example.data.repository

import com.example.core.db.dao.ContactDao
import com.example.core.db.entities.ContactEntity
import com.example.domain.contact.toAnalyticsProfile
import com.example.domain.contact.toAnalyticsSummary
import com.example.domain.contact.toAutomationReadinessProfile
import com.example.domain.contact.toDetailProfile
import com.example.domain.contact.toEventDiscoveryProfile
import com.example.domain.contact.toGiftAdvisorProfile
import com.example.domain.contact.toHealthProfile
import com.example.domain.contact.toHeader
import com.example.domain.contact.toListItem
import com.example.domain.contact.toMessageContext
import com.example.domain.contact.toMessageDispatchRecipient
import com.example.domain.contact.toPickerItem
import com.example.domain.contact.toRelationshipAnalyticsCount
import com.example.domain.contact.toWishContext
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
import com.example.domain.repository.ContactRepository
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getAll(): Flow<List<ContactEntity>> = contactDao.getAll()

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

    override fun getAllPaged(): Flow<PagingData<ContactEntity>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { contactDao.getAllPaged() }
    ).flow

    override suspend fun getAllSync(): List<ContactEntity> = contactDao.getAllSync()

    override suspend fun getAnalyticsProfiles(): List<ContactAnalyticsProfile> {
        return contactDao.getAllSync().map { it.toAnalyticsProfile() }
    }

    override suspend fun getAutomationReadinessProfiles(): List<ContactAutomationReadinessProfile> {
        return contactDao.getAllSync().map { it.toAutomationReadinessProfile() }
    }

    override suspend fun getEventDiscoveryProfiles(): List<ContactEventDiscoveryProfile> {
        return contactDao.getAllSync().map { it.toEventDiscoveryProfile() }
    }

    override suspend fun getHealthProfiles(): List<ContactHealthProfile> {
        return contactDao.getAllSync().map { it.toHealthProfile() }
    }

    override suspend fun getById(id: String): ContactEntity? = contactDao.getById(id)

    override suspend fun getMessageDispatchRecipient(id: String): MessageDispatchRecipient? {
        return contactDao.getById(id)?.toMessageDispatchRecipient()
    }

    override suspend fun getDetailProfile(id: String): ContactDetailProfile? {
        return contactDao.getById(id)?.toDetailProfile()
    }

    override suspend fun getHeader(id: String): ContactHeader? {
        return contactDao.getById(id)?.toHeader()
    }

    override suspend fun getGiftAdvisorProfile(id: String): ContactGiftAdvisorProfile? {
        return contactDao.getById(id)?.toGiftAdvisorProfile()
    }

    override suspend fun getWishContext(id: String): ContactWishContext? {
        return contactDao.getById(id)?.toWishContext()
    }

    override suspend fun contactExists(id: String): Boolean = contactDao.getById(id) != null

    override suspend fun upsert(contact: ContactEntity) = contactDao.upsert(contact)

    override suspend fun update(contact: ContactEntity) = contactDao.update(contact)

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

    override suspend fun getContactsForRevival(lastInteractionBeforeMs: Long): List<ContactEntity> =
        contactDao.getContactsForRevival(lastInteractionBeforeMs)

    override suspend fun updateLastRevivalAttempt(id: String, timestampMs: Long) =
        contactDao.updateLastRevivalAttempt(id, timestampMs)

    override fun countAll(): Flow<Int> = contactDao.countAll()

    override fun countByRelationshipType(): Flow<List<com.example.core.db.dao.RelationshipTypeCount>> = contactDao.countByRelationshipType()

    override fun getRelationshipAnalyticsCounts(): Flow<List<RelationshipAnalyticsCount>> {
        return contactDao.countByRelationshipType().map { counts ->
            counts.map { it.toRelationshipAnalyticsCount() }
        }
    }

    override suspend fun getTopHealthSummaries(limit: Int): List<ContactAnalyticsSummary> {
        return contactDao.getTopByHealthScore(limit).map { it.toAnalyticsSummary() }
    }

    override suspend fun getBottomHealthSummaries(limit: Int): List<ContactAnalyticsSummary> {
        return contactDao.getBottomByHealthScore(limit).map { it.toAnalyticsSummary() }
    }

    override suspend fun getTopByHealthScore(limit: Int): List<ContactEntity> = contactDao.getTopByHealthScore(limit)

    override suspend fun getBottomByHealthScore(limit: Int): List<ContactEntity> = contactDao.getBottomByHealthScore(limit)

    override suspend fun delete(contact: ContactEntity) = contactDao.delete(contact)
}
