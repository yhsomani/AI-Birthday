package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
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
    fun manifest_usesScheduleExactAlarmWithoutUseExactAlarmEntitlement() {
        val manifest = parseXml(appFile("src/main/AndroidManifest.xml"))
        val permissions = manifest.getElementsByTagName("uses-permission")
        val declaredPermissions = mutableSetOf<String>()

        for (index in 0 until permissions.length) {
            val permission = permissions.item(index) as Element
            declaredPermissions += permission.getAttributeNS(ANDROID_NS, "name")
        }

        assertTrue(declaredPermissions.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertFalse(declaredPermissions.contains("android.permission.USE_EXACT_ALARM"))
    }

    @Test
    fun manifest_declaresWhatsAppPackageQueriesForAutomationDetection() {
        val manifest = parseXml(appFile("src/main/AndroidManifest.xml"))
        val packages = manifest.getElementsByTagName("package")
        val declaredPackages = mutableSetOf<String>()

        for (index in 0 until packages.length) {
            val packageElement = packages.item(index) as Element
            declaredPackages += packageElement.getAttributeNS(ANDROID_NS, "name")
        }

        assertTrue(declaredPackages.contains("com.whatsapp"))
        assertTrue(declaredPackages.contains("com.whatsapp.w4b"))
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
    fun networkSecurityPins_doNotExpireWithinReleaseGate() {
        val networkSecurityConfig = parseXml(appFile("src/main/res/xml/network_security_config.xml"))
        val pinSets = networkSecurityConfig.getElementsByTagName("pin-set")
        val expirationDates = mutableListOf<LocalDate>()

        for (index in 0 until pinSets.length) {
            val pinSet = pinSets.item(index) as Element
            expirationDates += LocalDate.parse(pinSet.getAttribute("expiration"))
        }

        assertTrue("network_security_config.xml must define certificate pin sets", expirationDates.isNotEmpty())

        val soonestExpiration = requireNotNull(expirationDates.minOrNull())
        assertEquals(SecurityChecks.certificatePinExpiryDate(), soonestExpiration)

        val daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), soonestExpiration)
        assertTrue(
            "Certificate pins expire in $daysUntilExpiry days on $soonestExpiration; update pins at least " +
                "${SecurityChecks.PIN_EXPIRY_RELEASE_GATE_DAYS} days before expiry.",
            daysUntilExpiry > SecurityChecks.PIN_EXPIRY_RELEASE_GATE_DAYS,
        )
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

        assertTrue(workflow.contains("contents: read"))
        assertTrue(workflow.contains("pull-requests: read"))
        assertTrue(workflow.contains("java-version: \"21\""))
        assertTrue(workflow.contains("./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache"))
        assertTrue(workflow.contains("Verify screenshot baselines"))
        assertTrue(
            workflow.contains(
                "./gradlew :app:verifyRoborazziDebug -Pscreenshot --tests 'com.example.ui.screenshots.*' --no-configuration-cache"
            )
        )
        assertTrue(workflow.contains("Verify release readiness guardrails"))
        assertTrue(
            workflow.contains(
                "./gradlew :app:testDebugUnitTest --tests com.example.ProductionReadinessConfigTest --no-configuration-cache"
            )
        )
        assertTrue(workflow.contains("./gradlew assembleRelease --no-configuration-cache"))
        assertTrue(workflow.contains("Release signing is not configured"))
        assertTrue(workflow.contains("KEYSTORE_PATH"))
        assertTrue(workflow.contains("STORE_PASSWORD"))
        assertTrue(workflow.contains("KEY_ALIAS"))
        assertTrue(workflow.contains("KEY_PASSWORD"))
        assertTrue(workflow.contains("actions/upload-artifact@v4"))
        assertTrue(workflow.contains("lint-reports"))
        assertTrue(workflow.contains("unit-test-reports"))
        assertTrue(workflow.contains("roborazzi-reports"))
        assertTrue(workflow.contains("app/build/reports/roborazzi/**"))
        assertTrue(workflow.contains("app/build/outputs/roborazzi/**"))
        assertTrue(workflow.contains("app/build/test-results/roborazzi/**"))
        assertTrue(workflow.contains("./gradlew jacocoDebugUnitTestReport --no-configuration-cache"))
        assertTrue(workflow.contains("coverage-reports"))
        assertTrue(workflow.contains("debug-apk"))
    }

    @Test
    fun rootGradle_exposesMeasuredCoverageReportTask() {
        val buildScript = File(projectRoot(), "build.gradle.kts").readText()

        assertTrue(buildScript.contains("tasks.register<JacocoReport>(\"jacocoDebugUnitTestReport\")"))
        assertTrue(buildScript.contains("coverageReportRequested"))
        assertTrue(buildScript.contains("xml.required.set(true)"))
        assertTrue(buildScript.contains("html.required.set(true)"))
        assertTrue(buildScript.contains("testDebugUnitTest"))
        assertTrue(buildScript.contains("intermediates/classes/debug/transformDebugClassesWithAsm/dirs"))
        assertTrue(buildScript.contains("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"))
        assertTrue(buildScript.contains("jacoco/testDebugUnitTest.exec"))
    }

    @Test
    fun authScreen_releaseCopyUsesStringResources() {
        val source = rootFile("app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt").readText()

        assertTrue(source.contains("stringResource(R.string.app_name)"))
        assertTrue(source.contains("stringResource(R.string.auth_subtitle)"))
        assertTrue(source.contains("stringResource(R.string.auth_sign_in_google)"))
        assertTrue(source.contains("stringResource(R.string.auth_legal_agreement)"))
        assertFalse(source.contains("auth_dev_bypass"))
        assertFalse(source.contains("onDevBypass"))
        assertFalse(source.contains("bypassSignIn"))
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

    private fun projectRoot(): File {
        return requireNotNull(rootFile("settings.gradle.kts").parentFile)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
