package com.example.ui.viewmodel

import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.usecase.GetAnalyticsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `zero sent messages keeps monthly chart empty`() = runTest(dispatcher) {
        every { messageRepository.countAllSent() } returns MutableStateFlow(0)
        every { messageRepository.countPending() } returns MutableStateFlow(0)
        every { contactRepository.countAll() } returns MutableStateFlow(0)
        every { contactRepository.countByRelationshipType() } returns MutableStateFlow(emptyList())
        coEvery { contactRepository.getTopByHealthScore(5) } returns emptyList()
        coEvery { contactRepository.getBottomByHealthScore(5) } returns emptyList()
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { eventRepository.getUpcoming(30) } returns emptyList()
        coEvery { messageRepository.getSentSinceYearStart(any()) } returns emptyList()

        val viewModel = AnalyticsViewModel(
            getAnalyticsUseCase = GetAnalyticsUseCase(contactRepository, messageRepository),
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            messageRepository = messageRepository,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(emptyList<Pair<String, Float>>(), state.monthlyCounts)
    }
}
