package com.example.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing

@Composable
internal fun AnalyticsMetricDenominator(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = RelateSpacing.xxs),
    )
}

@Composable
internal fun BarChart(data: List<Pair<String, Float>>) {
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
            BarChartRow(label = label, value = value, maxValue = maxValue)
        }
    }
}

@Composable
internal fun DistributionRow(label: String, count: Int, color: Color, suffix: String = "") {
    AnalyticsValueRow(
        label = label,
        value = "$count$suffix",
        color = color,
    )
}

@Composable
internal fun HealthTrendRow(label: String, count: Int, color: Color) {
    AnalyticsValueRow(
        label = label,
        value = count.toString(),
        color = color,
    )
}

@Composable
private fun BarChartRow(
    label: String,
    value: Float,
    maxValue: Float,
) {
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

@Composable
private fun AnalyticsValueRow(label: String, value: String, color: Color) {
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
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
