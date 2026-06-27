package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount
import com.example.domain.model.memory.MemoryNoteSummary
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.occasion.UpcomingEventPreview
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
        coEvery { mockMemoryNoteRepository.getSummaryForContact("contact1") } returns MemoryNoteSummary.EMPTY
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadContact populates state with contact and upcoming birthday`() = runTest(testDispatcher) {
        val contact = contactProfile(displayName = "Alice", healthScore = 80)
        val event = upcomingEventPreview()

        coEvery { mockContactRepo.getDetailProfile("contact1") } returns contact
        coEvery { mockEventRepo.getNextUpcomingPreviewForContact("contact1", 365) } returns event
        coEvery { mockMemoryNoteRepository.getSummaryForContact("contact1") } returns MemoryNoteSummary(
            totalCount = 2,
            categoryCounts = listOf(
                MemoryNoteCategoryCount(category = "EVENT", count = 1),
                MemoryNoteCategoryCount(category = "GIFT", count = 1),
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

        assertEquals("Alice", viewModel.uiState.value.contact?.displayName)
        assertEquals(2, viewModel.uiState.value.memoryNoteCount)
        assertEquals(
            listOf(
                MemoryNoteCategoryCount(category = "EVENT", count = 1),
                MemoryNoteCategoryCount(category = "GIFT", count = 1),
            ),
            viewModel.uiState.value.memoryNoteCategorySummary,
        )
        assertNotNull(viewModel.uiState.value.upcomingBirthdayDaysLeft)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `generateWish calls use case and emits result`() = runTest(testDispatcher) {
        val contact = contactProfile(displayName = "Alice", healthScore = 80)
        val event = upcomingEventPreview()

        coEvery { mockContactRepo.getDetailProfile("contact1") } returns contact
        coEvery { mockEventRepo.getNextUpcomingPreviewForContact("contact1", 365) } returns event
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
        val contact = contactProfile(displayName = "Alice", healthScore = 80)
        val event = upcomingEventPreview(label = null)

        coEvery { mockContactRepo.getDetailProfile("contact1") } returns contact
        coEvery { mockEventRepo.getNextUpcomingPreviewForContact("contact1", 365) } returns event
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
        val contact = contactProfile(displayName = "Alice", healthScore = 80)
        val event = upcomingEventPreview(label = null)

        coEvery { mockContactRepo.getDetailProfile("contact1") } returns contact
        coEvery { mockEventRepo.getNextUpcomingPreviewForContact("contact1", 365) } returns event
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
        coEvery { mockContactRepo.getDetailProfile("contact1") } returns contactProfile(displayName = "Alice")
        coEvery { mockEventRepo.getNextUpcomingPreviewForContact("contact1", 365) } returns null
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

    private fun contactProfile(
        id: String = "contact1",
        displayName: String = "Alice",
        healthScore: Int = 80,
    ): ContactDetailProfile {
        return ContactDetailProfile(
            id = ContactId(id),
            displayName = displayName,
            contactGroup = null,
            healthScore = healthScore,
            nickname = null,
            birthdayDay = null,
            birthdayMonth = null,
            primaryPhone = null,
            primaryEmail = null,
            relationshipType = "UNKNOWN",
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
        )
    }

    private fun upcomingEventPreview(
        id: String = "event1",
        contactId: String = "contact1",
        type: OccasionType = OccasionType.BIRTHDAY,
        label: String? = "Alice's Birthday",
        daysFromNow: Int = 7,
    ): UpcomingEventPreview {
        return UpcomingEventPreview(
            id = OccasionId(id),
            contactId = ContactId(contactId),
            type = type,
            label = label,
            nextOccurrenceMs = System.currentTimeMillis() + daysFromNow * DAY_MS,
        )
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
