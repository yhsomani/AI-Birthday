package com.example.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import com.example.R
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.MessageRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatHistoryViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var messageRepository: MessageRepository

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
    fun `loadHistory exposes sent messages`() = runTest(testDispatcher) {
        val sentMessage = sentMessage()
        coEvery { messageRepository.getSentByContact("contact_1", 100) } returns listOf(sentMessage)

        val viewModel = ChatHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1")),
            messageRepository = messageRepository,
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(listOf(sentMessage), viewModel.uiState.value.messages)
        assertEquals(null, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `loadHistory exposes stable error when repository fails`() = runTest(testDispatcher) {
        coEvery { messageRepository.getSentByContact("contact_1", 100) } throws IllegalStateException("db locked")

        val viewModel = ChatHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1")),
            messageRepository = messageRepository,
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(emptyList<SentMessageEntity>(), viewModel.uiState.value.messages)
        assertEquals(R.string.chat_history_error_load, viewModel.uiState.value.errorMessageRes)
    }

    private fun sentMessage(): SentMessageEntity = SentMessageEntity(
        id = "sent_1",
        contactId = "contact_1",
        eventType = "BIRTHDAY",
        eventYear = 2026,
        messageText = "Happy birthday!",
        channel = "WHATSAPP",
        sentAtMs = 1_700_000_000_000L,
        deliveryStatus = "SENT",
    )
}
