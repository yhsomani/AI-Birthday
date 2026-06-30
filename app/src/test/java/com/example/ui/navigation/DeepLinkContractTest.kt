package com.example.ui.navigation

import com.example.domain.navigation.RelateDeepLinks
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
        ).forEach { host ->
            assertTrue("AndroidManifest.xml must declare relateai://$host", relateHosts.contains(host))
        }
    }

    @Test
    fun navGraphRegistersEveryExternalDeepLinkPatternThroughSharedContract() {
        val source = rootFile("app/src/main/java/com/example/ui/navigation/NavGraph.kt").readText()

        listOf(
            "RelateDeepLinks.Contact.pattern",
            "RelateDeepLinks.Home.pattern",
            "RelateDeepLinks.Contacts.pattern",
            "RelateDeepLinks.Messages.pattern",
            "RelateDeepLinks.Wish.pattern",
            "RelateDeepLinks.Settings.pattern",
            "RelateDeepLinks.BackupRestore.pattern",
        ).forEach { patternReference ->
            assertTrue("NavGraph must register $patternReference", source.contains("uriPattern = $patternReference"))
        }
    }

    @Test
    fun navGraphProtectsEverySignedInDestinationWithAuthGate() {
        val source = rootFile("app/src/main/java/com/example/ui/navigation/NavGraph.kt").readText()

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
    fun notificationHelperUsesSharedDeepLinksForRoutedNotifications() {
        val source = rootFile("core/data/src/main/kotlin/com/example/core/automation/notifications/NotificationHelper.kt").readText()

        listOf(
            "RelateDeepLinks.Wish.uri",
            "RelateDeepLinks.Contact.uri",
            "RelateDeepLinks.BackupRestore.uri",
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

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
