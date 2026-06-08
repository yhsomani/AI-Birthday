package com.example.ui.screens.giftadvisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.ui.components.EmptyState
import com.example.core.ui.components.RelateGlassCard
import com.example.core.ui.components.SectionHeader
import com.example.core.ui.components.StatCard
import com.example.core.ui.theme.*
import com.example.ui.viewmodel.GiftAdvisorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftAdvisorScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: GiftAdvisorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var giftName by remember { mutableStateOf("") }
    var giftCategory by remember { mutableStateOf("") }
    var occasionType by remember { mutableStateOf("") }
    var approxCost by remember { mutableStateOf("") }
    var receivedWellState by remember { mutableStateOf<Boolean?>(null) } // null: unknown, true: liked, false: not
    var giftNotes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gift Advisor: ${uiState.contact?.name ?: ""}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = RelatePrimary
            ) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = "Record Gift", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(RelateDarkBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RelatePrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RelateDarkBackground)
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Budget Stats Cards Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            label = "Annual Budget",
                            value = "₹${uiState.contact?.giftBudgetInr ?: 500}",
                            icon = Icons.Filled.CardGiftcard,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Total Spent",
                            value = "₹${uiState.totalSpentThisYear}",
                            icon = Icons.Filled.ShoppingCart,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Remaining",
                            value = "₹${uiState.remainingBudget}",
                            icon = Icons.Filled.AttachMoney,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // AI Gift Suggestions Panel
                item {
                    RelateGlassCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "AI Gift Recommendations",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RelatePrimary
                                )

                                Button(
                                    onClick = { viewModel.generateGiftSuggestions() },
                                    enabled = !uiState.isGeneratingSuggestions,
                                    colors = ButtonDefaults.buttonColors(containerColor = RelatePrimary)
                                ) {
                                    if (uiState.isGeneratingSuggestions) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Ask AI")
                                    }
                                }
                            }

                            Text(
                                "Analyze contact's interests and suggest 3 unique gift ideas within the budget.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (uiState.suggestions.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uiState.suggestions.forEach { sug ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(sug.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                                    Text("₹${sug.estimatedCostInr}", color = RelatePrimary, fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(sug.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Gift History Header
                item {
                    SectionHeader(title = "Gift History Journal")
                }

                if (uiState.giftHistory.isEmpty()) {
                    item {
                        EmptyState(
                            message = "No gifts recorded yet. Use the action button below to add your first recorded gift history.",
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                        )
                    }
                } else {
                    items(uiState.giftHistory) { gift ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = RelateCard)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(gift.giftName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("${gift.occasionType} (${gift.year})", style = MaterialTheme.typography.bodySmall, color = RelateOnSurfaceVariant)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("₹${gift.approxCostInr}", style = MaterialTheme.typography.titleMedium, color = RelatePrimary, modifier = Modifier.padding(horizontal = 8.dp))
                                        
                                        when (gift.receivedWell) {
                                            true -> Icon(Icons.Filled.ThumbUp, contentDescription = "Liked", tint = Color(0xFF10B981))
                                            false -> Icon(Icons.Filled.ThumbDown, contentDescription = "Disliked", tint = Color(0xFFEF4444))
                                            null -> Icon(Icons.Filled.Info, contentDescription = "Unknown feedback", tint = Color.Gray)
                                        }

                                        IconButton(onClick = { viewModel.deleteGiftRecord(gift) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                                if (gift.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Notes: ${gift.notes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Gift Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Record Gift History") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = giftName,
                        onValueChange = { giftName = it },
                        label = { Text("Gift Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = giftCategory,
                        onValueChange = { giftCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = occasionType,
                        onValueChange = { occasionType = it },
                        label = { Text("Occasion") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = approxCost,
                        onValueChange = { approxCost = it },
                        label = { Text("Cost (INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("How was it received?", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { receivedWellState = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (receivedWellState == true) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ThumbUp, contentDescription = "Liked", tint = if (receivedWellState == true) Color.White else MaterialTheme.colorScheme.onSurface)
                        }

                        Button(
                            onClick = { receivedWellState = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (receivedWellState == false) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ThumbDown, contentDescription = "Disliked", tint = if (receivedWellState == false) Color.White else MaterialTheme.colorScheme.onSurface)
                        }

                        Button(
                            onClick = { receivedWellState = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (receivedWellState == null) Color.Gray else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unknown", color = if (receivedWellState == null) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    OutlinedTextField(
                        value = giftNotes,
                        onValueChange = { giftNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cost = approxCost.toIntOrNull() ?: 0
                        viewModel.addGiftRecord(giftName, giftCategory, occasionType, cost, receivedWellState, giftNotes)
                        showAddDialog = false
                        giftName = ""
                        giftCategory = ""
                        occasionType = ""
                        approxCost = ""
                        giftNotes = ""
                        receivedWellState = null
                    },
                    enabled = giftName.isNotBlank() && approxCost.isNotBlank() && giftCategory.isNotBlank() && occasionType.isNotBlank()
                ) {
                    Text("Save Record")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
