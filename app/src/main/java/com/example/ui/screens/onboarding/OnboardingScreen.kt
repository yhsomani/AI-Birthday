package com.example.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.R
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.theme.RelateRadius
import com.example.core.ui.theme.RelateSize
import com.example.core.ui.theme.RelateSpacing
import com.example.ui.viewmodel.OnboardingViewModel

internal object OnboardingTestTags {
    const val MOMENTUM_CARD = "onboarding_momentum_card"
    const val CONTINUE_BUTTON = "onboarding_continue_button"
    const val SETUP_CHECKLIST_BUTTON = "onboarding_setup_checklist_button"
    const val LOCAL_MODE_BUTTON = "onboarding_local_mode_button"
}

private const val ONBOARDING_START_PROGRESS = 0.2f

data class OnboardingStep(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Filled.CheckCircle,
        titleRes = R.string.onboarding_setup_google_title,
        descriptionRes = R.string.onboarding_setup_google_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.Groups,
        titleRes = R.string.onboarding_setup_contacts_title,
        descriptionRes = R.string.onboarding_setup_contacts_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.SmartToy,
        titleRes = R.string.onboarding_setup_ai_title,
        descriptionRes = R.string.onboarding_setup_ai_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.Notifications,
        titleRes = R.string.onboarding_setup_permissions_title,
        descriptionRes = R.string.onboarding_setup_permissions_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.Favorite,
        titleRes = R.string.onboarding_setup_style_title,
        descriptionRes = R.string.onboarding_setup_style_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.CalendarMonth,
        titleRes = R.string.onboarding_setup_automation_title,
        descriptionRes = R.string.onboarding_setup_automation_description,
    ),
)

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    onOpenAutomationSetup: () -> Unit = {},
    onStartLocalModeComplete: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val completeOnboarding = {
        viewModel.completeOnboarding()
        onOnboardingComplete()
    }
    val startLocalOnlyMode = {
        viewModel.startLocalOnlyMode()
        onStartLocalModeComplete()
    }

    OnboardingContent(
        onContinue = completeOnboarding,
        onOpenAutomationSetup = onOpenAutomationSetup,
        onStartLocalMode = startLocalOnlyMode,
    )
}

@Composable
internal fun OnboardingContent(
    onContinue: () -> Unit,
    onOpenAutomationSetup: () -> Unit,
    onStartLocalMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(RelateSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(RelateSize.minTouchTarget))

        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(RelateSize.heroIcon),
        )
        Spacer(modifier = Modifier.height(RelateSpacing.lg))
        Text(
            text = stringResource(R.string.onboarding_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Text(
            text = stringResource(R.string.onboarding_setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.xl))

        OnboardingMomentumCard()

        Spacer(modifier = Modifier.height(RelateSpacing.xl))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            onboardingSteps.forEachIndexed { index, step ->
                SetupChecklistRow(
                    index = index + 1,
                    step = step,
                )
            }
        }

        Spacer(modifier = Modifier.height(RelateSpacing.xl))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OnboardingTestTags.CONTINUE_BUTTON),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            shape = RoundedCornerShape(RelateRadius.control),
        ) {
            Text(
                text = stringResource(R.string.onboarding_continue_to_sign_in),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        OutlinedButton(
            onClick = onOpenAutomationSetup,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OnboardingTestTags.SETUP_CHECKLIST_BUTTON),
            shape = RoundedCornerShape(RelateRadius.control),
        ) {
            Text(
                text = stringResource(R.string.onboarding_open_setup_checklist),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        OutlinedButton(
            onClick = onStartLocalMode,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(OnboardingTestTags.LOCAL_MODE_BUTTON),
            shape = RoundedCornerShape(RelateRadius.control),
        ) {
            Text(
                text = stringResource(R.string.onboarding_start_local_only),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(RelateSpacing.sm))
        Text(
            text = stringResource(R.string.onboarding_local_only_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(RelateSpacing.lg))
    }
}

@Composable
private fun OnboardingMomentumCard() {
    RelateGlassCard(modifier = Modifier.testTag(OnboardingTestTags.MOMENTUM_CARD)) {
        Column(
            modifier = Modifier.padding(RelateSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.onboarding_progress_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { ONBOARDING_START_PROGRESS },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RelateSize.progressTrack),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = stringResource(R.string.onboarding_progress_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OnboardingValueLine(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.onboarding_value_defaults_title),
                body = stringResource(R.string.onboarding_value_defaults_body),
            )
            OnboardingValueLine(
                icon = Icons.Filled.SmartToy,
                title = stringResource(R.string.onboarding_value_preview_title),
                body = stringResource(R.string.onboarding_value_preview_body),
            )
            OnboardingValueLine(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.onboarding_value_voice_title),
                body = stringResource(R.string.onboarding_value_voice_body),
            )
            Text(
                text = stringResource(R.string.onboarding_loss_contrast_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingValueLine(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RelateSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(RelateSize.iconMd),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupChecklistRow(
    index: Int,
    step: OnboardingStep,
) {
    RelateGlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RelateSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(RelateSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(RelateSize.setupStepIndex)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(RelateRadius.control),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RelateSize.iconLg),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(RelateSpacing.xs))
                Text(
                    text = stringResource(step.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
