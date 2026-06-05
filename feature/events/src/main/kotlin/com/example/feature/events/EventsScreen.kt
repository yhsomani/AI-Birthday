package com.example.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.Emerald
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

data class TimelineEvent(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val dateLabel: String,
    val isToday: Boolean = false,
    val status: EventStatus = EventStatus.NOT_STARTED,
    val statusText: String = "",
    val hasAvatar: Boolean = false
)

enum class EventStatus { READY, GENERATING, SCHEDULED, NOT_STARTED, SENT }

private val sampleEvents = listOf(
    TimelineEvent("🎂", "Raj Patel's Birthday", "College Friend • Turning 33", "TODAY", true, EventStatus.READY, "Message ready for approval", true),
    TimelineEvent("🎂", "Mom's Birthday", "Family • Turning 58", "Jun 5", false, EventStatus.READY, "3 variants generated"),
    TimelineEvent("💍", "Vikram & Neha Anniversary", "Best Friend • 5th Anniversary", "Jun 8", false, EventStatus.GENERATING, "Generating message..."),
    TimelineEvent("🎉", "Priya's Work Anniversary", "Colleague • 3 years", "Jun 15", false, EventStatus.SCHEDULED, "Scheduled"),
    TimelineEvent("🎂", "Arjun Kapoor Birthday", "Work • Turning 29", "Jun 22", false, EventStatus.NOT_STARTED, "Not started"),
)

private val eventFilters = listOf("All", "Birthdays", "Anniversaries", "Work", "Custom")

@Composable
fun EventsScreen(
    onCalendarView: () -> Unit = {},
    onEventReview: (String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("All") }

    Box(modifier = Modifier.fillMaxSize().background(ObsidianBlack)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBlack.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp)
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Events", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSlate)
                        .border(1.dp, GlassEdge, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onCalendarView) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", tint = NeonViolet)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = TextSecondary)
                }
                Text("June 2026", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary)
                IconButton(onClick = {}) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eventFilters.forEach { chip ->
                    val isActive = chip == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) NeonViolet else DarkSlate.copy(alpha = 0.7f))
                            .border(1.dp, if (isActive) NeonViolet else GlassEdge, RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = chip }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(chip, style = MaterialTheme.typography.labelLarge, color = if (isActive) TextPrimary else TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp)) {
                Column {
                    sampleEvents.forEachIndexed { index, event ->
                        EventTimelineRow(event, index == sampleEvents.lastIndex, onEventReview)
                    }
                }
            }

            Spacer(modifier = Modifier.height(88.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 88.dp)
        ) {
            FloatingActionButton(
                onClick = {},
                containerColor = NeonViolet,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add event")
            }
        }
    }
}

@Composable
private fun EventTimelineRow(
    event: TimelineEvent,
    isLast: Boolean,
    onEventReview: (String) -> Unit
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp).fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .size(if (event.isToday) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(if (event.isToday) NeonViolet else NeonViolet.copy(alpha = 0.4f))
                    .border(if (event.isToday) 4.dp else 2.dp, ObsidianBlack, CircleShape)
            )
            if (!isLast) {
                val lineColor = if (event.isToday) {
                    Brush.verticalGradient(listOf(NeonViolet, NeonViolet.copy(alpha = 0.1f)))
                } else {
                    Brush.verticalGradient(listOf(NeonViolet.copy(alpha = 0.2f), NeonViolet.copy(alpha = 0.1f)))
                }
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSlate.copy(alpha = 0.7f))
                .border(
                    width = 1.dp,
                    color = if (event.isToday) NeonViolet.copy(alpha = 0.4f) else GlassEdge,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (event.isToday) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonViolet.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("TODAY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = NeonViolet)
                        }
                    } else {
                        Text(event.dateLabel, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }

                    if (event.hasAvatar) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, NeonViolet.copy(alpha = 0.3f), CircleShape)
                                .background(NeonViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                event.title.substringAfter("'s").substringBefore(" ").trim().take(1).ifEmpty { "R" },
                                color = NeonViolet,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("${event.emoji} ${event.title}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(event.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                when (event.status) {
                    EventStatus.READY -> {
                        if (event.isToday) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Message ready for approval", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onEventReview(event.title) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Review & Send", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("\u2728", color = ElectricCyan, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(event.statusText, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                                Button(
                                    onClick = { onEventReview(event.title) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Review", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    EventStatus.GENERATING -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkSlate.copy(alpha = 0.5f)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                            Text(event.statusText, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    EventStatus.SCHEDULED -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(event.statusText, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                    }
                    EventStatus.NOT_STARTED -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("NOT STARTED", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = TextTertiary, fontWeight = FontWeight.Bold)
                    }
                    EventStatus.SENT -> {}
                }
            }
        }
    }
}
