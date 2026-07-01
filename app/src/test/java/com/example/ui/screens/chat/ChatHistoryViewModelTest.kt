package com.example.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import com.example.R
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.message.ChatHistoryMessageItem
import com.example.domain.repository.MessageRepository
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var sentMessages: MutableStateFlow<List<SentMessageEntity>>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sentMessages = MutableStateFlow(emptyList())
        every { messageRepository.getSentByContactFlow("contact_1", 100) } returns sentMessages
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadHistory exposes sent messages`() = runTest(testDispatcher) {
        val sentMessage = sentMessage()
        every { messageRepository.getSentByContactFlow("contact_1", 100) } returns flowOf(listOf(sentMessage))

        val viewModel = ChatHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1")),
            messageRepository = messageRepository,
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(listOf(sentMessage.toChatHistoryItem()), viewModel.uiState.value.messages)
        assertEquals(1, viewModel.uiState.value.totalMessageCount)
        assertEquals(null, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `loadHistory exposes stable error when repository fails`() = runTest(testDispatcher) {
        every { messageRepository.getSentByContactFlow("contact_1", 100) } returns flow {
            throw IllegalStateException("db locked")
        }

        val viewModel = ChatHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1")),
            messageRepository = messageRepository,
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(emptyList<ChatHistoryMessageItem>(), viewModel.uiState.value.messages)
        assertEquals(0, viewModel.uiState.value.totalMessageCount)
        assertEquals(R.string.chat_history_error_load, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `sent message updates immediately refresh chat history`() = runTest(testDispatcher) {
        val viewModel = ChatHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1")),
            messageRepository = messageRepository,
        )
        advanceUntilIdle()
        assertEquals(emptyList<ChatHistoryMessageItem>(), viewModel.uiState.value.messages)

        val sentMessage = sentMessage()
        sentMessages.value = listOf(sentMessage)
        advanceUntilIdle()

        assertEquals(listOf(sentMessage.toChatHistoryItem()), viewModel.uiState.value.messages)
        assertEquals(1, viewModel.uiState.value.totalMessageCount)
        assertEquals(null, viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `updateSearchQuery filters visible messages and survives history updates`() = runTest(testDispatcher) {
        val birthday = sentMessage(
            id = "sent_1",
            messageText = "Happy birthday!",
            channel = MessageChannel.WHATSAPP.raw,
        )
        val project = sentMessage(
            id = "sent_2",
            messageText = "Project milestone note",
            channel = MessageChannel.EMAIL.raw,
        )
        sentMessages.value = listOf(birthday, project)
        val viewModel = ChatHistoryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("contactId" to "contact_1")),
            messageRepository = messageRepository,
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("project")

        assertEquals("project", viewModel.uiState.value.searchQuery)
        assertEquals(2, viewModel.uiState.value.totalMessageCount)
        assertEquals(listOf(project.toChatHistoryItem()), viewModel.uiState.value.messages)

        val projectFollowUp = sentMessage(
            id = "sent_3",
            messageText = "Project celebration follow-up",
            channel = MessageChannel.SMS.raw,
        )
        sentMessages.value = listOf(birthday, project, projectFollowUp)
        advanceUntilIdle()

        assertEquals("project", viewModel.uiState.value.searchQuery)
        assertEquals(3, viewModel.uiState.value.totalMessageCount)
        assertEquals(
            listOf(project.toChatHistoryItem(), projectFollowUp.toChatHistoryItem()),
            viewModel.uiState.value.messages,
        )
    }

    private fun sentMessage(
        id: String = "sent_1",
        messageText: String = "Happy birthday!",
        channel: String = MessageChannel.WHATSAPP.raw,
    ): SentMessageEntity = SentMessageEntity(
        id = id,
        contactId = "contact_1",
        eventType = "BIRTHDAY",
        eventYear = 2026,
        messageText = messageText,
        channel = channel,
        sentAtMs = 1_700_000_000_000L,
        deliveryStatus = "SENT",
    )

    private fun SentMessageEntity.toChatHistoryItem(): ChatHistoryMessageItem {
        return ChatHistoryMessageItem(
            id = SentMessageId(id),
            messageText = messageText,
            channel = MessageChannel.fromRaw(channel),
            sentAtMs = sentAtMs,
        )
    }
}
