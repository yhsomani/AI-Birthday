package com.example.ui.screens.analytics

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.AnalyticsUiState
import com.example.ui.viewmodel.AnalyticsViewModel

internal object AnalyticsScreenTestTags {
    const val ACTIVITY_HISTORY_BUTTON = "analytics_activity_history_button"
    const val EXPORT_BUTTON = "analytics_export_button"
    const val LOADING = "analytics_loading"
    const val MONTHLY_SECTION = "analytics_monthly_section"
    const val DISTRIBUTION_SECTION = "analytics_distribution_section"
    const val HEALTH_SECTION = "analytics_health_section"
    const val GROWTH_SECTION = "analytics_growth_section"
    const val NEGLECTED_SECTION = "analytics_neglected_section"
}

@Composable
fun AnalyticsScreen(
    onNavigateToActivityHistory: () -> Unit = {},
    onNavigateToContact: (String) -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.analytics_share_chooser)
    val exportError = stringResource(R.string.analytics_export_failed)

    LaunchedEffect(state.exportReport) {
        state.exportReport?.let { report ->
            runCatching {
                val sendIntent = AnalyticsExportShare.createSendIntent(context, report)
                context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
            }.onFailure {
                Toast.makeText(context, exportError, Toast.LENGTH_LONG).show()
            }.also {
                viewModel.clearExportReport()
            }
        }
    }

    LaunchedEffect(state.exportError) {
        if (state.exportError) {
            Toast.makeText(context, exportError, Toast.LENGTH_LONG).show()
            viewModel.clearExportError()
        }
    }

    AnalyticsContent(
        state = state,
        onNavigateToActivityHistory = onNavigateToActivityHistory,
        onNavigateToContact = onNavigateToContact,
        onExportReport = viewModel::exportRelationshipReport,
    )
}

@Composable
internal fun AnalyticsContent(
    state: AnalyticsUiState,
    onNavigateToActivityHistory: () -> Unit,
    onNavigateToContact: (String) -> Unit,
    onExportReport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = RelateSpacing.screenHorizontal)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(RelateSize.minTouchTarget))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.analytics),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row {
                IconButton(
                    onClick = onNavigateToActivityHistory,
                    modifier = Modifier.testTag(AnalyticsScreenTestTags.ACTIVITY_HISTORY_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(R.string.activity_history_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onExportReport,
                    enabled = !state.isExporting,
                    modifier = Modifier.testTag(AnalyticsScreenTestTags.EXPORT_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.analytics_export_report),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RelateSize.loadingPanelHeight),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(AnalyticsScreenTestTags.LOADING),
                )
            }
        } else {
            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            AnalyticsStatsGrid(state = state)

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            AnalyticsMonthlyWishesSection(monthlyCounts = state.monthlyCounts)

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            AnalyticsContactDistributionSection(relationshipCounts = state.relationshipCounts)

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            AnalyticsRelationshipHealthSection(healthCounts = state.healthCounts)

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            AnalyticsGrowthMetricsSection(state = state)

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            AnalyticsNeglectedContactsSection(
                state = state,
                onNavigateToContact = onNavigateToContact,
            )

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
        }
    }
}
