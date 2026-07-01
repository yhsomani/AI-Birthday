package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.R
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.service.AiService
import com.example.domain.service.GiftSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        stubGiftAdvisorFlows(contact = contactProfile(), history = emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData calculates budget stats correctly`() = runTest(testDispatcher) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val contact = contactProfile(giftBudgetInr = 1000)
        val history = listOf(
            giftRecord(
                id = "gift_1",
                giftName = "Gadget",
                giftCategory = "Tech",
                occasionType = "Birthday",
                year = currentYear,
                approxCostInr = 400
            ),
            giftRecord(
                id = "gift_2",
                giftName = "Book",
                giftCategory = "Books",
                occasionType = "Christmas",
                year = currentYear - 1, // Last year
                approxCostInr = 300
            )
        )

        stubGiftAdvisorFlows(contact = contact, history = history)

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
        every { contactRepository.getGiftAdvisorProfileFlow("contact_1") } throws IllegalStateException("boom")

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(R.string.gift_advisor_error_load, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `generateGiftSuggestions updates state with suggestions`() = runTest(testDispatcher) {
        val contact = contactProfile(giftBudgetInr = 1000)
        val suggestions = listOf(
            GiftSuggestion("AI Suggestion 1", "Reason 1", 500),
            GiftSuggestion("AI Suggestion 2", "Reason 2", 800)
        )

        stubGiftAdvisorFlows(contact = contact, history = emptyList())
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
    fun `generateGiftSuggestions annotates duplicate and budget evidence`() = runTest(testDispatcher) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val contact = contactProfile(giftBudgetInr = 1000)
        val history = listOf(
            giftRecord(
                id = "gift_1",
                giftName = "Noise-canceling headphones",
                year = currentYear,
                approxCostInr = 800,
            ),
        )
        val suggestions = listOf(
            GiftSuggestion(
                name = "Noise canceling headphones",
                reason = "They would help with commute and work calls.",
                estimatedCostInr = 1200,
            ),
        )

        stubGiftAdvisorFlows(contact = contact, history = history)
        coEvery { aiService.generateGiftSuggestions(any(), any()) } returns suggestions

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.generateGiftSuggestions()
        advanceUntilIdle()

        val suggestion = viewModel.uiState.value.suggestions.single()
        assertEquals("Noise canceling headphones", suggestion.name)
        assertEquals("Noise-canceling headphones", suggestion.duplicateGiftName)
        assertEquals(GiftSuggestionBudgetStatus.OVER_REMAINING_BUDGET, suggestion.budgetStatus)
        assertEquals(1000, suggestion.budgetOverageInr)
        assertEquals(true, suggestion.checkedAgainstHistory)
        assertEquals(40, suggestion.confidencePercent)
    }

    @Test
    fun `dismissGiftSuggestion removes selected suggestion without affecting others`() = runTest(testDispatcher) {
        val contact = contactProfile(giftBudgetInr = 1000)
        val suggestions = listOf(
            GiftSuggestion("AI Suggestion 1", "Reason 1", 500),
            GiftSuggestion("AI Suggestion 2", "Reason 2", 800),
        )

        stubGiftAdvisorFlows(contact = contact, history = emptyList())
        coEvery { aiService.generateGiftSuggestions(any(), any()) } returns suggestions

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()
        viewModel.generateGiftSuggestions()
        advanceUntilIdle()

        viewModel.dismissGiftSuggestion(0)

        assertEquals(1, viewModel.uiState.value.suggestions.size)
        assertEquals("AI Suggestion 2", viewModel.uiState.value.suggestions.single().name)

        viewModel.dismissGiftSuggestion(10)

        assertEquals(1, viewModel.uiState.value.suggestions.size)
    }

    @Test
    fun `generateGiftSuggestions exposes stable error when ai fails`() = runTest(testDispatcher) {
        val contact = contactProfile(giftBudgetInr = 1000)

        stubGiftAdvisorFlows(contact = contact, history = emptyList())
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
        val contact = contactProfile(giftBudgetInr = 5000)

        stubGiftAdvisorFlows(contact = contact, history = emptyList())

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
            giftHistoryRepository.upsertRecord(
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
        coVerify(exactly = 0) { giftHistoryRepository.upsertRecord(any()) }
    }

    @Test
    fun `addGiftRecord rejects overlong notes without persisting`() = runTest(testDispatcher) {
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
        coVerify(exactly = 0) { giftHistoryRepository.upsertRecord(any()) }
    }

    @Test
    fun `deleteGiftRecord deletes gift and reloads history`() = runTest(testDispatcher) {
        val gift = giftRecord(
            id = "gift_1",
            giftName = "Book",
            giftCategory = "Books",
            occasionType = "Birthday",
            year = Calendar.getInstance().get(Calendar.YEAR),
            approxCostInr = 400,
        )
        stubGiftAdvisorFlows(contact = contactProfile(), history = listOf(gift))

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.deleteGiftRecord(gift)
        advanceUntilIdle()

        coVerify { giftHistoryRepository.deleteRecord(GiftHistoryId("gift_1")) }
    }

    @Test
    fun `generateGiftSuggestions without contact exposes stable error`() = runTest(testDispatcher) {
        stubGiftAdvisorFlows(contact = null, history = emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        viewModel.generateGiftSuggestions()
        advanceUntilIdle()

        assertEquals(R.string.gift_advisor_error_missing_contact, viewModel.uiState.value.errorMessageRes)
        coVerify(exactly = 0) { aiService.generateGiftSuggestions(any(), any()) }
    }

    @Test
    fun `gift history flow update immediately updates budget state`() = runTest(testDispatcher) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val historyFlow = MutableStateFlow<List<GiftHistoryRecord>>(emptyList())
        every { contactRepository.getGiftAdvisorProfileFlow("contact_1") } returns flowOf(contactProfile(giftBudgetInr = 1000))
        every { giftHistoryRepository.getRecordsByContactFlow("contact_1") } returns historyFlow

        val savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1"))
        val viewModel = GiftAdvisorViewModel(savedStateHandle, contactRepository, giftHistoryRepository, aiService)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.totalSpentThisYear)
        assertEquals(1000, viewModel.uiState.value.remainingBudget)

        historyFlow.value = listOf(
            giftRecord(id = "gift_1", year = currentYear, approxCostInr = 350),
            giftRecord(id = "gift_2", year = currentYear - 1, approxCostInr = 200),
        )
        advanceUntilIdle()

        assertEquals(350, viewModel.uiState.value.totalSpentThisYear)
        assertEquals(650, viewModel.uiState.value.remainingBudget)
        assertEquals(2, viewModel.uiState.value.giftHistory.size)
    }

    private fun stubGiftAdvisorFlows(
        contact: ContactGiftAdvisorProfile?,
        history: List<GiftHistoryRecord>,
    ) {
        every { contactRepository.getGiftAdvisorProfileFlow("contact_1") } returns flowOf(contact)
        every { giftHistoryRepository.getRecordsByContactFlow("contact_1") } returns flowOf(history)
    }

    private fun giftRecord(
        id: String = "gift_1",
        year: Int = Calendar.getInstance().get(Calendar.YEAR),
        approxCostInr: Int = 400,
        giftName: String = "Book",
        giftCategory: String = "Books",
        occasionType: String = "Birthday",
        receivedWell: Boolean? = null,
        notes: String = "",
    ): GiftHistoryRecord {
        return GiftHistoryRecord(
            id = GiftHistoryId(id),
            contactId = ContactId("contact_1"),
            giftName = giftName,
            giftCategory = giftCategory,
            occasionType = occasionType,
            year = year,
            approxCostInr = approxCostInr,
            receivedWell = receivedWell,
            notes = notes,
        )
    }

    private fun contactProfile(
        giftBudgetInr: Int = 500,
    ): ContactGiftAdvisorProfile {
        return ContactGiftAdvisorProfile(
            id = ContactId("contact_1"),
            displayName = "John Doe",
            nickname = null,
            relationshipType = "FRIEND",
            interestsJson = "[]",
            giftBudgetInr = giftBudgetInr,
        )
    }
}
