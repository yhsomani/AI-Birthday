package com.example.ui.screens.stylecoach

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.core.ui.theme.RelateAITheme
import com.example.ui.viewmodel.StyleCoachUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StyleCoachScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sampleTrainingAutoAnalysisBackProfileAndHistory_dispatchAndRenderExpectedState() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(
            StyleCoachUiState(
                profile = styleProfile(),
                history = listOf(styleHistory()),
            )
        )
        var samplesText by mutableStateOf("")

        composeRule.setStyleCoachContent(
            state = { state },
            samplesText = { samplesText },
            onSamplesChange = { samplesText = it },
            onBack = { actions += "back" },
            onManualAnalyze = { samples -> actions += "manual:${samples.joinToString("|")}" },
            onAutoAnalyze = { actions += "auto" },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(StyleCoachTestTags.SAMPLE_FIELD)
            .assertIsDisplayed()
            .performTextInput("Hey friend\n\nRespectfully wishing you well")
        composeRule.onNodeWithTag(StyleCoachTestTags.AUTO_ANALYZE_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(StyleCoachTestTags.MANUAL_ANALYZE_BUTTON)
            .assertIsDisplayed()
            .performClick()

        state = state.copy(
            statusMessageRes = R.string.style_coach_status_manual_success,
            statusIsError = false,
        )
        composeRule.onNodeWithTag(StyleCoachTestTags.STATUS_MESSAGE)
            .assertIsDisplayed()
        composeRule.assertLazyItemVisible(StyleCoachTestTags.PROFILE_CARD)
        composeRule.onNodeWithText(context.getString(R.string.style_coach_language_hindi))
            .assertIsDisplayed()
        composeRule.assertLazyItemVisible(StyleCoachTestTags.HISTORY_CARD_PREFIX + 7)
        composeRule.onNodeWithText(context.getString(R.string.style_coach_source_manual))
            .assertIsDisplayed()

        assertEquals(
            listOf(
                "back",
                "auto",
                "manual:Hey friend|Respectfully wishing you well",
            ),
            actions,
        )
    }

    @Test
    fun blankBusyAndEmptyStates_renderExpectedControls() {
        var state by mutableStateOf(StyleCoachUiState())
        var samplesText by mutableStateOf("")

        composeRule.setStyleCoachContent(
            state = { state },
            samplesText = { samplesText },
            onSamplesChange = { samplesText = it },
        )

        composeRule.onNodeWithTag(StyleCoachTestTags.MANUAL_ANALYZE_BUTTON)
            .assertIsNotEnabled()
        composeRule.assertLazyItemVisible(StyleCoachTestTags.EMPTY_HISTORY)

        samplesText = "One sample"
        state = StyleCoachUiState(isTraining = true)
        composeRule.onNodeWithTag(StyleCoachTestTags.MANUAL_PROGRESS)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(StyleCoachTestTags.AUTO_ANALYZE_BUTTON)
            .assertIsNotEnabled()

        state = StyleCoachUiState(isAutoAnalyzing = true)
        composeRule.onNodeWithTag(StyleCoachTestTags.AUTO_PROGRESS)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(StyleCoachTestTags.MANUAL_ANALYZE_BUTTON)
            .assertIsNotEnabled()

        state = StyleCoachUiState(
            statusMessageRes = R.string.style_coach_error_auto_failed,
            statusIsError = true,
        )
        composeRule.onNodeWithTag(StyleCoachTestTags.STATUS_MESSAGE)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.style_coach_error_auto_failed))
            .assertIsDisplayed()
    }

    private fun ComposeContentTestRule.setStyleCoachContent(
        state: () -> StyleCoachUiState,
        samplesText: () -> String,
        onSamplesChange: (String) -> Unit = {},
        onBack: () -> Unit = {},
        onManualAnalyze: (List<String>) -> Unit = {},
        onAutoAnalyze: () -> Unit = {},
    ) {
        setContent {
            RelateAITheme {
                StyleCoachContent(
                    uiState = state(),
                    samplesText = samplesText(),
                    onSamplesChange = onSamplesChange,
                    onBack = onBack,
                    onManualAnalyze = onManualAnalyze,
                    onAutoAnalyze = onAutoAnalyze,
                )
            }
        }
    }

    private fun ComposeContentTestRule.assertLazyItemVisible(tag: String) {
        onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun styleProfile() = StyleProfileEntity(
        usesEmoji = false,
        avgMessageLength = 42,
        commonGreetingsJson = """["Hey","Namaste"]""",
        formalityLevel = "CASUAL",
        preferredLanguage = "hi",
        emojiSetJson = "[]",
        sampleCount = 6,
    )

    private fun styleHistory() = StyleProfileHistoryEntity(
        id = 7,
        profileJson = """{"formalityLevel":"CASUAL","preferredLanguage":"hi","avgMessageLength":42}""",
        savedAtMs = 1_700_000_000_000L,
        source = "MANUAL_TRAINING",
    )
}
