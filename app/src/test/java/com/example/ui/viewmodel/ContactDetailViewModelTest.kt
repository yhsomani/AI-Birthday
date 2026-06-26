package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.usecase.GenerateMessageUseCase
import com.example.domain.usecase.UpdateContactPreferencesUseCase
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactDetailViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var mockContactRepo: ContactRepository

    @RelaxedMockK
    private lateinit var mockEventRepo: EventRepository

    @RelaxedMockK
    private lateinit var mockMemoryNoteRepository: MemoryNoteRepository

    @RelaxedMockK
    private lateinit var mockGenerateUseCase: GenerateMessageUseCase

    @RelaxedMockK
    private lateinit var mockUpdateContactPreferencesUseCase: UpdateContactPreferencesUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockMemoryNoteRepository.getByContact("contact1") } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadContact populates state with contact and upcoming birthday`() = runTest(testDispatcher) {
        val contact = ContactEntity(
            id = "contact1",
            name = "Alice",
            healthScore = 80,
        )
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
        val event = EventEntity(
            id = "event1",
            contactId = "contact1",
            type = "BIRTHDAY",
            label = "Alice's Birthday",
            dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            nextOccurrenceMs = cal.timeInMillis,
        )

        coEvery { mockContactRepo.getById("contact1") } returns contact
        coEvery { mockEventRepo.getUpcoming(365) } returns listOf(event)
        coEvery { mockMemoryNoteRepository.getByContact("contact1") } returns listOf(
            MemoryNoteEntity(
                id = "memory1",
                contactId = "contact1",
                noteText = "Met at college reunion.",
                category = "EVENT",
            ),
            MemoryNoteEntity(
                id = "memory2",
                contactId = "contact1",
                noteText = "Likes books.",
                category = "GIFT",
            ),
        )

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact1"))
        val viewModel = ContactDetailViewModel(
            savedStateHandle = savedStateHandle,
            contactRepository = mockContactRepo,
            eventRepository = mockEventRepo,
            memoryNoteRepository = mockMemoryNoteRepository,
            generateMessageUseCase = mockGenerateUseCase,
            updateContactPreferencesUseCase = mockUpdateContactPreferencesUseCase,
        )
        advanceUntilIdle()

        assertEquals("Alice", viewModel.uiState.value.contact?.name)
        assertEquals(2, viewModel.uiState.value.memoryNoteCount)
        assertEquals(
            listOf(
                MemoryNoteCategorySummary(category = "EVENT", count = 1),
                MemoryNoteCategorySummary(category = "GIFT", count = 1),
            ),
            viewModel.uiState.value.memoryNoteCategorySummary,
        )
        assertNotNull(viewModel.uiState.value.upcomingBirthdayDaysLeft)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `generateWish calls use case and emits result`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact1", name = "Alice", healthScore = 80)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
        val event = EventEntity(
            id = "event1",
            contactId = "contact1",
            type = "BIRTHDAY",
            label = "Alice's Birthday",
            dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            nextOccurrenceMs = cal.timeInMillis,
        )

        coEvery { mockContactRepo.getById("contact1") } returns contact
        coEvery { mockEventRepo.getUpcoming(365) } returns listOf(event)
        coEvery { mockGenerateUseCase("event1") } returns
            GenerateMessageUseCase.GenerationOutcome.Generated("pending1", ApprovalMode.SMART_APPROVE, 0)

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact1"))
        val viewModel = ContactDetailViewModel(
            savedStateHandle = savedStateHandle,
            contactRepository = mockContactRepo,
            eventRepository = mockEventRepo,
            memoryNoteRepository = mockMemoryNoteRepository,
            generateMessageUseCase = mockGenerateUseCase,
            updateContactPreferencesUseCase = mockUpdateContactPreferencesUseCase,
        )
        advanceUntilIdle()

        viewModel.generateWish()
        advanceUntilIdle()

        assertEquals("pending1", viewModel.uiState.value.generationResult)
        assertEquals(false, viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `generateWish shows error when use case fails`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact1", name = "Alice", healthScore = 80)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
        val event = EventEntity(
            id = "event1",
            contactId = "contact1",
            type = "BIRTHDAY",
            dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            nextOccurrenceMs = cal.timeInMillis,
        )

        coEvery { mockContactRepo.getById("contact1") } returns contact
        coEvery { mockEventRepo.getUpcoming(365) } returns listOf(event)
        coEvery { mockGenerateUseCase("event1") } returns
            GenerateMessageUseCase.GenerationOutcome.ContactNotFound

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact1"))
        val viewModel = ContactDetailViewModel(
            savedStateHandle = savedStateHandle,
            contactRepository = mockContactRepo,
            eventRepository = mockEventRepo,
            memoryNoteRepository = mockMemoryNoteRepository,
            generateMessageUseCase = mockGenerateUseCase,
            updateContactPreferencesUseCase = mockUpdateContactPreferencesUseCase,
        )
        advanceUntilIdle()

        viewModel.generateWish()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.generationResult)
        assertNotNull(viewModel.uiState.value.generationErrorRes)
        assertEquals(false, viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `generateWish shows settings error when AI generation is disabled`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact1", name = "Alice", healthScore = 80)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
        val event = EventEntity(
            id = "event1",
            contactId = "contact1",
            type = "BIRTHDAY",
            dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            nextOccurrenceMs = cal.timeInMillis,
        )

        coEvery { mockContactRepo.getById("contact1") } returns contact
        coEvery { mockEventRepo.getUpcoming(365) } returns listOf(event)
        coEvery { mockGenerateUseCase("event1") } returns
            GenerateMessageUseCase.GenerationOutcome.AiDisabled

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact1"))
        val viewModel = ContactDetailViewModel(
            savedStateHandle = savedStateHandle,
            contactRepository = mockContactRepo,
            eventRepository = mockEventRepo,
            memoryNoteRepository = mockMemoryNoteRepository,
            generateMessageUseCase = mockGenerateUseCase,
            updateContactPreferencesUseCase = mockUpdateContactPreferencesUseCase,
        )
        advanceUntilIdle()

        viewModel.generateWish()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.generationResult)
        assertEquals(R.string.contact_detail_error_ai_disabled, viewModel.uiState.value.generationErrorRes)
    }

    @Test
    fun `savePreferences maps invalid input reason to resource error`() = runTest(testDispatcher) {
        coEvery { mockContactRepo.getById("contact1") } returns ContactEntity(id = "contact1", name = "Alice")
        coEvery { mockEventRepo.getUpcoming(365) } returns emptyList()
        coEvery { mockUpdateContactPreferencesUseCase(any()) } returns
            UpdateContactPreferencesUseCase.Outcome.InvalidInput(
                UpdateContactPreferencesUseCase.InvalidInputReason.NEGATIVE_BUDGET,
            )

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact1"))
        val viewModel = ContactDetailViewModel(
            savedStateHandle = savedStateHandle,
            contactRepository = mockContactRepo,
            eventRepository = mockEventRepo,
            memoryNoteRepository = mockMemoryNoteRepository,
            generateMessageUseCase = mockGenerateUseCase,
            updateContactPreferencesUseCase = mockUpdateContactPreferencesUseCase,
        )
        advanceUntilIdle()

        viewModel.savePreferences(
            UpdateContactPreferencesUseCase.Request(
                contactId = "contact1",
                annualBudgetInr = -1,
            )
        )
        advanceUntilIdle()

        assertEquals(R.string.contact_preferences_error_negative_budget, viewModel.uiState.value.preferenceErrorRes)
        assertEquals(false, viewModel.uiState.value.isSavingPreferences)
    }
}
