package com.example.ui.viewmodel

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.SaveManualEventUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var eventRepository: EventRepository

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var saveManualEventUseCase: SaveManualEventUseCase

    @RelaxedMockK
    private lateinit var activityLogRepository: ActivityLogRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init collects events and contacts`() = runTest(testDispatcher) {
        every { eventRepository.getAll() } returns MutableStateFlow(
            listOf(EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", dayOfMonth = 5, month = 6, nextOccurrenceMs = 100L))
        )
        every { contactRepository.getAll() } returns MutableStateFlow(
            listOf(ContactEntity(id = "c1", name = "Alice"))
        )

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase, activityLogRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.events.size)
        assertEquals(1, viewModel.uiState.value.contacts.size)
    }

    @Test
    fun `saveManualEvent success exposes snackbar message`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "c1", name = "Alice")
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", dayOfMonth = 5, month = 6, nextOccurrenceMs = 100L)
        every { eventRepository.getAll() } returns MutableStateFlow(emptyList())
        every { contactRepository.getAll() } returns MutableStateFlow(listOf(contact))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.Saved(contact, event)

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase, activityLogRepository)
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 6, 5, null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveMessage?.contains("Alice") == true)
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
    }

    @Test
    fun `saveManualEvent invalid input exposes error`() = runTest(testDispatcher) {
        every { eventRepository.getAll() } returns MutableStateFlow(emptyList())
        every { contactRepository.getAll() } returns MutableStateFlow(emptyList())
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.InvalidInput("Enter a valid date.")

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase, activityLogRepository)
        advanceUntilIdle()
        viewModel.saveManualEvent(null, "Bad Date", "BIRTHDAY", null, 2, 30, null)
        advanceUntilIdle()

        assertEquals("Enter a valid date.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
    }

    @Test
    fun `saveManualEvent duplicate exposes warning without snackbar error`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "c1", name = "Alice")
        val event = EventEntity(
            id = "existing",
            contactId = "c1",
            type = "BIRTHDAY",
            dayOfMonth = 5,
            month = 6,
            nextOccurrenceMs = 100L,
        )
        every { eventRepository.getAll() } returns MutableStateFlow(listOf(event))
        every { contactRepository.getAll() } returns MutableStateFlow(listOf(contact))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.DuplicateFound(contact, event)

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase, activityLogRepository)
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 6, 5, null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingManualEvent)
        assertNull(viewModel.uiState.value.error)
        assertEquals("Alice", viewModel.uiState.value.duplicateWarning?.contactName)
        assertEquals("BIRTHDAY", viewModel.uiState.value.duplicateWarning?.eventType)
    }

    @Test
    fun `saveManualEvent override passes allowDuplicate to use case`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "c1", name = "Alice")
        val event = EventEntity(
            id = "manual",
            contactId = "c1",
            type = "BIRTHDAY",
            dayOfMonth = 5,
            month = 6,
            nextOccurrenceMs = 100L,
        )
        every { eventRepository.getAll() } returns MutableStateFlow(emptyList())
        every { contactRepository.getAll() } returns MutableStateFlow(listOf(contact))
        coEvery { saveManualEventUseCase(any()) } returns SaveManualEventUseCase.Outcome.Saved(contact, event)

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase, activityLogRepository)
        advanceUntilIdle()
        viewModel.saveManualEvent("c1", null, "BIRTHDAY", null, 6, 5, null, allowDuplicate = true)
        advanceUntilIdle()

        coVerify {
            saveManualEventUseCase(match { it.allowDuplicate })
        }
        assertTrue(viewModel.uiState.value.saveMessage?.contains("Alice") == true)
    }

    @Test
    fun `search type and horizon filters are applied in viewmodel`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val contacts = listOf(
            ContactEntity(id = "c1", name = "Alice"),
            ContactEntity(id = "c2", name = "Bob"),
        )
        val events = listOf(
            EventEntity(
                id = "e1",
                contactId = "c1",
                type = "BIRTHDAY",
                dayOfMonth = 1,
                month = 1,
                nextOccurrenceMs = now + 2 * 86_400_000L,
            ),
            EventEntity(
                id = "e2",
                contactId = "c2",
                type = "ANNIVERSARY",
                dayOfMonth = 2,
                month = 1,
                nextOccurrenceMs = now + 20 * 86_400_000L,
            ),
        )
        every { eventRepository.getAll() } returns MutableStateFlow(events)
        every { contactRepository.getAll() } returns MutableStateFlow(contacts)

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase, activityLogRepository)
        advanceUntilIdle()

        viewModel.selectHorizonFilter(EventHorizonFilter.NEXT_7_DAYS)
        assertEquals(listOf("e1"), viewModel.uiState.value.events.map { it.id })

        viewModel.selectHorizonFilter(EventHorizonFilter.ALL)
        viewModel.selectTypeFilter(EventTypeFilter.ANNIVERSARY)
        assertEquals(listOf("e2"), viewModel.uiState.value.events.map { it.id })

        viewModel.selectTypeFilter(EventTypeFilter.ALL)
        viewModel.updateSearchQuery("alice")
        assertEquals(listOf("e1"), viewModel.uiState.value.events.map { it.id })
    }
}
