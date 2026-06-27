package com.example.ui.screens.messages

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.SentMessageListItem
import com.example.ui.viewmodel.MessageChannelFilter
import com.example.ui.viewmodel.MessageReadiness
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
@Config(sdk = [35], application = Application::class)
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

        composeRule.onNodeWithText("Needs review (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Scheduled (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked (1)").assertIsDisplayed()

        assertMessageCardVisible(MessagesTestTags.PENDING_CARD_PREFIX + TODAY_ID, tabIndex = 0)
        assertMessageCardVisible(MessagesTestTags.APPROVED_CARD_PREFIX + APPROVED_ID, tabIndex = 1)
        assertMessageCardVisible(MessagesTestTags.BLOCKED_CARD_PREFIX + BLOCKED_ID, tabIndex = 2)
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

        assertMessageCardVisible(MessagesTestTags.APPROVED_CARD_PREFIX + APPROVED_ID, tabIndex = 1)
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

    @Test
    fun blockedTabShowsFixableDraftsWithoutApproveAction() {
        composeRule.setMessagesContent(
            state = { messagesState() },
            initialPage = 2,
        )

        composeRule.onNodeWithTag(MessagesTestTags.BLOCKED_CARD_PREFIX + BLOCKED_ID)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesTestTags.READINESS_PREFIX + BLOCKED_ID)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Email address missing")
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(MessagesTestTags.PENDING_APPROVE_PREFIX + BLOCKED_ID)
            .assertCountEquals(0)
    }

    @Test
    fun messageCards_renderChannelLabelsThroughMessageChannelParser() {
        composeRule.setMessagesContent(
            state = {
                messagesState().copy(
                    needsReviewMessages = listOf(
                        pendingItem(
                            TODAY_ID,
                            "contact-today",
                            "Tara",
                            " ${MessageChannel.WHATSAPP.raw.lowercase()} ",
                            "BIRTHDAY",
                        ),
                    ),
                    sentMessages = listOf(
                        sentItem(channel = " ${MessageChannel.EMAIL.raw.lowercase()} "),
                    ),
                )
            },
        )

        composeRule.onNodeWithTag(MessagesTestTags.CHANNEL_PREFIX + TODAY_ID)
            .assertIsDisplayed()
            .assertTextEquals("WhatsApp")

        assertMessageCardVisible(MessagesTestTags.SENT_CARD_PREFIX + SENT_ID, tabIndex = 3)
        composeRule.onNodeWithTag(MessagesTestTags.CHANNEL_PREFIX + SENT_ID)
            .assertIsDisplayed()
            .assertTextEquals("Channel: Email")
    }

    @Test
    fun failedRecoveryAssistant_opensAutomationSetup() {
        val actions = mutableListOf<String>()

        composeRule.setContent {
            RelateAITheme {
                FailedRecoveryAssistant(
                    messages = messagesState().failedMessages,
                    onOpenAutomationSetup = { actions += "automation" },
                    modifier = Modifier.testTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT),
                )
            }
        }

        composeRule.onNodeWithTag(MessagesTestTags.FAILED_RECOVERY_ASSISTANT)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Failed send recovery")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesTestTags.FAILED_RECOVERY_OPEN_SETUP)
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("automation"), actions)
    }

    @Test
    fun readinessBadges_renderExpectedStatuses() {
        composeRule.setMessagesContent(
            state = { messagesState() },
        )

        composeRule.onNodeWithTag(MessagesTestTags.READINESS_PREFIX + TODAY_ID)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ready for review")
            .assertIsDisplayed()

        assertMessageCardVisible(MessagesTestTags.APPROVED_CARD_PREFIX + APPROVED_ID, tabIndex = 1)
        composeRule.onNodeWithTag(MessagesTestTags.READINESS_PREFIX + APPROVED_ID)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Approved and scheduled")
            .assertIsDisplayed()

        assertMessageCardVisible(MessagesTestTags.FAILED_CARD_PREFIX + FAILED_ID, tabIndex = 4)
        composeRule.onNodeWithTag(MessagesTestTags.READINESS_PREFIX + FAILED_ID)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Failed - check setup before retry")
            .assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setMessagesContent(
        state: () -> MessagesUiState,
        initialPage: Int = 0,
        onNavigateToWish: (String, String) -> Unit = { _, _ -> },
        onNavigateToAutomationSetup: () -> Unit = {},
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
                    onNavigateToAutomationSetup = onNavigateToAutomationSetup,
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
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        if (tabIndex == 4) {
            composeRule.onNodeWithTag(tag).performScrollTo()
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
        needsReviewMessages = listOf(
            pendingItem(TODAY_ID, "contact-today", "Tara", MessageChannel.SMS.raw, "BIRTHDAY"),
        ),
        scheduledMessages = listOf(
            pendingItem(
                APPROVED_ID,
                "contact-approved",
                "Dev",
                MessageChannel.WHATSAPP.raw,
                "WORK_ANNIVERSARY",
                status = "APPROVED",
                readiness = MessageReadiness.APPROVED_SCHEDULED,
            ),
        ),
        blockedMessages = listOf(
            pendingItem(
                BLOCKED_ID,
                "contact-blocked",
                "Blake",
                MessageChannel.EMAIL.raw,
                "ANNIVERSARY",
                readiness = MessageReadiness.MISSING_EMAIL,
            ),
        ),
        sentMessages = listOf(sentItem()),
        failedMessages = listOf(
            pendingItem(
                FAILED_ID,
                "contact-failed",
                "Faye",
                MessageChannel.SMS.raw,
                "BIRTHDAY",
                status = "FAILED",
                readiness = MessageReadiness.FAILED_CHECK_SETUP,
            ),
        ),
        isLoading = false,
    )

    private fun pendingItem(
        id: String,
        contactId: String,
        contactName: String,
        channel: String,
        eventType: String,
        status: String = "PENDING",
        readiness: MessageReadiness = MessageReadiness.READY_FOR_REVIEW,
    ) = PendingMessageItem(
        message = PendingMessageListItem(
            id = MessageDraftId(id),
            contactId = ContactId(contactId),
            occasionId = OccasionId("event-$id"),
            selectedVariantText = "standard $id",
            standardVariant = "standard $id",
            channel = MessageChannel.fromRaw(channel),
            scheduledForMs = 1_800_000_000_000L,
            approvalMode = ApprovalMode.UNKNOWN,
            status = MessageStatus.fromRaw(status),
            editedByUser = false,
            userEditedText = null,
        ),
        contactName = contactName,
        eventType = eventType,
        readiness = readiness,
    )

    private fun sentItem(channel: String = MessageChannel.EMAIL.raw) = SentMessageItem(
        message = SentMessageListItem(
            id = SentMessageId(SENT_ID),
            contactId = ContactId("contact-sent"),
            occasionType = "BIRTHDAY",
            messageText = "Sent message",
            channel = MessageChannel.fromRaw(channel),
            sentAtMs = 1_800_000_000_000L,
            deliveryStatus = MessageDeliveryStatus.SENT,
        ),
        contactName = "Sam",
    )

    private companion object {
        const val TODAY_ID = "today-1"
        const val BLOCKED_ID = "blocked-1"
        const val APPROVED_ID = "approved-1"
        const val SENT_ID = "sent-1"
        const val FAILED_ID = "failed-1"
    }
}
