package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Auth : Screen("auth")
    data object Home : Screen("home")
    data object ContactList : Screen("contacts")
    data object ContactDetail : Screen("contacts/{contactId}") {
        fun createRoute(contactId: String) = "contacts/${java.net.URLEncoder.encode(contactId, "UTF-8")}"
    }
    data object Events : Screen("events")
    data object Messages : Screen("messages")
    data object Settings : Screen("settings")
    data object Analytics : Screen("analytics")
    data object WishPreview : Screen("wish/{contactId}/{eventId}") {
        fun createRoute(contactId: String, eventId: String) = "wish/${java.net.URLEncoder.encode(contactId, "UTF-8")}/${java.net.URLEncoder.encode(eventId, "UTF-8")}"
    }
    data object StyleCoach : Screen("style-coach")
    data object BackupRestore : Screen("backup-restore")
    data object MemoryVault : Screen("memory-vault/{contactId}") {
        fun createRoute(contactId: String) = "memory-vault/${java.net.URLEncoder.encode(contactId, "UTF-8")}"
    }
    data object GiftAdvisor : Screen("gift-advisor/{contactId}") {
        fun createRoute(contactId: String) = "gift-advisor/${java.net.URLEncoder.encode(contactId, "UTF-8")}"
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Filled.Home, Screen.Home),
    BottomNavItem("Contacts", Icons.Filled.People, Screen.ContactList),
    BottomNavItem("Events", Icons.Filled.CalendarMonth, Screen.Events),
    BottomNavItem("Messages", Icons.Filled.MailOutline, Screen.Messages),
    BottomNavItem("Analytics", Icons.Filled.Analytics, Screen.Analytics),
)
