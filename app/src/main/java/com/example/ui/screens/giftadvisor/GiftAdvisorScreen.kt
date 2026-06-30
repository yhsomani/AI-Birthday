package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.service.GiftSuggestion
import com.example.ui.viewmodel.GiftAdvisorUiState
import com.example.ui.viewmodel.GiftAdvisorViewModel

internal object GiftAdvisorTestTags {
    const val LOADING = "gift_advisor_loading"
    const val RECORD_FAB = "gift_advisor_record_fab"
    const val STATS = "gift_advisor_stats"
    const val GENERATE_SUGGESTIONS_BUTTON = "gift_advisor_generate_suggestions_button"
    const val SUGGESTIONS_PROGRESS = "gift_advisor_suggestions_progress"
    const val SUGGESTIONS_EMPTY = "gift_advisor_suggestions_empty"
    const val SUGGESTION_CARD_PREFIX = "gift_advisor_suggestion_"
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
        onShowAddDialog = { showAddDialog = true },
        onDismissDialog = {
            showAddDialog = false
            attemptedSubmit = false
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
    onShowAddDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onSaveGift: () -> Unit,
    onDeleteGift: (GiftHistoryRecord) -> Unit,
    onGenerateSuggestions: () -> Unit,
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
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.gift_history_journal_title),
                        modifier = Modifier.testTag(GiftAdvisorTestTags.HISTORY_HEADER),
                    )
                }

                if (uiState.giftHistory.isEmpty()) {
                    item {
                        EmptyState(
                            message = stringResource(R.string.gift_history_empty_message),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(GiftAdvisorTestTags.EMPTY_HISTORY)
                                .height(RelateSize.actionCardMinHeight),
                        )
                    }
                } else {
                    items(uiState.giftHistory, key = { it.id.value }) { gift ->
                        GiftHistoryCard(
                            gift = gift,
                            onDelete = { onDeleteGift(gift) },
                            modifier = Modifier.testTag(GiftAdvisorTestTags.HISTORY_CARD_PREFIX + gift.id.value),
                        )
                    }
                }
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

@Composable
private fun GiftRecordBottomBar(onShowAddDialog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(RelateElevation.appBar))
            .padding(horizontal = RelateSpacing.screenHorizontal, vertical = RelateSpacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onShowAddDialog,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag(GiftAdvisorTestTags.RECORD_FAB),
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = null,
                modifier = Modifier.size(RelateSize.iconSm),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.sm))
            Text(text = stringResource(R.string.gift_advisor_record_gift))
        }
    }
}

@Composable
private fun BudgetStats(
    uiState: GiftAdvisorUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        BudgetStatCard(
            label = stringResource(R.string.gift_stat_annual_budget),
            value = stringResource(R.string.gift_currency_inr_format, uiState.contact?.giftBudgetInr ?: 500),
            icon = Icons.Filled.CardGiftcard,
            modifier = Modifier.weight(1f),
        )
        BudgetStatCard(
            label = stringResource(R.string.gift_stat_total_spent),
            value = stringResource(R.string.gift_currency_inr_format, uiState.totalSpentThisYear),
            icon = Icons.Filled.ShoppingCart,
            modifier = Modifier.weight(1f),
        )
        BudgetStatCard(
            label = stringResource(R.string.gift_stat_remaining),
            value = stringResource(R.string.gift_currency_inr_format, uiState.remainingBudget),
            icon = Icons.Filled.AttachMoney,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BudgetStatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RelateSize.iconLg),
            )
            Spacer(modifier = Modifier.height(RelateSpacing.xs))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GiftSuggestionsPanel(
    uiState: GiftAdvisorUiState,
    onGenerateSuggestions: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gift_suggestions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Button(
                    onClick = onGenerateSuggestions,
                    enabled = !uiState.isGeneratingSuggestions,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag(GiftAdvisorTestTags.GENERATE_SUGGESTIONS_BUTTON),
                ) {
                    if (uiState.isGeneratingSuggestions) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(RelateSize.iconSm)
                                .testTag(GiftAdvisorTestTags.SUGGESTIONS_PROGRESS),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = RelateSize.progressStroke,
                        )
                    } else {
                        Text(text = stringResource(R.string.gift_ask_ai))
                    }
                }
            }

            Text(
                text = stringResource(R.string.gift_ai_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.gift_ai_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(GiftAdvisorTestTags.SUGGESTIONS_EMPTY),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)) {
                    uiState.suggestions.forEachIndexed { index, suggestion ->
                        GiftSuggestionCard(
                            suggestion = suggestion,
                            modifier = Modifier.testTag(GiftAdvisorTestTags.SUGGESTION_CARD_PREFIX + index),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftSuggestionCard(
    suggestion: GiftSuggestion,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
        ),
    ) {
        Column(modifier = Modifier.padding(RelateSpacing.compactCardContent)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = suggestion.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Text(
                    text = stringResource(R.string.gift_currency_inr_format, suggestion.estimatedCostInr),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(RelateSpacing.xs))
            Text(
                text = suggestion.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GiftHistoryCard(
    gift: GiftHistoryRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.relateSemanticColors.cardContainer),
    ) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.xxs),
            ) {
                Text(
                    text = gift.giftName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = gift.giftCategory,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.gift_occasion_year_format, gift.occasionType, gift.year),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gift_currency_inr_format, gift.approxCostInr),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GiftFeedbackIcon(receivedWell = gift.receivedWell)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag(GiftAdvisorTestTags.DELETE_BUTTON_PREFIX + gift.id.value),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.gift_delete_record, gift.giftName),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = RelateAlpha.subtle),
                        )
                    }
                }
            }
            if (gift.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.gift_notes_format, gift.notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GiftFeedbackIcon(receivedWell: Boolean?) {
    when (receivedWell) {
        true -> Icon(
            imageVector = Icons.Filled.ThumbUp,
            contentDescription = stringResource(R.string.gift_feedback_liked),
            tint = MaterialTheme.relateSemanticColors.success,
        )

        false -> Icon(
            imageVector = Icons.Filled.ThumbDown,
            contentDescription = stringResource(R.string.gift_feedback_disliked),
            tint = MaterialTheme.colorScheme.error,
        )

        null -> Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = stringResource(R.string.gift_feedback_unknown),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddGiftDialog(
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
    errorMessageRes: Int?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val showGiftNameError = attemptedSubmit && giftName.isBlank()
    val showCategoryError = attemptedSubmit && giftCategory.isBlank()
    val showOccasionError = attemptedSubmit && occasionType.isBlank()
    val showCostError = attemptedSubmit && GiftAdvisorViewModel.parseCostInput(approxCost) == null

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(GiftAdvisorTestTags.DIALOG),
        title = {
            Text(
                text = stringResource(R.string.gift_record_history_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            AddGiftDialogBody(
                giftName = giftName,
                onGiftNameChange = onGiftNameChange,
                showGiftNameError = showGiftNameError,
                giftCategory = giftCategory,
                onGiftCategoryChange = onGiftCategoryChange,
                showCategoryError = showCategoryError,
                occasionType = occasionType,
                onOccasionTypeChange = onOccasionTypeChange,
                showOccasionError = showOccasionError,
                approxCost = approxCost,
                onApproxCostChange = onApproxCostChange,
                showCostError = showCostError,
                receivedWellState = receivedWellState,
                onReceivedWellChange = onReceivedWellChange,
                giftNotes = giftNotes,
                onGiftNotesChange = onGiftNotesChange,
                errorMessageRes = errorMessageRes,
            )
        },
        confirmButton = {
            Button(
                onClick = onSave,
                modifier = Modifier.testTag(GiftAdvisorTestTags.SAVE_BUTTON),
            ) {
                Text(text = stringResource(R.string.gift_save_record))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(GiftAdvisorTestTags.CANCEL_BUTTON),
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun AddGiftDialogBody(
    giftName: String,
    onGiftNameChange: (String) -> Unit,
    showGiftNameError: Boolean,
    giftCategory: String,
    onGiftCategoryChange: (String) -> Unit,
    showCategoryError: Boolean,
    occasionType: String,
    onOccasionTypeChange: (String) -> Unit,
    showOccasionError: Boolean,
    approxCost: String,
    onApproxCostChange: (String) -> Unit,
    showCostError: Boolean,
    receivedWellState: Boolean?,
    onReceivedWellChange: (Boolean?) -> Unit,
    giftNotes: String,
    onGiftNotesChange: (String) -> Unit,
    errorMessageRes: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = RelateSize.dialogContentMaxHeight)
            .verticalScroll(rememberScrollState()),
    ) {
        errorMessageRes?.let { errorRes ->
            GiftAdvisorErrorCard(message = stringResource(errorRes))
        }

        RequiredTextField(
            value = giftName,
            onValueChange = onGiftNameChange,
            labelRes = R.string.gift_name_label,
            isError = showGiftNameError,
            modifier = Modifier.testTag(GiftAdvisorTestTags.GIFT_NAME_FIELD),
        )

        RequiredTextField(
            value = giftCategory,
            onValueChange = onGiftCategoryChange,
            labelRes = R.string.gift_category_label,
            isError = showCategoryError,
            modifier = Modifier.testTag(GiftAdvisorTestTags.GIFT_CATEGORY_FIELD),
        )

        RequiredTextField(
            value = occasionType,
            onValueChange = onOccasionTypeChange,
            labelRes = R.string.gift_occasion_label,
            isError = showOccasionError,
            modifier = Modifier.testTag(GiftAdvisorTestTags.OCCASION_FIELD),
        )

        OutlinedTextField(
            value = approxCost,
            onValueChange = onApproxCostChange,
            label = { Text(text = stringResource(R.string.gift_cost_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = showCostError,
            supportingText = {
                if (showCostError) {
                    Text(text = stringResource(R.string.gift_advisor_error_invalid_cost))
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(GiftAdvisorTestTags.COST_FIELD),
        )

        Text(
            text = stringResource(R.string.gift_feedback_question),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            FeedbackButton(
                selected = receivedWellState == true,
                onClick = { onReceivedWellChange(true) },
                selectedColor = MaterialTheme.relateSemanticColors.success,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = stringResource(R.string.gift_feedback_liked),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(GiftAdvisorTestTags.FEEDBACK_LIKED),
            )
            FeedbackButton(
                selected = receivedWellState == false,
                onClick = { onReceivedWellChange(false) },
                selectedColor = MaterialTheme.colorScheme.error,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.ThumbDown,
                        contentDescription = stringResource(R.string.gift_feedback_disliked),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(GiftAdvisorTestTags.FEEDBACK_DISLIKED),
            )
            FeedbackButton(
                selected = receivedWellState == null,
                onClick = { onReceivedWellChange(null) },
                selectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = {
                    Text(text = stringResource(R.string.gift_feedback_unknown_short))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(GiftAdvisorTestTags.FEEDBACK_UNKNOWN),
            )
        }

        OutlinedTextField(
            value = giftNotes,
            onValueChange = onGiftNotesChange,
            label = { Text(text = stringResource(R.string.gift_notes_label)) },
            supportingText = {
                Text(
                    text = stringResource(
                        R.string.gift_notes_counter,
                        giftNotes.length,
                        GiftAdvisorViewModel.MAX_NOTES_LENGTH,
                    ),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(GiftAdvisorTestTags.NOTES_FIELD),
        )
    }
}

@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(labelRes)) },
        isError = isError,
        supportingText = {
            if (isError) {
                Text(text = stringResource(R.string.gift_required_field))
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FeedbackButton(
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun GiftAdvisorErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GiftAdvisorTestTags.ERROR_CARD),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
