package com.example.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AccentGlassmorphicCard
import com.example.ui.components.GlassmorphicCard
import com.example.ui.theme.CyberRose
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.Emerald
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.GlassEdgeStrong
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun MessagesScreen(
    onReviewMessage: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(ObsidianBlack)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // TopAppBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = NeonViolet)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Messages", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextSecondary)
                    }
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                // Pending tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pending", style = MaterialTheme.typography.labelMedium, color = if (selectedTab == 0) NeonViolet else TextTertiary, fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(NeonViolet.copy(alpha = 0.2f)).padding(horizontal = 7.dp, vertical = 2.dp)
                            ) { Text("3", fontSize = 10.sp, color = NeonViolet, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (selectedTab == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(NeonViolet)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }

                // Sent History tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 }
                        .padding(bottom = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Sent History", style = MaterialTheme.typography.labelMedium, color = if (selectedTab == 1) NeonViolet else TextTertiary, fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    if (selectedTab == 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(NeonViolet)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }
            }
            HorizontalDivider(color = GlassEdge)

            Spacer(modifier = Modifier.height(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (selectedTab == 0) {
                    // Pending messages
                    AccentGlassmorphicCard(
                        accentColor = NeonViolet,
                        cornerRadius = 16.dp,
                        contentPadding = 16.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        PendingContent(
                            initials = "M",
                            eventName = "Birthday Wish for Mom",
                            channel = "WhatsApp",
                            generatedAgo = "2h ago",
                            messagePreview = "Happy Birthday, Mom! \uD83C\uDF82 You are the strongest person I know. Every year you teach me something new about kindness and resilience. Love you to the moon and back! ❤️",
                            variantLabel = "Heartfelt • Variant 2 of 6",
                            showSmartApprove = true,
                            onReview = { onReviewMessage("mom_birthday") }
                        )
                    }

                    GlassmorphicCard(cornerRadius = 16.dp, contentPadding = 16.dp, modifier = Modifier.padding(bottom = 16.dp)) {
                        CollapsedCard(
                            initials = "RP",
                            eventName = "Anniversary Message for Raj & Priya",
                            statusText = "VIP Approve • Requires your explicit approval",
                            statusColor = CyberRose,
                            isExpanded = false
                        )
                    }

                    GlassmorphicCard(cornerRadius = 16.dp, contentPadding = 16.dp, modifier = Modifier.padding(bottom = 16.dp)) {
                        CollapsedCard(
                            initials = "VS",
                            eventName = "Birthday Wish for Vikram",
                            statusText = "Fully Auto • Sent automatically at 9 AM",
                            statusColor = Emerald,
                            isExpanded = false
                        )
                    }

                    Spacer(modifier = Modifier.height(88.dp))
                } else {
                    SentMessageRow("Mom's Birthday 2025", "Sent via WhatsApp", "Delivered ✓")
                    SentMessageRow("Raj & Priya Anniversary", "Sent via WhatsApp", "Read ✓✓")
                    SentMessageRow("Vikram's Birthday 2025", "Sent via SMS", "Delivered ✓")
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            FloatingActionButton(
                onClick = {},
                containerColor = NeonViolet,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New message")
            }
        }
    }
}

@Composable
private fun PendingContent(
    initials: String,
    eventName: String,
    channel: String,
    generatedAgo: String,
    messagePreview: String,
    variantLabel: String,
    showSmartApprove: Boolean,
    onReview: () -> Unit
) {
    Column {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(NeonViolet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Text(initials, color = NeonViolet, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(eventName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = TextPrimary)
                Text("Generated $generatedAgo • $channel", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Icon(Icons.Default.Menu, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Message preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSlate.copy(alpha = 0.7f))
                .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(messagePreview, style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Variant selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonViolet.copy(alpha = 0.1f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(variantLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = NeonViolet, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("6 variants", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onReview,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Approve & Send", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassEdgeStrong, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("Edit", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CyberRose.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("Reject", style = MaterialTheme.typography.labelMedium, color = CyberRose)
            }
        }

        // Smart Approve badge
        if (showSmartApprove) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u23F0", fontSize = 14.sp, color = ElectricCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Smart Approve • Auto-sends in 1h 45m", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
            }
        }
    }
}

@Composable
private fun CollapsedCard(
    initials: String,
    eventName: String,
    statusText: String,
    statusColor: Color,
    isExpanded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (statusColor == CyberRose) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(NeonViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Text(initials, color = NeonViolet, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(eventName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = TextPrimary)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            } else {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(NeonViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Text(initials, color = NeonViolet, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(eventName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = TextPrimary)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
        }
    }
}

@Composable
private fun SentMessageRow(title: String, channel: String, status: String) {
    GlassmorphicCard(contentPadding = 14.dp, cornerRadius = 12.dp, modifier = Modifier.padding(bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(channel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Text(status, style = MaterialTheme.typography.labelSmall, color = Emerald)
        }
    }
}
