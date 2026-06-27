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
            "RelateDeepLinks.Wish.pattern",
            "RelateDeepLinks.Settings.pattern",
            "RelateDeepLinks.BackupRestore.pattern",
        ).forEach { patternReference ->
            assertTrue("NavGraph must register $patternReference", source.contains("uriPattern = $patternReference"))
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
