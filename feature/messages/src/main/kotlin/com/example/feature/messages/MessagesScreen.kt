package com.example.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.ui.components.ElevatedCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.RelateAIColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(pendingMessages: List<PendingMessageEntity>, contacts: List<ContactEntity>) {
    var selectedTone by remember { mutableStateOf("Warm") }
    var selectedLength by remember { mutableStateOf("Standard") }
    
    val tones = listOf("Funny", "Warm", "Formal")
    val lengths = listOf("Short", "Standard", "Long")
    
    val scrollState = rememberScrollState()

    // Grab first pending message or fallback to a template for demonstration
    val pending = pendingMessages.firstOrNull()
    val contact = pending?.let { p -> contacts.find { it.id == p.contactId } }
    val recipientName = contact?.name ?: "Rohan"
    val recipientRelation = contact?.relationshipType ?: "Close Friend"
    
    var draftText by remember(pending, selectedTone, selectedLength) {
        val baseText = if (pending != null) {
            when (selectedTone.lowercase()) {
                "funny" -> pending.funnyVariant.ifBlank { pending.standardVariant }
                "formal" -> pending.formalVariant.ifBlank { pending.standardVariant }
                else -> {
                    when (selectedLength.lowercase()) {
                        "short" -> pending.shortVariant.ifBlank { pending.standardVariant }
                        "long" -> pending.longVariant.ifBlank { pending.standardVariant }
                        else -> pending.standardVariant
                    }
                }
            }
        } else {
            "Happy birthday, $recipientName! Hope you have an awesome day filled with laughter and joy. Let's catch up and celebrate soon!"
        }
        mutableStateOf(baseText)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "AI Message Center",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp),
            letterSpacing = (-0.5).sp
        )

        // Recipient Profile Card Header
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 12.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RelateAIColors.Primary.copy(alpha = 0.12f))
                        .border(1.dp, RelateAIColors.Primary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = recipientName.take(1).uppercase(),
                        color = RelateAIColors.Primary,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = recipientName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = recipientRelation,
                        style = MaterialTheme.typography.bodySmall,
                        color = RelateAIColors.OnSurfaceVariantDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Current Draft Box Card
        Text(
            text = "Current Draft",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = draftText,
            onValueChange = { draftText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.03f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedBorderColor = RelateAIColors.Primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Tone & Length Chips Selection Controls
        Text(
            text = "Tone",
            style = MaterialTheme.typography.labelMedium,
            color = RelateAIColors.OnSurfaceVariantDark,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tones.forEach { tone ->
                val isSelected = selectedTone == tone
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) RelateAIColors.Primary else Color.White.copy(alpha = 0.04f))
                        .border(
                            1.dp,
                            if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedTone = tone }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tone,
                        color = if (isSelected) Color.White else RelateAIColors.OnSurfaceVariantDark,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Length",
            style = MaterialTheme.typography.labelMedium,
            color = RelateAIColors.OnSurfaceVariantDark,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lengths.forEach { length ->
                val isSelected = selectedLength == length
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) RelateAIColors.Primary else Color.White.copy(alpha = 0.04f))
                        .border(
                            1.dp,
                            if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedLength = length }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = length,
                        color = if (isSelected) Color.White else RelateAIColors.OnSurfaceVariantDark,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons Stack
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryButton(
                text = "Approve & Send",
                icon = Icons.Default.Send,
                onClick = { /* Approve send trigger */ },
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton(
                text = "Regenerate Wish",
                icon = Icons.Default.AutoAwesome,
                onClick = { /* Regnerate action */ },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Recently Sent Wishes
        Text(
            text = "Recently Sent Wishes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ListItem(
                    headlineContent = { Text("To $recipientName: 'Hope your 28th is as legendary...'", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Color.White) },
                    supportingContent = { Text("Sent via WhatsApp • May 30", style = MaterialTheme.typography.bodySmall, color = RelateAIColors.OnSurfaceVariantDark) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = "Sent", tint = RelateAIColors.Secondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delivered", style = MaterialTheme.typography.labelSmall, color = RelateAIColors.Secondary, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
