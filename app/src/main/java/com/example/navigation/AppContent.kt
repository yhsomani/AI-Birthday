package com.example.navigation

import androidx.compose.foundation.layout.*
import com.example.feature.dashboard.DashboardScreen
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
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.StyleCoachScreen

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

    val now = remember { System.currentTimeMillis() }
    val upcomingEventItems = remember(events, contacts) {
        val thirtyDaysFromNow = now + 30L * 24 * 60 * 60 * 1000
        events.filter { event ->
            event.nextOccurrenceMs >= now && event.nextOccurrenceMs <= thirtyDaysFromNow
        }.sortedBy { it.nextOccurrenceMs }
        .mapNotNull { event ->
            val contact = contacts.find { it.id == event.contactId } ?: return@mapNotNull null
            val diffDays = ((event.nextOccurrenceMs - now) / (1000 * 60 * 60 * 24)).toInt()
            val relativeTime = when {
                diffDays == 0 -> "Today"
                diffDays == 1 -> "Tomorrow"
                else -> "In $diffDays Days"
            }
            val emoji = when (event.type) {
                "BIRTHDAY" -> "🎂"
                "ANNIVERSARY" -> "💍"
                else -> "🎉"
            }
            com.example.feature.dashboard.DashboardEventItem(
                eventId = event.id,
                contactName = contact.name,
                eventType = event.type,
                relativeTime = relativeTime,
                relationshipType = contact.relationshipType,
                emoji = emoji
            )
        }
    }

    val pendingApprovalItems = remember(pendingMessages, contacts) {
        pendingMessages.mapNotNull { msg ->
            val contact = contacts.find { it.id == msg.contactId } ?: return@mapNotNull null
            com.example.feature.dashboard.DashboardApprovalItem(
                messageId = msg.id,
                contactName = contact.name,
                eventType = "Outreach draft",
                draftText = msg.selectedVariantText,
                channel = msg.channel
            )
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
                    contactsCount = contacts.size,
                    eventsCount = events.size,
                    pendingCount = pendingMessages.size,
                    upcomingEvents = upcomingEventItems,
                    pendingApprovals = pendingApprovalItems,
                    onSeeAllEvents = { onNavigateTab("EVENTS") },
                    onNavigateToContacts = { onNavigateTab("CONTACTS") },
                    onNavigateToMessages = { onNavigateTab("MESSAGES") },
                    onNavigateToAnalytics = { onNavigateTab("ANALYTICS") },
                    onNavigateToStyleCoach = { onNavigateTab("STYLE_COACH") },
                    onNavigateToSettings = { onNavigateTab("MORE") }
                )
                "CONTACTS" -> ContactsContent(pagedContacts, onContactClick)
                "EVENTS" -> EventsScreen(
                    onEventReview = { onNavigateTab("MESSAGES") }
                )
                "MESSAGES" -> MessagesScreen()
                "MORE" -> SettingsScreen(
                    userName = userName,
                    userEmail = userEmail,
                    onNavigateStyleCoach = { onNavigateTab("STYLE_COACH") },
                    onNavigateAnalytics = { onNavigateTab("ANALYTICS") },
                    onSignOut = onSignOut
                )
                "ANALYTICS" -> AnalyticsScreen(
                    onBack = { onNavigateTab("HOME") },
                    onReconnectClick = { contactName -> onNavigateTab("MESSAGES") }
                )
                "STYLE_COACH" -> StyleCoachScreen(
                    onNavigateBack = { onNavigateTab("MORE") },
                    onSaveTrainingText = onSaveTrainingText
                )
            }
        }
    }
}
