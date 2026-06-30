package com.example.data.repository

import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.RelationshipTypeCount
import com.example.core.db.entities.ContactEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.contact.ContactPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRepositoryImplTest {

    private val contactDao: ContactDao = mockk(relaxed = true)
    private val repository = ContactRepositoryImpl(contactDao)

    @Test
    fun updateClassification_persistsAiConfidence() = runTest {
        repository.updateClassification(
            id = "c1",
            type = "FRIEND",
            subtype = "CLOSE",
            lang = "en",
            formality = "SEMI_FORMAL",
            style = "PROFESSIONAL",
            confidence = 0.82,
        )

        coVerify {
            contactDao.updateClassification(
                id = "c1",
                type = "FRIEND",
                subtype = "CLOSE",
                lang = "en",
                formality = "SEMI_FORMAL",
                style = "PROFESSIONAL",
                confidence = 0.82,
            )
        }
    }

    @Test
    fun getHealthProfiles_mapsRoomContactsToPureModel() = runTest {
        coEvery { contactDao.getAllSync() } returns listOf(
            ContactEntity(
                id = "c1",
                name = "Asha",
                healthScore = 72,
                interactionFrequencyPerMonth = 4.5f,
                lastInteractionDate = 1_700_000_000_000,
                lastWishedDate = null,
                consecutiveYearsWished = 3,
            ),
        )

        val profiles = repository.getHealthProfiles()

        assertEquals(1, profiles.size)
        assertEquals(ContactId("c1"), profiles.single().id)
        assertEquals(72, profiles.single().currentHealthScore)
        assertEquals(4.5f, profiles.single().interactionFrequencyPerMonth)
        assertEquals(1_700_000_000_000, profiles.single().lastInteractionAtMs)
        assertEquals(null, profiles.single().lastWishedAtMs)
        assertEquals(3, profiles.single().consecutiveYearsWished)
    }

    @Test
    fun getAnalyticsProfiles_mapsRoomContactsToPureModel() = runTest {
        coEvery { contactDao.getAllSync() } returns listOf(
            ContactEntity(
                id = "c1",
                name = "Asha",
                nickname = "Ash",
                healthScore = 82,
                notesText = "College friend",
                interestsJson = "[\"music\"]",
                sharedHistoryJson = "[\"met in 2018\"]",
            ),
        )

        val profiles = repository.getAnalyticsProfiles()

        assertEquals(1, profiles.size)
        assertEquals(ContactId("c1"), profiles.single().id)
        assertEquals(82, profiles.single().healthScore)
        assertEquals("Ash", profiles.single().nickname)
        assertEquals("College friend", profiles.single().notesText)
        assertEquals("[\"music\"]", profiles.single().interestsJson)
        assertEquals("[\"met in 2018\"]", profiles.single().sharedHistoryJson)
        assertTrue(profiles.single().hasPersonalizationSignals)
    }

    @Test
    fun getAnalyticsProfilesFlow_mapsRoomContactsToPureModel() = runTest {
        every { contactDao.getAll() } returns flowOf(
            listOf(
                ContactEntity(
                    id = "c1",
                    name = "Asha",
                    nickname = "Ash",
                    healthScore = 82,
                    notesText = "College friend",
                    interestsJson = "[\"music\"]",
                    sharedHistoryJson = "[\"met in 2018\"]",
                ),
            ),
        )

        val profiles = repository.getAnalyticsProfilesFlow().first()

        assertEquals(1, profiles.size)
        assertEquals(ContactId("c1"), profiles.single().id)
        assertEquals(82, profiles.single().healthScore)
        assertTrue(profiles.single().hasPersonalizationSignals)
    }

    @Test
    fun getAutomationReadinessProfiles_mapsRoomContactsToPureModel() = runTest {
        coEvery { contactDao.getAllSync() } returns listOf(
            ContactEntity(
                id = "c1",
                name = "Asha",
                preferredChannel = " email ",
                nickname = "Ash",
                notesText = "College friend",
                interestsJson = "[\"music\"]",
                sharedHistoryJson = "[\"met in 2018\"]",
                classificationConfidence = 0.73,
            ),
            ContactEntity(
                id = "c2",
                name = "Neha",
                preferredChannel = "telegram",
                classificationConfidence = 0.2,
            ),
        )

        val profiles = repository.getAutomationReadinessProfiles()

        assertEquals(2, profiles.size)
        assertEquals(ContactId("c1"), profiles[0].id)
        assertEquals(MessageChannel.EMAIL, profiles[0].preferredChannel)
        assertEquals("Ash", profiles[0].nickname)
        assertEquals("College friend", profiles[0].notesText)
        assertEquals("[\"music\"]", profiles[0].interestsJson)
        assertEquals("[\"met in 2018\"]", profiles[0].sharedHistoryJson)
        assertEquals(0.73, profiles[0].classificationConfidence, 0.0)
        assertTrue(profiles[0].hasPersonalizationData)
        assertEquals(ContactId("c2"), profiles[1].id)
        assertEquals(MessageChannel.UNKNOWN, profiles[1].preferredChannel)
        assertFalse(profiles[1].hasPersonalizationContextForAi(0.6))
    }

    @Test
    fun getEventDiscoveryProfiles_mapsRoomContactsToPureModel() = runTest {
        coEvery { contactDao.getAllSync() } returns listOf(
            ContactEntity(
                id = "c1",
                name = "Asha",
                birthdayDay = 5,
                birthdayMonth = 6,
                birthdayYear = 1992,
                anniversaryDay = 12,
                anniversaryMonth = 10,
                anniversaryYear = 2017,
                workStartDay = 1,
                workStartMonth = 4,
                workStartYear = 2020,
                primaryPhone = "+911234567890",
                notesText = "Should not be needed by discovery",
            ),
        )

        val profiles = repository.getEventDiscoveryProfiles()

        assertEquals(1, profiles.size)
        assertEquals(ContactId("c1"), profiles.single().id)
        assertEquals("Asha", profiles.single().displayName)
        assertEquals(5, profiles.single().birthdayDay)
        assertEquals(6, profiles.single().birthdayMonth)
        assertEquals(1992, profiles.single().birthdayYear)
        assertEquals(12, profiles.single().anniversaryDay)
        assertEquals(10, profiles.single().anniversaryMonth)
        assertEquals(2017, profiles.single().anniversaryYear)
        assertEquals(1, profiles.single().workStartDay)
        assertEquals(4, profiles.single().workStartMonth)
        assertEquals(2020, profiles.single().workStartYear)
    }

    @Test
    fun getDetailProfile_mapsRoomContactToPureProfile() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(
            id = "c1",
            name = "Asha",
            contactGroup = "Family",
            healthScore = 82,
            nickname = "Ash",
            birthdayDay = 5,
            birthdayMonth = 6,
            primaryPhone = "+911234567890",
            primaryEmail = "asha@example.com",
            relationshipType = "FRIEND",
            preferredLanguage = "hi",
            preferredChannel = MessageChannel.WHATSAPP.raw,
            formalityLevel = "SEMI_FORMAL",
            communicationStyle = "EMOTIONAL",
            automationMode = ApprovalMode.VIP_APPROVE.raw,
            customSendTimeHour = 9,
            customSendTimeMinute = 30,
            giftBudgetInr = 1500,
            annualBudgetInr = 5000,
            skipAutoWish = true,
            interestsJson = "[\"music\"]",
            sensitiveTopicsJson = "[\"work\"]",
            currentLifePhaseJson = "{\"phase\":\"new_job\"}",
            notesText = "Prefers WhatsApp.",
        )

        val profile = repository.getDetailProfile("c1")

        assertEquals(ContactId("c1"), profile?.id)
        assertEquals("Asha", profile?.displayName)
        assertEquals("Family", profile?.contactGroup)
        assertEquals(82, profile?.healthScore)
        assertEquals("Ash", profile?.nickname)
        assertEquals(5, profile?.birthdayDay)
        assertEquals(6, profile?.birthdayMonth)
        assertEquals("+911234567890", profile?.primaryPhone)
        assertEquals("asha@example.com", profile?.primaryEmail)
        assertEquals("FRIEND", profile?.relationshipType)
        assertEquals("hi", profile?.preferredLanguage)
        assertEquals(MessageChannel.WHATSAPP, profile?.preferredChannel)
        assertEquals("SEMI_FORMAL", profile?.formalityLevel)
        assertEquals("EMOTIONAL", profile?.communicationStyle)
        assertEquals(ApprovalMode.VIP_APPROVE, profile?.automationMode)
        assertEquals(9, profile?.customSendTimeHour)
        assertEquals(30, profile?.customSendTimeMinute)
        assertEquals(1500, profile?.giftBudgetInr)
        assertEquals(5000, profile?.annualBudgetInr)
        assertEquals(true, profile?.skipAutoWish)
        assertEquals("[\"music\"]", profile?.interestsJson)
        assertEquals("[\"work\"]", profile?.sensitiveTopicsJson)
        assertEquals("{\"phase\":\"new_job\"}", profile?.currentLifePhaseJson)
        assertEquals("Prefers WhatsApp.", profile?.notesText)
    }

    @Test
    fun getWishContext_mapsRoomContactToPureContext() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(
            id = "c1",
            name = "Asha",
            relationshipType = "FRIEND",
            preferredLanguage = "hi",
        )

        val context = repository.getWishContext("c1")

        assertEquals(ContactId("c1"), context?.id)
        assertEquals("FRIEND", context?.relationshipType)
        assertEquals("hi", context?.preferredLanguage)
    }

    @Test
    fun getHeader_mapsRoomContactToPureHeader() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(
            id = "c1",
            name = "Asha",
            primaryEmail = "asha@example.com",
        )

        val header = repository.getHeader("c1")

        assertEquals(ContactId("c1"), header?.id)
        assertEquals("Asha", header?.displayName)
    }

    @Test
    fun getGiftAdvisorProfile_mapsRoomContactToPureProfile() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(
            id = "c1",
            name = "Asha Sharma",
            nickname = "Ash",
            relationshipType = "FRIEND",
            interestsJson = "[\"music\",\"books\"]",
            giftBudgetInr = 2500,
        )

        val profile = repository.getGiftAdvisorProfile("c1")

        assertEquals(ContactId("c1"), profile?.id)
        assertEquals("Asha Sharma", profile?.displayName)
        assertEquals("Ash", profile?.nickname)
        assertEquals("FRIEND", profile?.relationshipType)
        assertEquals("[\"music\",\"books\"]", profile?.interestsJson)
        assertEquals(2500, profile?.giftBudgetInr)
    }

    @Test
    fun getMessageDispatchRecipient_mapsRoomContactToPureRecipient() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(
            id = "c1",
            name = "Asha Sharma",
            primaryPhone = "+911234567890",
            primaryEmail = "asha@example.com",
            notesText = "Not needed for dispatch recipient.",
        )

        val recipient = repository.getMessageDispatchRecipient("c1")

        assertEquals(ContactId("c1"), recipient?.id)
        assertEquals("Asha Sharma", recipient?.displayName)
        assertEquals("+911234567890", recipient?.primaryPhone)
        assertEquals("asha@example.com", recipient?.primaryEmail)
    }

    @Test
    fun getContactListItems_mapsRoomContactsToPureListItems() = runTest {
        every { contactDao.getAll() } returns flowOf(
            listOf(
                ContactEntity(
                    id = "c1",
                    name = "Asha",
                    nickname = "Ash",
                    company = "Acme",
                    contactGroup = "Friends",
                    relationshipType = "FRIEND",
                    healthScore = 82,
                    automationMode = "vip_approve",
                    preferredChannel = " whatsapp ",
                    primaryPhone = "+911234567890",
                    secondaryPhone = "+919999999999",
                    primaryEmail = "asha@example.com",
                    birthdayDay = 5,
                    birthdayMonth = 6,
                    anniversaryDay = 7,
                    anniversaryMonth = 8,
                    workStartDay = 9,
                    workStartMonth = 10,
                    notesText = "College friend",
                    interestsJson = "[\"music\"]",
                    sharedHistoryJson = "[\"met in 2018\"]",
                    classificationConfidence = 0.73,
                ),
            ),
        )

        val items = repository.getContactListItems().first()

        assertEquals(ContactId("c1"), items.single().id)
        assertEquals("Asha", items.single().displayName)
        assertEquals("Ash", items.single().nickname)
        assertEquals("Acme", items.single().company)
        assertEquals("Friends", items.single().contactGroup)
        assertEquals("FRIEND", items.single().relationshipType)
        assertEquals(82, items.single().healthScore)
        assertEquals(ApprovalMode.VIP_APPROVE, items.single().automationMode)
        assertEquals(MessageChannel.WHATSAPP, items.single().preferredChannel)
        assertEquals("+911234567890", items.single().primaryPhone)
        assertEquals("+919999999999", items.single().secondaryPhone)
        assertEquals("asha@example.com", items.single().primaryEmail)
        assertEquals(5, items.single().birthdayDay)
        assertEquals(6, items.single().birthdayMonth)
        assertEquals(7, items.single().anniversaryDay)
        assertEquals(8, items.single().anniversaryMonth)
        assertEquals(9, items.single().workStartDay)
        assertEquals(10, items.single().workStartMonth)
        assertEquals("College friend", items.single().notesText)
        assertEquals("[\"music\"]", items.single().interestsJson)
        assertEquals("[\"met in 2018\"]", items.single().sharedHistoryJson)
        assertEquals(0.73, items.single().classificationConfidence, 0.0)
    }

    @Test
    fun getContactPickerItems_mapsRoomContactsToPurePickerItems() = runTest {
        every { contactDao.getAll() } returns flowOf(
            listOf(ContactEntity(id = "c1", name = "Asha")),
        )

        val items = repository.getContactPickerItems().first()

        assertEquals(ContactId("c1"), items.single().id)
        assertEquals("Asha", items.single().displayName)
    }

    @Test
    fun getMessageContexts_mapsRoomContactsToPureMessageContext() = runTest {
        every { contactDao.getAll() } returns flowOf(
            listOf(
                ContactEntity(
                    id = "c1",
                    name = "Asha",
                    profilePhotoUri = "content://avatar",
                    primaryPhone = "+911234567890",
                    primaryEmail = "asha@example.com",
                ),
            ),
        )

        val contexts = repository.getMessageContexts().first()

        assertEquals(ContactId("c1"), contexts.single().id)
        assertEquals("Asha", contexts.single().displayName)
        assertEquals("content://avatar", contexts.single().avatarUrl)
        assertEquals("+911234567890", contexts.single().primaryPhone)
        assertEquals("asha@example.com", contexts.single().primaryEmail)
    }

    @Test
    fun getRelationshipAnalyticsCounts_mapsDaoCountsToPureModel() = runTest {
        every { contactDao.countByRelationshipType() } returns flowOf(
            listOf(
                RelationshipTypeCount("FRIEND", 4),
                RelationshipTypeCount("FAMILY", 2),
            ),
        )

        val counts = repository.getRelationshipAnalyticsCounts().first()

        assertEquals(2, counts.size)
        assertEquals("FRIEND", counts[0].relationshipType)
        assertEquals(4, counts[0].count)
        assertEquals("FAMILY", counts[1].relationshipType)
        assertEquals(2, counts[1].count)
    }

    @Test
    fun getHealthSummaries_mapRankedContactsToPureAnalyticsModel() = runTest {
        coEvery { contactDao.getTopByHealthScore(2) } returns listOf(
            ContactEntity(id = "c_top", name = "Asha", relationshipType = "FRIEND", healthScore = 95),
        )
        coEvery { contactDao.getBottomByHealthScore(2) } returns listOf(
            ContactEntity(id = "c_low", name = "Neha", relationshipType = "FAMILY", healthScore = 18),
        )

        val top = repository.getTopHealthSummaries(2)
        val bottom = repository.getBottomHealthSummaries(2)

        assertEquals(ContactId("c_top"), top.single().id)
        assertEquals("Asha", top.single().displayName)
        assertEquals("FRIEND", top.single().relationshipType)
        assertEquals(95, top.single().healthScore)
        assertEquals(ContactId("c_low"), bottom.single().id)
        assertEquals("Neha", bottom.single().displayName)
        assertEquals("FAMILY", bottom.single().relationshipType)
        assertEquals(18, bottom.single().healthScore)
    }

    @Test
    fun contactExists_returnsWhetherActiveContactCanBeLoaded() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(id = "c1", name = "Asha")
        coEvery { contactDao.getById("missing") } returns null

        assertTrue(repository.contactExists("c1"))
        assertFalse(repository.contactExists("missing"))
    }

    @Test
    fun updatePreferences_mapsPurePreferencesToRoomContactCopy() = runTest {
        coEvery { contactDao.getById("c1") } returns ContactEntity(
            id = "c1",
            name = "Asha",
            relationshipType = "UNKNOWN",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = ApprovalMode.DEFAULT.raw,
        )
        val preferences = ContactPreferences(
            contactId = ContactId("c1"),
            nickname = "Ash",
            relationshipType = "FRIEND",
            preferredLanguage = "hi",
            preferredChannel = MessageChannel.EMAIL,
            formalityLevel = "FORMAL",
            communicationStyle = "PROFESSIONAL",
            automationMode = ApprovalMode.VIP_APPROVE,
            customSendTimeHour = 9,
            customSendTimeMinute = 30,
            giftBudgetInr = 1500,
            annualBudgetInr = 5000,
            skipAutoWish = true,
            interestsJson = "[\"music\"]",
            sensitiveTopicsJson = "[\"work\"]",
            currentLifePhaseJson = "{\"phase\":\"new_job\"}",
            notesText = "Prefers email.",
            updatedAtMs = 1_700_000_000_000,
        )

        assertTrue(repository.updatePreferences(preferences))

        coVerify {
            contactDao.update(match {
                it.id == "c1" &&
                    it.nickname == "Ash" &&
                    it.relationshipType == "FRIEND" &&
                    it.preferredLanguage == "hi" &&
                    it.preferredChannel == MessageChannel.EMAIL.raw &&
                    it.formalityLevel == "FORMAL" &&
                    it.communicationStyle == "PROFESSIONAL" &&
                    it.automationMode == ApprovalMode.VIP_APPROVE.raw &&
                    it.customSendTimeHour == 9 &&
                    it.customSendTimeMinute == 30 &&
                    it.giftBudgetInr == 1500 &&
                    it.annualBudgetInr == 5000 &&
                    it.skipAutoWish &&
                    it.interestsJson == "[\"music\"]" &&
                    it.sensitiveTopicsJson == "[\"work\"]" &&
                    it.currentLifePhaseJson == "{\"phase\":\"new_job\"}" &&
                    it.notesText == "Prefers email." &&
                    it.updatedAt == 1_700_000_000_000
            })
        }
    }

    @Test
    fun updatePreferences_returnsFalseWhenContactIsMissing() = runTest {
        coEvery { contactDao.getById("missing") } returns null

        val preferences = ContactPreferences(
            contactId = ContactId("missing"),
            nickname = null,
            relationshipType = "FRIEND",
            preferredLanguage = "en",
            preferredChannel = MessageChannel.SMS,
            formalityLevel = "CASUAL",
            communicationStyle = "WARM",
            automationMode = ApprovalMode.DEFAULT,
            customSendTimeHour = null,
            customSendTimeMinute = null,
            giftBudgetInr = 500,
            annualBudgetInr = 0,
            skipAutoWish = false,
            interestsJson = "[]",
            sensitiveTopicsJson = "[]",
            currentLifePhaseJson = "{}",
            notesText = "",
            updatedAtMs = 1_700_000_000_000,
        )

        assertFalse(repository.updatePreferences(preferences))
        coVerify(exactly = 0) { contactDao.update(any()) }
    }
}
