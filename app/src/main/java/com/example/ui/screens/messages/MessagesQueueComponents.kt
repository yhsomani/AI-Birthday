package com.example.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing

@Composable
internal fun <T> MessageQueueList(
    queueItems: List<T>,
    emptyText: String,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    trailingContent: LazyListScope.() -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    if (queueItems.isEmpty()) {
        EmptyState(message = emptyText)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = RelateSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            items(queueItems, key = key) { item ->
                itemContent(item)
            }
            trailingContent()
        }
    }
}

@Composable
internal fun MessageSelectionCheckbox(
    selected: Boolean,
    onToggleSelection: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Checkbox(
        checked = selected,
        onCheckedChange = { onToggleSelection() },
        modifier = modifier.testTag(testTag),
        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
    )
}

@Composable
internal fun MessageContactAvatar(
    contactName: String,
    contactAvatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (contactAvatarUrl != null) {
        AsyncImage(
            model = contactAvatarUrl,
            contentDescription = stringResource(R.string.avatar),
            modifier = modifier
                .size(RelateSize.avatar)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(RelateSize.avatar)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contactName.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
