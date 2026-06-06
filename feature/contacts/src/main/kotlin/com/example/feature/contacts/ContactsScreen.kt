package com.example.feature.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberRose
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

data class ContactPreview(
    val name: String,
    val initials: String,
    val relationship: String,
    val healthScore: Int,
    val isVip: Boolean = false
)

private val sampleContacts = listOf(
    ContactPreview("Ananya Sharma", "AS", "Sister", 92, true),
    ContactPreview("Raj Patel", "RP", "College Friend", 78),
    ContactPreview("Priya Verma", "PV", "Colleague", 65),
    ContactPreview("Vikram Singh", "VS", "Best Friend", 88, true),
    ContactPreview("Meera Deshpande", "MD", "Aunt", 45),
    ContactPreview("Arjun Kapoor", "AK", "Work", 72),
    ContactPreview("Neha Gupta", "NG", "School Friend", 81),
)

private val filterChips = listOf("All", "Family", "Friends", "Work", "VIP")

/**
 * Contacts List Screen matching Stitch "RelateAI Contacts List" design.
 */
@Composable
fun ContactsScreen(
    onContactClick: (String) -> Unit = {},
    onAddContact: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = ObsidianBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContact,
                containerColor = NeonViolet,
                contentColor = TextPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Contacts",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search contacts...", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassEdge, RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkSlate,
                    unfocusedContainerColor = DarkSlate,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NeonViolet,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterChips.forEach { chip ->
                    val isActive = chip == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) NeonViolet else Color.Transparent)
                            .border(1.dp, if (isActive) NeonViolet else TextTertiary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = chip }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            chip,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isActive) TextPrimary else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Count
            Text(
                "${sampleContacts.size} contacts",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Contact list
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ⚡ Bolt Optimization: Added stable key to LazyColumn
                // 💡 What: Used key = { it.name } to provide stable keys for each contact preview
                // 🎯 Why: Without keys, Compose may recreate all visible items when the list changes or scrolls
                // 📊 Impact: Reduces unnecessary recompositions
                items(sampleContacts, key = { it.name }) { contact ->
                    ContactRow(contact = contact, onClick = { onContactClick(contact.name) })
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NeonViolet.copy(alpha = 0.2f))
                .then(
                    if (contact.isVip) Modifier.border(2.dp, NeonViolet.copy(alpha = 0.6f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.initials, color = NeonViolet, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name + relationship
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = TextPrimary)
            Text(contact.relationship, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        // Health score badge
        val scoreColor = when {
            contact.healthScore >= 70 -> ElectricCyan
            contact.healthScore >= 40 -> Color(0xFFFBBF24)
            else -> CyberRose
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(scoreColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("${contact.healthScore}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = scoreColor)
        }
    }
}
