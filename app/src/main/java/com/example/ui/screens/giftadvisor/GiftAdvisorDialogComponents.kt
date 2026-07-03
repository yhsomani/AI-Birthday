package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.GiftAdvisorViewModel

@Composable
internal fun AddGiftDialog(
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
internal fun GiftAdvisorErrorCard(message: String) {
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
