package com.example.feature.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.db.entities.ContactEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactScreen(
    contact: ContactEntity,
    onBack: () -> Unit,
    onSave: (ContactEntity) -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var relationshipType by remember { mutableStateOf(contact.relationshipType) }
    var jobTitle by remember { mutableStateOf(contact.jobTitle ?: "") }
    var communicationStyle by remember { mutableStateOf(contact.communicationStyle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Contact", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        onSave(contact.copy(
                            name = name,
                            relationshipType = relationshipType,
                            jobTitle = jobTitle.ifBlank { null },
                            communicationStyle = communicationStyle
                        )) 
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = relationshipType,
                onValueChange = { relationshipType = it },
                label = { Text("Relationship (e.g. Sister, Colleague)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = jobTitle,
                onValueChange = { jobTitle = it },
                label = { Text("Job Title (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = communicationStyle,
                onValueChange = { communicationStyle = it },
                label = { Text("Communication Style") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
