package com.example.ui.screens.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.SentMessageEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.MessageChannel
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
    ) {
        setContent {
            RelateAITheme {
                ChatHistoryContent(
                    uiState = uiState(),
                    onBack = onBack,
                )
            }
        }
    }

    private fun sentMessage(): SentMessageEntity = SentMessageEntity(
        id = "sent_1",
        contactId = "contact_1",
        eventType = "BIRTHDAY",
        eventYear = 2026,
        messageText = "Happy birthday!",
        channel = MessageChannel.WHATSAPP.raw,
        sentAtMs = 1_700_000_000_000L,
        deliveryStatus = "SENT",
    )
}
