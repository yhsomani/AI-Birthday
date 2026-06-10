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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.R
import com.example.core.ui.components.RelateGlassCard
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
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val completeOnboarding = {
        viewModel.completeOnboarding()
        onOnboardingComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = RelatePrimary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.onboarding_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = RelateOnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            onboardingSteps.forEachIndexed { index, step ->
                SetupChecklistRow(
                    index = index + 1,
                    step = step,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = completeOnboarding,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_continue_to_sign_in),
                color = RelateDarkBackground,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenAutomationSetup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_open_setup_checklist),
                color = RelateOnBackground,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
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
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(RelateSurfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = RelatePrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = RelatePrimary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(step.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = RelateOnSurfaceVariant,
                )
            }
        }
    }
}
