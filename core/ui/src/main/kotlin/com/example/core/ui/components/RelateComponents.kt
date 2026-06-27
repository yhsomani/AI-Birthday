package com.example.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import com.example.core.ui.theme.RelateAlpha
import com.example.core.ui.theme.RelateCard
import com.example.core.ui.theme.RelateCardBorder
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateError
import com.example.core.ui.theme.RelateFraction
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.core.ui.theme.RelateSuccess
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.core.ui.theme.RelateWarning

@Composable
fun RelateScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = RelateSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(RelateSpacing.screenTop))
        RelateTopBar(
            title = title,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            navigationContentDescription = navigationContentDescription,
            onNavigationClick = onNavigationClick,
            action = action,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        content()
    }
}

@Composable
fun RelateTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null && onNavigationClick != null) {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationContentDescription,
                    tint = RelateOnBackground,
                )
            }
            Spacer(modifier = Modifier.width(RelateSpacing.xs))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun RelateGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(RelateSize.outlineStroke, RelateCardBorder, RoundedCornerShape(RelateRadius.card)),
        shape = RoundedCornerShape(RelateRadius.card),
        colors = CardDefaults.cardColors(containerColor = RelateCard),
        content = content,
    )
}

@Composable
fun RelatePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(RelateSize.primaryButtonHeight),
        shape = RoundedCornerShape(RelateRadius.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = RelatePrimary,
            disabledContainerColor = RelatePrimary.copy(alpha = RelateAlpha.disabled),
        ),
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun RelateStatusBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = RelateSurfaceVariant,
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
fun RelateAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = RelateSize.avatar,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RelateSurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "?" },
            color = RelateOnBackground,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun relateTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RelatePrimary,
    unfocusedBorderColor = RelateSurfaceVariant,
    focusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
    unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = RelateAlpha.fieldContainer),
    focusedTextColor = RelateOnBackground,
    unfocusedTextColor = RelateOnBackground,
    focusedPlaceholderColor = RelateOnSurfaceVariant,
    unfocusedPlaceholderColor = RelateOnSurfaceVariant,
    focusedLabelColor = RelatePrimary,
    unfocusedLabelColor = RelateOnSurfaceVariant,
)

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
                tint = RelatePrimary,
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

@Composable
fun HealthIndicatorDot(
    health: Float,
    modifier: Modifier = Modifier,
    size: Dp = RelateSize.indicatorDot,
) {
    val color = if (health > RelateFraction.healthStrongThreshold) RelateSuccess
    else if (health > RelateFraction.healthAttentionThreshold) RelateWarning
    else RelateError

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun HealthBar(
    health: Float,
    modifier: Modifier = Modifier,
) {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            RelateSuccess,
            RelateWarning,
            RelateError,
        )
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RelateSize.progressTrack)
            .clip(RoundedCornerShape(RelateRadius.xs))
            .background(RelateSurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(health.coerceIn(0f, 1f))
                .height(RelateSize.progressTrack)
                .clip(RoundedCornerShape(RelateRadius.xs))
                .background(brush)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = RelateSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = RelateOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(RelateRadius.pill),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) RelatePrimary else RelateSurfaceVariant,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = RelateSpacing.lg, vertical = RelateSpacing.sm),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
