package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppBottomNavigation(selectedTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.background(Color.Transparent)
    ) {
        val items = buildTabs()
        items.forEach { (id, label, icon) ->
            NavigationBarItem(
                selected = selectedTab == id,
                onClick = { onTabSelected(id) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun AppNavigationRail(selectedTab: String, onTabSelected: (String) -> Unit) {
    NavigationRail(
        containerColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        contentColor = Color.White,
        modifier = Modifier.padding(top = 24.dp)
    ) {
        val items = buildTabs()
        items.forEach { (id, label, icon) ->
            NavigationRailItem(
                selected = selectedTab == id,
                onClick = { onTabSelected(id) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f)
                )
            )
        }
    }
}

private fun buildTabs(): List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>> {
    return listOf(
        Triple("HOME", "Home", Icons.Default.Home),
        Triple("CONTACTS", "Contacts", Icons.Default.Contacts),
        Triple("EVENTS", "Events", Icons.Default.Event),
        Triple("MESSAGES", "Messages", Icons.Default.Chat),
        Triple("MORE", "More", Icons.Default.Menu)
    )
}
