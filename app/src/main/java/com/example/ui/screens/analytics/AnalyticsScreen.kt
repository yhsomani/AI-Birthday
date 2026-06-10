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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.AnalyticsViewModel

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
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = report.mimeType
                putExtra(Intent.EXTRA_TITLE, report.fileName)
                putExtra(Intent.EXTRA_SUBJECT, report.fileName)
                putExtra(Intent.EXTRA_TEXT, report.content)
            }
            context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
            viewModel.clearExportReport()
        }
    }

    LaunchedEffect(state.exportError) {
        if (state.exportError) {
            Toast.makeText(context, exportError, Toast.LENGTH_LONG).show()
            viewModel.clearExportError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
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
                IconButton(onClick = onNavigateToActivityHistory) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(R.string.activity_history_title),
                        tint = RelateOnSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = viewModel::exportRelationshipReport,
                    enabled = !state.isExporting,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.analytics_export_report),
                        tint = RelatePrimary,
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(label = stringResource(R.string.dashboard_contacts), value = "${state.totalContacts}", icon = Icons.Filled.People, modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.home_stat_wishes_sent), value = "${state.totalWishesSent}", icon = Icons.Filled.Star, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(label = stringResource(R.string.messages_pending), value = "${state.pendingApprovals}", icon = Icons.Filled.MailOutline, modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.home_stat_upcoming), value = "${state.upcomingEventsCount}", icon = Icons.Filled.CalendarMonth, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.analytics_monthly_wishes))
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.monthlyCounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.analytics_no_wishes_this_year),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RelateOnSurfaceVariant,
                    )
                } else {
                    BarChart(data = state.monthlyCounts)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.analytics_contact_distribution))
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val family = state.relationshipCounts["FAMILY"] ?: 0
                val friends = state.relationshipCounts["FRIEND"] ?: 0
                val work = state.relationshipCounts["WORK"] ?: 0
                val closeFriends = state.relationshipCounts["CLOSE_FRIEND"] ?: 0
                val other = state.relationshipCounts.filterKeys { it !in listOf("FAMILY", "FRIEND", "WORK", "CLOSE_FRIEND") }.values.sum()

                DistributionRow(stringResource(R.string.contact_filter_family), family, RelatePrimary)
                DistributionRow(stringResource(R.string.contact_filter_friends), friends, Color(0xFF22D3EE))
                DistributionRow(stringResource(R.string.contact_filter_work), work, Color(0xFFFB7185))
                DistributionRow(stringResource(R.string.contact_filter_close_friends), closeFriends, Color(0xFFF59E0B))
                if (other > 0) {
                    DistributionRow(stringResource(R.string.analytics_others), other, RelateOnSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.analytics_relationship_health))
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val healthy = state.healthCounts["Healthy (70%+)"] ?: 0
                val attention = state.healthCounts["Needs Attention"] ?: 0
                val atRisk = state.healthCounts["At Risk"] ?: 0

                HealthTrendRow(stringResource(R.string.analytics_health_healthy), healthy, Color(0xFF22C55E))
                HealthTrendRow(stringResource(R.string.analytics_health_attention), attention, Color(0xFFF59E0B))
                HealthTrendRow(stringResource(R.string.analytics_health_at_risk), atRisk, Color(0xFFEF4444))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.analytics_growth_metrics))
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                DistributionRow(
                    stringResource(R.string.analytics_delivery_reliability),
                    state.deliveryReliabilityPercent,
                    Color(0xFF22C55E),
                    suffix = "%",
                )
                DistributionRow(
                    stringResource(R.string.analytics_response_rate),
                    state.responseRatePercent,
                    Color(0xFF22D3EE),
                    suffix = "%",
                )
                DistributionRow(
                    stringResource(R.string.analytics_personalization_coverage),
                    state.personalizationCoveragePercent,
                    RelatePrimary,
                    suffix = "%",
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.analytics_top_neglected))
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.topNeglectedContacts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.analytics_no_neglected_contacts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RelateOnSurfaceVariant,
                    )
                } else {
                    state.topNeglectedContacts.forEach { contact ->
                        Text(
                            text = contact,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Float>>) {
    val maxValue = data.maxOf { it.second }
    if (maxValue <= 0f) {
        Text(
            text = stringResource(R.string.analytics_no_wishes_this_year),
            style = MaterialTheme.typography.bodyMedium,
            color = RelateOnSurfaceVariant,
        )
        return
    }
    Column {
        data.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = RelateOnSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RelateSurfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value / maxValue)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RelatePrimary),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = value.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(24.dp),
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count$suffix",
            style = MaterialTheme.typography.bodyMedium,
            color = RelateOnSurfaceVariant,
        )
    }
}

@Composable
private fun HealthTrendRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = RelateOnSurfaceVariant,
        )
    }
}
