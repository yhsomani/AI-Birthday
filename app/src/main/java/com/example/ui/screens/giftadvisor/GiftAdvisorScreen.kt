package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.ui.viewmodel.GiftAdvisorUiState
import com.example.ui.viewmodel.GiftAdvisorViewModel
import com.example.ui.viewmodel.GiftSuggestionUiModel

internal object GiftAdvisorTestTags {
    const val LOADING = "gift_advisor_loading"
    const val RECORD_FAB = "gift_advisor_record_fab"
    const val STATS = "gift_advisor_stats"
    const val ADJUST_BUDGET_BUTTON = "gift_advisor_adjust_budget"
    const val GENERATE_SUGGESTIONS_BUTTON = "gift_advisor_generate_suggestions_button"
    const val SUGGESTIONS_PROGRESS = "gift_advisor_suggestions_progress"
    const val SUGGESTIONS_EMPTY = "gift_advisor_suggestions_empty"
    const val SUGGESTION_CARD_PREFIX = "gift_advisor_suggestion_"
    const val SUGGESTION_RECORD_BUTTON_PREFIX = "gift_advisor_suggestion_record_"
    const val SUGGESTION_DISMISS_BUTTON_PREFIX = "gift_advisor_suggestion_dismiss_"
    const val ERROR_CARD = "gift_advisor_error_card"
    const val EMPTY_HISTORY = "gift_advisor_empty_history"
    const val HISTORY_HEADER = "gift_advisor_history_header"
    const val HISTORY_CARD_PREFIX = "gift_advisor_history_"
    const val DELETE_BUTTON_PREFIX = "gift_advisor_delete_"
    const val DIALOG = "gift_advisor_dialog"
    const val GIFT_NAME_FIELD = "gift_advisor_gift_name_field"
    const val GIFT_CATEGORY_FIELD = "gift_advisor_gift_category_field"
    const val OCCASION_FIELD = "gift_advisor_occasion_field"
    const val COST_FIELD = "gift_advisor_cost_field"
    const val NOTES_FIELD = "gift_advisor_notes_field"
    const val FEEDBACK_LIKED = "gift_advisor_feedback_liked"
    const val FEEDBACK_DISLIKED = "gift_advisor_feedback_disliked"
    const val FEEDBACK_UNKNOWN = "gift_advisor_feedback_unknown"
    const val SAVE_BUTTON = "gift_advisor_save_button"
    const val CANCEL_BUTTON = "gift_advisor_cancel_button"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftAdvisorScreen(
    contactId: String,
    onBack: () -> Unit,
    onAdjustBudget: () -> Unit = {},
    viewModel: GiftAdvisorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var giftName by remember { mutableStateOf("") }
    var giftCategory by remember { mutableStateOf("") }
    var occasionType by remember { mutableStateOf("") }
    var approxCost by remember { mutableStateOf("") }
    var receivedWellState by remember { mutableStateOf<Boolean?>(null) }
    var giftNotes by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }

    fun resetGiftForm() {
        giftName = ""
        giftCategory = ""
        occasionType = ""
        approxCost = ""
        receivedWellState = null
        giftNotes = ""
        attemptedSubmit = false
    }

    GiftAdvisorContent(
        uiState = uiState,
        showAddDialog = showAddDialog,
        giftName = giftName,
        onGiftNameChange = {
            if (it.length <= GiftAdvisorViewModel.MAX_TEXT_FIELD_LENGTH) giftName = it
        },
        giftCategory = giftCategory,
        onGiftCategoryChange = {
            if (it.length <= GiftAdvisorViewModel.MAX_TEXT_FIELD_LENGTH) giftCategory = it
        },
        occasionType = occasionType,
        onOccasionTypeChange = {
            if (it.length <= GiftAdvisorViewModel.MAX_TEXT_FIELD_LENGTH) occasionType = it
        },
        approxCost = approxCost,
        onApproxCostChange = { input ->
            if (input.length <= 12 && input.all { it.isDigit() || it == ',' || it.isWhitespace() }) {
                approxCost = input
            }
        },
        receivedWellState = receivedWellState,
        onReceivedWellChange = { receivedWellState = it },
        giftNotes = giftNotes,
        onGiftNotesChange = {
            if (it.length <= GiftAdvisorViewModel.MAX_NOTES_LENGTH) giftNotes = it
        },
        attemptedSubmit = attemptedSubmit,
        onBack = onBack,
        onAdjustBudget = onAdjustBudget,
        onShowAddDialog = { showAddDialog = true },
        onDismissDialog = {
            showAddDialog = false
            resetGiftForm()
        },
        onSaveGift = {
            attemptedSubmit = true
            val accepted = viewModel.addGiftRecord(
                giftName,
                giftCategory,
                occasionType,
                approxCost,
                receivedWellState,
                giftNotes,
            )
            if (accepted) {
                showAddDialog = false
                resetGiftForm()
            }
        },
        onDeleteGift = viewModel::deleteGiftRecord,
        onGenerateSuggestions = viewModel::generateGiftSuggestions,
        onDismissSuggestion = viewModel::dismissGiftSuggestion,
        onRecordSuggestion = { suggestion ->
            giftName = suggestion.name
            giftCategory = ""
            occasionType = ""
            approxCost = suggestion.estimatedCostInr.takeIf { it > 0 }?.toString().orEmpty()
            receivedWellState = null
            giftNotes = suggestion.reason.take(GiftAdvisorViewModel.MAX_NOTES_LENGTH)
            attemptedSubmit = false
            showAddDialog = true
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiftAdvisorContent(
    uiState: GiftAdvisorUiState,
    showAddDialog: Boolean,
    giftName: String,
    onGiftNameChange: (String) -> Unit,
    giftCategory: String,
    onGiftCategoryChange: (String) -> Unit,
    occasionType: String,
    onOccasionTypeChange: (String) -> Unit,
    approxCost: String,
    onApproxCostChange: (String) -> Unit,
    receivedWellState: Boolean?,
    onReceivedWellChange: (Boolean?) -> Unit,
    giftNotes: String,
    onGiftNotesChange: (String) -> Unit,
    attemptedSubmit: Boolean,
    onBack: () -> Unit,
    onAdjustBudget: () -> Unit,
    onShowAddDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onSaveGift: () -> Unit,
    onDeleteGift: (GiftHistoryRecord) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onDismissSuggestion: (Int) -> Unit,
    onRecordSuggestion: (GiftSuggestionUiModel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.contact?.displayName?.let {
                            stringResource(R.string.gift_advisor_title_with_contact, it)
                        } ?: stringResource(R.string.gift_advisor_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(RelateElevation.appBar),
                ),
            )
        },
        bottomBar = {
            GiftRecordBottomBar(onShowAddDialog = onShowAddDialog)
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(GiftAdvisorTestTags.LOADING)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.gift_advisor_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(RelateSpacing.screenHorizontal),
                contentPadding = PaddingValues(bottom = RelateSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
            ) {
                item {
                    BudgetStats(
                        uiState = uiState,
                        onAdjustBudget = onAdjustBudget,
                        modifier = Modifier.testTag(GiftAdvisorTestTags.STATS),
                    )
                }

                uiState.errorMessageRes?.let { errorRes ->
                    item {
                        GiftAdvisorErrorCard(message = stringResource(errorRes))
                    }
                }

                item {
                    GiftSuggestionsPanel(
                        uiState = uiState,
                        onGenerateSuggestions = onGenerateSuggestions,
                        onDismissSuggestion = onDismissSuggestion,
                        onRecordSuggestion = onRecordSuggestion,
                    )
                }

                giftHistoryItems(
                    giftHistory = uiState.giftHistory,
                    onDeleteGift = onDeleteGift,
                )
            }
        }
    }

    if (showAddDialog) {
        AddGiftDialog(
            giftName = giftName,
            onGiftNameChange = {
                onGiftNameChange(it)
            },
            giftCategory = giftCategory,
            onGiftCategoryChange = {
                onGiftCategoryChange(it)
            },
            occasionType = occasionType,
            onOccasionTypeChange = {
                onOccasionTypeChange(it)
            },
            approxCost = approxCost,
            onApproxCostChange = onApproxCostChange,
            receivedWellState = receivedWellState,
            onReceivedWellChange = onReceivedWellChange,
            giftNotes = giftNotes,
            onGiftNotesChange = {
                onGiftNotesChange(it)
            },
            attemptedSubmit = attemptedSubmit,
            errorMessageRes = uiState.errorMessageRes,
            onDismiss = onDismissDialog,
            onSave = onSaveGift,
        )
    }
}
