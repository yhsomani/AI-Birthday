package com.example.ui.screens.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.MessageSort
import com.example.ui.viewmodel.MessagesUiState
import com.example.ui.viewmodel.PendingMessageItem
import com.example.ui.viewmodel.SentMessageItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MessagesScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabsSearchFiltersAndSort_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(messagesState())

        composeRule.setMessagesContent(
            state = { state },
            onSearchQueryChange = {
                actions += "search:$it"
                state = state.copy(searchQuery = it)
            },
            onChannelFilterSelected = {
                actions += "filter:${it.name}"
                state = state.copy(selectedChannelFilter = it)
            },
            onSortSelected = {
                actions += "sort:${it.name}"
                state = state.copy(selectedSort = it)
            },
        )

        composeRule.onNodeWithTag(MessagesTestTags.SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("alice")
        composeRule.onNodeWithTag(MessagesTestTags.CHANNEL_FILTER_PREFIX + MessageChannelFilter.EMAIL.name)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.SORT_PREFIX + MessageSort.CONTACT_ASC.name)
            .assertIsDisplayed()
            .performClick()

        assertMessageCardVisible(MessagesTestTags.PENDING_CARD_PREFIX + PENDING_ID, tabIndex = 1)
        assertMessageCardVisible(MessagesTestTags.APPROVED_CARD_PREFIX + APPROVED_ID, tabIndex = 2)
        assertMessageCardVisible(MessagesTestTags.SENT_CARD_PREFIX + SENT_ID, tabIndex = 3)
        assertMessageCardVisible(MessagesTestTags.FAILED_CARD_PREFIX + FAILED_ID, tabIndex = 4)

        assertEquals(
            listOf(
                "search:alice",
                "filter:EMAIL",
                "sort:CONTACT_ASC",
            ),
            actions,
        )
    }

    @Test
    fun pendingRowActionsAndRejectDialog_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()

        composeRule.setMessagesContent(
            state = { messagesState() },
            onNavigateToWish = { contactId, messageId -> actions += "edit:$contactId:$messageId" },
            onApproveMessage = { actions += "approve:$it" },
            onRejectMessage = { actions += "reject:$it" },
        )

        clickScrollableTag(MessagesTestTags.PENDING_EDIT_PREFIX + TODAY_ID)
        clickScrollableTag(MessagesTestTags.PENDING_APPROVE_PREFIX + TODAY_ID)
        clickScrollableTag(MessagesTestTags.PENDING_REJECT_PREFIX + TODAY_ID)
        composeRule.onNodeWithTag(MessagesTestTags.REJECT_DIALOG)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesTestTags.REJECT_CONFIRM)
            .performClick()

        assertEquals(
            listOf(
                "edit:contact-today:$TODAY_ID",
                "approve:$TODAY_ID",
                "reject:$TODAY_ID",
            ),
            actions,
        )
    }

    @Test
    fun selectionBulkApprovedAndFailedActions_dispatchExpectedCallbacks() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(messagesState())

        composeRule.setMessagesContent(
            state = { state },
            onToggleSelection = {
                actions += "toggle:$it"
                state = state.copy(
                    selectedMessageIds = if (it in state.selectedMessageIds) {
                        state.selectedMessageIds - it
                    } else {
                        state.selectedMessageIds + it
                    },
                )
            },
            onBulkApproveSelected = { actions += "bulkApprove" },
            onBulkRejectSelected = { actions += "bulkReject" },
            onClearSelection = {
                actions += "clear"
                state = state.copy(selectedMessageIds = emptySet())
            },
            onNavigateToWish = { contactId, messageId -> actions += "approvedEdit:$contactId:$messageId" },
            onRevokeApproval = { actions += "revoke:$it" },
            onRejectMessage = { actions += "reject:$it" },
            onRetryMessage = { actions += "retry:$it" },
            onBulkRetrySelected = { actions += "bulkRetry" },
        )

        composeRule.onNodeWithTag(MessagesTestTags.SELECT_PREFIX + TODAY_ID)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.BULK_BAR)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesTestTags.BULK_APPROVE)
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.BULK_REJECT)
            .performClick()
        composeRule.onNodeWithTag(MessagesTestTags.BULK_CLEAR)
            .performClick()

        assertMessageCardVisible(MessagesTestTags.APPROVED_CARD_PREFIX + APPROVED_ID, tabIndex = 2)
        clickScrollableTag(MessagesTestTags.APPROVED_EDIT_PREFIX + APPROVED_ID)
        clickScrollableTag(MessagesTestTags.APPROVED_REVOKE_PREFIX + APPROVED_ID)
        clickScrollableTag(MessagesTestTags.APPROVED_REJECT_PREFIX + APPROVED_ID)
        composeRule.onNodeWithTag(MessagesTestTags.REJECT_CONFIRM)
            .performClick()

        assertMessageCardVisible(MessagesTestTags.FAILED_CARD_PREFIX + FAILED_ID, tabIndex = 4)
        clickScrollableTag(MessagesTestTags.FAILED_RETRY_PREFIX + FAILED_ID)

        assertEquals(
            listOf(
                "toggle:$TODAY_ID",
                "bulkApprove",
                "bulkReject",
                "clear",
                "approvedEdit:contact-approved:$APPROVED_ID",
                "revoke:$APPROVED_ID",
                "reject:$APPROVED_ID",
                "retry:$FAILED_ID",
            ),
            actions,
        )
    }

    @Test
    fun failedBulkRetry_dispatchesExpectedCallback() {
        val actions = mutableListOf<String>()

        composeRule.setMessagesContent(
            state = { messagesState().copy(selectedMessageIds = setOf(FAILED_ID)) },
            initialPage = 4,
            onBulkRetrySelected = { actions += "bulkRetry" },
        )

        composeRule.onNodeWithTag(MessagesTestTags.BULK_RETRY)
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("bulkRetry"), actions)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setMessagesContent(
        state: () -> MessagesUiState,
        initialPage: Int = 0,
        onNavigateToWish: (String, String) -> Unit = { _, _ -> },
        onSearchQueryChange: (String) -> Unit = {},
        onChannelFilterSelected: (MessageChannelFilter) -> Unit = {},
        onSortSelected: (MessageSort) -> Unit = {},
        onApproveMessage: (String) -> Unit = {},
        onRejectMessage: (String) -> Unit = {},
        onRetryMessage: (String) -> Unit = {},
        onRevokeApproval: (String) -> Unit = {},
        onBulkApproveSelected: () -> Unit = {},
        onBulkRejectSelected: () -> Unit = {},
        onBulkRetrySelected: () -> Unit = {},
        onClearSelection: () -> Unit = {},
        onToggleSelection: (String) -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                MessagesContent(
                    state = state(),
                    initialPage = initialPage,
                    onNavigateToWish = onNavigateToWish,
                    onSearchQueryChange = onSearchQueryChange,
                    onChannelFilterSelected = onChannelFilterSelected,
                    onSortSelected = onSortSelected,
                    onApproveMessage = onApproveMessage,
                    onRejectMessage = onRejectMessage,
                    onRetryMessage = onRetryMessage,
                    onRevokeApproval = onRevokeApproval,
                    onBulkApproveSelected = onBulkApproveSelected,
                    onBulkRejectSelected = onBulkRejectSelected,
                    onBulkRetrySelected = onBulkRetrySelected,
                    onClearSelection = onClearSelection,
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }

    private fun assertMessageCardVisible(tag: String, tabIndex: Int) {
        composeRule.onNodeWithTag(MessagesTestTags.TAB_PREFIX + tabIndex)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun clickScrollableTag(tag: String) {
        composeRule.onNodeWithTag(tag)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    private fun messagesState(): MessagesUiState = MessagesUiState(
        todayMessages = listOf(pendingItem(TODAY_ID, "contact-today", "Tara", "SMS", "BIRTHDAY")),
        pendingMessages = listOf(pendingItem(PENDING_ID, "contact-pending", "Alice", "EMAIL", "ANNIVERSARY")),
        approvedMessages = listOf(
            pendingItem(
                APPROVED_ID,
                "contact-approved",
                "Dev",
                "WHATSAPP",
                "WORK_ANNIVERSARY",
                status = "APPROVED",
            ),
        ),
        sentMessages = listOf(sentItem()),
        failedMessages = listOf(pendingItem(FAILED_ID, "contact-failed", "Faye", "SMS", "BIRTHDAY", status = "FAILED")),
        isLoading = false,
    )

    private fun pendingItem(
        id: String,
        contactId: String,
        contactName: String,
        channel: String,
        eventType: String,
        status: String = "PENDING",
    ) = PendingMessageItem(
        entity = PendingMessageEntity(
            id = id,
            contactId = contactId,
            eventId = "event-$id",
            shortVariant = "short $id",
            standardVariant = "standard $id",
            longVariant = "long $id",
            formalVariant = "formal $id",
            funnyVariant = "funny $id",
            emotionalVariant = "emotional $id",
            selectedVariant = "standard",
            selectedVariantText = "standard $id",
            channel = channel,
            scheduledForMs = 1_800_000_000_000L,
            approvalMode = "MANUAL",
            status = status,
        ),
        contactName = contactName,
        eventType = eventType,
    )

    private fun sentItem() = SentMessageItem(
        entity = SentMessageEntity(
            id = SENT_ID,
            contactId = "contact-sent",
            eventType = "BIRTHDAY",
            eventYear = 2026,
            messageText = "Sent message",
            channel = "EMAIL",
            sentAtMs = 1_800_000_000_000L,
            deliveryStatus = "SENT",
        ),
        contactName = "Sam",
    )

    private companion object {
        const val TODAY_ID = "today-1"
        const val PENDING_ID = "pending-1"
        const val APPROVED_ID = "approved-1"
        const val SENT_ID = "sent-1"
        const val FAILED_ID = "failed-1"
    }
}
