package com.example.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.core.ui.components.HealthIndicatorDot
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.contact.ContactListItem
import com.example.ui.viewmodel.ContactQualityState
import com.example.ui.viewmodel.ContactQualityStatus

@Composable
internal fun ContactRow(
    contact: ContactListItem,
    quality: ContactQualityState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = RelateSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(RelateSize.minTouchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.displayName.take(1),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(RelateSpacing.sm))
                HealthIndicatorDot(health = contact.healthScore / 100f)
            }
            val group = contact.contactGroup ?: contact.relationshipType
            Text(
                text = group,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quality?.let {
                Text(
                    text = it.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = it.labelColor(),
                    modifier = Modifier.testTag(ContactListTestTags.QUALITY_PREFIX + contact.id.value),
                )
            }
        }
    }
}

@Composable
private fun ContactQualityState.label(): String = when (status) {
    ContactQualityStatus.READY -> stringResource(R.string.contact_quality_ready)
    ContactQualityStatus.MISSING_EVENT -> stringResource(R.string.contact_quality_missing_event)
    ContactQualityStatus.MISSING_CHANNEL -> stringResource(R.string.contact_quality_missing_channel)
    ContactQualityStatus.MISSING_CONTEXT -> stringResource(R.string.contact_quality_missing_context)
}

@Composable
private fun ContactQualityState.labelColor() = when (status) {
    ContactQualityStatus.READY -> MaterialTheme.colorScheme.onSurfaceVariant
    ContactQualityStatus.MISSING_EVENT,
    ContactQualityStatus.MISSING_CHANNEL,
    ContactQualityStatus.MISSING_CONTEXT -> MaterialTheme.colorScheme.error
}
