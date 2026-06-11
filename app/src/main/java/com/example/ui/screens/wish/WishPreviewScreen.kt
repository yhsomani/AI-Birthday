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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.feedback.asString
import com.example.ui.viewmodel.WishPreviewUiState
import com.example.ui.viewmodel.WishPreviewViewModel
import com.example.ui.viewmodel.WhySignal

private val variantOptions = listOf(
    "short" to R.string.wish_variant_short,
    "standard" to R.string.wish_variant_standard,
    "long" to R.string.wish_variant_long,
    "formal" to R.string.wish_variant_formal,
    "funny" to R.string.wish_variant_funny,
    "emotional" to R.string.wish_variant_emotional,
)

internal object WishPreviewTestTags {
    const val BACK_BUTTON = "wish_preview_back"
    const val MESSAGE_FIELD = "wish_preview_message_field"
    const val WHY_PANEL = "wish_preview_why_panel"
    const val REGENERATE_BUTTON = "wish_preview_regenerate"
    const val TEST_SEND_BUTTON = "wish_preview_test_send"
    const val REJECT_BUTTON = "wish_preview_reject"
    const val APPROVE_BUTTON = "wish_preview_approve"
    const val APPROVED_MESSAGE = "wish_preview_approved_message"
    const val ERROR_MESSAGE = "wish_preview_error_message"
    const val VARIANT_PREFIX = "wish_preview_variant_"
    const val FEEDBACK_PREFIX = "wish_preview_feedback_"
}

@Composable
fun WishPreviewScreen(
    contactId: String,
    messageRef: String,
    onBack: () -> Unit = {},
    onSent: () -> Unit = {},
    viewModel: WishPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val testSentMessage = stringResource(R.string.wish_preview_test_sent)
    val feedbackText = state.feedbackEvent?.message?.asString()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.testSent) {
        if (state.testSent) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(testSentMessage)
                viewModel.dismissTestSent()
            }
        }
    }

    LaunchedEffect(state.feedbackEvent?.id, feedbackText) {
        if (feedbackText != null) {
            snackbarHostState.showSnackbar(feedbackText)
            viewModel.clearFeedbackEvent()
        }
    }


    LaunchedEffect(messageRef) {
        viewModel.loadPending(messageRef)
    }

    LaunchedEffect(state.approved) {
        if (state.approved) {
            onSent()
        }
    }

    WishPreviewScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onVariantSelected = viewModel::selectVariant,
        onEditedTextChange = viewModel::updateEditedText,
        onFeedbackSelected = viewModel::submitFeedback,
        onRegenerate = viewModel::regenerate,
        onSendTest = viewModel::sendTestToMyself,
        onReject = viewModel::reject,
        onApprove = viewModel::approve,
    )
}

@Composable
internal fun WishPreviewScreenContent(
    state: WishPreviewUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit = {},
    onVariantSelected: (String) -> Unit = {},
    onEditedTextChange: (String) -> Unit = {},
    onFeedbackSelected: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onSendTest: () -> Unit = {},
    onReject: () -> Unit = {},
    onApprove: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(WishPreviewTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RelateOnBackground,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wish_preview_title),
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
            } else if (state.errorMessageRes != null && state.pendingMessage == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(state.errorMessageRes ?: R.string.wish_preview_error_unknown),
                        color = RelateOnSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(WishPreviewTestTags.ERROR_MESSAGE),
                    )
                }
            } else {
                WishPreviewContent(
                    state = state,
                    onVariantSelected = onVariantSelected,
                    onEditedTextChange = onEditedTextChange,
                    onFeedbackSelected = onFeedbackSelected,
                    onRegenerate = onRegenerate,
                    onSendTest = onSendTest,
                    onReject = onReject,
                    onApprove = onApprove,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
internal fun WishPreviewContent(
    state: WishPreviewUiState,
    onVariantSelected: (String) -> Unit = {},
    onEditedTextChange: (String) -> Unit = {},
    onFeedbackSelected: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onSendTest: () -> Unit = {},
    onReject: () -> Unit = {},
    onApprove: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.wish_preview_choose_tone),
            style = MaterialTheme.typography.titleSmall,
            color = RelatePrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            variantOptions.take(3).forEach { (key, labelRes) ->
                ToneChip(
                    label = stringResource(labelRes),
                    isSelected = state.selectedVariant == key,
                    onClick = { onVariantSelected(key) },
                    modifier = Modifier.testTag(WishPreviewTestTags.VARIANT_PREFIX + key),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            variantOptions.drop(3).forEach { (key, labelRes) ->
                ToneChip(
                    label = stringResource(labelRes),
                    isSelected = state.selectedVariant == key,
                    onClick = { onVariantSelected(key) },
                    modifier = Modifier.testTag(WishPreviewTestTags.VARIANT_PREFIX + key),
                )
            }
        }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.wish_preview_message_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = RelatePrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                RelateGlassCard {
                    OutlinedTextField(
                        value = state.editedText,
                        onValueChange = onEditedTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .testTag(WishPreviewTestTags.MESSAGE_FIELD),
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

                state.errorMessageRes?.let { errorRes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }

                state.qualityMessageRes?.let { messageRes ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = messageResource(messageRes, state.qualityMessageArgRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }

                if (state.whySignals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    WhyThisMessagePanel(
                        signals = state.whySignals,
                        modifier = Modifier.testTag(WishPreviewTestTags.WHY_PANEL),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.wish_preview_feedback_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = RelatePrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                state.feedbackOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowOptions.forEach { option ->
                            FeedbackChip(
                                label = stringResource(option.labelRes),
                                isSelected = state.selectedFeedbackKey == option.key,
                                onClick = { onFeedbackSelected(option.key) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(WishPreviewTestTags.FEEDBACK_PREFIX + option.key),
                            )
                        }
                        if (rowOptions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                state.feedbackMessageRes?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRegenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WishPreviewTestTags.REGENERATE_BUTTON),
                    enabled = !state.isRegenerating && !state.isApproving && !state.isRejecting && !state.isTestingSend,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelateSurfaceVariant,
                    ),
                ) {
                    if (state.isRegenerating) {
                        CircularProgressIndicator(
                            color = RelateOnBackground,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = RelateOnBackground,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wish_preview_regenerate),
                            color = RelateOnBackground,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSendTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WishPreviewTestTags.TEST_SEND_BUTTON),
                    enabled = !state.isTestingSend && !state.isRegenerating && !state.isApproving && !state.isRejecting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RelateSurfaceVariant,
                    ),
                ) {
                    if (state.isTestingSend) {
                        CircularProgressIndicator(
                            color = RelateOnBackground,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.wish_preview_send_test),
                            color = RelateOnBackground,
                        )
                    }
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
                            onClick = onReject,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag(WishPreviewTestTags.REJECT_BUTTON),
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
                                    text = stringResource(R.string.wish_preview_reject),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RelateOnBackground,
                                )
                            }
                        }
                        RelatePrimaryButton(
                            text = stringResource(R.string.wish_preview_approve_schedule),
                            onClick = onApprove,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(WishPreviewTestTags.APPROVE_BUTTON),
                        )
                    }
                } else if (state.approved) {
                    Text(
                        text = stringResource(R.string.wish_preview_approved),
                        style = MaterialTheme.typography.bodyLarge,
                        color = RelatePrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag(WishPreviewTestTags.APPROVED_MESSAGE),
                    )
                }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun messageResource(messageRes: Int, argRes: Int?): String {
    return if (argRes != null) {
        stringResource(messageRes, stringResource(argRes))
    } else {
        stringResource(messageRes)
    }
}

@Composable
private fun FeedbackChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) RelatePrimary else RelateSurfaceVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WhyThisMessagePanel(
    signals: List<WhySignal>,
    modifier: Modifier = Modifier,
) {
    RelateGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.wish_why_title),
                style = MaterialTheme.typography.titleSmall,
                color = RelatePrimary,
                fontWeight = FontWeight.SemiBold,
            )
            signals.forEach { signal ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(signal.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateOnSurfaceVariant,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        text = signal.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToneChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
