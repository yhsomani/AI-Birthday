package com.example.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.service.GiftSuggestion
import com.example.ui.screens.giftadvisor.AddGiftDialogBody
import com.example.ui.screens.giftadvisor.GiftAdvisorContent
import com.example.ui.screens.giftadvisor.GiftAdvisorTestTags
import com.example.ui.viewmodel.GiftAdvisorUiState
import com.example.ui.viewmodel.GiftAdvisorViewModel
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.github.takahirom.roborazzi.captureRoboImage
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
class GiftAdvisorScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun giftAdvisorSuggestions_compactPhone() {
        setGiftAdvisorContent(state = populatedGiftAdvisorState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_suggestions_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorSuggestions_typicalPhone() {
        setGiftAdvisorContent(state = populatedGiftAdvisorState())

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_suggestions_typical_phone.png")
    }

    @Test
    fun giftAdvisorHistory_compactPhoneLargeFont() {
        setGiftAdvisorContent(
            state = historyGiftAdvisorState(),
            fontScale = LargeFontScale,
        )
        scrollToHistory(GiftAdvisorTestTags.HISTORY_CARD_PREFIX + "gift_stationery")

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_history_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorHistory_typicalPhone() {
        setGiftAdvisorContent(state = historyGiftAdvisorState())
        scrollToHistory(GiftAdvisorTestTags.HISTORY_CARD_PREFIX + "gift_stationery")

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_history_typical_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun giftAdvisorSuggestions_compactPhoneHindiLargeFont() {
        setGiftAdvisorContent(
            state = populatedGiftAdvisorStateHindi(),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_suggestions_compact_phone_hindi_large_font.png")
    }

    @Test
    fun giftAdvisorErrorEmpty_compactPhone() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(
                contact = contact(),
                isLoading = false,
                errorMessageRes = R.string.gift_advisor_error_load,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_error_empty_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun giftAdvisorErrorEmpty_compactPhoneHindiLargeFont() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(
                contact = contact(
                    displayName = "आशा मेहरा",
                    nickname = "आशा",
                ),
                isLoading = false,
                errorMessageRes = R.string.gift_advisor_error_load,
            ),
            fontScale = LargeFontScale,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_error_empty_compact_phone_hindi_large_font.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorErrorEmpty_typicalPhone() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(
                contact = contact(),
                isLoading = false,
                errorMessageRes = R.string.gift_advisor_error_load,
            ),
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_error_empty_typical_phone.png")
    }

    @Test
    fun giftAdvisorGenerating_compactPhone() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(
                contact = contact(),
                giftHistory = listOf(giftRecord()),
                totalSpentThisYear = 1_200,
                remainingBudget = 6_300,
                isLoading = false,
                isGeneratingSuggestions = true,
            ),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_generating_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorGenerating_typicalPhone() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(
                contact = contact(),
                giftHistory = listOf(giftRecord()),
                totalSpentThisYear = 1_200,
                remainingBudget = 6_300,
                isLoading = false,
                isGeneratingSuggestions = true,
            ),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_generating_typical_phone.png")
    }

    @Test
    fun giftAdvisorAddDialogForm_compactPhoneLargeFont() {
        setGiftAdvisorAddDialogFormContent(
            giftName = "Botanical notebook set",
            giftCategory = "Stationery",
            occasionType = "Birthday",
            approxCost = "1200",
            receivedWellState = true,
            giftNotes = "Used daily for planning classes and weekly notes.",
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_add_dialog_form_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun giftAdvisorAddDialogForm_compactPhoneHindiLargeFont() {
        setGiftAdvisorAddDialogFormContent(
            giftName = "वनस्पति नोटबुक सेट",
            giftCategory = "स्टेशनरी",
            occasionType = "जन्मदिन",
            approxCost = "1200",
            receivedWellState = true,
            giftNotes = "क्लास और साप्ताहिक नोट्स की योजना बनाने में रोज इस्तेमाल होता है।",
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .captureRoboImage(
                "src/test/screenshots/baseline/gift_advisor_add_dialog_form_compact_phone_hindi_large_font.png"
            )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorAddDialogForm_typicalPhone() {
        setGiftAdvisorAddDialogFormContent(
            giftName = "Botanical notebook set",
            giftCategory = "Stationery",
            occasionType = "Birthday",
            approxCost = "1200",
            receivedWellState = true,
            giftNotes = "Used daily for planning classes and weekly notes.",
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_add_dialog_form_typical_phone.png")
    }

    @Test
    fun giftAdvisorAddDialogFormBottom_compactPhoneLargeFont() {
        setGiftAdvisorAddDialogFormContent(
            giftName = "Botanical notebook set",
            giftCategory = "Stationery",
            occasionType = "Birthday",
            approxCost = "1200",
            receivedWellState = true,
            giftNotes = "Used daily for planning classes, weekly notes, and quiet creative time.",
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.NOTES_FIELD)
            .performScrollTo()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_add_dialog_form_bottom_compact_phone_large_font.png")
    }

    @Test
    @Config(qualifiers = "hi-rIN-w360dp-h800dp-xhdpi")
    fun giftAdvisorAddDialogFormBottom_compactPhoneHindiLargeFont() {
        setGiftAdvisorAddDialogFormContent(
            giftName = "वनस्पति नोटबुक सेट",
            giftCategory = "स्टेशनरी",
            occasionType = "जन्मदिन",
            approxCost = "1200",
            receivedWellState = true,
            giftNotes = "क्लास, साप्ताहिक नोट्स, और शांत रचनात्मक समय की योजना बनाने में रोज इस्तेमाल होता है।",
            fontScale = LargeFontScale,
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.NOTES_FIELD)
            .performScrollTo()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .captureRoboImage(
                "src/test/screenshots/baseline/gift_advisor_add_dialog_form_bottom_compact_phone_hindi_large_font.png"
            )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorAddDialogFormBottom_typicalPhone() {
        setGiftAdvisorAddDialogFormContent(
            giftName = "Botanical notebook set",
            giftCategory = "Stationery",
            occasionType = "Birthday",
            approxCost = "1200",
            receivedWellState = true,
            giftNotes = "Used daily for planning classes, weekly notes, and quiet creative time.",
        )

        composeRule.onNodeWithTag(GiftAdvisorTestTags.NOTES_FIELD)
            .performScrollTo()
        composeRule.onNodeWithTag(GiftAdvisorTestTags.DIALOG)
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_add_dialog_form_bottom_typical_phone.png")
    }

    @Test
    fun giftAdvisorLoading_compactPhone() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_loading_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun giftAdvisorLoading_typicalPhone() {
        setGiftAdvisorContent(
            state = GiftAdvisorUiState(isLoading = true),
            animationFrameMillis = ProgressAnimationFrameMillis,
        )

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/gift_advisor_loading_typical_phone.png")
    }

    private fun setGiftAdvisorContent(
        state: GiftAdvisorUiState,
        showAddDialog: Boolean = false,
        giftName: String = "",
        giftCategory: String = "",
        occasionType: String = "",
        approxCost: String = "",
        receivedWellState: Boolean? = null,
        giftNotes: String = "",
        attemptedSubmit: Boolean = false,
        fontScale: Float = DefaultFontScale,
        animationFrameMillis: Long? = null,
    ) {
        if (animationFrameMillis != null) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            GiftAdvisorContent(
                uiState = state,
                showAddDialog = showAddDialog,
                giftName = giftName,
                onGiftNameChange = {},
                giftCategory = giftCategory,
                onGiftCategoryChange = {},
                occasionType = occasionType,
                onOccasionTypeChange = {},
                approxCost = approxCost,
                onApproxCostChange = {},
                receivedWellState = receivedWellState,
                onReceivedWellChange = {},
                giftNotes = giftNotes,
                onGiftNotesChange = {},
                attemptedSubmit = attemptedSubmit,
                onBack = {},
                onShowAddDialog = {},
                onDismissDialog = {},
                onSaveGift = {},
                onDeleteGift = {},
                onGenerateSuggestions = {},
            )
        }
        if (animationFrameMillis != null) {
            composeRule.mainClock.advanceTimeBy(animationFrameMillis)
            composeRule.waitForIdle()
        }
    }

    private fun setGiftAdvisorAddDialogFormContent(
        giftName: String,
        giftCategory: String,
        occasionType: String,
        approxCost: String,
        receivedWellState: Boolean?,
        giftNotes: String,
        attemptedSubmit: Boolean = false,
        errorMessageRes: Int? = null,
        fontScale: Float = DefaultFontScale,
    ) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            GiftAdvisorAddDialogFormSnapshot(
                giftName = giftName,
                giftCategory = giftCategory,
                occasionType = occasionType,
                approxCost = approxCost,
                receivedWellState = receivedWellState,
                giftNotes = giftNotes,
                attemptedSubmit = attemptedSubmit,
                errorMessageRes = errorMessageRes,
            )
        }
    }

    @Composable
    private fun GiftAdvisorAddDialogFormSnapshot(
        giftName: String,
        giftCategory: String,
        occasionType: String,
        approxCost: String,
        receivedWellState: Boolean?,
        giftNotes: String,
        attemptedSubmit: Boolean,
        errorMessageRes: Int?,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(RelateSpacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GiftAdvisorTestTags.DIALOG),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.relateSemanticColors.cardContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(RelateSpacing.cardContent),
                    verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.gift_record_history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AddGiftDialogBody(
                        giftName = giftName,
                        onGiftNameChange = {},
                        showGiftNameError = attemptedSubmit && giftName.isBlank(),
                        giftCategory = giftCategory,
                        onGiftCategoryChange = {},
                        showCategoryError = attemptedSubmit && giftCategory.isBlank(),
                        occasionType = occasionType,
                        onOccasionTypeChange = {},
                        showOccasionError = attemptedSubmit && occasionType.isBlank(),
                        approxCost = approxCost,
                        onApproxCostChange = {},
                        showCostError = attemptedSubmit && GiftAdvisorViewModel.parseCostInput(approxCost) == null,
                        receivedWellState = receivedWellState,
                        onReceivedWellChange = {},
                        giftNotes = giftNotes,
                        onGiftNotesChange = {},
                        errorMessageRes = errorMessageRes,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.testTag(GiftAdvisorTestTags.CANCEL_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(RelateSpacing.xs))
                        Button(
                            onClick = {},
                            modifier = Modifier.testTag(GiftAdvisorTestTags.SAVE_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.gift_save_record))
                        }
                    }
                }
            }
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

    private fun populatedGiftAdvisorState(): GiftAdvisorUiState {
        return GiftAdvisorUiState(
            contact = contact(),
            giftHistory = listOf(
                giftRecord(
                    id = "gift_stationery",
                    giftName = "Botanical notebook set",
                    giftCategory = "Stationery",
                    occasionType = "Birthday",
                    approxCostInr = 1_200,
                    receivedWell = true,
                    notes = "Used daily for planning classes.",
                ),
                giftRecord(
                    id = "gift_tea",
                    giftName = "Loose-leaf tea sampler",
                    giftCategory = "Food",
                    occasionType = "Festival",
                    approxCostInr = 900,
                    receivedWell = null,
                ),
            ),
            suggestions = listOf(
                GiftSuggestion(
                    name = "Weekend pottery class",
                    reason = "Matches her recent interest in slow, hands-on creative hobbies.",
                    estimatedCostInr = 3_200,
                ),
                GiftSuggestion(
                    name = "Botanical desk calendar",
                    reason = "Useful, low-fragrance, and aligned with her stationery preferences.",
                    estimatedCostInr = 850,
                ),
            ),
            totalSpentThisYear = 2_100,
            remainingBudget = 5_400,
            isLoading = false,
        )
    }

    private fun populatedGiftAdvisorStateHindi(): GiftAdvisorUiState {
        return GiftAdvisorUiState(
            contact = contact(
                displayName = "आशा मेहरा",
                nickname = "आशा",
            ),
            giftHistory = listOf(
                giftRecord(
                    id = "gift_stationery_hi",
                    giftName = "बॉटैनिकल नोटबुक सेट",
                    giftCategory = "स्टेशनरी",
                    occasionType = "जन्मदिन",
                    approxCostInr = 1_200,
                    receivedWell = true,
                    notes = "क्लास प्लान करने के लिए रोज इस्तेमाल करती हैं।",
                ),
                giftRecord(
                    id = "gift_tea_hi",
                    giftName = "ढीली पत्ती वाली चाय सैंपलर",
                    giftCategory = "फूड",
                    occasionType = "त्योहार",
                    approxCostInr = 900,
                    receivedWell = null,
                ),
            ),
            suggestions = listOf(
                GiftSuggestion(
                    name = "वीकेंड पॉटरी क्लास",
                    reason = "धीमे, हाथों से किए जाने वाले रचनात्मक शौक में उनकी हाल की रुचि से मेल खाता है।",
                    estimatedCostInr = 3_200,
                ),
                GiftSuggestion(
                    name = "बॉटैनिकल डेस्क कैलेंडर",
                    reason = "उपयोगी, कम खुशबू वाला और स्टेशनरी पसंद के अनुरूप।",
                    estimatedCostInr = 850,
                ),
            ),
            totalSpentThisYear = 2_100,
            remainingBudget = 5_400,
            isLoading = false,
        )
    }

    private fun historyGiftAdvisorState(): GiftAdvisorUiState {
        return populatedGiftAdvisorState().copy(
            giftHistory = listOf(
                giftRecord(
                    id = "gift_stationery",
                    giftName = "Botanical notebook set",
                    giftCategory = "Stationery",
                    occasionType = "Birthday",
                    approxCostInr = 1_200,
                    receivedWell = true,
                    notes = "Used daily for planning classes.",
                ),
            ),
            suggestions = emptyList(),
        )
    }

    private fun contact(
        displayName: String = "Asha Mehra",
        nickname: String = "Asha",
    ) = ContactGiftAdvisorProfile(
        id = ContactId(ContactIdValue),
        displayName = displayName,
        nickname = nickname,
        relationshipType = "FRIEND",
        interestsJson = "[]",
        giftBudgetInr = 7_500,
    )

    private fun giftRecord(
        id: String = "gift_stationery",
        giftName: String = "Botanical notebook set",
        giftCategory: String = "Stationery",
        occasionType: String = "Birthday",
        approxCostInr: Int = 1_200,
        receivedWell: Boolean? = true,
        notes: String = "Used daily for planning classes.",
    ) = GiftHistoryRecord(
        id = GiftHistoryId(id),
        contactId = ContactId(ContactIdValue),
        giftName = giftName,
        giftCategory = giftCategory,
        occasionType = occasionType,
        year = 2026,
        approxCostInr = approxCostInr,
        receivedWell = receivedWell,
        notes = notes,
    )

    private companion object {
        const val ContactIdValue = "contact_asha"
        const val ProgressAnimationFrameMillis = 750L
    }
}
