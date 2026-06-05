package com.example

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.usecase.GetDashboardMetricsUseCase
import com.example.navigation.MainViewModel
import com.example.core.db.dao.RelationshipTypeCount
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.GiftHistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var contactRepo: FakeContactRepository
    private lateinit var eventRepo: FakeEventRepository
    private lateinit var messageRepo: FakeMessageRepository
    private lateinit var styleProfileRepo: FakeStyleProfileRepository
    private lateinit var memoryNoteRepo: FakeMemoryNoteRepository
    private lateinit var giftHistoryRepo: FakeGiftHistoryRepository
    private lateinit var getDashboardMetrics: GetDashboardMetricsUseCase
    private lateinit var authManager: FakeAuthManager
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        contactRepo = FakeContactRepository()
        eventRepo = FakeEventRepository()
        messageRepo = FakeMessageRepository()
        styleProfileRepo = FakeStyleProfileRepository()
        memoryNoteRepo = FakeMemoryNoteRepository()
        giftHistoryRepo = FakeGiftHistoryRepository()
        authManager = FakeAuthManager()
        getDashboardMetrics = GetDashboardMetricsUseCase(contactRepo, eventRepo, messageRepo)
        viewModel = MainViewModel(contactRepo, eventRepo, messageRepo, styleProfileRepo, memoryNoteRepo, giftHistoryRepo, getDashboardMetrics, authManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun healthScore_emptyContacts_returnsZero() = kotlinx.coroutines.test.runTest {
        assertEquals(0, viewModel.healthScore.value)
    }

    @Test
    fun healthScore_withContacts_returnsAverage() = kotlinx.coroutines.test.runTest {
        contactRepo.contactsList = listOf(
            ContactEntity(id = "1", name = "A", healthScore = 80),
            ContactEntity(id = "2", name = "B", healthScore = 60)
        )
        viewModel = MainViewModel(contactRepo, eventRepo, messageRepo, styleProfileRepo, memoryNoteRepo, giftHistoryRepo, getDashboardMetrics, authManager)
        testScheduler.advanceUntilIdle()
        assertEquals(70, viewModel.healthScore.value)
    }
}

class FakeMemoryNoteRepository : MemoryNoteRepository {
    override suspend fun getByContact(contactId: String) = emptyList<com.example.core.db.entities.MemoryNoteEntity>()
    override suspend fun upsert(note: com.example.core.db.entities.MemoryNoteEntity) {}
    override suspend fun delete(note: com.example.core.db.entities.MemoryNoteEntity) {}
}

class FakeGiftHistoryRepository : GiftHistoryRepository {
    override suspend fun getByContact(contactId: String) = emptyList<com.example.core.db.entities.GiftHistoryEntity>()
    override suspend fun upsert(gift: com.example.core.db.entities.GiftHistoryEntity) {}
    override suspend fun delete(gift: com.example.core.db.entities.GiftHistoryEntity) {}
}

class FakeAuthManager : com.example.core.auth.AuthManager {
    constructor() : super()

    override fun getUserDisplayName(): String = "Test User"
    override fun getUserEmail(): String = "test@example.com"
}

class FakeContactRepository : ContactRepository {
    var contactsList = emptyList<ContactEntity>()
    override fun getAll() = flowOf(contactsList)
    override suspend fun getAllSync() = contactsList
    override suspend fun getById(id: String) = null
    override suspend fun upsert(contact: ContactEntity) {}
    override suspend fun update(contact: ContactEntity) {}
    override suspend fun updateClassification(id: String, type: String, subtype: String?, lang: String, formality: String, style: String) {}
    override suspend fun updateHealthScore(id: String, score: Int) {}
    override suspend fun updateLastWished(id: String, timestamp: Long) {}
    override suspend fun incrementEngagementScore(id: String, delta: Int) {}
    override suspend fun incrementConsecutiveYearsWished(id: String) {}
    override fun countAll() = flowOf(0)
    override fun countByRelationshipType() = flowOf(emptyList<RelationshipTypeCount>())
    override suspend fun getTopByHealthScore(limit: Int) = emptyList<ContactEntity>()
    override suspend fun getBottomByHealthScore(limit: Int) = emptyList<ContactEntity>()
    override suspend fun delete(contact: ContactEntity) {}
    override fun getAllPaged(): Flow<PagingData<ContactEntity>> = flowOf(PagingData.empty())
}

class FakeEventRepository : EventRepository {
    override fun getAll() = flowOf(emptyList<EventEntity>())
    override suspend fun getEventsBefore(timeMs: Long) = emptyList<EventEntity>()
    override suspend fun getUpcoming(days: Int) = emptyList<EventEntity>()
    override suspend fun upsert(event: EventEntity) {}
    override suspend fun delete(event: EventEntity) {}
}

class FakeMessageRepository : MessageRepository {
    override fun getAllPending() = flowOf(emptyList<PendingMessageEntity>())
    override suspend fun getAllApproved() = emptyList<PendingMessageEntity>()
    override suspend fun getPendingByEventId(eventId: String) = null
    override suspend fun pendingExistsForEvent(eventId: String) = false
    override suspend fun insertPending(message: PendingMessageEntity) {}
    override suspend fun updatePendingStatus(id: String, status: String) {}
    override suspend fun updatePendingStatusByEventId(eventId: String, status: String) {}
    override fun getAllSent() = flowOf(emptyList<com.example.core.db.entities.SentMessageEntity>())
    override suspend fun getSentByContact(contactId: String, limit: Int) = emptyList<com.example.core.db.entities.SentMessageEntity>()
    override fun countAllSent() = flowOf(0)
    override fun countPending() = flowOf(0)
    override suspend fun insertSent(message: com.example.core.db.entities.SentMessageEntity) {}
}

class FakeStyleProfileRepository : StyleProfileRepository {
    override fun getProfile(): Flow<StyleProfileEntity?> = flowOf(null)
    override suspend fun getProfileOnce(): StyleProfileEntity? = null
    override suspend fun upsert(profile: StyleProfileEntity) {}
}
