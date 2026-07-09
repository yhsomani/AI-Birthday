package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.ui.theme.RelateElevation
import com.example.ui.navigation.RelateNavGraph
import com.example.ui.navigation.Screen
import com.example.ui.navigation.bottomNavItems
import com.google.firebase.auth.FirebaseAuth

@Composable
fun RelateApp(
    onRequestCorePermissions: () -> Unit = {},
    isSignedIn: () -> Boolean = { FirebaseAuth.getInstance().currentUser != null },
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var hasShownPermissionRationale by rememberSaveable { mutableStateOf(false) }

    val showBottomBar = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.ContactList.route,
        Screen.Events.route,
        Screen.Messages.route,
        Screen.Messages.filteredRoute,
        Screen.Analytics.route,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = RelateElevation.flat,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route ||
                                (item.screen == Screen.Messages && it.route == Screen.Messages.filteredRoute)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(Screen.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(text = stringResource(item.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        RelateNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            isSignedIn = isSignedIn,
        )
    }

    if (
        showBottomBar &&
        !hasShownPermissionRationale &&
        context.hasMissingCorePermissions()
    ) {
        AlertDialog(
            onDismissRequest = { hasShownPermissionRationale = true },
            title = { Text(text = stringResource(R.string.permission_rationale_title)) },
            text = { Text(text = stringResource(R.string.permission_rationale_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        hasShownPermissionRationale = true
                        onRequestCorePermissions()
                    },
                ) {
                    Text(text = stringResource(R.string.permission_rationale_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { hasShownPermissionRationale = true }) {
                    Text(text = stringResource(R.string.permission_rationale_not_now))
                }
            },
        )
    }
}

private fun Context.hasMissingCorePermissions(): Boolean {
    val smsMissing = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.SEND_SMS,
    ) != PackageManager.PERMISSION_GRANTED
    val notificationsMissing =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    return smsMissing || notificationsMissing
}
