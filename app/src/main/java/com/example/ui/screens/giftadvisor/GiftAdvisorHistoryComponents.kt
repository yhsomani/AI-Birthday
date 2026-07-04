package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.viewmodel.GiftAdvisorUiState

@Composable
internal fun GiftRecordBottomBar(onShowAddDialog: () -> Unit) {
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
internal fun BudgetStats(
    uiState: GiftAdvisorUiState,
    onAdjustBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        TextButton(
            onClick = onAdjustBudget,
            modifier = Modifier
                .align(Alignment.End)
                .testTag(GiftAdvisorTestTags.ADJUST_BUDGET_BUTTON),
        ) {
            Icon(
                imageVector = Icons.Filled.AttachMoney,
                contentDescription = null,
                modifier = Modifier.size(RelateSize.iconSm),
            )
            Spacer(modifier = Modifier.width(RelateSpacing.xs))
            Text(text = stringResource(R.string.gift_advisor_adjust_budget))
        }
    }
}

internal fun LazyListScope.giftHistoryItems(
    giftHistory: List<GiftHistoryRecord>,
    onDeleteGift: (GiftHistoryRecord) -> Unit,
) {
    item {
        SectionHeader(
            title = stringResource(R.string.gift_history_journal_title),
            modifier = Modifier.testTag(GiftAdvisorTestTags.HISTORY_HEADER),
        )
    }

    if (giftHistory.isEmpty()) {
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
        items(giftHistory, key = { it.id.value }) { gift ->
            GiftHistoryCard(
                gift = gift,
                onDelete = { onDeleteGift(gift) },
                modifier = Modifier.testTag(GiftAdvisorTestTags.HISTORY_CARD_PREFIX + gift.id.value),
            )
        }
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
