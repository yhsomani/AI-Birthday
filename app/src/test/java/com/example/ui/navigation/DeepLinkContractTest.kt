package com.example.ui.navigation

import com.example.domain.navigation.RelateDeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DeepLinkContractTest {

    @Test
    fun manifestDeclaresEveryExternalDeepLinkHost() {
        val manifest = parseXml(rootFile("app/src/main/AndroidManifest.xml"))
        val dataNodes = manifest.getElementsByTagName("data")
        val relateHosts = mutableSetOf<String>()

        for (index in 0 until dataNodes.length) {
            val data = dataNodes.item(index) as Element
            if (data.getAttributeNS(ANDROID_NS, "scheme") == RelateDeepLinks.SCHEME) {
                relateHosts += data.getAttributeNS(ANDROID_NS, "host")
            }
        }

        listOf(
            RelateDeepLinks.Contact.HOST,
            RelateDeepLinks.Home.HOST,
            RelateDeepLinks.Contacts.HOST,
            RelateDeepLinks.Messages.HOST,
            RelateDeepLinks.Wish.HOST,
            RelateDeepLinks.Settings.HOST,
            RelateDeepLinks.BackupRestore.HOST,
            RelateDeepLinks.AutomationSetup.HOST,
        ).forEach { host ->
            assertTrue("AndroidManifest.xml must declare relateai://$host", relateHosts.contains(host))
        }
    }

    @Test
    fun navGraphRegistersEveryExternalDeepLinkPatternThroughSharedContract() {
        val source = navigationSourceText()

        listOf(
            "RelateDeepLinks.Contact.pattern",
            "RelateDeepLinks.Home.pattern",
            "RelateDeepLinks.Contacts.pattern",
            "RelateDeepLinks.Messages.pattern",
            "RelateDeepLinks.Wish.pattern",
            "RelateDeepLinks.Settings.pattern",
            "RelateDeepLinks.BackupRestore.pattern",
            "RelateDeepLinks.AutomationSetup.pattern",
        ).forEach { patternReference ->
            assertTrue("NavGraph must register $patternReference", source.contains("uriPattern = $patternReference"))
        }
    }

    @Test
    fun navGraphProtectsEverySignedInDestinationWithAuthGate() {
        val source = navigationSourceText()

        listOf(
            "Screen.Home.route",
            "Screen.ContactList.route",
            "Screen.ContactDetail.route",
            "Screen.Events.route",
            "Screen.Messages.route",
            "Screen.Settings.route",
            "Screen.Analytics.route",
            "Screen.ActivityHistory.route",
            "Screen.WishPreview.route",
            "Screen.ChatHistory.route",
            "Screen.StyleCoach.route",
            "Screen.BackupRestore.route",
            "Screen.AutomationSetup.route",
            "Screen.MemoryVault.route",
            "Screen.GiftAdvisor.route",
        ).forEach { routeReference ->
            val authGateRegistration = Regex(
                pattern = """authenticatedComposable\(\s*route = ${Regex.escape(routeReference)}\b""",
                option = RegexOption.MULTILINE,
            )
            assertTrue(
                "NavGraph must register $routeReference through authenticatedComposable",
                authGateRegistration.containsMatchIn(source),
            )
        }
    }

    @Test
    fun mainActivityKeepsDeepLinkedNavGraphBehindBiometricGate() {
        val source = rootFile("app/src/main/java/com/example/MainActivity.kt").readText()
        val setContentBlock = source
            .substringAfter("setContent {")
            .substringBefore("override fun onResume")

        val unlockedBranch = Regex(
            pattern = """BiometricGateState\.Unlocked\s*->\s*\{\s*RelateApp\(""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
        val lockedBranch = Regex(
            pattern = """else\s*->\s*\{\s*BiometricLockGate\(""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
        val relateAppCalls = Regex("""\bRelateApp\(""").findAll(setContentBlock).count()

        assertTrue(
            "MainActivity must render RelateApp only after biometric state is unlocked",
            unlockedBranch.containsMatchIn(setContentBlock),
        )
        assertTrue(
            "MainActivity must render BiometricLockGate for locked/authenticating/error biometric states",
            lockedBranch.containsMatchIn(setContentBlock),
        )
        assertEquals(
            "MainActivity should not expose a second nav graph path outside the biometric gate",
            1,
            relateAppCalls,
        )
    }

    @Test
    fun appShellAllowsLocalOnlyModeThroughSignedInRouteGate() {
        val mainActivitySource = rootFile("app/src/main/java/com/example/MainActivity.kt").readText()
        val authManagerSource = rootFile("core/data/src/main/kotlin/com/example/core/auth/AuthManager.kt").readText()

        assertTrue(
            "MainActivity route gate must treat local-only mode as signed in",
            mainActivitySource.contains("preferencesRepository.isLocalOnlyModeEnabled()"),
        )
        assertTrue(
            "MainActivity must route app-shell preferences through PreferencesRepository",
            mainActivitySource.contains("PreferencesRepository") &&
                !mainActivitySource.contains("com.example.core.prefs.SecurePrefs"),
        )
        assertTrue(
            "MainActivity route gate must still allow Firebase authenticated users",
            mainActivitySource.contains("FirebaseAuth.getInstance().currentUser != null"),
        )
        assertTrue(
            "AuthManager.isSignedIn must include local-only mode for splash/auth flows",
            authManagerSource.contains("auth.currentUser != null || isLocalOnlyModeEnabled()"),
        )
    }

    @Test
    fun contactDetailRouteCanOpenPreferencesFromInternalActions() {
        assertEquals(
            "contacts/contact_1?openPreferences=true",
            Screen.ContactDetail.createRoute(contactId = "contact_1", openPreferences = true),
        )
        assertEquals(
            "contacts/contact_1",
            Screen.ContactDetail.createRoute(contactId = "contact_1"),
        )
    }

    @Test
    fun onboardingSetupChecklistRoutesThroughAuthToAutomationSetup() {
        val source = rootFile("app/src/main/java/com/example/ui/navigation/NavGraph.kt").readText()
        val onboardingBlock = source
            .substringAfter("composable(Screen.Onboarding.route)")
            .substringBefore("composable(Screen.Auth.route)")

        assertTrue(
            "Onboarding setup checklist action must be wired",
            onboardingBlock.contains("onOpenAutomationSetup = {"),
        )
        assertTrue(
            "Onboarding setup checklist must store Automation Setup as the post-auth target",
            onboardingBlock.contains("postAuthDestination = Screen.AutomationSetup.route"),
        )
        assertTrue(
            "Onboarding setup checklist must continue through Auth before opening signed-in setup",
            onboardingBlock.contains("navController.navigate(Screen.Auth.route)"),
        )
    }

    @Test
    fun messagesRoutesWireRecoverySetupActionsToAutomationSetup() {
        val source = navigationSourceText()
        val messagesDestinationBlock = source
            .substringAfter("private fun MessagesDestination(")
            .substringBefore("private fun String.toMessageChannelFilter")

        listOf(
            "Screen.Messages.route",
            "Screen.Messages.filteredRoute",
        ).forEach { routeReference ->
            val routeBlock = source.authenticatedRouteBlock(routeReference)

            assertTrue(
                "$routeReference must render the shared messages destination",
                routeBlock.contains("MessagesDestination("),
            )
        }

        assertTrue(
            "Messages destination must render MessagesScreen",
            messagesDestinationBlock.contains("MessagesScreen("),
        )
        assertTrue(
            "Messages destination must expose the Messages recovery setup callback",
            messagesDestinationBlock.contains("onNavigateToAutomationSetup = {"),
        )
        assertTrue(
            "Messages destination must route failed-send recovery setup actions to Automation Setup",
            messagesDestinationBlock.contains("navController.navigate(Screen.AutomationSetup.route)"),
        )
    }

    @Test
    fun notificationHelperUsesSharedDeepLinksForRoutedNotifications() {
        val source = rootFile("core/data/src/main/kotlin/com/example/core/automation/notifications/NotificationHelper.kt").readText()

        listOf(
            "RelateDeepLinks.Wish.uri",
            "RelateDeepLinks.Contact.uri",
            "RelateDeepLinks.BackupRestore.uri",
            "RelateDeepLinks.AutomationSetup.uri",
        ).forEach { builderReference ->
            assertTrue("NotificationHelper must use $builderReference", source.contains(builderReference))
        }
    }

    @Test
    fun birthdayWidgetUsesSharedDeepLinksForClickThrough() {
        val source = rootFile("app/src/main/java/com/example/widget/BirthdayWidgetProvider.kt").readText()

        listOf(
            "RelateDeepLinks.Home.uri",
            "RelateDeepLinks.Messages.uri",
        ).forEach { builderReference ->
            assertTrue("BirthdayWidgetProvider must use $builderReference", source.contains(builderReference))
        }
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun rootFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val candidates = generateSequence(start) { it.parentFile }
            .map { dir -> File(dir, relativePath) }

        return candidates.firstOrNull { it.isFile }
            ?: error("Could not find source file: $relativePath from $start")
    }

    private fun navigationSourceText(): String {
        val navigationDir = requireNotNull(
            rootFile("app/src/main/java/com/example/ui/navigation/NavGraph.kt").parentFile
        )
        return navigationDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .joinToString(separator = "\n") { it.readText() }
    }

    private fun String.authenticatedRouteBlock(routeReference: String): String {
        val routeStart = indexOf("route = $routeReference")
        require(routeStart >= 0) { "Could not find authenticated route block for $routeReference" }

        val nextRouteStart = indexOf("authenticatedComposable(", startIndex = routeStart + routeReference.length)
            .takeIf { it >= 0 }
            ?: length
        return substring(routeStart, nextRouteStart)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
