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
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.ui.screens.memoryvault.MemoryVaultContent
import com.example.ui.screens.memoryvault.MemoryVaultTestTags
import com.example.ui.viewmodel.MemoryVaultUiState
import com.example.ui.viewmodel.MemoryVaultViewModel
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
class MemoryVaultScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun memoryVaultAddNote_compactPhone() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(
                contact = contactHeader(),
                isLoading = false,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_add_note_compact_phone.png")
    }

    @Test
    fun memoryVaultNotes_compactPhone() {
        setMemoryVaultContent(state = populatedMemoryVaultState())
        scrollToNote(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_pinned")

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_notes_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun memoryVaultNotes_typicalPhone() {
        setMemoryVaultContent(state = populatedMemoryVaultState())
        scrollToNote(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_pinned")

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_notes_typical_phone.png")
    }

    @Test
    fun memoryVaultPinnedNote_compactPhoneLargeFont() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(
                contact = contactHeader(),
                notes = listOf(
                    memoryNote(
                        id = "note_large",
                        noteText = "Asha prefers quiet celebrations, handwritten cards, and practical gifts that are easy to carry while travelling.",
                        category = "PREFERENCE",
                        isPinned = true,
                    ),
                ),
                isLoading = false,
            ),
            fontScale = LargeFontScale,
        )
        scrollToNote(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_large")

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_pinned_note_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun memoryVaultPinnedNote_compactPhoneHindiLargeFont() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("hi-IN"))
        try {
            setMemoryVaultContent(
                state = MemoryVaultUiState(
                    contact = contactHeader(displayName = "आशा मेहरा"),
                    notes = listOf(
                        memoryNote(
                            id = "note_large_hi",
                            noteText = "आशा को शांत समारोह, हाथ से लिखे कार्ड और यात्रा में आसानी से ले जाने वाले उपयोगी गिफ्ट पसंद हैं।",
                            category = "PREFERENCE",
                            isPinned = true,
                        ),
                    ),
                    isLoading = false,
                ),
                fontScale = LargeFontScale,
            )
            scrollToNote(MemoryVaultTestTags.NOTE_CARD_PREFIX + "note_large_hi")

            composeRule.onRoot()
                .captureRoboImage("src/test/screenshots/baseline/memory_vault_pinned_note_compact_phone_hindi_large_font.png")
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun memoryVaultErrorEmpty_compactPhone() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(
                contact = contactHeader(),
                isLoading = false,
                errorMessageRes = R.string.memory_vault_error_note_too_long,
            ),
            newNoteText = "   ",
        )
        scrollTo(MemoryVaultTestTags.ERROR_CARD)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_error_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun memoryVaultErrorEmpty_compactPhoneHindiLargeFont() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(
                contact = contactHeader(displayName = "आशा मेहरा"),
                isLoading = false,
                errorMessageRes = R.string.memory_vault_error_note_too_long,
            ),
            newNoteText = "   ",
            fontScale = LargeFontScale,
        )
        scrollTo(MemoryVaultTestTags.ERROR_CARD)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_error_empty_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun memoryVaultErrorEmpty_typicalPhone() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(
                contact = contactHeader(),
                isLoading = false,
                errorMessageRes = R.string.memory_vault_error_note_too_long,
            ),
            newNoteText = "   ",
        )
        scrollTo(MemoryVaultTestTags.ERROR_CARD)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_error_empty_typical_phone.png")
    }

    @Test
    fun memoryVaultLoading_compactPhone() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun memoryVaultLoading_typicalPhone() {
        setMemoryVaultContent(
            state = MemoryVaultUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/memory_vault_loading_typical_phone.png")
    }

    private fun setMemoryVaultContent(
        state: MemoryVaultUiState,
        newNoteText: String = "",
        selectedCategory: String = MemoryVaultViewModel.CATEGORY_GENERAL,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            MemoryVaultContent(
                uiState = state,
                newNoteText = newNoteText,
                selectedCategory = selectedCategory,
                onNoteChange = {},
                onPromptSelected = { _, _ -> },
                onCategoryChange = {},
                onAdd = {},
                onBack = {},
                onTogglePin = {},
                onDelete = {},
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

    private fun scrollToNote(tag: String) {
        scrollTo(tag)
        composeRule.onNode(hasScrollAction())
            .performTouchInput {
                swipeUp(startY = bottom - 48f, endY = bottom - 220f, durationMillis = 120)
            }
    }

    private fun populatedMemoryVaultState(): MemoryVaultUiState {
        return MemoryVaultUiState(
            contact = contactHeader(),
            notes = listOf(
                memoryNote(
                    id = "note_pinned",
                    noteText = "Call every Sunday evening before dinner.",
                    category = "GENERAL",
                    isPinned = true,
                ),
                memoryNote(
                    id = "note_gift",
                    noteText = "Enjoys botanical stationery, loose-leaf tea, and low-fragrance candles.",
                    category = "GIFT",
                ),
                memoryNote(
                    id = "note_event",
                    noteText = "Planning a small family lunch after the birthday week.",
                    category = "EVENT",
                ),
            ),
            isLoading = false,
        )
    }

    private fun contactHeader(displayName: String = "Asha Mehra") = ContactHeader(
        id = ContactId(ContactIdValue),
        displayName = displayName,
    )

    private fun memoryNote(
        id: String,
        noteText: String,
        category: String,
        isPinned: Boolean = false,
    ) = MemoryNoteRecord(
        id = MemoryNoteId(id),
        contactId = ContactId(ContactIdValue),
        noteText = noteText,
        category = category,
        dateMs = 1_767_688_200_000L,
        isPinned = isPinned,
    )

    private companion object {
        const val ContactIdValue = "contact_asha"
        const val ProgressAnimationFrameMillis = 750L
    }
}
