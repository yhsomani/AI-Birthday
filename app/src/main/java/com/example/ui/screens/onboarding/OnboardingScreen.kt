package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.R
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.theme.RelateCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.OnboardingViewModel

data class OnboardingStep(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Filled.Favorite,
        titleRes = R.string.onboarding_step_welcome_title,
        descriptionRes = R.string.onboarding_step_welcome_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.Groups,
        titleRes = R.string.onboarding_step_contacts_title,
        descriptionRes = R.string.onboarding_step_contacts_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.SmartToy,
        titleRes = R.string.onboarding_step_ai_title,
        descriptionRes = R.string.onboarding_step_ai_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.Notifications,
        titleRes = R.string.onboarding_step_reminders_title,
        descriptionRes = R.string.onboarding_step_reminders_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.CalendarMonth,
        titleRes = R.string.onboarding_step_events_title,
        descriptionRes = R.string.onboarding_step_events_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.Favorite,
        titleRes = R.string.onboarding_step_health_title,
        descriptionRes = R.string.onboarding_step_health_description,
    ),
    OnboardingStep(
        icon = Icons.Filled.SmartToy,
        titleRes = R.string.onboarding_step_done_title,
        descriptionRes = R.string.onboarding_step_done_description,
    ),
)

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    onOpenAutomationSetup: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = onboardingSteps[currentStep]
    val completeOnboarding = {
        viewModel.completeOnboarding()
        onOnboardingComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(onboardingSteps.size) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index <= currentStep) RelatePrimary
                            else RelateSurfaceVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        AnimatedContent(targetState = currentStep) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = RelatePrimary,
                    modifier = Modifier.size(80.dp),
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(step.descriptionRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = RelateOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (currentStep < onboardingSteps.lastIndex) {
            RelatePrimaryButton(
                text = stringResource(R.string.continue_action),
                onClick = { currentStep++ },
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = completeOnboarding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = RelateOnSurfaceVariant,
                )
            }
        } else {
            RelatePrimaryButton(
                text = stringResource(R.string.onboarding_get_started),
                onClick = completeOnboarding,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onOpenAutomationSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_setup_whatsapp_automation),
                    color = RelateOnSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
