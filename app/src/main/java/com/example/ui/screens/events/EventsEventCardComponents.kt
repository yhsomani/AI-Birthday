package com.example.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.domain.event.EventResolutionPolicy
import com.example.domain.model.occasion.EventListItem
import com.example.domain.model.occasion.OccasionType
import com.example.ui.viewmodel.EventTrustConflictState
import com.example.ui.viewmodel.EventTrustState
import com.example.ui.viewmodel.EventVerificationState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun eventTypeLabel(type: String): String = when (OccasionType.fromRaw(type)) {
    OccasionType.BIRTHDAY -> stringResource(R.string.event_type_birthday)
    OccasionType.ANNIVERSARY -> stringResource(R.string.event_type_anniversary)
    OccasionType.WORK_ANNIVERSARY -> stringResource(R.string.event_type_work_anniversary)
    OccasionType.GRADUATION -> stringResource(R.string.event_type_graduation)
    OccasionType.HOLIDAY -> stringResource(R.string.event_type_holiday)
    OccasionType.REVIVAL -> stringResource(R.string.event_type_revival)
    OccasionType.FOLLOW_UP -> stringResource(R.string.event_type_follow_up)
    OccasionType.CUSTOM -> stringResource(R.string.event_type_custom)
    else -> stringResource(R.string.event_type_custom)
}

private fun eventTypeIcon(type: String): ImageVector = when (OccasionType.fromRaw(type)) {
    OccasionType.BIRTHDAY -> Icons.Filled.Favorite
    OccasionType.ANNIVERSARY,
    OccasionType.WORK_ANNIVERSARY -> Icons.Filled.Star
    else -> Icons.Filled.CalendarMonth
}

@Composable
private fun eventSourceLabel(source: String): String {
    val baseSource = EventResolutionPolicy.baseSource(source)
    return when (baseSource.trim().uppercase(Locale.US)) {
        "CONTACTS" -> stringResource(R.string.event_source_contacts)
        "MANUAL" -> stringResource(R.string.event_source_manual)
        "CALENDAR" -> stringResource(R.string.event_source_calendar)
        "AI_INFERRED" -> stringResource(R.string.event_source_ai_inferred)
        "MERGED" -> stringResource(R.string.event_source_merged)
        "CONFLICT" -> stringResource(R.string.event_source_conflict)
        else -> baseSource.toReadableEventSource()
    }
}

private fun String.toReadableEventSource(): String {
    val words = trim()
        .replace('_', ' ')
        .lowercase(Locale.US)
        .split(' ')
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    return words.joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun EventCard(
    event: EventListItem,
    trustState: EventTrustState,
    isResolving: Boolean,
    currentTimeMillis: Long,
    onMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
) {
    val daysUntil = event.daysUntil(currentTimeMillis)
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val sourceLabel = eventSourceLabel(trustState.source).ifBlank { stringResource(R.string.event_source_unknown) }
    val verificationLabel = eventVerificationLabel(trustState)
    val sourceColor = when (EventResolutionPolicy.baseSource(trustState.source).trim().uppercase(Locale.US)) {
        "MANUAL" -> MaterialTheme.colorScheme.primary
        "CONFLICT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val verificationColor = when (trustState.verification) {
        EventVerificationState.CONFLICT -> MaterialTheme.colorScheme.error
        EventVerificationState.VERIFIED -> MaterialTheme.colorScheme.primary
        EventVerificationState.NEEDS_REVIEW -> MaterialTheme.relateSemanticColors.warning
    }

    RelateGlassCard {
        Row(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(RelateSize.minTouchTarget)
                    .clip(RoundedCornerShape(RelateRadius.control))
                    .background(
                        if (daysUntil <= 14) {
                            MaterialTheme.colorScheme.primary.copy(alpha = RelateAlpha.feedbackContainer)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    eventTypeIcon(event.type.raw),
                    contentDescription = null,
                    tint = if (daysUntil <= 14) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(RelateSize.iconLg),
                )
            }
            Spacer(modifier = Modifier.width(RelateSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.label ?: eventTypeLabel(event.type.raw),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(
                        R.string.events_card_subtitle,
                        eventTypeLabel(event.type.raw),
                        dateFormat.format(Date(event.nextOccurrenceMs)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
                ) {
                    EventMetadataChip(
                        text = sourceLabel,
                        color = sourceColor,
                    )
                    EventMetadataChip(
                        text = verificationLabel,
                        color = verificationColor,
                    )
                    eventConflictLabel(trustState.conflict)?.let { conflictLabel ->
                        EventMetadataChip(
                            text = conflictLabel,
                            color = if (trustState.conflict == EventTrustConflictState.DATE_CONFLICT) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.relateSemanticColors.warning
                            },
                        )
                    }
                }
                if (trustState.conflict != EventTrustConflictState.NONE) {
                    Spacer(modifier = Modifier.height(RelateSpacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
                    ) {
                        TextButton(
                            enabled = !isResolving,
                            onClick = onMerge,
                        ) {
                            Text(
                                if (isResolving) {
                                    stringResource(R.string.saving)
                                } else {
                                    stringResource(R.string.event_resolution_merge_here)
                                }
                            )
                        }
                        TextButton(
                            enabled = !isResolving,
                            onClick = onKeepSeparate,
                        ) {
                            Text(stringResource(R.string.event_resolution_keep_separate))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$daysUntil",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.days),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun eventVerificationLabel(trustState: EventTrustState): String {
    return when (trustState.verification) {
        EventVerificationState.CONFLICT -> stringResource(R.string.event_verification_conflict)
        EventVerificationState.VERIFIED -> stringResource(R.string.event_verification_verified)
        EventVerificationState.NEEDS_REVIEW -> stringResource(
            R.string.event_verification_needs_review,
            trustState.confidenceScore,
        )
    }
}

@Composable
private fun eventConflictLabel(conflict: EventTrustConflictState): String? {
    return when (conflict) {
        EventTrustConflictState.NONE -> null
        EventTrustConflictState.DUPLICATE -> stringResource(R.string.event_conflict_duplicate)
        EventTrustConflictState.DATE_CONFLICT -> stringResource(R.string.event_conflict_date)
    }
}

@Composable
private fun EventMetadataChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .sizeIn(minHeight = RelateSize.chipMinHeight)
            .clip(RoundedCornerShape(RelateRadius.control))
            .background(color.copy(alpha = RelateAlpha.feedbackContainer))
            .padding(horizontal = RelateSpacing.sm, vertical = RelateSpacing.xs),
    )
}
