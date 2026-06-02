package com.example.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.feature.contacts.ContactDetailScreen
import com.example.feature.contacts.EditContactScreen
import com.example.ui.components.RelateTimePicker
import com.example.ui.navigation.AppBottomNavigation
import com.example.ui.navigation.AppNavigationRail
import androidx.paging.compose.collectAsLazyPagingItems

@Composable
fun MainAppScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onSaveTrainingText: ((String) -> Unit)? = null,
    onAddBirthday: ((contactId: String, dayOfMonth: Int, month: Int, year: Int?) -> Unit)? = null,
    onSignOut: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf("HOME") }
    var selectedContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val pendingMessages by viewModel.pendingMessages.collectAsStateWithLifecycle()
    val healthScore by viewModel.healthScore.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val pagedContacts = viewModel.pagedContacts.collectAsLazyPagingItems()
    val userName = viewModel.userName
    val userEmail = viewModel.userEmail

    if (showTimePicker && selectedContactId != null) {
        val contact = contacts.find { it.id == selectedContactId }
        if (contact != null) {
            RelateTimePicker(
                initialHour = contact.customSendTimeHour ?: 9,
                initialMinute = contact.customSendTimeMinute ?: 0,
                onTimeSelected = { hour, minute ->
                    viewModel.updateContact(contact.copy(customSendTimeHour = hour, customSendTimeMinute = minute))
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false }
            )
        } else {
            showTimePicker = false
        }
    }
    
    if (editingContactId != null) {
        val contact = contacts.find { it.id == editingContactId }
        if (contact != null) {
            EditContactScreen(
                contact = contact,
                onBack = { editingContactId = null },
                onSave = { updatedContact ->
                    viewModel.updateContact(updatedContact)
                    editingContactId = null
                }
            )
            return
        } else {
            editingContactId = null
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    
    if (selectedContactId != null) {
        val contact = contacts.find { it.id == selectedContactId }
        if (contact != null) {
            ContactDetailScreen(
                contact = contact,
                onBack = { selectedContactId = null },
                onEditContact = { editingContactId = contact.id },
                onToggleDnd = { enabled -> 
                    viewModel.updateContact(contact.copy(skipAutoWish = enabled)) 
                },
                onEditSendTime = { showTimePicker = true }
            )
        } else {
            selectedContactId = null
        }
        return
    }

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppNavigationRail(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            Box(modifier = Modifier.weight(1f)) {
                AppContent(
                    tab = selectedTab,
                    healthScore = healthScore,
                    pendingMessages = pendingMessages,
                    contacts = contacts,
                    events = events,
                    pagedContacts = pagedContacts,
                    userName = userName,
                    userEmail = userEmail,
                    onContactClick = { selectedContactId = it },
                    onNavigateTab = { selectedTab = it },
                    onSaveTrainingText = onSaveTrainingText,
                    onAddBirthday = onAddBirthday,
                    onSignOut = onSignOut
                )
            }
        }
    } else {
        Scaffold(
            bottomBar = { AppBottomNavigation(selectedTab = selectedTab, onTabSelected = { selectedTab = it }) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                AppContent(
                    tab = selectedTab,
                    healthScore = healthScore,
                    pendingMessages = pendingMessages,
                    contacts = contacts,
                    events = events,
                    pagedContacts = pagedContacts,
                    userName = userName,
                    userEmail = userEmail,
                    onContactClick = { selectedContactId = it },
                    onNavigateTab = { selectedTab = it },
                    onSaveTrainingText = onSaveTrainingText,
                    onAddBirthday = onAddBirthday,
                    onSignOut = onSignOut
                )
            }
        }
    }
}
