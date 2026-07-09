package com.example.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.domain.navigation.RelateDeepLinks
import com.example.ui.screens.chat.ChatHistoryScreen
import com.example.ui.screens.contacts.ContactDetailScreen
import com.example.ui.screens.contacts.ContactListScreen
import com.example.ui.screens.giftadvisor.GiftAdvisorScreen
import com.example.ui.screens.memoryvault.MemoryVaultScreen
import com.example.ui.screens.wish.WishPreviewScreen

internal fun NavGraphBuilder.relationshipDestinations(
    navController: NavHostController,
    isSignedIn: () -> Boolean,
) {
    authenticatedComposable(
        route = Screen.ContactList.route,
        navController = navController,
        isSignedIn = isSignedIn,
        deepLinks = listOf(
            navDeepLink {
                uriPattern = RelateDeepLinks.Contacts.pattern
            }
        )
    ) {
        ContactListScreen(
            onContactClick = { contactId ->
                navController.navigate(Screen.ContactDetail.createRoute(contactId))
            }
        )
    }

    authenticatedComposable(
        route = Screen.ContactDetail.route,
        navController = navController,
        isSignedIn = isSignedIn,
        arguments = listOf(
            navArgument("contactId") { type = NavType.StringType },
            navArgument(Screen.ContactDetail.openPreferencesArg) {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
        deepLinks = listOf(
            navDeepLink {
                uriPattern = RelateDeepLinks.Contact.pattern
            }
        )
    ) { backStackEntry ->
        val contactId = RouteArgumentCodec.decode(backStackEntry.arguments?.getString("contactId"))
        val openPreferences = backStackEntry.arguments
            ?.getBoolean(Screen.ContactDetail.openPreferencesArg)
            ?: false
        ContactDetailScreen(
            contactId = contactId,
            openPreferencesOnStart = openPreferences,
            onBack = { navController.popBackStack() },
            onNavigateToWish = { pendingMessageId ->
                navController.navigate(Screen.WishPreview.createRoute(contactId, pendingMessageId))
            },
            onNavigateToMemoryVault = { cid ->
                navController.navigate(Screen.MemoryVault.createRoute(cid))
            },
            onNavigateToGiftAdvisor = { cid ->
                navController.navigate(Screen.GiftAdvisor.createRoute(cid))
            },
            onNavigateToChatHistory = { cid ->
                navController.navigate(Screen.ChatHistory.createRoute(cid))
            }
        )
    }

    authenticatedComposable(
        route = Screen.WishPreview.route,
        navController = navController,
        isSignedIn = isSignedIn,
        arguments = listOf(
            navArgument("contactId") { type = NavType.StringType },
            navArgument("messageRef") { type = NavType.StringType },
        ),
        deepLinks = listOf(
            navDeepLink {
                uriPattern = RelateDeepLinks.Wish.pattern
            }
        )
    ) { backStackEntry ->
        val contactId = RouteArgumentCodec.decode(backStackEntry.arguments?.getString("contactId"))
        val messageRef = RouteArgumentCodec.decode(backStackEntry.arguments?.getString("messageRef"))
        WishPreviewScreen(
            contactId = contactId,
            messageRef = messageRef,
            onBack = { navController.popBackStack() },
            onSent = {
                navController.popBackStack()
            },
            onReviewNext = { nextContactId, nextMessageRef ->
                navController.navigate(Screen.WishPreview.createRoute(nextContactId, nextMessageRef)) {
                    popUpTo(Screen.WishPreview.route) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    contactScopedDestinations(navController, isSignedIn)
}

private fun NavGraphBuilder.contactScopedDestinations(
    navController: NavHostController,
    isSignedIn: () -> Boolean,
) {
    authenticatedComposable(
        route = Screen.ChatHistory.route,
        navController = navController,
        isSignedIn = isSignedIn,
        arguments = listOf(navArgument("contactId") { type = NavType.StringType })
    ) {
        ChatHistoryScreen(
            onBack = { navController.popBackStack() }
        )
    }

    authenticatedComposable(
        route = Screen.MemoryVault.route,
        navController = navController,
        isSignedIn = isSignedIn,
        arguments = listOf(navArgument("contactId") { type = NavType.StringType })
    ) { backStackEntry ->
        val contactId = RouteArgumentCodec.decode(backStackEntry.arguments?.getString("contactId"))
        MemoryVaultScreen(
            contactId = contactId,
            onBack = { navController.popBackStack() }
        )
    }

    authenticatedComposable(
        route = Screen.GiftAdvisor.route,
        navController = navController,
        isSignedIn = isSignedIn,
        arguments = listOf(navArgument("contactId") { type = NavType.StringType })
    ) { backStackEntry ->
        val contactId = RouteArgumentCodec.decode(backStackEntry.arguments?.getString("contactId"))
        GiftAdvisorScreen(
            contactId = contactId,
            onBack = { navController.popBackStack() },
            onAdjustBudget = {
                navController.navigate(
                    Screen.ContactDetail.createRoute(
                        contactId = contactId,
                        openPreferences = true,
                    ),
                )
            },
        )
    }
}
