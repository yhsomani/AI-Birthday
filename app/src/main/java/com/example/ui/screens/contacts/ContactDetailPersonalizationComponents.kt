package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactDetailProfile
import com.example.domain.model.memory.MemoryNoteCategoryCount

@Composable
internal fun PersonalizationQualityCard(
    contact: ContactDetailProfile,
    memoryNoteCount: Int = 0,
    memoryNoteCategorySummary: List<MemoryNoteCategoryCount> = emptyList(),
    onAddMemory: () -> Unit = {},
) {
    val checklist = listOf(
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_nickname,
            promptRes = R.string.personalization_quality_add_nickname,
            isComplete = !contact.nickname.isNullOrBlank(),
        ),
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_interests,
            promptRes = R.string.personalization_quality_add_interests,
            isComplete = contact.interestsJson.hasJsonArrayContent(),
        ),
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_memory_notes,
            promptRes = R.string.personalization_quality_add_memory_notes,
            isComplete = memoryNoteCount > 0,
        ),
        PersonalizationQualityItem(
            labelRes = R.string.personalization_quality_channel,
            promptRes = R.string.personalization_quality_choose_channel,
            isComplete = contact.preferredChannel != MessageChannel.UNKNOWN,
        ),
    )
    val complete = checklist.count { it.isComplete }
    val score = (complete * 100) / checklist.size
    val nextPromptRes = checklist.firstOrNull { !it.isComplete }?.promptRes
    val impactRes = when {
        nextPromptRes == null -> R.string.personalization_quality_impact_ready
        score < 50 -> R.string.personalization_quality_impact_low
        else -> R.string.personalization_quality_impact_partial
    }

    RelateGlassCard {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.personalization_quality_title, score),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (nextPromptRes == null) {
                    stringResource(R.string.personalization_quality_ready)
                } else {
                    stringResource(R.string.personalization_quality_next_step, stringResource(nextPromptRes))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(impactRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (memoryNoteCount > 0) {
                Text(
                    text = stringResource(
                        R.string.personalization_quality_memory_summary,
                        memoryNoteCount,
                        memoryNoteCategorySummaryText(memoryNoteCategorySummary),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = onAddMemory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RelateSize.compactButtonHeight)
                        .testTag(ContactDetailTestTags.PERSONALIZATION_ADD_MEMORY),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(RelateRadius.control),
                ) {
                    Text(
                        text = stringResource(R.string.personalization_quality_add_one_memory),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            checklist.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isComplete) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (item.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(RelateSize.iconSm),
                    )
                    Spacer(modifier = Modifier.width(RelateSpacing.sm))
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isComplete) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun memoryNoteCategorySummaryText(
    summary: List<MemoryNoteCategoryCount>,
): String {
    if (summary.isEmpty()) return stringResource(R.string.memory_category_general)
    val parts = mutableListOf<String>()
    for (item in summary) {
        val label = memoryCategoryLabel(item.category)
        parts += stringResource(R.string.personalization_quality_memory_category_count, label, item.count)
    }
    return parts.joinToString(", ")
}

@Composable
private fun memoryCategoryLabel(category: String): String {
    return when (category) {
        "PREFERENCE" -> stringResource(R.string.memory_category_preference)
        "EVENT" -> stringResource(R.string.memory_category_event)
        "GIFT" -> stringResource(R.string.memory_category_gift)
        "MILESTONE" -> stringResource(R.string.memory_category_milestone)
        else -> stringResource(R.string.memory_category_general)
    }
}

private data class PersonalizationQualityItem(
    val labelRes: Int,
    val promptRes: Int,
    val isComplete: Boolean,
)

private fun String.hasJsonArrayContent(): Boolean {
    return try {
        org.json.JSONArray(this).length() > 0
    } catch (_: Exception) {
        trim().isNotBlank() && trim() != "[]"
    }
}
