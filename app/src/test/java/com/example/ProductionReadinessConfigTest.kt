package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ProductionReadinessConfigTest {

    @Test
    fun manifest_disablesAndroidAutoBackup() {
        val manifest = parseXml(appFile("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals(
            "false",
            application.getAttributeNS(ANDROID_NS, "allowBackup"),
        )
    }

    @Test
    fun backupRuleFiles_excludeSensitiveStoresIfBackupIsReenabled() {
        val dataExtractionRules = appFile("src/main/res/xml/data_extraction_rules.xml").readText()
        val legacyBackupRules = appFile("src/main/res/xml/backup_rules.xml").readText()
        val sensitivePaths = listOf(
            "relateai.db",
            "relateai.db-wal",
            "relateai.db-shm",
            "relateai_auth_prefs.xml",
            "relateai_config_prefs.xml",
            "relateai_db_meta_secure.xml",
            "relateai_db_meta.xml",
        )

        sensitivePaths.forEach { path ->
            assertTrue("data_extraction_rules.xml must exclude $path", dataExtractionRules.contains("path=\"$path\""))
            assertTrue("backup_rules.xml must exclude $path", legacyBackupRules.contains("path=\"$path\""))
        }
    }

    @Test
    fun releaseBuild_doesNotFallBackToDebugSigning() {
        val buildScript = appFile("build.gradle.kts").readText()

        assertFalse(buildScript.contains("signingConfigs.getByName(\"debug\")"))
        assertFalse(buildScript.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(buildScript.contains("Release signing is not configured"))
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun appFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val candidates = generateSequence(start) { it.parentFile }
            .flatMap { dir ->
                sequenceOf(
                    File(dir, "app/$relativePath"),
                    File(dir, relativePath),
                )
            }

        return candidates.firstOrNull { it.isFile }
            ?: error("Could not find app source file: $relativePath from $start")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
