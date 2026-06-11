package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.R
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.service.AiService
import com.example.domain.service.GiftSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class GiftAdvisorViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var contactRepository: ContactRepository

    @RelaxedMockK
    private lateinit var giftHistoryRepository: GiftHistoryRepository

    @RelaxedMockK
    private lateinit var aiService: AiService

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
    fun `loadData calculates budget stats correctly`() = runTest(testDispatcher) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val contact = ContactEntity(id = "contact_1", name = "John Doe", giftBudgetInr = 1000)
        val history = listOf(
            GiftHistoryEntity(
                id = "gift_1",
                contactId = "contact_1",
                giftName = "Gadget",
                giftCategory = "Tech",
                occasionType = "Birthday",
                year = currentYear,
                approxCostInr = 400
            ),
            GiftHistoryEntity(
                id = "gift_2",
                contactId = "contact_1",
                giftName = "Book",
                giftCategory = "Books",
                occasionType = "Christmas",
                year = currentYear - 1, // Last year
                approxCostInr = 300
            )
        )

        coEvery { contactRepository.getById("contact_1") } returns contact
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns history

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        assertEquals(contact, viewModel.uiState.value.contact)
        assertEquals(2, viewModel.uiState.value.giftHistory.size)
        assertEquals(400, viewModel.uiState.value.totalSpentThisYear)
        assertEquals(600, viewModel.uiState.value.remainingBudget)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadData emits stable error when repository fails`() = runTest(testDispatcher) {
        coEvery { contactRepository.getById("contact_1") } throws IllegalStateException("boom")

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(R.string.gift_advisor_error_load, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `generateGiftSuggestions updates state with suggestions`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact_1", name = "John Doe", giftBudgetInr = 1000)
        val suggestions = listOf(
            GiftSuggestion("AI Suggestion 1", "Reason 1", 500),
            GiftSuggestion("AI Suggestion 2", "Reason 2", 800)
        )

        coEvery { contactRepository.getById("contact_1") } returns contact
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns emptyList()
        coEvery { aiService.generateGiftSuggestions(any(), any()) } returns suggestions

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.generateGiftSuggestions()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.suggestions.size)
        assertEquals("AI Suggestion 1", viewModel.uiState.value.suggestions[0].name)
        assertEquals(false, viewModel.uiState.value.isGeneratingSuggestions)
    }

    @Test
    fun `generateGiftSuggestions exposes stable error when ai fails`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact_1", name = "John Doe", giftBudgetInr = 1000)

        coEvery { contactRepository.getById("contact_1") } returns contact
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns emptyList()
        coEvery { aiService.generateGiftSuggestions(any(), any()) } throws IllegalStateException("ai down")

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.generateGiftSuggestions()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isGeneratingSuggestions)
        assertEquals(R.string.gift_advisor_error_suggestions, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `addGiftRecord trims input and parses formatted cost`() = runTest(testDispatcher) {
        val contact = ContactEntity(id = "contact_1", name = "John Doe", giftBudgetInr = 5000)

        coEvery { contactRepository.getById("contact_1") } returns contact
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns emptyList()

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        val accepted = viewModel.addGiftRecord(
            name = "  Travel journal  ",
            category = "  Books  ",
            occasion = "  Birthday  ",
            costInput = "1,250",
            liked = true,
            notes = "  Loved the paper quality  "
        )
        advanceUntilIdle()

        assertEquals(true, accepted)
        coVerify {
            giftHistoryRepository.upsert(
                match {
                    it.giftName == "Travel journal" &&
                        it.giftCategory == "Books" &&
                        it.occasionType == "Birthday" &&
                        it.approxCostInr == 1250 &&
                        it.notes == "Loved the paper quality" &&
                        it.receivedWell == true
                }
            )
        }
    }

    @Test
    fun `addGiftRecord rejects invalid cost without persisting`() = runTest(testDispatcher) {
        coEvery { contactRepository.getById("contact_1") } returns ContactEntity(id = "contact_1", name = "John Doe")
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns emptyList()

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        val accepted = viewModel.addGiftRecord(
            name = "Headphones",
            category = "Tech",
            occasion = "Birthday",
            costInput = "12abc",
            liked = null,
            notes = ""
        )
        advanceUntilIdle()

        assertEquals(false, accepted)
        assertEquals(R.string.gift_advisor_error_invalid_cost, viewModel.uiState.value.errorMessageRes)
        coVerify(exactly = 0) { giftHistoryRepository.upsert(any()) }
    }

    @Test
    fun `addGiftRecord rejects overlong notes without persisting`() = runTest(testDispatcher) {
        coEvery { contactRepository.getById("contact_1") } returns ContactEntity(id = "contact_1", name = "John Doe")
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns emptyList()

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        val accepted = viewModel.addGiftRecord(
            name = "Headphones",
            category = "Tech",
            occasion = "Birthday",
            costInput = "1200",
            liked = null,
            notes = "x".repeat(GiftAdvisorViewModel.MAX_NOTES_LENGTH + 1)
        )
        advanceUntilIdle()

        assertEquals(false, accepted)
        assertEquals(R.string.gift_advisor_error_notes_too_long, viewModel.uiState.value.errorMessageRes)
        coVerify(exactly = 0) { giftHistoryRepository.upsert(any()) }
    }

    @Test
    fun `deleteGiftRecord deletes gift and reloads history`() = runTest(testDispatcher) {
        val gift = GiftHistoryEntity(
            id = "gift_1",
            contactId = "contact_1",
            giftName = "Book",
            giftCategory = "Books",
            occasionType = "Birthday",
            year = Calendar.getInstance().get(Calendar.YEAR),
            approxCostInr = 400,
        )
        coEvery { contactRepository.getById("contact_1") } returns ContactEntity(id = "contact_1", name = "John Doe")
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns listOf(gift)

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.deleteGiftRecord(gift)
        advanceUntilIdle()

        coVerify { giftHistoryRepository.delete(gift) }
        coVerify(atLeast = 2) { giftHistoryRepository.getByContact("contact_1") }
    }

    @Test
    fun `generateGiftSuggestions without contact exposes stable error`() = runTest(testDispatcher) {
        coEvery { contactRepository.getById("contact_1") } returns null
        coEvery { giftHistoryRepository.getByContact("contact_1") } returns emptyList()

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.generateGiftSuggestions()
        advanceUntilIdle()

        assertEquals(R.string.gift_advisor_error_missing_contact, viewModel.uiState.value.errorMessageRes)
        coVerify(exactly = 0) { aiService.generateGiftSuggestions(any(), any()) }
    }
}
