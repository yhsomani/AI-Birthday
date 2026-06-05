package com.example.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.HealthScoreRing
import com.example.ui.theme.CyberRose
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.Emerald
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Analytics Dashboard matching Stitch "RelateAI Analytics Dashboard" design.
 */
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit = {},
    onReconnectClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Text("Analytics", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Health Score
        GlassmorphicCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HealthScoreRing(score = 78, size = 110.dp, strokeWidth = 8.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Overall Health", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("↑ +5 from last month", style = MaterialTheme.typography.labelSmall, color = Emerald)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMiniCard("Messages\nSent", "47", "↑ 12%", Emerald, Modifier.weight(1f))
            StatMiniCard("Events\nCovered", "23/28", "82%", ElectricCyan, Modifier.weight(1f))
            StatMiniCard("Avg\nResponse", "4.2h", "↓ -15%", Emerald, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Relationship Breakdown
        Text("Relationship Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        BreakdownBar("Family", 92, ElectricCyan)
        BreakdownBar("Friends", 78, NeonViolet)
        BreakdownBar("Work", 65, NeonViolet.copy(alpha = 0.6f))
        BreakdownBar("Extended", 45, CyberRose)

        Spacer(modifier = Modifier.height(24.dp))

        // Revival Suggestions
        Text("Revival Suggestions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        RevivalCard("Meera Deshpande", "Aunt • Score: 45 → declining", CyberRose, onReconnect = { onReconnectClick("Meera Deshpande") })
        Spacer(modifier = Modifier.height(8.dp))
        RevivalCard("Sanjay Kumar", "College Friend • Score: 38 → declining", CyberRose, onReconnect = { onReconnectClick("Sanjay") })

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, trend: String, trendColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Text(trend, style = MaterialTheme.typography.labelSmall, color = trendColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun BreakdownBar(category: String, score: Int, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(category, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.width(80.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(GlassEdge)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("$score", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
    }
}

@Composable
private fun RevivalCard(name: String, subtitle: String, accentColor: Color, onReconnect: () -> Unit) {
    GlassmorphicCard(contentPadding = 14.dp, cornerRadius = 12.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = accentColor)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonViolet)
                    .clickable { onReconnect() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Reconnect", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
        }
    }
}
