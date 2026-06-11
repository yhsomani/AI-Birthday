package com.example.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import java.text.DateFormat
import java.util.Date

internal object ChatHistoryTestTags {
    const val LOADING = "chat_history_loading"
    const val EMPTY = "chat_history_empty"
    const val ERROR = "chat_history_error"
    const val MESSAGE_PREFIX = "chat_history_message_"
}

@Composable
fun ChatHistoryScreen(
    onBack: () -> Unit,
    viewModel: ChatHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatHistoryContent(
        uiState = uiState,
        onBack = onBack,
    )
}

@Composable
internal fun ChatHistoryContent(
    uiState: ChatHistoryUiState,
    onBack: () -> Unit,
) {
    RelateScreen(
        title = stringResource(R.string.chat_history_title),
        subtitle = stringResource(R.string.chat_history_subtitle),
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
    ) {
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = RelatePrimary,
                    modifier = Modifier.testTag(ChatHistoryTestTags.LOADING),
                )
            }
        } else if (uiState.errorMessageRes != null) {
            EmptyState(
                message = stringResource(uiState.errorMessageRes ?: R.string.chat_history_error_load),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ChatHistoryTestTags.ERROR),
            )
        } else if (uiState.messages.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.chat_history_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ChatHistoryTestTags.EMPTY),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    RelateGlassCard(
                        modifier = Modifier.testTag(ChatHistoryTestTags.MESSAGE_PREFIX + message.id),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = message.messageText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val date = DateFormat
                                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(message.sentAtMs))
                            Text(
                                text = stringResource(R.string.chat_history_sent_via_format, message.channel, date),
                                style = MaterialTheme.typography.labelSmall,
                                color = RelateOnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
