package com.example.ui.screens.events

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.EventEntity
import com.example.core.ui.theme.RelateAITheme
import java.util.Calendar
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

    private fun event(
        id: String,
        source: String,
        isVerified: Boolean,
        confidenceScore: Int = 100,
    ): EventEntity {
        val nextOccurrenceMs = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 10)
        }.timeInMillis

        return EventEntity(
            id = id,
            contactId = "contact_$id",
            type = "BIRTHDAY",
            label = "Birthday $id",
            dayOfMonth = 1,
            month = 1,
            nextOccurrenceMs = nextOccurrenceMs,
            source = source,
            confidenceScore = confidenceScore,
            isVerified = isVerified,
        )
    }
}
