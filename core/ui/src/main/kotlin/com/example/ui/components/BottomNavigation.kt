package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

/**
 * Bottom navigation tabs matching the Stitch design:
 * Frosted glass background, 5 tabs, neon violet active indicator.
 */
enum class BottomNavTab(
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val route: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "HOME"),
    CONTACTS("Contacts", Icons.Filled.People, Icons.Outlined.People, "CONTACTS"),
    EVENTS("Events", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "EVENTS"),
    MESSAGES("Messages", Icons.Filled.MailOutline, Icons.Outlined.MailOutline, "MESSAGES"),
    MORE("More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "MORE")
}

/** Maps a tab string (e.g. "HOME") to BottomNavTab. Defaults to HOME. */
fun String.toBottomNavTab(): BottomNavTab =
    BottomNavTab.entries.find { it.route == this } ?: BottomNavTab.HOME

/** Maps BottomNavTab to its route string. */
fun BottomNavTab.toRoute(): String = this.route

@Composable
fun RelateAIBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = selectedTab.toBottomNavTab()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = GlassEdge,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            )
    ) {
        NavigationBar(
            containerColor = ObsidianBlack.copy(alpha = 0.85f),
            contentColor = TextSecondary,
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp)
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = tab == current
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab.toRoute()) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                            contentDescription = tab.label
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonViolet,
                        selectedTextColor = NeonViolet,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary,
                        indicatorColor = NeonViolet.copy(alpha = 0.12f)
                    )
                )
            }
        }
    }
}

/**
 * Navigation rail for tablet layout, matching the Stitch design system.
 */
@Composable
fun RelateAINavigationRail(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        containerColor = ObsidianBlack.copy(alpha = 0.85f),
        contentColor = Color.White,
        modifier = modifier.padding(top = 24.dp)
    ) {
        BottomNavTab.entries.forEach { tab ->
            val isSelected = tab.toRoute() == selectedTab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onTabSelected(tab.toRoute()) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = NeonViolet,
                    indicatorColor = NeonViolet.copy(alpha = 0.2f),
                    unselectedIconColor = Color.White.copy(alpha = 0.5f),
                    unselectedTextColor = Color.White.copy(alpha = 0.5f)
                )
            )
        }
    }
}
