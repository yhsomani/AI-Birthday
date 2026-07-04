package com.example.ui.screens.stylecoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.theme.RelateElevation
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.StyleCoachUiState
import com.example.ui.viewmodel.StyleCoachViewModel
import java.text.DateFormat
import java.util.Date

internal object StyleCoachTestTags {
    const val SAMPLE_FIELD = "style_coach_sample_field"
    const val MANUAL_ANALYZE_BUTTON = "style_coach_manual_analyze_button"
    const val AUTO_ANALYZE_BUTTON = "style_coach_auto_analyze_button"
    const val MANUAL_PROGRESS = "style_coach_manual_progress"
    const val AUTO_PROGRESS = "style_coach_auto_progress"
    const val STATUS_MESSAGE = "style_coach_status_message"
    const val PROFILE_CARD = "style_coach_profile_card"
    const val STYLE_PREVIEW = "style_coach_style_preview"
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
                .background(MaterialTheme.colorScheme.background)
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

                item {
                    StyleImpactPreviewCard(
                        profile = profile,
                        modifier = Modifier.testTag(StyleCoachTestTags.STYLE_PREVIEW),
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

private fun parseSampleBlocks(samplesText: String): List<String> =
    samplesText
        .split("\n\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
