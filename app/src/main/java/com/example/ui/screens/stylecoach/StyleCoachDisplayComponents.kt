package com.example.ui.screens.stylecoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.style.StyleProfileRecord
import java.util.Locale
import org.json.JSONArray

@Composable
internal fun LearnedProfileCard(
    profile: StyleProfileRecord,
    modifier: Modifier = Modifier,
) {
    val commonGreetings = parseJsonArray(profile.commonGreetingsJson)
    val emojiSet = parseJsonArray(profile.emojiSetJson)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(RelateElevation.card),
    ) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricBlock(
                    label = stringResource(R.string.style_coach_confidence_level),
                    value = styleConfidenceLabel(profile.sampleCount),
                )
                MetricBlock(
                    label = stringResource(R.string.style_coach_samples_learned),
                    value = stringResource(
                        R.string.style_coach_samples_learned_value,
                        profile.sampleCount.coerceAtLeast(0),
                    ),
                    horizontalAlignment = Alignment.End,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricBlock(
                    label = stringResource(R.string.style_coach_formality_level),
                    value = formalityLabel(profile.formalityLevel),
                )
                MetricBlock(
                    label = stringResource(R.string.style_coach_emoji_preference),
                    value = if (profile.usesEmoji) {
                        stringResource(R.string.style_coach_emoji_expressive)
                    } else {
                        stringResource(R.string.style_coach_emoji_plain)
                    },
                    horizontalAlignment = Alignment.End,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricBlock(
                    label = stringResource(R.string.style_coach_language_accent),
                    value = if (profile.preferredLanguage == "hi") {
                        stringResource(R.string.style_coach_language_hindi)
                    } else {
                        stringResource(R.string.style_coach_language_english)
                    },
                )
                MetricBlock(
                    label = stringResource(R.string.style_coach_avg_message_length),
                    value = stringResource(
                        R.string.style_coach_avg_message_length_value,
                        profile.avgMessageLength,
                    ),
                    horizontalAlignment = Alignment.End,
                )
            }

            HorizontalDivider()

            ProfileListBlock(
                label = stringResource(R.string.style_coach_common_greetings),
                values = commonGreetings,
                emptyText = stringResource(R.string.style_coach_none_detected_yet),
            )

            ProfileListBlock(
                label = stringResource(R.string.style_coach_most_used_emojis),
                values = emojiSet,
                emptyText = stringResource(R.string.style_coach_none_detected),
                expressive = true,
            )
        }
    }
}

@Composable
internal fun StyleImpactPreviewCard(
    profile: StyleProfileRecord,
    modifier: Modifier = Modifier,
) {
    val commonGreetings = parseJsonArray(profile.commonGreetingsJson)
    val emojiSet = parseJsonArray(profile.emojiSetJson)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
        ),
    ) {
        Column(
            modifier = Modifier.padding(RelateSpacing.compactCardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.style_coach_preview_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            StylePreviewLine(
                label = stringResource(R.string.style_coach_preview_opening),
                value = stylePreviewOpening(profile, commonGreetings),
            )
            StylePreviewLine(
                label = stringResource(R.string.style_coach_preview_tone),
                value = stylePreviewTone(profile),
            )
            StylePreviewLine(
                label = stringResource(R.string.style_coach_preview_length),
                value = stringResource(
                    R.string.style_coach_preview_length_value,
                    profile.avgMessageLength.coerceAtLeast(0),
                ),
            )
            StylePreviewLine(
                label = stringResource(R.string.style_coach_preview_emoji),
                value = stylePreviewEmoji(profile, emojiSet),
            )
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileListBlock(
    label: String,
    values: List<String>,
    emptyText: String,
    expressive: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = values.takeIf { it.isNotEmpty() }?.joinToString(if (expressive) "  " else ", ")
                ?: emptyText,
            style = if (expressive && values.isNotEmpty()) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (values.isNotEmpty() && !expressive) FontWeight.Medium else null,
        )
    }
}

@Composable
private fun StylePreviewLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RelateSpacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun stylePreviewOpening(
    profile: StyleProfileRecord,
    commonGreetings: List<String>,
): String {
    val opener = commonGreetings.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: defaultPreviewOpening(profile)
    return stringResource(R.string.style_coach_preview_opening_value, opener)
}

@Composable
private fun defaultPreviewOpening(profile: StyleProfileRecord): String {
    return when {
        profile.preferredLanguage.equals("hi", ignoreCase = true) -> {
            stringResource(R.string.style_coach_preview_opening_hindi)
        }

        profile.formalityLevel.trim().uppercase(Locale.ROOT) == "FORMAL" -> {
            stringResource(R.string.style_coach_preview_opening_formal)
        }

        else -> stringResource(R.string.style_coach_preview_opening_casual)
    }
}

@Composable
private fun stylePreviewTone(profile: StyleProfileRecord): String {
    val language = if (profile.preferredLanguage.equals("hi", ignoreCase = true)) {
        stringResource(R.string.style_coach_language_hindi)
    } else {
        stringResource(R.string.style_coach_language_english)
    }
    return stringResource(
        R.string.style_coach_preview_tone_value,
        formalityLabel(profile.formalityLevel),
        language,
    )
}

@Composable
private fun stylePreviewEmoji(
    profile: StyleProfileRecord,
    emojiSet: List<String>,
): String {
    return when {
        !profile.usesEmoji -> stringResource(R.string.style_coach_preview_emoji_none)
        emojiSet.isNotEmpty() -> stringResource(
            R.string.style_coach_preview_emoji_value,
            emojiSet.take(3).joinToString(" "),
        )

        else -> stringResource(R.string.style_coach_preview_emoji_light)
    }
}

@Composable
internal fun formalityLabel(formality: String): String = when (formality.trim().uppercase(Locale.ROOT)) {
    "CASUAL" -> stringResource(R.string.formality_casual)
    "SEMI_FORMAL" -> stringResource(R.string.formality_semi_formal)
    "FORMAL" -> stringResource(R.string.formality_formal)
    else -> formality
}

@Composable
private fun styleConfidenceLabel(sampleCount: Int): String {
    return when {
        sampleCount >= 12 -> stringResource(R.string.style_coach_confidence_strong)
        sampleCount >= 5 -> stringResource(R.string.style_coach_confidence_growing)
        sampleCount > 0 -> stringResource(R.string.style_coach_confidence_starting)
        else -> stringResource(R.string.style_coach_confidence_untrained)
    }
}

private fun parseJsonArray(raw: String): List<String> {
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.getString(index) }
    }.getOrDefault(emptyList())
}
