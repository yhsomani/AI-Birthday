package com.example.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelateScreen
import com.example.core.ui.theme.RelateSpacing
import com.example.domain.model.MessageChannel
import java.text.DateFormat
import java.util.Date

internal object ChatHistoryTestTags {
    const val LOADING = "chat_history_loading"
    const val EMPTY = "chat_history_empty"
    const val EMPTY_SEARCH = "chat_history_empty_search"
    const val ERROR = "chat_history_error"
    const val SEARCH_FIELD = "chat_history_search_field"
    const val SEARCH_CLEAR = "chat_history_search_clear"
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
        onSearchQueryChange = viewModel::updateSearchQuery,
    )
}

@Composable
internal fun ChatHistoryContent(
    uiState: ChatHistoryUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
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
                    color = MaterialTheme.colorScheme.primary,
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
        } else if (uiState.totalMessageCount == 0) {
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
                    .padding(bottom = RelateSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm)
            ) {
                item {
                    ChatHistorySearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                    )
                }

                if (uiState.messages.isEmpty()) {
                    item {
                        EmptyState(
                            message = stringResource(R.string.chat_history_search_empty),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = RelateSpacing.xl)
                                .testTag(ChatHistoryTestTags.EMPTY_SEARCH),
                        )
                    }
                }

                items(uiState.messages, key = { it.id.value }) { message ->
                    RelateGlassCard(
                        modifier = Modifier.testTag(ChatHistoryTestTags.MESSAGE_PREFIX + message.id.value),
                    ) {
                        Column(modifier = Modifier.padding(RelateSpacing.cardContent)) {
                            Text(
                                text = message.messageText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(RelateSpacing.xs))
                            val date = DateFormat
                                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(message.sentAtMs))
                            Text(
                                text = stringResource(
                                    R.string.chat_history_sent_via_format,
                                    channelLabel(message.channel),
                                    date,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.chat_history_search_label)) },
        placeholder = { Text(stringResource(R.string.chat_history_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag(ChatHistoryTestTags.SEARCH_CLEAR),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_history_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ChatHistoryTestTags.SEARCH_FIELD),
    )
}

@Composable
private fun channelLabel(channel: MessageChannel): String = when (channel) {
    MessageChannel.SMS -> stringResource(R.string.channel_sms)
    MessageChannel.WHATSAPP -> stringResource(R.string.channel_whatsapp)
    MessageChannel.EMAIL -> stringResource(R.string.channel_email)
    MessageChannel.UNKNOWN -> stringResource(R.string.channel_unknown)
}
