package com.example.ui.screens.messages

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MessageContactNameText(
    contactName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = contactName,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
internal fun MessagePreviewText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun MessageDateLabel(
    epochMs: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = messageCardDate(epochMs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun MessageScheduledSubtext(
    scheduledForMs: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.messages_scheduled_format, messageCardDate(scheduledForMs)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun messageCardDate(epochMs: Long): String =
    SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(epochMs))
