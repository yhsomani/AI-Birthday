package com.example.ui.screens.contacts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import com.example.ui.components.RelateGlassCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.db.entities.ContactEntity
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChip
import com.example.ui.components.HealthIndicatorDot
import com.example.ui.components.ShimmerItem
import com.example.ui.components.ShimmerItem
import androidx.compose.foundation.layout.PaddingValues
import com.example.ui.theme.RelateDarkBackground
import com.example.ui.theme.RelateOnBackground
import com.example.ui.theme.RelateOnSurfaceVariant
import com.example.ui.theme.RelatePrimary
import com.example.ui.theme.RelateSurfaceVariant
import com.example.ui.viewmodel.ContactListViewModel

private val filterOptions = listOf("All", "Family", "Friends", "Work", "Close Friends")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit = {},
    viewModel: ContactListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredContacts = state.contacts.filter { contact ->
        val matchesSearch = contact.name.contains(searchQuery, ignoreCase = true) || searchQuery.isBlank()
        val matchesFilter = selectedFilter == "All" ||
                contact.contactGroup.equals(selectedFilter, ignoreCase = true) ||
                (selectedFilter == "Friends" && contact.relationshipType == "FRIEND")
        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RelateDarkBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Contacts",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search contacts...", color = RelateOnSurfaceVariant)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = RelateOnSurfaceVariant)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RelatePrimary,
                unfocusedBorderColor = RelateSurfaceVariant,
                focusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = RelateSurfaceVariant.copy(alpha = 0.3f),
                focusedTextColor = RelateOnBackground,
                unfocusedTextColor = RelateOnBackground,
            ),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filterOptions.forEach { filter ->
                FilterChip(
                    label = filter,
                    isSelected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                state.syncError?.let { errorMsg ->
                    RelateGlassCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Sync Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFBBF24), // Amber color
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = RelateOnSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.dismissSyncError() }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RelateOnBackground
                            )
                            if (errorMsg.contains("People API") || errorMsg.contains("disabled") || errorMsg.contains("403")) {
                                Spacer(modifier = Modifier.height(12.dp))
                                val context = androidx.compose.ui.platform.LocalContext.current
                                androidx.compose.material3.Button(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://console.developers.google.com/apis/api/people.googleapis.com/overview?project=339889410493")
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFBBF24),
                                        contentColor = RelateDarkBackground
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Enable People API in GCP Console", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (state.isLoading && state.contacts.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(10) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ShimmerItem(modifier = Modifier.size(48.dp).clip(CircleShape))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    ShimmerItem(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ShimmerItem(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp))
                                }
                            }
                        }
                    }
                } else if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(message = "No contacts found")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredContacts) { contact ->
                            ContactRow(
                                contact = contact,
                                onClick = { onContactClick(contact.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(RelateSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.name.take(1),
                color = RelateOnBackground,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                HealthIndicatorDot(health = contact.healthScore / 100f)
            }
            val group = contact.contactGroup ?: contact.relationshipType
            Text(
                text = group,
                style = MaterialTheme.typography.bodySmall,
                color = RelateOnSurfaceVariant,
            )
        }
    }
}
