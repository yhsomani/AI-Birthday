package com.example.feature.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.ui.components.HealthScoreRing
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
import org.json.JSONArray

@Composable
fun ContactDetailScreen(
    contact: ContactEntity,
    notes: List<MemoryNoteEntity> = emptyList(),
    gifts: List<GiftHistoryEntity> = emptyList(),
    onAddNote: (category: String, noteText: String, mood: String) -> Unit = { _, _, _ -> },
    onDeleteNote: (String) -> Unit = {},
    onAddGift: (giftName: String, occasion: String, price: Int) -> Unit = { _, _, _ -> },
    onDeleteGift: (String) -> Unit = {},
    onBack: () -> Unit,
    onEditContact: () -> Unit = {},
    onToggleDnd: (Boolean) -> Unit = {},
    onEditSendTime: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("DETAILS") }
    val tabs = listOf("Details", "Memories", "Gifts")

    Box(modifier = Modifier.fillMaxSize().background(ObsidianBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TopAppBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBlack.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp)
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonViolet)
                }
                Text("Contact Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                IconButton(onClick = onEditContact) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NeonViolet)
                }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                // Hero section
                HeroSection(contact)

                Spacer(modifier = Modifier.height(24.dp))

                // Stats grid
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Health Score Ring
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSlate.copy(alpha = 0.7f))
                            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HealthScoreRing(
                                score = contact.healthScore,
                                size = 72.dp,
                                strokeWidth = 6.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Health Score", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
                        }
                    }

                    // Engagement card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSlate.copy(alpha = 0.7f))
                            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${contact.engagementScore}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = NeonViolet)
                            Text("Engagement", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Emerald.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Emerald))
                                    Text("Active", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium), color = Emerald)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .border(0.dp, Color.Transparent, RoundedCornerShape(0.dp))
                ) {
                    tabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                tab,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isSelected) NeonViolet else TextTertiary
                            )
                        }
                    }
                }
                HorizontalDivider(color = GlassEdge, modifier = Modifier.padding(horizontal = 20.dp))

                Spacer(modifier = Modifier.height(16.dp))

                // Tab content
                when (selectedTab) {
                    "Details" -> DetailsTab(contact, onToggleDnd, onEditSendTime)
                    "Memories" -> MemoryVaultView(notes = notes, onAddNote = onAddNote, onDeleteNote = onDeleteNote)
                    "Gifts" -> GiftAdvisorView(gifts = gifts, contactInterests = contact.interestsJson, onAddGift = onAddGift, onDeleteGift = onDeleteGift)
                }
            }
        }

        // FAB - chat bubble
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
        ) {
            FloatingActionButton(
                onClick = {},
                containerColor = NeonViolet,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Create, contentDescription = "Message", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun HeroSection(contact: ContactEntity) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with gradient border
        Box(modifier = Modifier.size(96.dp)) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(NeonViolet, ElectricCyan))
                    )
                    .padding(2.dp)
            ) {
                val photoUri = contact.profilePhotoUri
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${contact.name} photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(DarkSlate),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(if (contact.name.isNotEmpty()) 1 else 0).uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(contact.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))

        // Relationship badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(NeonViolet.copy(alpha = 0.1f))
                .border(1.dp, NeonViolet.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                contact.relationshipType.replace("_", " "),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                color = NeonViolet,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Phone & email
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!contact.primaryPhone.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(contact.primaryPhone!!, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            if (!contact.primaryEmail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(contact.primaryEmail!!, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DetailsTab(
    contact: ContactEntity,
    onToggleDnd: (Boolean) -> Unit,
    onEditSendTime: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info card
        InfoCard(contact)

        // Communication Style
        CommStyleCard(contact)

        // Automation card
        AutomationCard(contact, onToggleDnd, onEditSendTime)

        // Interests
        InterestsSection(contact.interestsJson)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun InfoCard(contact: ContactEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    LabelWithIcon("cake", "Birthday")
                    val bday = buildBirthday(contact.birthdayDay, contact.birthdayMonth, contact.birthdayYear)
                    Text(bday, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    LabelWithIcon("favorite", "Anniversary")
                    val anni = buildAnniversary(contact.anniversaryDay, contact.anniversaryMonth, contact.anniversaryYear)
                    Text(anni, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                }
            }
            HorizontalDivider(color = GlassEdge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    LabelWithIcon("chat", "Channel")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCyan.copy(alpha = 0.1f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            contact.preferredChannel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    LabelWithIcon("translate", "Language")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonViolet.copy(alpha = 0.1f))
                            .border(1.dp, NeonViolet.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            contact.preferredLanguage,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeonViolet
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelWithIcon(icon: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp), color = TextTertiary, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun CommStyleCard(contact: ContactEntity) {
    val styleLevel = mapOf("FORMAL" to 0f, "WARM" to 0.33f, "CASUAL" to 0.65f, "INTIMATE" to 1f)
    val progress = styleLevel[contact.formalityLevel] ?: 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Communication Style", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp), color = TextTertiary, fontWeight = FontWeight.Bold)
                Text(
                    contact.communicationStyle.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NeonViolet
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(GlassEdge)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(listOf(NeonViolet, ElectricCyan))
                        )
                )
                Box(
                    modifier = Modifier
                        .offset(x = ((progress * 1000) - 8).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .border(2.dp, NeonViolet, CircleShape)
                        .align(Alignment.CenterStart)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Formal", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp), color = TextTertiary, fontWeight = FontWeight.Bold)
                Text("Intimate", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp), color = TextTertiary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutomationCard(
    contact: ContactEntity,
    onToggleDnd: (Boolean) -> Unit,
    onEditSendTime: () -> Unit
) {
    val smartApproveEnabled = contact.automationMode == "SMART_APPROVE" || contact.automationMode == "DEFAULT"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSlate.copy(alpha = 0.7f))
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Smart Approve
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NeonViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Smart Approve", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text("Review before automation", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Switch(
                    checked = smartApproveEnabled,
                    onCheckedChange = { onToggleDnd(!it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = NeonViolet,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = GlassEdgeStrong
                    )
                )
            }

            HorizontalDivider(color = GlassEdge)

            // Skip Auto-Wish
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Skip Auto-Wish", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Switch(
                    checked = contact.skipAutoWish,
                    onCheckedChange = { onToggleDnd(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = NeonViolet,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = GlassEdgeStrong
                    )
                )
            }

            // Send Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Send Time", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                TextButton(onClick = onEditSendTime) {
                    val timeText = if (contact.customSendTimeHour != null && contact.customSendTimeMinute != null) {
                        val h = contact.customSendTimeHour!!
                        val m = contact.customSendTimeMinute!!
                        String.format("%02d:%02d ${if (h < 12) "AM" else "PM"}", if (h % 12 == 0) 12 else h % 12, m)
                    } else {
                        "9:00 AM"
                    }
                    Text(timeText, color = NeonViolet, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun InterestsSection(interestsJson: String) {
    val interests = remember(interestsJson) {
        try {
            val arr = JSONArray(interestsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Interests", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp), color = TextTertiary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            interests.take(4).forEach { interest ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NeonViolet.copy(alpha = 0.05f))
                        .border(1.dp, NeonViolet.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(interest, style = MaterialTheme.typography.bodySmall, color = NeonViolet)
                }
            }
            if (interests.size > 4) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, GlassEdge, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun buildBirthday(day: Int?, month: Int?, year: Int?): String {
    if (day == null || month == null) return "N/A"
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val m = if (month in 1..12) months[month - 1] else "?"
    val y = if (year != null) ", $year" else ""
    return "$m $day$y"
}

private fun buildAnniversary(day: Int?, month: Int?, year: Int?): String {
    if (day == null || month == null) return "N/A"
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val m = if (month in 1..12) months[month - 1] else "?"
    val y = if (year != null) ", $year" else ""
    return "$m $day$y"
}
