package com.example.ui.screens.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.message.ChatHistoryMessageItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ChatHistoryScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun populatedHistoryAndBack_renderAndDispatchExpectedCallback() {
        val actions = mutableListOf<String>()

        composeRule.setChatHistoryContent(
            uiState = {
                ChatHistoryUiState(
                    isLoading = false,
                    contactId = "contact_1",
                    messages = listOf(sentMessage()),
                )
            },
            onBack = { actions += "back" },
        )

        composeRule.onNodeWithTag(ChatHistoryTestTags.MESSAGE_PREFIX + "sent_1")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Happy birthday!")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("back"), actions)
    }

    @Test
    fun loadingEmptyAndErrorStates_renderExpectedContent() {
        var state by mutableStateOf(ChatHistoryUiState(isLoading = true, contactId = "contact_1"))

        composeRule.setChatHistoryContent(uiState = { state })

        composeRule.onNodeWithTag(ChatHistoryTestTags.LOADING)
            .assertIsDisplayed()

        state = ChatHistoryUiState(isLoading = false, contactId = "contact_1", messages = emptyList())
        composeRule.onNodeWithTag(ChatHistoryTestTags.EMPTY)
            .assertIsDisplayed()

        state = ChatHistoryUiState(
            isLoading = false,
            contactId = "contact_1",
            errorMessageRes = R.string.chat_history_error_load,
        )
        composeRule.onNodeWithTag(ChatHistoryTestTags.ERROR)
            .assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setChatHistoryContent(
        uiState: () -> ChatHistoryUiState,
        onBack: () -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                ChatHistoryContent(
                    uiState = uiState(),
                    onBack = onBack,
                    onSearchQueryChange = onSearchQueryChange,
                )
            }
        }
    }

    @Test
    fun searchFiltersHistoryAndShowsEmptyResultState() {
        var query by mutableStateOf("")
        val allMessages = listOf(
            sentMessage(
                id = "sent_1",
                messageText = "Happy birthday!",
                channel = MessageChannel.WHATSAPP,
            ),
            sentMessage(
                id = "sent_2",
                messageText = "Project milestone note",
                channel = MessageChannel.EMAIL,
            ),
        )

        composeRule.setChatHistoryContent(
            uiState = {
                val visibleMessages = if (query.isBlank()) {
                    allMessages
                } else {
                    allMessages.filter {
                        it.messageText.contains(query, ignoreCase = true) ||
                            it.channel.raw.contains(query, ignoreCase = true)
                    }
                }
                ChatHistoryUiState(
                    isLoading = false,
                    contactId = "contact_1",
                    messages = visibleMessages,
                    totalMessageCount = allMessages.size,
                    searchQuery = query,
                )
            },
            onSearchQueryChange = { query = it },
        )

        composeRule.onNodeWithTag(ChatHistoryTestTags.SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("email")
        composeRule.onNodeWithText("Project milestone note")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Happy birthday!")
            .assertCountEquals(0)

        composeRule.onNodeWithTag(ChatHistoryTestTags.SEARCH_CLEAR)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Happy birthday!")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(ChatHistoryTestTags.SEARCH_FIELD)
            .performTextInput("missing")
        composeRule.onNodeWithTag(ChatHistoryTestTags.EMPTY_SEARCH)
            .assertIsDisplayed()
    }

    private fun sentMessage(
        id: String = "sent_1",
        messageText: String = "Happy birthday!",
        channel: MessageChannel = MessageChannel.WHATSAPP,
    ): ChatHistoryMessageItem = ChatHistoryMessageItem(
        id = SentMessageId(id),
        messageText = messageText,
        channel = channel,
        sentAtMs = 1_700_000_000_000L,
    )
}
