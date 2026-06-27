package com.example.ui.navigation

import com.example.domain.navigation.RelateDeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ShortcutContractTest {

    @Test
    fun staticShortcutsRouteToTheirOwnedScreens() {
        val shortcuts = parseXml(rootFile("app/src/main/res/xml/shortcuts.xml"))
            .getElementsByTagName("shortcut")
        val intentsByShortcutId = mutableMapOf<String, Element>()

        for (index in 0 until shortcuts.length) {
            val shortcut = shortcuts.item(index) as Element
            val shortcutId = shortcut.getAttributeNS(ANDROID_NS, "shortcutId")
            val intent = shortcut.getElementsByTagName("intent").item(0) as? Element
            assertNotNull("Shortcut $shortcutId must declare an intent", intent)
            intentsByShortcutId[shortcutId] = requireNotNull(intent)
        }

        assertShortcutIntent(
            intent = intentsByShortcutId.getValue("compose_message"),
            expectedUri = RelateDeepLinks.Messages.uri,
        )
        assertShortcutIntent(
            intent = intentsByShortcutId.getValue("view_contacts"),
            expectedUri = RelateDeepLinks.Contacts.uri,
        )
    }

    private fun assertShortcutIntent(intent: Element, expectedUri: String) {
        assertEquals("android.intent.action.VIEW", intent.getAttributeNS(ANDROID_NS, "action"))
        assertEquals(expectedUri, intent.getAttributeNS(ANDROID_NS, "data"))
        assertEquals("com.example.MainActivity", intent.getAttributeNS(ANDROID_NS, "targetClass"))
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
