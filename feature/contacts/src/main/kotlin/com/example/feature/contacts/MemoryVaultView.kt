package com.example.feature.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.db.entities.MemoryNoteEntity
import com.example.ui.theme.CyberRose
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.Emerald
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryVaultView(
    notes: List<MemoryNoteEntity> = emptyList(),
    onAddNote: ((title: String, content: String, mood: String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            InfoBanner()
            Spacer(modifier = Modifier.height(20.dp))
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No memories yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add a memory to help the AI write more personal messages", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            } else {
                notes.forEach { note ->
                    MemoryCard(
                        category = note.category,
                        noteText = note.noteText,
                        dateMs = note.dateMs,
                        onDelete = if (onDeleteNote != null) {{ onDeleteNote(note.id) }} else null
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            // Bottom spacer for the Add button
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Fixed Add Memory button at bottom
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBlack.copy(alpha = 0.95f))
                    .padding(horizontal = 0.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { if (onAddNote != null) showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    enabled = onAddNote != null
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Memory", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showAddDialog && onAddNote != null) {
            var dialogTitle by remember { mutableStateOf("") }
            var dialogContent by remember { mutableStateOf("") }
            var dialogMood by remember { mutableStateOf("NEUTRAL") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Memory") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = dialogTitle, onValueChange = { dialogTitle = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = dialogContent, onValueChange = { dialogContent = it }, label = { Text("What happened?") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("HAPPY", "NEUTRAL", "SAD", "GRATEFUL", "PROUD").forEach { m ->
                                FilterChip(selected = dialogMood == m, onClick = { dialogMood = m }, label = { Text(m, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (dialogTitle.isNotBlank()) {
                            onAddNote(dialogTitle, dialogContent, dialogMood)
                            showAddDialog = false
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun InfoBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .padding(vertical = 8.dp)
                    .background(NeonViolet, RoundedCornerShape(4.dp))
                    .align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp).align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Memories help the AI write more personal, meaningful messages. Add details about shared experiences, inside jokes, and important life events.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun MemoryCard(
    category: String,
    noteText: String,
    dateMs: Long,
    onDelete: (() -> Unit)?
) {
    val (badgeColor, badgeText) = when {
        category.contains("Experience", ignoreCase = true) || category.contains("Shared", ignoreCase = true) -> NeonViolet to "Shared Experience"
        category.contains("Joke", ignoreCase = true) || category.contains("Inside", ignoreCase = true) -> ElectricCyan to "Inside Joke"
        category.contains("Life", ignoreCase = true) || category.contains("Event", ignoreCase = true) -> Emerald to "Life Event"
        category.contains("Preference", ignoreCase = true) -> NeonViolet to "Preference"
        else -> NeonViolet to category.uppercase()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.1f))
                        .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = badgeColor
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(noteText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Added ${timeAgo(dateMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

private fun timeAgo(dateMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - dateMs
    val days = diff / (1000 * 60 * 60 * 24)
    return when {
        days >= 30 -> "${days / 30} month${if (days / 30 > 1) "s" else ""} ago"
        days >= 7 -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
        days >= 1 -> "$days day${if (days > 1) "s" else ""} ago"
        diff >= 0 -> "Today"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(dateMs))
    }
}
