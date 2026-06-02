package com.example.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.ui.components.ElevatedCard
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.RelateAIColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    healthScore: Int,
    pendingMessages: List<PendingMessageEntity>,
    contacts: List<ContactEntity>,
    events: List<EventEntity>,
    userName: String,
    onNavigateTab: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome Gradient Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(RelateAIColors.Primary, RelateAIColors.SecondaryDark)
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Hello, $userName!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Welcome back to your relationship manager.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // Relationship Vitals Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Relationship Vitals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Overall health score across your circle",
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateAIColors.OnSurfaceVariantDark
                    )
                }
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    Canvas(modifier = Modifier.size(72.dp)) {
                        // Background circle
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Active color ring (using Mint to Indigo gradient)
                        val sweep = (healthScore / 100f) * 360f
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(RelateAIColors.Primary, RelateAIColors.Secondary, RelateAIColors.Primary)
                            ),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "$healthScore%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Upcoming Events Timeline Feed
        Column {
            Text(
                text = "Upcoming Reminders",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            val sortedTimelineEvents = remember(events) {
                events.sortedBy { it.nextOccurrenceMs }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                if (contacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Start by syncing your contacts.", style = MaterialTheme.typography.bodyMedium, color = RelateAIColors.OnSurfaceVariantDark)
                    }
                } else if (sortedTimelineEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No upcoming reminders.", style = MaterialTheme.typography.bodyMedium, color = RelateAIColors.OnSurfaceVariantDark)
                    }
                } else {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        sortedTimelineEvents.forEachIndexed { index, event ->
                            val contact = contacts.find { it.id == event.contactId }
                            val name = contact?.name ?: "Unknown"
                            val isAnniversary = event.type == "ANNIVERSARY" || event.type == "WORK_ANNIVERSARY"
                            
                            val daysText = when (event.daysUntil) {
                                0 -> "Today"
                                1 -> "Tomorrow"
                                else -> {
                                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(event.nextOccurrenceMs))
                                }
                            }
                            
                            val typeLabel = when (event.type) {
                                "BIRTHDAY" -> "Birthday"
                                "ANNIVERSARY" -> "Anniversary"
                                "WORK_ANNIVERSARY" -> "Work Anniversary"
                                "GRADUATION" -> "Graduation"
                                else -> event.label ?: event.type
                            }
                            
                            val supportText = "$typeLabel • $daysText"

                            ListItem(
                                headlineContent = { Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White) },
                                supportingContent = { Text(supportText, style = MaterialTheme.typography.bodySmall, color = RelateAIColors.OnSurfaceVariantDark) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isAnniversary) RelateAIColors.Tertiary.copy(alpha = 0.15f)
                                                else RelateAIColors.Primary.copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isAnniversary) Icons.Default.Chat else Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = if (isAnniversary) RelateAIColors.Tertiary else RelateAIColors.Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                trailingContent = {
                                    Badge(
                                        containerColor = if (isAnniversary) RelateAIColors.Secondary.copy(alpha = 0.2f) else RelateAIColors.Primary.copy(alpha = 0.2f),
                                        contentColor = if (isAnniversary) RelateAIColors.Secondary else RelateAIColors.Primary
                                    ) {
                                        Text(
                                            text = if (isAnniversary) "WhatsApp" else "Alert",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            
                            if (index < sortedTimelineEvents.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }
        }

        // AI Generated Drafts Summary
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onNavigateTab("MESSAGES") },
            padding = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Drafts",
                        tint = RelateAIColors.Secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "AI Generated Drafts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${pendingMessages.size} drafts ready for approval",
                            style = MaterialTheme.typography.bodySmall,
                            color = RelateAIColors.OnSurfaceVariantDark
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Review drafts",
                    tint = RelateAIColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        PrimaryButton(
            text = "View Detailed Analytics",
            icon = Icons.Default.Analytics,
            onClick = {
                onNavigateTab("ANALYTICS")
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
