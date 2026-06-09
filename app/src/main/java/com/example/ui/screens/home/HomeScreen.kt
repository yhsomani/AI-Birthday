package com.example.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.ui.components.SyncErrorCard
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigateToContact: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.userPhotoUrl != null) {
                        AsyncImage(
                            model = state.userPhotoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = "Hello, ${state.userName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = RelateOnSurfaceVariant,
                    )
                }
            }
        }

        state.syncError?.let { errorMsg ->
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SyncErrorCard(
                    message = errorMsg,
                    onRetry = { viewModel.loadMetrics() },
                    onDismiss = { viewModel.dismissSyncError() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = RelatePrimary)
                }
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = "Wishes Sent",
                        value = "${state.sentCount}",
                        icon = Icons.Filled.Star,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Upcoming",
                        value = "${state.upcomingEventsCount}",
                        icon = Icons.Filled.CalendarMonth,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = "Contacts",
                        value = "${state.contactCount}",
                        icon = Icons.Filled.People,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Pending",
                        value = "${state.pendingCount}",
                        icon = Icons.Filled.MailOutline,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Score",
                        value = "${state.healthScore}",
                        icon = Icons.Filled.Favorite,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Upcoming Birthdays")
        }

        item {
            RelateGlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.upcomingBirthdays.isEmpty()) {
                        Text(
                            text = "No upcoming birthdays",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RelateOnSurfaceVariant,
                        )
                    } else {
                        state.upcomingBirthdays.forEachIndexed { index, birthday ->
                            BirthdayRow(name = birthday.name, date = birthday.date)
                            if (index < state.upcomingBirthdays.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BirthdayRow(
    name: String,
    date: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RelatePrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.take(3),
                style = MaterialTheme.typography.labelMedium,
                color = RelatePrimary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Birthday on $date",
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}
