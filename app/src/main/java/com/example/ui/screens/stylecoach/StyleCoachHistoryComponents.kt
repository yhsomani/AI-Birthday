package com.example.ui.screens.stylecoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.common.JsonTextCodec
import com.example.domain.model.style.StyleProfileHistoryRecord

@Composable
internal fun EmptyHistoryRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StyleCoachTestTags.EMPTY_HISTORY)
            .padding(vertical = RelateSpacing.cardContent),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(RelateSpacing.sm))
        Text(
            text = stringResource(R.string.style_coach_history_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun HistorySnapshotCard(
    snapshot: StyleProfileHistoryRecord,
    savedAt: String,
    modifier: Modifier = Modifier,
) {
    val summary = remember(snapshot.profileJson) {
        StyleHistorySummary.fromJson(snapshot.profileJson)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
        ),
    ) {
        Column(modifier = Modifier.padding(RelateSpacing.compactCardContent)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.xs),
            ) {
                Text(
                    text = historySourceLabel(snapshot.source),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = savedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary?.let {
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = stringResource(
                        R.string.style_coach_history_summary,
                        formalityLabel(it.formalityLevel),
                        it.preferredLanguage,
                        it.avgMessageLength,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private data class StyleHistorySummary(
    val formalityLevel: String,
    val preferredLanguage: String,
    val avgMessageLength: Int,
) {
    companion object {
        fun fromJson(raw: String): StyleHistorySummary? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            return StyleHistorySummary(
                formalityLevel = JsonTextCodec.readStringField(trimmed, "formalityLevel").orEmpty(),
                preferredLanguage = JsonTextCodec.readStringField(trimmed, "preferredLanguage").orEmpty(),
                avgMessageLength = JsonTextCodec.readIntField(trimmed, "avgMessageLength") ?: 0,
            )
        }
    }
}

@Composable
private fun historySourceLabel(source: String): String {
    return when (source) {
        "MANUAL_TRAINING" -> stringResource(R.string.style_coach_source_manual)
        "AUTO_ANALYSIS" -> stringResource(R.string.style_coach_source_auto)
        else -> source
    }
}
