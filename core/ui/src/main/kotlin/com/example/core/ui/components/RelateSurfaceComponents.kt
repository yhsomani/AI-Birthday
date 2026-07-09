package com.example.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.relateSemanticColors

@Composable
fun RelateGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val semanticColors = MaterialTheme.relateSemanticColors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(RelateSize.outlineStroke, semanticColors.cardOutline, RoundedCornerShape(RelateRadius.card)),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(containerColor = semanticColors.cardContainer),
        content = content,
    )
}

@Composable
fun RelateStatusBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(RelateSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(RelateSize.iconMd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = RelateAlpha.subtle),
                )
            }
            action?.invoke()
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(RelateSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RelateSize.iconLg),
            )
            Spacer(modifier = Modifier.height(RelateSpacing.xs))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
