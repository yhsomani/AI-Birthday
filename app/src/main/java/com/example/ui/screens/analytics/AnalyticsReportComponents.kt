package com.example.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors
import com.example.ui.viewmodel.AnalyticsUiState

@Composable
internal fun AnalyticsStatsGrid(state: AnalyticsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
    ) {
        StatCard(
            label = stringResource(R.string.dashboard_contacts),
            value = "${state.totalContacts}",
            icon = Icons.Filled.People,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.home_stat_wishes_sent),
            value = "${state.totalWishesSent}",
            icon = Icons.Filled.Star,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(RelateSpacing.sm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
    ) {
        StatCard(
            label = stringResource(R.string.messages_pending),
            value = "${state.pendingApprovals}",
            icon = Icons.Filled.MailOutline,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.home_stat_upcoming),
            value = "${state.upcomingEventsCount}",
            icon = Icons.Filled.CalendarMonth,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun AnalyticsMonthlyWishesSection(monthlyCounts: List<Pair<String, Float>>) {
    SectionHeader(title = stringResource(R.string.analytics_monthly_wishes))
    RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.MONTHLY_SECTION)) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            if (monthlyCounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_no_wishes_this_year),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BarChart(data = monthlyCounts)
            }
        }
    }
}

@Composable
internal fun AnalyticsContactDistributionSection(relationshipCounts: Map<String, Int>) {
    SectionHeader(title = stringResource(R.string.analytics_contact_distribution))
    RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.DISTRIBUTION_SECTION)) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            val family = relationshipCounts["FAMILY"] ?: 0
            val friends = relationshipCounts["FRIEND"] ?: 0
            val work = relationshipCounts["WORK"] ?: 0
            val closeFriends = relationshipCounts["CLOSE_FRIEND"] ?: 0
            val other = relationshipCounts.filterKeys {
                it !in listOf("FAMILY", "FRIEND", "WORK", "CLOSE_FRIEND")
            }.values.sum()

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
}

@Composable
internal fun AnalyticsRelationshipHealthSection(healthCounts: Map<String, Int>) {
    SectionHeader(title = stringResource(R.string.analytics_relationship_health))
    RelateGlassCard(modifier = Modifier.testTag(AnalyticsScreenTestTags.HEALTH_SECTION)) {
        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
            val healthy = healthCounts["Healthy (70%+)"] ?: 0
            val attention = healthCounts["Needs Attention"] ?: 0
            val atRisk = healthCounts["At Risk"] ?: 0

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
}

@Composable
internal fun AnalyticsGrowthMetricsSection(state: AnalyticsUiState) {
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
            Spacer(modifier = Modifier.height(RelateSpacing.sm))
            AnalyticsMetricDenominator(
                text = stringResource(
                    R.string.analytics_growth_denominator_sent,
                    state.sentMessagesThisYearCount,
                ),
            )
            AnalyticsMetricDenominator(
                text = stringResource(
                    R.string.analytics_growth_denominator_personalization,
                    state.analyticsProfileCount,
                ),
            )
        }
    }
}

@Composable
internal fun AnalyticsNeglectedContactsSection(
    state: AnalyticsUiState,
    onNavigateToContact: (String) -> Unit,
) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = RelateSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = contact.displayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onNavigateToContact(contact.contactId) },
                        ) {
                            Text(stringResource(R.string.analytics_open_contact))
                        }
                    }
                }
            }
        }
    }
}
