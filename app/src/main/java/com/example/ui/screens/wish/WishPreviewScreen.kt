package com.example.ui.screens.wish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.components.RelateGlassCard
import com.example.ui.components.RelatePrimaryButton
import com.example.ui.theme.RelateDarkBackground
import com.example.ui.theme.RelateOnBackground
import com.example.ui.theme.RelateOnSurfaceVariant
import com.example.ui.theme.RelatePrimary
import com.example.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.WishPreviewViewModel

private val variantOptions = listOf(
    "short" to "Short",
    "standard" to "Standard",
    "long" to "Long",
    "formal" to "Formal",
    "funny" to "Funny",
    "emotional" to "Emotional",
)

@Composable
fun WishPreviewScreen(
    contactId: String,
    eventId: String,
    onBack: () -> Unit = {},
    onSent: () -> Unit = {},
    viewModel: WishPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.testSent) {
        if (state.testSent) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Test message sent to your device!")
                viewModel.dismissTestSent()
            }
        }
    }


    LaunchedEffect(eventId) {
        viewModel.loadPending(eventId)
    }

    LaunchedEffect(state.approved) {
        if (state.approved) {
            onSent()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RelateOnBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Preview AI Wish",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else if (state.error != null && state.pendingMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error ?: "Unknown error",
                    color = RelateOnSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Choose a tone",
                    style = MaterialTheme.typography.titleSmall,
                    color = RelatePrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    variantOptions.take(3).forEach { (key, label) ->
                        ToneChip(
                            label = label,
                            isSelected = state.selectedVariant == key,
                            onClick = { viewModel.selectVariant(key) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    variantOptions.drop(3).forEach { (key, label) ->
                        ToneChip(
                            label = label,
                            isSelected = state.selectedVariant == key,
                            onClick = { viewModel.selectVariant(key) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Message",
                    style = MaterialTheme.typography.titleSmall,
                    color = RelatePrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                RelateGlassCard {
                    OutlinedTextField(
                        value = state.editedText,
                        onValueChange = { viewModel.updateEditedText(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RelatePrimary,
                            unfocusedBorderColor = RelateSurfaceVariant,
                            focusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.2f),
                            unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.2f),
                            focusedTextColor = RelateOnBackground,
                            unfocusedTextColor = RelateOnBackground,
                        ),
                        minLines = 4,
                        maxLines = 8,
                    )
                }

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (state.isApproving) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = RelatePrimary)
                    }
                } else if (!state.approved && !state.rejected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { viewModel.reject() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RelateSurfaceVariant,
                            ),
                            enabled = !state.isRejecting,
                        ) {
                            if (state.isRejecting) {
                                CircularProgressIndicator(
                                    color = RelateOnBackground,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "Reject",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RelateOnBackground,
                                )
                            }
                        }
                        RelatePrimaryButton(
                            text = "Approve & Schedule",
                            onClick = { viewModel.approve() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else if (state.approved) {
                    Text(
                        text = "Wish approved and scheduled!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RelatePrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToneChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) RelatePrimary else RelateSurfaceVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
