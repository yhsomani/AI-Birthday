package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.domain.model.style.StyleProfileHistoryRecord
import com.example.domain.model.style.StyleProfileRecord
import com.example.ui.screens.stylecoach.StyleCoachContent
import com.example.ui.screens.stylecoach.StyleCoachTestTags
import com.example.ui.viewmodel.StyleCoachUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StyleCoachScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun styleCoachTraining_compactPhone() {
        setStyleCoachContent(
            state = StyleCoachUiState(),
            samplesText = TrainingSamples,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_training_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun styleCoachTraining_typicalPhone() {
        setStyleCoachContent(
            state = StyleCoachUiState(),
            samplesText = TrainingSamples,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_training_typical_phone.png")
    }

    @Test
    fun styleCoachProfile_compactPhoneLargeFont() {
        setStyleCoachContent(
            state = populatedStyleCoachState(),
            samplesText = TrainingSamples,
            fontScale = LargeFontScale,
        )
        scrollTo(StyleCoachTestTags.PROFILE_CARD)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_profile_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun styleCoachProfile_typicalPhone() {
        setStyleCoachContent(
            state = populatedStyleCoachState(),
            samplesText = TrainingSamples,
        )
        scrollTo(StyleCoachTestTags.PROFILE_CARD)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_profile_typical_phone.png")
    }

    @Test
    fun styleCoachHistory_compactPhoneLargeFont() {
        setStyleCoachContent(
            state = populatedStyleCoachState(),
            samplesText = TrainingSamples,
            fontScale = LargeFontScale,
        )
        scrollToHistory(StyleCoachTestTags.HISTORY_CARD_PREFIX + 7)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_history_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun styleCoachHistory_typicalPhone() {
        setStyleCoachContent(
            state = populatedStyleCoachState(),
            samplesText = TrainingSamples,
        )
        scrollToHistory(StyleCoachTestTags.HISTORY_CARD_PREFIX + 7)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_history_typical_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun styleCoachProfile_compactPhoneHindiLargeFont() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("hi-IN"))
        try {
            setStyleCoachContent(
                state = populatedStyleCoachStateHindi(),
                samplesText = TrainingSamplesHindi,
                fontScale = LargeFontScale,
            )
            scrollTo(StyleCoachTestTags.PROFILE_CARD)

            composeRule.onRoot()
                .captureRoboImage("src/test/screenshots/baseline/style_coach_profile_compact_phone_hindi_large_font.png")
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun styleCoachManualProgress_compactPhone() {
        setStyleCoachContent(
            state = StyleCoachUiState(isTraining = true),
            samplesText = TrainingSamples,
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_manual_progress_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun styleCoachManualProgress_typicalPhone() {
        setStyleCoachContent(
            state = StyleCoachUiState(isTraining = true),
            samplesText = TrainingSamples,
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_manual_progress_typical_phone.png")
    }

    @Test
    fun styleCoachAutoErrorEmpty_compactPhone() {
        setStyleCoachContent(
            state = StyleCoachUiState(
                statusMessageRes = R.string.style_coach_error_auto_failed,
                statusIsError = true,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_auto_error_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun styleCoachAutoErrorEmpty_compactPhoneHindiLargeFont() {
        setStyleCoachContent(
            state = StyleCoachUiState(
                statusMessageRes = R.string.style_coach_error_auto_failed,
                statusIsError = true,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_auto_error_empty_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun styleCoachAutoErrorEmpty_typicalPhone() {
        setStyleCoachContent(
            state = StyleCoachUiState(
                statusMessageRes = R.string.style_coach_error_auto_failed,
                statusIsError = true,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/style_coach_auto_error_empty_typical_phone.png")
    }

    private fun setStyleCoachContent(
        state: StyleCoachUiState,
        samplesText: String = "",
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            StyleCoachContent(
                uiState = state,
                samplesText = samplesText,
                onSamplesChange = {},
                onBack = {},
                onManualAnalyze = {},
                onAutoAnalyze = {},
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performScrollTo()
    }

    private fun scrollToHistory(tag: String) {
        scrollTo(tag)
        composeRule.onNode(hasScrollAction())
            .performTouchInput {
                swipeUp(startY = bottom - 48f, endY = bottom - 220f, durationMillis = 120)
            }
    }

    private fun populatedStyleCoachState() = StyleCoachUiState(
        profile = styleProfile(),
        history = listOf(
            styleHistory(
                id = 7,
                source = "MANUAL_TRAINING",
                profileJson = """{"formalityLevel":"CASUAL","preferredLanguage":"hi","avgMessageLength":68}""",
            ),
            styleHistory(
                id = 8,
                source = "AUTO_ANALYSIS",
                profileJson = """{"formalityLevel":"FORMAL","preferredLanguage":"en","avgMessageLength":52}""",
            ),
        ),
        statusMessageRes = R.string.style_coach_status_manual_success,
        statusIsError = false,
    )

    private fun styleProfile() = StyleProfileRecord(
        usesEmoji = false,
        avgMessageLength = 68,
        commonGreetingsJson = """["Hey there","Namaste","Hope you are well"]""",
        formalityLevel = "CASUAL",
        preferredLanguage = "hi",
        emojiSetJson = "[]",
        sampleCount = 8,
        updatedAtMs = 1_767_688_200_000L,
    )

    private fun populatedStyleCoachStateHindi() = populatedStyleCoachState().copy(
        profile = styleProfileHindi(),
        history = listOf(
            styleHistory(
                id = 7,
                source = "MANUAL_TRAINING",
                profileJson = """{"formalityLevel":"CASUAL","preferredLanguage":"hi","avgMessageLength":68}""",
            ),
        ),
    )

    private fun styleProfileHindi() = StyleProfileRecord(
        usesEmoji = false,
        avgMessageLength = 68,
        commonGreetingsJson = """["नमस्ते","कैसे हैं","आपका दिन शुभ हो"]""",
        formalityLevel = "CASUAL",
        preferredLanguage = "hi",
        emojiSetJson = "[]",
        sampleCount = 8,
        updatedAtMs = 1_767_688_200_000L,
    )

    private fun styleHistory(
        id: Int,
        source: String,
        profileJson: String,
    ) = StyleProfileHistoryRecord(
        id = id,
        profileJson = profileJson,
        savedAtMs = 1_767_688_200_000L + id,
        source = source,
    )

    private companion object {
        const val ProgressAnimationFrameMillis = 750L
        const val TrainingSamples =
            "Hey there, hope your week is going well.\n\nWishing you a calm birthday and a year full of good work."
        const val TrainingSamplesHindi =
            "नमस्ते, उम्मीद है आपका सप्ताह अच्छा चल रहा है।\n\nआपको शांत जन्मदिन और अच्छे कामों से भरा साल मिले।"
    }
}
