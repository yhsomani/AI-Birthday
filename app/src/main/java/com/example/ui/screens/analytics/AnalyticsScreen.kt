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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
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
        onExportReport = viewModel::exportRelationshipReport,
    )
}

@Composable
internal fun AnalyticsContent(
    state: AnalyticsUiState,
    onNavigateToActivityHistory: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                StatCard(label = stringResource(R.string.dashboard_contacts), value = "${state.totalContacts}", icon = Icons.Filled.People, modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.home_stat_wishes_sent), value = "${state.totalWishesSent}", icon = Icons.Filled.Star, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            ) {
                StatCard(label = stringResource(R.string.messages_pending), value = "${state.pendingApprovals}", icon = Icons.Filled.MailOutline, modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.home_stat_upcoming), value = "${state.upcomingEventsCount}", icon = Icons.Filled.CalendarMonth, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SectionHeader(title = stringResource(R.string.analytics_monthly_wishes))
            RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.MONTHLY_SECTION)) {
                Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                    if (state.monthlyCounts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.analytics_no_wishes_this_year),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        BarChart(data = state.monthlyCounts)
                    }
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SectionHeader(title = stringResource(R.string.analytics_contact_distribution))
            RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.DISTRIBUTION_SECTION)) {
                Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                    val family = state.relationshipCounts["FAMILY"] ?: 0
                    val friends = state.relationshipCounts["FRIEND"] ?: 0
                    val work = state.relationshipCounts["WORK"] ?: 0
                    val closeFriends = state.relationshipCounts["CLOSE_FRIEND"] ?: 0
                    val other = state.relationshipCounts.filterKeys { it !in listOf("FAMILY", "FRIEND", "WORK", "CLOSE_FRIEND") }.values.sum()

                    DistributionRow(stringResource(R.string.contact_filter_family), family, MaterialTheme.colorScheme.primary)
                    DistributionRow(stringResource(R.string.contact_filter_friends), friends, MaterialTheme.colorScheme.secondary)
                    DistributionRow(stringResource(R.string.contact_filter_work), work, MaterialTheme.colorScheme.tertiary)
                    DistributionRow(
                        stringResource(R.string.contact_filter_close_friends),
                        closeFriends,
                        MaterialTheme.relateSemanticColors.warning,
                    )
                    if (other > 0) {
                        DistributionRow(
                            stringResource(R.string.analytics_others),
                            other,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SectionHeader(title = stringResource(R.string.analytics_relationship_health))
            RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.HEALTH_SECTION)) {
                Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                    val healthy = state.healthCounts["Healthy (70%+)"] ?: 0
                    val attention = state.healthCounts["Needs Attention"] ?: 0
                    val atRisk = state.healthCounts["At Risk"] ?: 0

                    HealthTrendRow(
                        stringResource(R.string.analytics_health_healthy),
                        healthy,
                        MaterialTheme.relateSemanticColors.success,
                    )
                    HealthTrendRow(
                        stringResource(R.string.analytics_health_attention),
                        attention,
                        MaterialTheme.relateSemanticColors.warning,
                    )
                    HealthTrendRow(
                        stringResource(R.string.analytics_health_at_risk),
                        atRisk,
                        MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SectionHeader(title = stringResource(R.string.analytics_growth_metrics))
            RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.GROWTH_SECTION)) {
                Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                    DistributionRow(
                        stringResource(R.string.analytics_delivery_reliability),
                        state.deliveryReliabilityPercent,
                        MaterialTheme.relateSemanticColors.success,
                        suffix = "%",
                    )
                    DistributionRow(
                        stringResource(R.string.analytics_response_rate),
                        state.responseRatePercent,
                        MaterialTheme.colorScheme.secondary,
                        suffix = "%",
                    )
                    DistributionRow(
                        stringResource(R.string.analytics_personalization_coverage),
                        state.personalizationCoveragePercent,
                        MaterialTheme.colorScheme.primary,
                        suffix = "%",
                    )
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
            SectionHeader(title = stringResource(R.string.analytics_top_neglected))
            RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.NEGLECTED_SECTION)) {
                Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                    if (state.topNeglectedContacts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.analytics_no_neglected_contacts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.topNeglectedContacts.forEach { contact ->
                            Text(
                                text = contact,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = RelateSpacing.xs),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(RelateSpacing.xl))
        }
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Float>>) {
    val maxValue = data.maxOf { it.second }
    if (maxValue <= 0f) {
        Text(
            text = stringResource(R.string.analytics_no_wishes_this_year),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        data.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RelateSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(RelateSpacing.xxl),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(RelateSize.chartBarHeight)
                        .clip(RoundedCornerShape(RelateRadius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value / maxValue)
                            .height(RelateSize.chartBarHeight)
                            .clip(RoundedCornerShape(RelateRadius.sm))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                Text(
                    text = value.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(RelateSpacing.xl),
                )
            }
        }
    }
}

@Composable
private fun DistributionRow(label: String, count: Int, color: Color, suffix: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RelateSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(RelateSize.indicatorDot)
                .clip(RoundedCornerShape(RelateRadius.xs))
                .background(color),
        )
        Spacer(modifier = Modifier.width(RelateSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count$suffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthTrendRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RelateSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(RelateSize.indicatorDot)
                .clip(RoundedCornerShape(RelateRadius.xs))
                .background(color),
        )
        Spacer(modifier = Modifier.width(RelateSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
