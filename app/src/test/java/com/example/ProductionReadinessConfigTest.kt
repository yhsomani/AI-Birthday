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

    @Test
    fun ciWorkflow_keepsReleaseReadinessGuardrails() {
        val workflow = rootFile(".github/workflows/android.yml").readText()

        assertTrue(workflow.contains("java-version: \"21\""))
        assertTrue(workflow.contains("./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache"))
        assertTrue(workflow.contains("./gradlew assembleRelease --no-configuration-cache"))
        assertTrue(workflow.contains("Release signing is not configured"))
        assertTrue(workflow.contains("KEYSTORE_PATH"))
        assertTrue(workflow.contains("STORE_PASSWORD"))
        assertTrue(workflow.contains("KEY_ALIAS"))
        assertTrue(workflow.contains("KEY_PASSWORD"))
        assertTrue(workflow.contains("actions/upload-artifact@v4"))
        assertTrue(workflow.contains("lint-reports"))
        assertTrue(workflow.contains("unit-test-reports"))
        assertTrue(workflow.contains("debug-apk"))
    }

    @Test
    fun authScreen_releaseCopyUsesStringResources() {
        val source = rootFile("app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt").readText()

        assertTrue(source.contains("stringResource(R.string.app_name)"))
        assertTrue(source.contains("stringResource(R.string.auth_subtitle)"))
        assertTrue(source.contains("stringResource(R.string.auth_sign_in_google)"))
        assertTrue(source.contains("stringResource(R.string.auth_dev_bypass)"))
        assertTrue(source.contains("stringResource(R.string.auth_legal_agreement)"))
        assertFalse(source.contains("Nurture your connections"))
        assertFalse(source.contains("Sign in with Google"))
        assertFalse(source.contains("Bypass Sign-In (Dev)"))
        assertFalse(source.contains("By signing in, you agree"))
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun appFile(relativePath: String): File {
        return rootFile("app/$relativePath")
    }

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
