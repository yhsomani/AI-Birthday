package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

internal fun NavGraphBuilder.authenticatedComposable(
    route: String,
    navController: NavHostController,
    isSignedIn: () -> Boolean,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
    ) { backStackEntry ->
        RequireSignedIn(
            navController = navController,
            isSignedIn = isSignedIn,
        ) {
            content(backStackEntry)
        }
    }
}

@Composable
private fun RequireSignedIn(
    navController: NavHostController,
    isSignedIn: () -> Boolean,
    content: @Composable () -> Unit,
) {
    if (isSignedIn()) {
        content()
    } else {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
}
