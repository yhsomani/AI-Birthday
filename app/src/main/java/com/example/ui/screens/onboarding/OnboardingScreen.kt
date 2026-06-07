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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.RelatePrimaryButton
import com.example.ui.theme.RelateCard
import com.example.ui.theme.RelateDarkBackground
import com.example.ui.theme.RelateOnBackground
import com.example.ui.theme.RelateOnSurfaceVariant
import com.example.ui.theme.RelatePrimary
import com.example.ui.theme.RelateSurfaceVariant

data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Filled.Favorite,
        title = "Welcome to RelateAI",
        description = "Never miss a special moment. Let AI help you nurture your relationships with personalized wishes.",
    ),
    OnboardingStep(
        icon = Icons.Filled.Groups,
        title = "Sync Your Contacts",
        description = "Import your contacts to discover important dates and stay connected with the people who matter.",
    ),
    OnboardingStep(
        icon = Icons.Filled.SmartToy,
        title = "AI Birthday Wishes",
        description = "Generate unique, heartfelt birthday wishes with AI. Each message is personalized for every relationship.",
    ),
    OnboardingStep(
        icon = Icons.Filled.Notifications,
        title = "Smart Reminders",
        description = "Get notified before important events so you always have time to prepare the perfect message.",
    ),
    OnboardingStep(
        icon = Icons.Filled.CalendarMonth,
        title = "Track Events",
        description = "Keep track of birthdays, anniversaries, and custom events all in one place.",
    ),
    OnboardingStep(
        icon = Icons.Filled.Favorite,
        title = "Relationship Health",
        description = "Monitor your relationship health with insights and suggestions to stay connected.",
    ),
    OnboardingStep(
        icon = Icons.Filled.SmartToy,
        title = "You're All Set!",
        description = "Start nurturing your connections with AI-powered wishes and smart reminders.",
    ),
)

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = onboardingSteps[currentStep]

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
                    text = step.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RelateOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (currentStep < onboardingSteps.lastIndex) {
            RelatePrimaryButton(
                text = "Continue",
                onClick = { currentStep++ },
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onOnboardingComplete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Skip",
                    color = RelateOnSurfaceVariant,
                )
            }
        } else {
            RelatePrimaryButton(
                text = "Get Started",
                onClick = onOnboardingComplete,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
