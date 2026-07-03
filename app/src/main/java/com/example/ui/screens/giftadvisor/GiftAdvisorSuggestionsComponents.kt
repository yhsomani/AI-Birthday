package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.GiftAdvisorUiState
import com.example.ui.viewmodel.GiftSuggestionBudgetStatus
import com.example.ui.viewmodel.GiftSuggestionUiModel

@Composable
internal fun GiftSuggestionsPanel(
    uiState: GiftAdvisorUiState,
    onGenerateSuggestions: () -> Unit,
    onDismissSuggestion: (Int) -> Unit,
    onRecordSuggestion: (GiftSuggestionUiModel) -> Unit,
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
                            onDismissSuggestion = { onDismissSuggestion(index) },
                            onRecordSuggestion = { onRecordSuggestion(suggestion) },
                            modifier = Modifier.testTag(GiftAdvisorTestTags.SUGGESTION_CARD_PREFIX + index),
                            recordButtonModifier = Modifier.testTag(
                                GiftAdvisorTestTags.SUGGESTION_RECORD_BUTTON_PREFIX + index,
                            ),
                            dismissButtonModifier = Modifier.testTag(
                                GiftAdvisorTestTags.SUGGESTION_DISMISS_BUTTON_PREFIX + index,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftSuggestionCard(
    suggestion: GiftSuggestionUiModel,
    onDismissSuggestion: () -> Unit,
    onRecordSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
    recordButtonModifier: Modifier = Modifier,
    dismissButtonModifier: Modifier = Modifier,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.gift_currency_inr_format, suggestion.estimatedCostInr),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.width(RelateSpacing.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismissSuggestion,
                        modifier = dismissButtonModifier,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(
                                R.string.gift_suggestion_dismiss,
                                suggestion.name,
                            ),
                        )
                    }
                    Button(
                        onClick = onRecordSuggestion,
                        modifier = recordButtonModifier,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CardGiftcard,
                            contentDescription = null,
                            modifier = Modifier.size(RelateSize.iconSm),
                        )
                        Spacer(modifier = Modifier.width(RelateSpacing.xs))
                        Text(text = stringResource(R.string.gift_suggestion_record_button))
                    }
                }
            }
            Spacer(modifier = Modifier.height(RelateSpacing.xs))
            Text(
                text = suggestion.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            GiftSuggestionEvidence(suggestion = suggestion)
        }
    }
}

@Composable
private fun GiftSuggestionEvidence(suggestion: GiftSuggestionUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.xxs)) {
        GiftSuggestionEvidenceLine(
            text = stringResource(R.string.gift_suggestion_confidence, suggestion.confidencePercent),
            color = MaterialTheme.colorScheme.primary,
        )

        when (suggestion.budgetStatus) {
            GiftSuggestionBudgetStatus.WITHIN_REMAINING_BUDGET -> GiftSuggestionEvidenceLine(
                text = stringResource(R.string.gift_suggestion_budget_within),
                color = MaterialTheme.relateSemanticColors.success,
            )

            GiftSuggestionBudgetStatus.OVER_REMAINING_BUDGET -> GiftSuggestionEvidenceLine(
                text = stringResource(R.string.gift_suggestion_budget_over, suggestion.budgetOverageInr),
                color = MaterialTheme.colorScheme.error,
            )

            GiftSuggestionBudgetStatus.UNKNOWN -> Unit
        }

        val duplicateName = suggestion.duplicateGiftName
        if (duplicateName != null) {
            GiftSuggestionEvidenceLine(
                text = stringResource(R.string.gift_suggestion_duplicate_warning, duplicateName),
                color = MaterialTheme.colorScheme.error,
            )
        } else if (suggestion.checkedAgainstHistory) {
            GiftSuggestionEvidenceLine(
                text = stringResource(R.string.gift_suggestion_history_checked),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GiftSuggestionEvidenceLine(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
