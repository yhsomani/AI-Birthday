package com.example.ui.screens.events

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.ui.theme.RelateAITheme
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.EventTrustConflictState
import com.example.ui.viewmodel.EventTrustState
import com.example.ui.viewmodel.EventVerificationState
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventsScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun eventRowsShowManualImportedAndVerificationState() {
        composeRule.setContent {
            RelateAITheme {
                EventsList(
                    events = listOf(
                        event(id = "manual", source = "MANUAL", isVerified = true),
                        event(id = "imported", source = "CONTACTS", isVerified = false, confidenceScore = 62),
                    ),
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.event_source_manual)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.event_verification_verified)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.event_source_contacts)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.event_verification_needs_review, 62)).assertIsDisplayed()
    }

    @Test
    fun eventRowsShowMergedConflictAndVerificationState() {
        composeRule.setContent {
            RelateAITheme {
                EventsList(
                    events = listOf(
                        event(id = "merged", source = "MERGED", isVerified = false, confidenceScore = 84),
                        event(id = "conflict", source = "CONFLICT", isVerified = false, confidenceScore = 35),
                    ),
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.event_source_merged)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.event_verification_needs_review, 84)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.event_source_conflict)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.event_verification_conflict)).assertIsDisplayed()
    }

    @Test
    fun eventRowsShowDerivedDateConflictStateForSeparateActiveEvents() {
        composeRule.setContent {
            RelateAITheme {
                EventsList(
                    events = listOf(
                        event(
                            id = "imported",
                            contactId = "c1",
                            month = 6,
                            dayOfMonth = 12,
                            source = "CONTACTS",
                            isVerified = true,
                        ),
                        event(
                            id = "manual",
                            contactId = "c1",
                            month = 7,
                            dayOfMonth = 1,
                            source = "MANUAL",
                            isVerified = true,
                        ),
                    ),
                )
            }
        }

        composeRule
            .onAllNodesWithText(context.getString(R.string.event_verification_conflict))
            .assertCountEquals(2)
        composeRule
            .onAllNodesWithText(context.getString(R.string.event_conflict_date))
            .assertCountEquals(2)
    }

    @Test
    fun eventRowsShowDerivedDuplicateConflictStateForSameDateEvents() {
        composeRule.setContent {
            RelateAITheme {
                EventsList(
                    events = listOf(
                        event(
                            id = "first",
                            contactId = "c1",
                            source = "CONTACTS",
                            isVerified = true,
                        ),
                        event(
                            id = "second",
                            contactId = "c1",
                            source = "MANUAL",
                            isVerified = true,
                        ),
                    ),
                )
            }
        }

        composeRule
            .onAllNodesWithText(context.getString(R.string.event_verification_conflict))
            .assertCountEquals(2)
        composeRule
            .onAllNodesWithText(context.getString(R.string.event_conflict_duplicate))
            .assertCountEquals(2)
    }

    @Test
    fun eventRowsExposeMergeAndKeepSeparateActionsForConflicts() {
        var mergedEventId: String? = null
        var keptEventId: String? = null
        val event = event(id = "manual", source = "MANUAL", isVerified = true)

        composeRule.setContent {
            RelateAITheme {
                EventsList(
                    events = listOf(event),
                    eventTrust = mapOf(
                        event.id.value to EventTrustState(
                            source = event.source,
                            verification = EventVerificationState.CONFLICT,
                            confidenceScore = 100,
                            conflict = EventTrustConflictState.DATE_CONFLICT,
                        )
                    ),
                    onMergeEvent = { mergedEventId = it },
                    onKeepSeparateEvent = { keptEventId = it },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.event_resolution_merge_here))
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.event_resolution_keep_separate))
            .assertIsDisplayed()
            .performClick()

        assertEquals(event.id.value, mergedEventId)
        assertEquals(event.id.value, keptEventId)
    }

    private fun event(
        id: String,
        contactId: String = "contact_$id",
        month: Int = 1,
        dayOfMonth: Int = 1,
        source: String,
        isVerified: Boolean,
        confidenceScore: Int = 100,
    ): EventListItem {
        val nextOccurrenceMs = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 10)
        }.timeInMillis

        return EventListItem(
            id = OccasionId(id),
            contactId = ContactId(contactId),
            type = OccasionType.BIRTHDAY,
            label = "Birthday $id",
            dayOfMonth = dayOfMonth,
            month = month,
            year = null,
            nextOccurrenceMs = nextOccurrenceMs,
            isActive = true,
            notifyDaysBefore = 1,
            source = source,
            confidenceScore = confidenceScore,
            isVerified = isVerified,
        )
    }
}
