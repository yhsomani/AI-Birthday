package com.example.ui.screens.analytics

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
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.components.RelateGlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.theme.RelateDarkBackground
import com.example.ui.theme.RelateOnBackground
import com.example.ui.theme.RelateOnSurfaceVariant
import com.example.ui.theme.RelatePrimary
import com.example.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.AnalyticsViewModel

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Analytics",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

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
                StatCard(label = "Contacts", value = "${state.totalContacts}", icon = Icons.Filled.People, modifier = Modifier.weight(1f))
                StatCard(label = "Wishes Sent", value = "${state.totalWishesSent}", icon = Icons.Filled.Star, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(label = "Pending", value = "${state.pendingApprovals}", icon = Icons.Filled.MailOutline, modifier = Modifier.weight(1f))
                StatCard(label = "Upcoming", value = "${state.upcomingEventsCount}", icon = Icons.Filled.CalendarMonth, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "Monthly Wishes")
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.monthlyCounts.isEmpty()) {
                    Text(
                        text = "No wishes sent yet this year",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RelateOnSurfaceVariant,
                    )
                } else {
                    BarChart(data = state.monthlyCounts)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "Contact Distribution")
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val family = state.relationshipCounts["FAMILY"] ?: 0
                val friends = state.relationshipCounts["FRIEND"] ?: 0
                val work = state.relationshipCounts["WORK"] ?: 0
                val closeFriends = state.relationshipCounts["CLOSE_FRIEND"] ?: 0
                val other = state.relationshipCounts.filterKeys { it !in listOf("FAMILY", "FRIEND", "WORK", "CLOSE_FRIEND") }.values.sum()

                DistributionRow("Family", family, RelatePrimary)
                DistributionRow("Friends", friends, Color(0xFF22D3EE))
                DistributionRow("Work", work, Color(0xFFFB7185))
                DistributionRow("Close Friends", closeFriends, Color(0xFFF59E0B))
                if (other > 0) {
                    DistributionRow("Others", other, RelateOnSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(title = "Relationship Health")
        RelateGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                val healthy = state.healthCounts["Healthy (70%+)"] ?: 0
                val attention = state.healthCounts["Needs Attention"] ?: 0
                val atRisk = state.healthCounts["At Risk"] ?: 0

                HealthTrendRow("Healthy (70%+)", healthy, Color(0xFF22C55E))
                HealthTrendRow("Needs Attention", attention, Color(0xFFF59E0B))
                HealthTrendRow("At Risk", atRisk, Color(0xFFEF4444))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Float>>) {
    val maxValue = data.maxOf { it.second }
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
private fun DistributionRow(label: String, count: Int, color: Color) {
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
