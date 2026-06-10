package com.example.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationParityTest {

    @Test
    fun appStrings_haveHindiParity() {
        assertStringKeyParity(
            englishPath = "app/src/main/res/values/strings.xml",
            hindiPath = "app/src/main/res/values-hi/strings.xml",
        )
    }

    @Test
    fun coreDataStrings_haveHindiParity() {
        assertStringKeyParity(
            englishPath = "core/data/src/main/res/values/strings.xml",
            hindiPath = "core/data/src/main/res/values-hi/strings.xml",
        )
    }

    private fun assertStringKeyParity(englishPath: String, hindiPath: String) {
        val englishKeys = stringKeys(sourceFile(englishPath))
        val hindiKeys = stringKeys(sourceFile(hindiPath))

        assertEquals(
            "Hindi string resources must contain the same keys as English resources for $englishPath",
            englishKeys,
            hindiKeys,
        )
    }

    private fun stringKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                add(nodes.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }

    private fun sourceFile(rootRelativePath: String): File {
        return listOf(
            File(rootRelativePath),
            File("../$rootRelativePath"),
        ).firstOrNull { it.exists() }
            ?: error("Could not locate source file $rootRelativePath from ${File(".").absolutePath}")
    }
}
