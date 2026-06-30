package com.example.ui.screens.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.core.ui.theme.RelateSpacing

@Composable
internal fun MessageSelectableCardHeader(
    selected: Boolean,
    onToggleSelection: () -> Unit,
    selectionTestTag: String,
    contactName: String,
    contactAvatarUrl: String?,
    modifier: Modifier = Modifier,
    secondaryContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessageSelectionCheckbox(
            selected = selected,
            onToggleSelection = onToggleSelection,
            testTag = selectionTestTag,
        )
        MessageContactAvatar(
            contactName = contactName,
            contactAvatarUrl = contactAvatarUrl,
        )
        Spacer(modifier = Modifier.width(RelateSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            MessageContactNameText(contactName = contactName)
            Spacer(modifier = Modifier.height(RelateSpacing.xxs))
            secondaryContent()
        }
        trailingContent()
    }
}
