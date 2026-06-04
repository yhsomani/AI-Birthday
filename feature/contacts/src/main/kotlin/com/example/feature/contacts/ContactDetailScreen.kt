package com.example.feature.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.db.entities.ContactEntity
import com.example.ui.components.ElevatedCard
import com.example.ui.theme.RelateAIColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: ContactEntity, 
    onBack: () -> Unit,
    onEditContact: () -> Unit = {},
    onToggleDnd: (Boolean) -> Unit = {},
    onEditSendTime: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("OVERVIEW") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = onEditContact) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = listOf("OVERVIEW", "MEMORIES", "GIFTS").indexOf(selectedTab),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab in listOf("OVERVIEW", "MEMORIES", "GIFTS")) {
                        TabRowDefaults.SecondaryIndicator(
                           Modifier.tabIndicatorOffset(tabPositions[listOf("OVERVIEW", "MEMORIES", "GIFTS").indexOf(selectedTab)]),
                           color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
            ) {
                listOf("OVERVIEW", "MEMORIES", "GIFTS").forEach { it ->
                    Tab(
                        selected = selectedTab == it,
                        onClick = { selectedTab = it },
                        text = { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = if (selectedTab == it) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
            
            Box(modifier = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 16.dp)) {
                when (selectedTab) {
                    "OVERVIEW" -> ContactOverview(contact, onToggleDnd, onEditSendTime)
                    "MEMORIES" -> MemoryVaultView(
                        notes = emptyList(), // TODO: Wire to ViewModel
                        onAddNote = { title, content, mood -> /* TODO: Implement */ },
                        onDeleteNote = { id -> /* TODO: Implement */ }
                    )
                    "GIFTS" -> GiftAdvisorView(
                        gifts = emptyList(), // TODO: Wire to ViewModel
                        contactInterests = contact.interests ?: "[]",
                        onAddGift = { name, occasion, price -> /* TODO: Implement */ },
                        onDeleteGift = { id -> /* TODO: Implement */ }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactOverview(
    contact: ContactEntity,
    onToggleDnd: (Boolean) -> Unit,
    onEditSendTime: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val photoUri = contact.profilePhotoUri
            if (photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${contact.name} photo",
                    modifier = Modifier.size(80.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(if(contact.name.isNotEmpty()) 1 else 0).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text(contact.relationshipType, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = "Health", tint = RelateAIColors.Thriving, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Health Score: ${contact.healthScore}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                if (contact.jobTitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(contact.jobTitle!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Engagement Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { contact.engagementScore / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${contact.engagementScore} points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Communication Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(contact.communicationStyle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Automation Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Do Not Disturb", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Never auto-send messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = contact.skipAutoWish, 
                        onCheckedChange = { onToggleDnd(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Send Time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        val timeText = if (contact.customSendTimeHour != null && contact.customSendTimeMinute != null) {
                            String.format(java.util.Locale.getDefault(), "%02d:%02d", contact.customSendTimeHour, contact.customSendTimeMinute)
                        } else {
                            "Default (09:00 AM)"
                        }
                        Text(timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onEditSendTime) {
                        Text("EDIT", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


