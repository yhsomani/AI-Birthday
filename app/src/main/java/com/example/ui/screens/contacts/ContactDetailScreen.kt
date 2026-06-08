package com.example.ui.screens.contacts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.ui.components.HealthIndicatorDot
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.RelatePrimaryButton
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.ContactDetailViewModel

@Composable
fun ContactDetailScreen(
    contactId: String,
    onBack: () -> Unit = {},
    onNavigateToWish: (String) -> Unit = {},
    onNavigateToMemoryVault: (String) -> Unit = {},
    onNavigateToGiftAdvisor: (String) -> Unit = {},
    onNavigateToChatHistory: (String) -> Unit = {},
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.generationResult) {
        state.generationResult?.let { pendingId ->
            onNavigateToWish(pendingId)
            viewModel.clearGenerationResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RelateOnBackground,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else {
            val contact = state.contact

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                val displayName = contact?.name ?: contactId
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(RelateSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.take(1),
                        color = RelateOnBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                contact?.let {
                    HealthIndicatorDot(health = it.healthScore / 100f, size = 14)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val group = contact?.contactGroup ?: contact?.relationshipType ?: ""
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RelateOnSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onNavigateToMemoryVault(contactId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)
                    ) {
                        Text("Memory Vault", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(
                        onClick = { onNavigateToGiftAdvisor(contactId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RelateSurfaceVariant)
                    ) {
                        Text("Gift Advisor", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = "Contact Info")
                RelateGlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        contact?.primaryPhone?.let {
                            InfoRow(Icons.Filled.Phone, it)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        contact?.primaryEmail?.let {
                            InfoRow(Icons.Filled.Email, it)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        val birthday = if (contact?.birthdayDay != null && contact.birthdayMonth != null) {
                            "${contact.birthdayMonth}/${contact.birthdayDay}"
                        } else "Unknown"
                        InfoRow(Icons.Filled.CalendarMonth, "Birthday: $birthday")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = "Next Birthday")
                RelateGlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = RelatePrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val daysText = state.upcomingBirthdayDaysLeft?.let {
                                "in $it days"
                            } ?: "No upcoming event"
                            Text(
                                text = daysText,
                                style = MaterialTheme.typography.titleMedium,
                                color = RelatePrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.isGenerating) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = RelatePrimary)
                            }
                        } else {
                            RelatePrimaryButton(
                                text = "Generate AI Wish",
                                onClick = { viewModel.generateWish() },
                            )
                        }
                        state.generationError?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = RelateOnSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = RelateOnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
