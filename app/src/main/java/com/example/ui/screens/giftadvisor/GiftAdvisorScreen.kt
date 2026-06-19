package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.domain.service.GiftSuggestion
import com.example.ui.viewmodel.GiftAdvisorUiState
import com.example.ui.viewmodel.GiftAdvisorViewModel

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
    var itemToDelete by remember { mutableStateOf<GiftHistoryEntity?>(null) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.contact?.name?.let {
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
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = RelatePrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = stringResource(R.string.gift_advisor_record_gift),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RelateDarkBackground)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = RelatePrimary)
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
                    .background(RelateDarkBackground)
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    BudgetStats(uiState = uiState)
                }

                uiState.errorMessageRes?.let { errorRes ->
                    item {
                        GiftAdvisorErrorCard(message = stringResource(errorRes))
                    }
                }

                item {
                    GiftSuggestionsPanel(
                        uiState = uiState,
                        onGenerateSuggestions = viewModel::generateGiftSuggestions,
                    )
                }

                item {
                    SectionHeader(title = stringResource(R.string.gift_history_journal_title))
                }

                if (uiState.giftHistory.isEmpty()) {
                    item {
                        EmptyState(
                            message = stringResource(R.string.gift_history_empty_message),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                    }
                } else {
                    items(uiState.giftHistory, key = { it.id }) { gift ->
                        GiftHistoryCard(
                            gift = gift,
                            onDelete = { itemToDelete = gift },
                        )
                    }
                }
            }
        }
    }

    itemToDelete?.let { gift ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGiftRecord(gift)
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddDialog) {
        AddGiftDialog(
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
            errorMessageRes = uiState.errorMessageRes,
            onDismiss = {
                showAddDialog = false
                attemptedSubmit = false
            },
            onSave = {
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
        )
    }
}

@Composable
private fun BudgetStats(uiState: GiftAdvisorUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            label = stringResource(R.string.gift_stat_annual_budget),
            value = stringResource(R.string.gift_currency_inr_format, uiState.contact?.giftBudgetInr ?: 500),
            icon = Icons.Filled.CardGiftcard,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.gift_stat_total_spent),
            value = stringResource(R.string.gift_currency_inr_format, uiState.totalSpentThisYear),
            icon = Icons.Filled.ShoppingCart,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.gift_stat_remaining),
            value = stringResource(R.string.gift_currency_inr_format, uiState.remainingBudget),
            icon = Icons.Filled.AttachMoney,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GiftSuggestionsPanel(
    uiState: GiftAdvisorUiState,
    onGenerateSuggestions: () -> Unit,
) {
    RelateGlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    color = RelatePrimary,
                )

                Button(
                    onClick = onGenerateSuggestions,
                    enabled = !uiState.isGeneratingSuggestions,
                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
                ) {
                    if (uiState.isGeneratingSuggestions) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
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
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.suggestions.forEach { suggestion ->
                        GiftSuggestionCard(suggestion = suggestion)
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftSuggestionCard(suggestion: GiftSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.gift_currency_inr_format, suggestion.estimatedCostInr),
                    color = RelatePrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
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
    gift: GiftHistoryEntity,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RelateCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = gift.giftName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = gift.giftCategory,
                        style = MaterialTheme.typography.labelMedium,
                        color = RelatePrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.gift_occasion_year_format, gift.occasionType, gift.year),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.gift_currency_inr_format, gift.approxCostInr),
                        style = MaterialTheme.typography.titleMedium,
                        color = RelatePrimary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    GiftFeedbackIcon(receivedWell = gift.receivedWell)
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.gift_delete_record, gift.giftName),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            if (gift.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
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
            tint = Color(0xFF10B981),
        )

        false -> Icon(
            imageVector = Icons.Filled.ThumbDown,
            contentDescription = stringResource(R.string.gift_feedback_disliked),
            tint = Color(0xFFEF4444),
        )

        null -> Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = stringResource(R.string.gift_feedback_unknown),
            tint = Color.Gray,
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
        title = { Text(text = stringResource(R.string.gift_record_history_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                errorMessageRes?.let { errorRes ->
                    GiftAdvisorErrorCard(message = stringResource(errorRes))
                }

                RequiredTextField(
                    value = giftName,
                    onValueChange = onGiftNameChange,
                    labelRes = R.string.gift_name_label,
                    isError = showGiftNameError,
                )

                RequiredTextField(
                    value = giftCategory,
                    onValueChange = onGiftCategoryChange,
                    labelRes = R.string.gift_category_label,
                    isError = showCategoryError,
                )

                RequiredTextField(
                    value = occasionType,
                    onValueChange = onOccasionTypeChange,
                    labelRes = R.string.gift_occasion_label,
                    isError = showOccasionError,
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
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.gift_feedback_question),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedbackButton(
                        selected = receivedWellState == true,
                        onClick = { onReceivedWellChange(true) },
                        selectedColor = Color(0xFF10B981),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.ThumbUp,
                                contentDescription = stringResource(R.string.gift_feedback_liked),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FeedbackButton(
                        selected = receivedWellState == false,
                        onClick = { onReceivedWellChange(false) },
                        selectedColor = Color(0xFFEF4444),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.ThumbDown,
                                contentDescription = stringResource(R.string.gift_feedback_disliked),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FeedbackButton(
                        selected = receivedWellState == null,
                        onClick = { onReceivedWellChange(null) },
                        selectedColor = Color.Gray,
                        icon = {
                            Text(text = stringResource(R.string.gift_feedback_unknown_short))
                        },
                        modifier = Modifier.weight(1f),
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.gift_save_record))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    isError: Boolean,
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
        modifier = Modifier.fillMaxWidth(),
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
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
