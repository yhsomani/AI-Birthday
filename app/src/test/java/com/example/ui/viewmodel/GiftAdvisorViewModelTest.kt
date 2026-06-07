package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.service.AiService
import com.example.domain.service.GiftSuggestion
import io.mockk.coEvery
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
}
