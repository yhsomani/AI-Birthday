package com.example.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import androidx.paging.compose.LazyPagingItems
import com.example.feature.analytics.AnalyticsScreen
import com.example.feature.contacts.ContactsContent
import com.example.feature.events.EventsScreen
import com.example.feature.messages.MessagesScreen
import com.example.ui.components.TopBar
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.StyleCoachScreen

@Composable
fun AppContent(
    tab: String,
    healthScore: Int,
    pendingMessages: List<PendingMessageEntity>,
    contacts: List<ContactEntity>,
    events: List<EventEntity>,
    pagedContacts: LazyPagingItems<ContactEntity>,
    userName: String = "User",
    userEmail: String = "",
    onContactClick: (String) -> Unit,
    onNavigateTab: (String) -> Unit,
    onSaveTrainingText: ((String) -> Unit)? = null,
    onAddBirthday: ((contactId: String, dayOfMonth: Int, month: Int, year: Int?) -> Unit)? = null,
    onSignOut: () -> Unit = {}
) {
    val avatarText = remember(userName) {
        val parts = userName.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else if (parts.isNotEmpty()) {
            parts[0].take(2).uppercase()
        } else {
            "US"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tab != "ANALYTICS" && tab != "STYLE_COACH") {
            TopBar(title = "RELATE AI", subtitle = "Dashboard", avatarText = avatarText)
        }
        Box(modifier = Modifier.weight(1f).padding(horizontal = if (tab != "ANALYTICS" && tab != "STYLE_COACH") 24.dp else 0.dp)) {
            when (tab) {
                "HOME" -> DashboardScreen(
                    healthScore = healthScore,
                    pendingMessages = pendingMessages,
                    contacts = contacts,
                    events = events,
                    userName = userName,
                    onNavigateTab = onNavigateTab
                )
                "CONTACTS" -> ContactsContent(pagedContacts, onContactClick)
                "EVENTS" -> EventsScreen(
                    contacts = contacts,
                    events = events,
                    onAddBirthday = onAddBirthday ?: { _, _, _, _ -> }
                )
                "MESSAGES" -> MessagesScreen(hiltViewModel(), pendingMessages, contacts)
                "MORE" -> SettingsScreen(
                    userName = userName,
                    userEmail = userEmail,
                    onNavigateStyleCoach = { onNavigateTab("STYLE_COACH") },
                    onSignOut = onSignOut
                )
                "ANALYTICS" -> AnalyticsScreen(onNavigateBack = { onNavigateTab("HOME") })
                "STYLE_COACH" -> StyleCoachScreen(
                    onNavigateBack = { onNavigateTab("MORE") },
                    onSaveTrainingText = onSaveTrainingText
                )
            }
        }
    }
}
