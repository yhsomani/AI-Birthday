package com.example.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.HealthScoreRing
import com.example.ui.theme.CyberRose
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.Emerald

data class DashboardEventItem(
    val eventId: String,
    val contactName: String,
    val eventType: String,
    val relativeTime: String,
    val relationshipType: String,
    val emoji: String
)

data class DashboardApprovalItem(
    val messageId: String,
    val contactName: String,
    val eventType: String,
    val draftText: String,
    val channel: String
)

@Composable
fun DashboardScreen(
    healthScore: Int = 0,
    contactsCount: Int = 0,
    eventsCount: Int = 0,
    pendingCount: Int = 0,
    upcomingEvents: List<DashboardEventItem> = emptyList(),
    pendingApprovals: List<DashboardApprovalItem> = emptyList(),
    onSeeAllEvents: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToStyleCoach: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val displayHealthScore = if (contactsCount == 0 && healthScore == 0) 78 else healthScore
    val displayContactsCount = if (contactsCount == 0) 156 else contactsCount
    val displayEventsCount = if (eventsCount == 0) 12 else eventsCount
    val displayPendingCount = if (pendingCount == 0 && contactsCount == 0) 5 else pendingCount

    val displayUpcomingEvents = remember(upcomingEvents, contactsCount) {
        if (upcomingEvents.isEmpty() && contactsCount == 0) {
            listOf(
                DashboardEventItem("demo_1", "Mom", "BIRTHDAY", "Tomorrow", "Family", "\uD83C\uDF82"),
                DashboardEventItem("demo_2", "Raj & Priya", "ANNIVERSARY", "In 3 days", "Close Friends", "\uD83D\uDC8D"),
                DashboardEventItem("demo_3", "Vikram", "WORK_ANNIVERSARY", "In 5 days", "Work", "\uD83C\uDF89")
            )
        } else {
            upcomingEvents
        }
    }

    val displayPendingApprovals = remember(pendingApprovals, contactsCount) {
        if (pendingApprovals.isEmpty() && contactsCount == 0) {
            listOf(
                DashboardApprovalItem("msg_1", "Mom", "Birthday wish for Mom", "Happy Birthday, Mom! \uD83C\uDF82 You are the strongest person I know...", "WHATSAPP"),
                DashboardApprovalItem("msg_2", "Raj", "Follow-up with Raj", "Hey Raj, it's been a while! How have you been?", "SMS")
            )
        } else {
            pendingApprovals
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Greeting + Notification Bell
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good Evening",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = "Aarav",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = TextPrimary
                    )
                }
                Box(modifier = Modifier.size(48.dp)) {
                    IconButton(
                        onClick = onNavigateToMessages,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (displayPendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = 4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(CyberRose),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$displayPendingCount",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Health Score + Stats Row
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HealthScoreRing(
                            score = displayHealthScore,
                            size = 88.dp,
                            strokeWidth = 7.dp,
                            modifier = Modifier.clickable { onNavigateToAnalytics() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatsBadge(icon = Icons.Default.Group, count = "$displayContactsCount", label = "Contacts")
                            StatsBadge(icon = Icons.Default.CalendarToday, count = "$displayEventsCount", label = "Upcoming Events")
                            StatsBadge(icon = Icons.Default.ChatBubble, count = "$displayPendingCount", label = "Pending Messages")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Upcoming Events
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming Events",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                TextButton(onClick = onSeeAllEvents) {
                    Text("See All", color = NeonViolet, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (displayUpcomingEvents.isEmpty()) {
                EmptyPlaceholder("No upcoming events")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    displayUpcomingEvents.forEach { event ->
                        EventRow(event)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Pending Approvals
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Pending Approvals",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    if (displayPendingApprovals.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberRose.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${displayPendingApprovals.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = CyberRose
                            )
                        }
                    }
                }
            }

            if (displayPendingApprovals.isEmpty()) {
                EmptyPlaceholder("No pending approvals")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    displayPendingApprovals.take(2).forEach { approval ->
                        ApprovalCard(approval, onReview = onNavigateToMessages)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick Actions
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Default.Sync,
                        title = "Sync Contacts",
                        modifier = Modifier.weight(1f),
                        accentColor = ElectricCyan,
                        onClick = onNavigateToContacts
                    )
                    QuickActionTile(
                        icon = Icons.Default.AutoAwesome,
                        title = "Generate Wishes",
                        modifier = Modifier.weight(1f),
                        accentColor = NeonViolet,
                        onClick = onNavigateToMessages
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Default.AutoAwesome,
                        title = "Style Coach",
                        modifier = Modifier.weight(1f),
                        accentColor = CyberRose,
                        onClick = onNavigateToStyleCoach
                    )
                    QuickActionTile(
                        icon = Icons.Default.Cloud,
                        title = "Backup Data",
                        modifier = Modifier.weight(1f),
                        accentColor = Emerald,
                        onClick = onNavigateToSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatsBadge(icon: ImageVector, count: String, label: String) {
            Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = count,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EventRow(event: DashboardEventItem) {
    val tagColor = when {
        event.relationshipType.contains("Family", ignoreCase = true) -> NeonViolet
        event.relationshipType.contains("Close Friend", ignoreCase = true) || event.relationshipType.contains("Best Friend", ignoreCase = true) -> ElectricCyan
        event.relationshipType.contains("Work", ignoreCase = true) || event.relationshipType.contains("Colleague", ignoreCase = true) -> TextSecondary
        else -> Emerald
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.5f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSlate.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(event.emoji, fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.contactName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = event.relationshipType,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.relativeTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = tagColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: DashboardApprovalItem, onReview: () -> Unit) {
    val channelColor = if (approval.channel == "WHATSAPP") Emerald else NeonViolet

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.5f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = approval.contactName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = approval.eventType,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Button(
                onClick = onReview,
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Review", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.4f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun EmptyPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.4f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HeroStatsItem(icon: ImageVector, count: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun DashboardEventCard(event: DashboardEventItem) {
    val tagColor = when (event.relationshipType) {
        "FAMILY" -> NeonViolet
        "BEST_FRIEND" -> ElectricCyan
        "WORK" -> TextSecondary
        else -> Emerald
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate.copy(alpha = 0.5f))
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSlate.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(event.emoji, fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tagColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = event.relationshipType.take(8),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = tagColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = event.contactName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = event.eventType,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = event.relativeTime,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (event.relativeTime.contains("Tomorrow") || event.relativeTime.contains("Today")) CyberRose else ElectricCyan
            )
        }
    }
}


