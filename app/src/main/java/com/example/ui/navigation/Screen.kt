package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Auth : Screen("auth")
    data object Home : Screen("home")
    data object ContactList : Screen("contacts")
    data object ContactDetail : Screen("contacts/{contactId}") {
        fun createRoute(contactId: String) = "contacts/${RouteArgumentCodec.encode(contactId)}"
    }
    data object Events : Screen("events")
    data object Messages : Screen("messages")
    data object Settings : Screen("settings")
    data object Analytics : Screen("analytics")
    data object WishPreview : Screen("wish/{contactId}/{eventId}") {
        fun createRoute(contactId: String, eventId: String) =
            "wish/${RouteArgumentCodec.encode(contactId)}/${RouteArgumentCodec.encode(eventId)}"
    }
    data object ChatHistory : Screen("chat-history/{contactId}") {
        fun createRoute(contactId: String) = "chat-history/${RouteArgumentCodec.encode(contactId)}"
    }
    data object StyleCoach : Screen("style-coach")
    data object BackupRestore : Screen("backup-restore")
    data object AutomationSetup : Screen("automation-setup")
    data object MemoryVault : Screen("memory-vault/{contactId}") {
        fun createRoute(contactId: String) = "memory-vault/${RouteArgumentCodec.encode(contactId)}"
    }
    data object GiftAdvisor : Screen("gift-advisor/{contactId}") {
        fun createRoute(contactId: String) = "gift-advisor/${RouteArgumentCodec.encode(contactId)}"
    }
}

data class BottomNavItem(
    val labelRes: Int,
    val icon: ImageVector,
    val screen: Screen,
)

val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_home, Icons.Filled.Home, Screen.Home),
    BottomNavItem(R.string.nav_contacts, Icons.Filled.People, Screen.ContactList),
    BottomNavItem(R.string.nav_events, Icons.Filled.CalendarMonth, Screen.Events),
    BottomNavItem(R.string.nav_messages, Icons.Filled.MailOutline, Screen.Messages),
    BottomNavItem(R.string.analytics, Icons.Filled.Analytics, Screen.Analytics),
)
