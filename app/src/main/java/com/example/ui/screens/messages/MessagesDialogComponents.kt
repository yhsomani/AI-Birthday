package com.example.ui.screens.messages

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
internal fun MessagesRejectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(MessagesTestTags.REJECT_DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.messages_reject_title),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.messages_reject_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(MessagesTestTags.REJECT_CONFIRM),
                onClick = onConfirm,
            ) {
                Text(
                    text = stringResource(R.string.reject),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(MessagesTestTags.REJECT_CANCEL),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
