package com.example.ui.screens.memoryvault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.FilterChip
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.memory.MemoryNotePromptPolicy
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.ui.viewmodel.MemoryVaultViewModel

private data class MemoryCategoryOption(
    val value: String,
    val shortLabelRes: Int,
    val labelRes: Int,
)

private val memoryCategoryOptions = listOf(
    MemoryCategoryOption("GENERAL", R.string.memory_category_general_short, R.string.memory_category_general),
    MemoryCategoryOption(
        MemoryNotePromptPolicy.PRIVATE_REFERENCE_CATEGORY,
        R.string.memory_category_private_short,
        R.string.memory_category_private,
    ),
    MemoryCategoryOption("PREFERENCE", R.string.memory_category_preference_short, R.string.memory_category_preference),
    MemoryCategoryOption("EVENT", R.string.memory_category_event_short, R.string.memory_category_event),
    MemoryCategoryOption("GIFT", R.string.memory_category_gift_short, R.string.memory_category_gift),
    MemoryCategoryOption("MILESTONE", R.string.memory_category_milestone_short, R.string.memory_category_milestone),
)

private data class MemoryPromptOption(
    val category: String,
    val labelRes: Int,
    val templateRes: Int,
)

private val memoryPromptOptions = listOf(
    MemoryPromptOption("PREFERENCE", R.string.memory_prompt_favorite_food, R.string.memory_prompt_favorite_food_template),
    MemoryPromptOption("MILESTONE", R.string.memory_prompt_recent_life_update, R.string.memory_prompt_recent_life_update_template),
    MemoryPromptOption("GENERAL", R.string.memory_prompt_inside_joke, R.string.memory_prompt_inside_joke_template),
    MemoryPromptOption("PREFERENCE", R.string.memory_prompt_things_to_avoid, R.string.memory_prompt_things_to_avoid_template),
    MemoryPromptOption("GIFT", R.string.memory_prompt_gift_preference, R.string.memory_prompt_gift_preference_template),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddMemoryCard(
    newNoteText: String,
    selectedCategory: String,
    onNoteChange: (String) -> Unit,
    onPromptSelected: (String, String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val noteHasOnlyWhitespace = newNoteText.isNotEmpty() && newNoteText.isBlank()

    RelateGlassCard {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.memory_vault_add_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(R.string.memory_vault_suggested_prompts_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
            ) {
                memoryPromptOptions.forEach { option ->
                    val promptText = stringResource(option.templateRes)
                    SuggestionChip(
                        onClick = { onPromptSelected(promptText, option.category) },
                        label = { Text(stringResource(option.labelRes)) },
                        modifier = Modifier.testTag(MemoryVaultTestTags.PROMPT_PREFIX + option.category + "_" + option.labelRes),
                    )
                }
            }

            OutlinedTextField(
                value = newNoteText,
                onValueChange = onNoteChange,
                placeholder = { Text(stringResource(R.string.memory_vault_note_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MemoryVaultTestTags.NOTE_FIELD),
                minLines = 2,
                maxLines = 4,
                isError = noteHasOnlyWhitespace,
                supportingText = {
                    if (noteHasOnlyWhitespace) {
                        Text(text = stringResource(R.string.memory_vault_error_blank_note))
                    } else {
                        Text(
                            text = stringResource(
                                R.string.memory_vault_note_counter,
                                newNoteText.length,
                                MemoryVaultViewModel.MAX_NOTE_LENGTH,
                            ),
                        )
                    }
                },
            )

            CategoryChipRow(
                selectedCategory = selectedCategory,
                tagPrefix = MemoryVaultTestTags.CATEGORY_PREFIX,
                onCategoryChange = onCategoryChange,
            )

            Text(
                text = stringResource(R.string.memory_vault_ai_usage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onAdd,
                enabled = newNoteText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MemoryVaultTestTags.ADD_BUTTON),
            ) {
                Text(stringResource(R.string.memory_vault_add_button))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditMemoryNoteCard(
    note: MemoryNoteRecord,
    editNoteText: String,
    editCategory: String,
    onEditTextChange: (String) -> Unit,
    onEditCategoryChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noteHasOnlyWhitespace = editNoteText.isNotEmpty() && editNoteText.isBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.relateSemanticColors.cardContainer),
    ) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.memory_vault_edit_note),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = editNoteText,
                onValueChange = onEditTextChange,
                placeholder = { Text(stringResource(R.string.memory_vault_note_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MemoryVaultTestTags.EDIT_FIELD_PREFIX + note.id.value),
                minLines = 2,
                maxLines = 4,
                isError = noteHasOnlyWhitespace,
                supportingText = {
                    if (noteHasOnlyWhitespace) {
                        Text(text = stringResource(R.string.memory_vault_error_blank_note))
                    } else {
                        Text(
                            text = stringResource(
                                R.string.memory_vault_note_counter,
                                editNoteText.length,
                                MemoryVaultViewModel.MAX_NOTE_LENGTH,
                            ),
                        )
                    }
                },
            )

            CategoryChipRow(
                selectedCategory = editCategory,
                tagPrefix = MemoryVaultTestTags.EDIT_CATEGORY_PREFIX,
                onCategoryChange = onEditCategoryChange,
            )

            Text(
                text = stringResource(R.string.memory_vault_ai_usage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(MemoryVaultTestTags.EDIT_CANCEL_PREFIX + note.id.value),
                ) {
                    Text(stringResource(R.string.memory_vault_cancel_edit))
                }
                Button(
                    onClick = onSave,
                    enabled = editNoteText.isNotBlank(),
                    modifier = Modifier.testTag(MemoryVaultTestTags.EDIT_SAVE_PREFIX + note.id.value),
                ) {
                    Text(stringResource(R.string.memory_vault_save_edit))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChipRow(
    selectedCategory: String,
    tagPrefix: String,
    onCategoryChange: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
    ) {
        memoryCategoryOptions.forEach { option ->
            FilterChip(
                label = stringResource(option.shortLabelRes),
                isSelected = selectedCategory == option.value,
                onClick = { onCategoryChange(option.value) },
                modifier = Modifier.testTag(tagPrefix + option.value),
            )
        }
    }
}

@Composable
internal fun memoryCategoryLabel(category: String): String {
    val option = memoryCategoryOptions.firstOrNull { it.value == category }
    return option?.let { stringResource(it.labelRes) } ?: category
}
