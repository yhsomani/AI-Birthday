package com.example.ui.viewmodel

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.usecase.SaveManualEventUseCase
import io.mockk.coEvery
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

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase)
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

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase)
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

        val viewModel = EventsViewModel(eventRepository, contactRepository, saveManualEventUseCase)
        advanceUntilIdle()
        viewModel.saveManualEvent(null, "Bad Date", "BIRTHDAY", null, 2, 30, null)
        advanceUntilIdle()

        assertEquals("Enter a valid date.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSavingManualEvent)
    }
}
