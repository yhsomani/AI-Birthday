package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.domain.navigation.RelateDeepLinks
import com.example.ui.screens.messages.MessagesScreen
import com.example.ui.viewmodel.MessageChannelFilter

internal fun NavGraphBuilder.messagesDestinations(
    navController: NavHostController,
    isSignedIn: () -> Boolean,
) {
    authenticatedComposable(
        route = Screen.Messages.route,
        navController = navController,
        isSignedIn = isSignedIn,
        deepLinks = listOf(
            navDeepLink {
                uriPattern = RelateDeepLinks.Messages.pattern
            }
        )
    ) {
        MessagesDestination(navController = navController)
    }

    authenticatedComposable(
        route = Screen.Messages.filteredRoute,
        navController = navController,
        isSignedIn = isSignedIn,
        arguments = listOf(navArgument(Screen.Messages.channelArg) { type = NavType.StringType }),
    ) { backStackEntry ->
        val channel = RouteArgumentCodec.decode(
            backStackEntry.arguments?.getString(Screen.Messages.channelArg)
        )
        MessagesDestination(
            navController = navController,
            channelFilter = channel.toMessageChannelFilter(),
        )
    }
}

@Composable
private fun MessagesDestination(
    navController: NavHostController,
    channelFilter: MessageChannelFilter? = null,
) {
    MessagesScreen(
        initialChannelFilter = channelFilter,
        verificationChannelFilter = channelFilter,
        onNavigateToWish = { contactId, messageRef ->
            navController.navigate(Screen.WishPreview.createRoute(contactId, messageRef))
        },
        onNavigateToContact = { contactId ->
            navController.navigate(
                Screen.ContactDetail.createRoute(contactId, openPreferences = true)
            )
        },
        onNavigateToAutomationSetup = {
            navController.navigate(Screen.AutomationSetup.route)
        },
    )
}

private fun String.toMessageChannelFilter(): MessageChannelFilter? {
    return MessageChannelFilter.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
}
