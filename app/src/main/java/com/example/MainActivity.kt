package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.ui.theme.RelateAITheme
import com.example.core.ui.theme.RelateDarkBackground
import com.example.core.ui.theme.RelateOnSurfaceVariant
import com.example.core.ui.theme.RelatePrimary
import com.example.core.ui.theme.RelateSurfaceVariant
import com.example.ui.navigation.RelateNavGraph
import com.example.ui.navigation.Screen
import com.example.ui.navigation.bottomNavItems
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}

        setContent {
            RelateAITheme {
                RelateApp(
                    onRequestCorePermissions = {
                        val permissionsToRequest = getCorePermissionsToRequest()
                        if (permissionsToRequest.isNotEmpty()) {
                            requestPermissionLauncher.launch(permissionsToRequest)
                        }
                    },
                )
            }
        }
    }

    private fun getCorePermissionsToRequest(): Array<String> {
        return buildList {
            if (
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.SEND_SMS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.SEND_SMS)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
}

@Composable
fun RelateApp(
    onRequestCorePermissions: () -> Unit = {},
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
        Screen.Analytics.route,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = RelateDarkBackground,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = RelateDarkBackground,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
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
                                selectedIconColor = RelatePrimary,
                                selectedTextColor = RelatePrimary,
                                unselectedIconColor = RelateOnSurfaceVariant,
                                unselectedTextColor = RelateOnSurfaceVariant,
                                indicatorColor = RelateSurfaceVariant,
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
