package com.example.ui.screens.stylecoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.RelateSuccess
import com.example.ui.viewmodel.StyleCoachUiState
import com.example.ui.viewmodel.StyleCoachViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal object StyleCoachTestTags {
    const val SAMPLE_FIELD = "style_coach_sample_field"
    const val MANUAL_ANALYZE_BUTTON = "style_coach_manual_analyze_button"
    const val AUTO_ANALYZE_BUTTON = "style_coach_auto_analyze_button"
    const val MANUAL_PROGRESS = "style_coach_manual_progress"
    const val AUTO_PROGRESS = "style_coach_auto_progress"
    const val STATUS_MESSAGE = "style_coach_status_message"
    const val PROFILE_CARD = "style_coach_profile_card"
    const val EMPTY_HISTORY = "style_coach_empty_history"
    const val HISTORY_CARD_PREFIX = "style_coach_history_"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleCoachScreen(
    onBack: () -> Unit,
    viewModel: StyleCoachViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var samplesText by remember { mutableStateOf("") }

    StyleCoachContent(
        uiState = uiState,
        samplesText = samplesText,
        onSamplesChange = { samplesText = it },
        onBack = onBack,
        onManualAnalyze = viewModel::trainStyle,
        onAutoAnalyze = viewModel::analyzeRecentSentMessages,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StyleCoachContent(
    uiState: StyleCoachUiState,
    samplesText: String,
    onSamplesChange: (String) -> Unit,
    onBack: () -> Unit,
    onManualAnalyze: (List<String>) -> Unit,
    onAutoAnalyze: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.style_coach_title),
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(RelateDarkBackground)
                .padding(RelateSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.lg),
            contentPadding = PaddingValues(bottom = RelateSpacing.xxxl + RelateSpacing.xxl),
        ) {
            item {
                StyleTrainingCard(
                    samplesText = samplesText,
                    onSamplesChange = onSamplesChange,
                    isTraining = uiState.isTraining,
                    isAutoAnalyzing = uiState.isAutoAnalyzing,
                    statusMessageRes = uiState.statusMessageRes,
                    statusIsError = uiState.statusIsError,
                    onManualAnalyze = {
                        onManualAnalyze(parseSampleBlocks(samplesText))
                    },
                    onAutoAnalyze = onAutoAnalyze,
                )
            }

            uiState.profile?.let { profile ->
                item {
                    Text(
                        text = stringResource(R.string.style_coach_profile_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                item {
                    LearnedProfileCard(
                        profile = profile,
                        modifier = Modifier.testTag(StyleCoachTestTags.PROFILE_CARD),
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.style_coach_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (uiState.history.isEmpty()) {
                item {
                    EmptyHistoryRow()
                }
            } else {
                items(uiState.history, key = { it.id }) { snapshot ->
                    HistorySnapshotCard(
                        snapshot = snapshot,
                        savedAt = dateFormat.format(Date(snapshot.savedAtMs)),
                        modifier = Modifier.testTag(StyleCoachTestTags.HISTORY_CARD_PREFIX + snapshot.id),
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleTrainingCard(
    samplesText: String,
    onSamplesChange: (String) -> Unit,
    isTraining: Boolean,
    isAutoAnalyzing: Boolean,
    statusMessageRes: Int?,
    statusIsError: Boolean,
    onManualAnalyze: () -> Unit,
    onAutoAnalyze: () -> Unit,
) {
    val busy = isTraining || isAutoAnalyzing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
        ),
    ) {
        Column(
            modifier = Modifier.padding(RelateSpacing.cardContent),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(RelateSize.iconLg),
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = stringResource(R.string.style_coach_train_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(R.string.style_coach_train_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = samplesText,
                onValueChange = onSamplesChange,
                label = { Text(stringResource(R.string.style_coach_samples_label)) },
                placeholder = { Text(stringResource(R.string.style_coach_samples_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StyleCoachTestTags.SAMPLE_FIELD),
                minLines = 4,
                maxLines = 8,
            )

            statusMessageRes?.let { messageRes ->
                StatusMessage(
                    message = stringResource(messageRes),
                    isError = statusIsError,
                )
            }

            Button(
                onClick = onManualAnalyze,
                enabled = !busy && samplesText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StyleCoachTestTags.MANUAL_ANALYZE_BUTTON),
                shape = RoundedCornerShape(RelateRadius.control),
            ) {
                if (isTraining) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(RelateSize.iconLg)
                            .testTag(StyleCoachTestTags.MANUAL_PROGRESS),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.style_coach_manual_analyze))
                }
            }

            OutlinedButton(
                onClick = onAutoAnalyze,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StyleCoachTestTags.AUTO_ANALYZE_BUTTON),
                shape = RoundedCornerShape(RelateRadius.control),
            ) {
                if (isAutoAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(RelateSize.iconSm)
                            .testTag(StyleCoachTestTags.AUTO_PROGRESS),
                        strokeWidth = RelateSize.progressStroke,
                    )
                } else {
                    Text(stringResource(R.string.style_coach_auto_analyze))
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean,
) {
    val color = if (isError) MaterialTheme.colorScheme.error else RelateSuccess
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StyleCoachTestTags.STATUS_MESSAGE),
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Info else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = color,
        )
        Text(
            text = message,
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LearnedProfileCard(
    profile: StyleProfileEntity,
    modifier: Modifier = Modifier,
) {
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
                values = parseJsonArray(profile.commonGreetingsJson),
                emptyText = stringResource(R.string.style_coach_none_detected_yet),
            )

            ProfileListBlock(
                label = stringResource(R.string.style_coach_most_used_emojis),
                values = parseJsonArray(profile.emojiSetJson),
                emptyText = stringResource(R.string.style_coach_none_detected),
                expressive = true,
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
private fun EmptyHistoryRow() {
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
private fun HistorySnapshotCard(
    snapshot: StyleProfileHistoryEntity,
    savedAt: String,
    modifier: Modifier = Modifier,
) {
    val snapshotObj = remember(snapshot.profileJson) {
        runCatching { JSONObject(snapshot.profileJson) }.getOrNull()
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
            snapshotObj?.let { obj ->
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = stringResource(
                        R.string.style_coach_history_summary,
                        formalityLabel(obj.optString("formalityLevel")),
                        obj.optString("preferredLanguage"),
                        obj.optInt("avgMessageLength"),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun formalityLabel(formality: String): String = when (formality.trim().uppercase(Locale.ROOT)) {
    "CASUAL" -> stringResource(R.string.formality_casual)
    "SEMI_FORMAL" -> stringResource(R.string.formality_semi_formal)
    "FORMAL" -> stringResource(R.string.formality_formal)
    else -> formality
}

@Composable
private fun historySourceLabel(source: String): String {
    return when (source) {
        "MANUAL_TRAINING" -> stringResource(R.string.style_coach_source_manual)
        "AUTO_ANALYSIS" -> stringResource(R.string.style_coach_source_auto)
        else -> source
    }
}

private fun parseJsonArray(raw: String): List<String> {
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.getString(index) }
    }.getOrDefault(emptyList())
}

private fun parseSampleBlocks(samplesText: String): List<String> =
    samplesText
        .split("\n\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
