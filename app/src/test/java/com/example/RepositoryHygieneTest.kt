package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositoryHygieneTest {

    @Test
    fun gitignore_excludesLocalGeneratedArtifactsButKeepsApprovedScreenshotBaselines() {
        val gitignore = rootFile(".gitignore").readText()
        val gitignoreLines = gitignore.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        listOf(
            ".vscode/",
            ".codepulse/",
            ".intelligence/",
            ".gradle-user-home/",
            "app_logs*.txt",
            "logcat*.txt",
            "lint_baseline_pre_fixes.txt",
            "patch_*.py",
        ).forEach { pattern ->
            assertTrue(".gitignore should ignore local artifact pattern $pattern", gitignore.contains(pattern))
        }

        listOf("*.png", "*.jpg", "*.jpeg", "*.gif", "*.svg", "*.webp").forEach { pattern ->
            assertFalse(
                ".gitignore should not hide app asset changes with global media pattern $pattern",
                gitignoreLines.contains(pattern),
            )
        }

        listOf(
            "/reports/",
            "/exports/",
            "/tmp/",
            "/app/build/reports/",
            "/app/build/outputs/",
            "/core/**/build/reports/",
            "/core/**/build/outputs/",
            "/app/src/test/screenshots/diff/",
            "/app/src/test/screenshots/output/",
        ).forEach { pattern ->
            assertTrue(".gitignore should scope generated media/output ignores with $pattern", gitignore.contains(pattern))
        }

        assertTrue(gitignore.contains("!/app/src/test/screenshots/baseline/"))
        assertTrue(gitignore.contains("!/app/src/test/screenshots/baseline/*.png"))
    }

    @Test
    fun providerConfigs_areAllowlistedAndDoNotContainServerSecrets() {
        val gitignore = rootFile(".gitignore").readText()
        val gitignoreLines = gitignore.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        assertTrue(gitignoreLines.contains("google-services.json"))
        assertTrue(gitignoreLines.contains("!/app/google-services.json"))
        assertTrue(gitignoreLines.contains("!/app/src/debug/google-services.json"))

        val forbiddenMarkers = listOf(
            "\"private_key\"",
            "\"private_key_id\"",
            "\"client_secret\"",
            "\"refresh_token\"",
            "\"type\": \"service_account\"",
            "\"type\":\"service_account\"",
        )

        listOf(
            "app/google-services.json",
            "app/src/debug/google-services.json",
        ).forEach { relativePath ->
            val config = rootFile(relativePath)
            val text = config.readText()

            forbiddenMarkers.forEach { marker ->
                assertFalse(
                    "$relativePath must not contain server-side secret marker $marker",
                    text.contains(marker),
                )
            }
        }
    }

    @Test
    fun activeDocs_areLimitedToReviewedSourceOfTruthAndSupportingReferences() {
        val docsRoot = rootFile("docs", mustBeFile = false)
        val repoRoot = requireNotNull(docsRoot.parentFile)
        val approvedDocs = setOf(
            "docs/README.md",
            "docs/feature-fssot.md",
            "docs/architecture/adr/0001-domain-purity-and-module-boundaries.md",
            "docs/architecture/adr/0002-occasion-model.md",
            "docs/architecture/adr/0003-durable-dispatch-attempts.md",
            "docs/architecture/adr/0004-database-keying-and-backup-recovery.md",
            "docs/design/design-system.md",
            "docs/operations/release-checklist.md",
            "docs/security/privacy-and-permissions.md",
        )
        val actualDocs = docsRoot.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { file -> file.relativeTo(repoRoot).path.replace(File.separatorChar, '/') }
            .toSet()

        assertEquals(
            "Active docs should stay limited to the reviewed SSOT support set",
            approvedDocs,
            actualDocs,
        )
    }

    @Test
    fun startupIdeaDocs_areNotKeptInTheActiveProductRepository() {
        val startupIdeaDir = rootFile("docs/startup-idea", mustBeFile = false)

        assertFalse(
            "Separate startup-idea docs should not live in the active RelateAI Android repository",
            startupIdeaDir.exists(),
        )
    }

    @Test
    fun supersededHistoricalDocs_areNotKeptInTheActiveProductRepository() {
        listOf(
            "PRODUCT_UX_WORKFLOW_TECHNICAL_ANALYSIS.md",
            "CODEBASE_AUDIT_REPORT_2026-07-01.md",
            "CODEBASE_AUDIT_REPORT_2026-07-03.md",
            "IMPLEMENTATION_PROGRESS.md",
            "PLAN.md",
            "PRODUCT_BLUEPRINT.md",
            "IMPLEMENTATION_TASKS.md",
            "docs/user/complete-user-guide.md",
            "docs/security/dependency-review.md",
            "docs/testing/test-strategy.md",
            "docs/testing/screenshot-strategy.md",
            "docs/user/backup-restore.md",
            "docs/design/ux-audit-checklist.md",
            "docs/architecture/target-room-schema.md",
        ).forEach { relativePath ->
            val historicalDoc = rootFile(relativePath, mustBeFile = false)

            assertFalse(
                "$relativePath is historical and should stay outside the active product repository",
                historicalDoc.exists(),
            )
        }
    }

    @Test
    fun removedRetrofitAndPagingStack_hasNoBuildOrProguardReferences() {
        val checkedFiles = listOf(
            "app/build.gradle.kts",
            "core/data/build.gradle.kts",
            "core/domain/build.gradle.kts",
            "gradle/libs.versions.toml",
            "app/proguard-rules.pro",
        )
        val removedReferences = listOf(
            "retrofit",
            "converter.moshi",
            "logging.interceptor",
            "androidx.paging",
            "room.paging",
            "secrets-gradle-plugin",
            "libs.plugins.secrets",
            "secretsGradlePlugin",
        )

        checkedFiles.forEach { relativePath ->
            val text = rootFile(relativePath).readText()
            removedReferences.forEach { reference ->
                assertFalse(
                    "$relativePath should not keep unused dependency reference $reference",
                    text.contains(reference),
                )
            }
        }
    }

    @Test
    fun appBuild_doesNotDeclareDataLayerOnlyRuntimeDependencies() {
        val appBuild = rootFile("app/build.gradle.kts").readText()
        val dataOwnedRuntimeDependencies = listOf(
            "implementation(libs.androidx.sqlite.ktx)",
            "implementation(libs.androidx.security.crypto)",
            "implementation(libs.androidx.biometric)",
            "implementation(libs.sun.mail.android)",
            "implementation(libs.sun.mail.activation)",
            "implementation(libs.sqlcipher)",
            "implementation(libs.moshi.kotlin)",
            "implementation(libs.okhttp)",
            "implementation(libs.google.material)",
            "implementation(libs.firebase.analytics)",
            "implementation(libs.firebase.vertexai)",
            "implementation(libs.androidx.room.runtime)",
        )

        dataOwnedRuntimeDependencies.forEach { dependency ->
            assertFalse(
                "app/build.gradle.kts should not declare app-level runtime dependency $dependency",
                appBuild.contains(dependency),
            )
        }

        assertTrue(
            "JavaMail remains test-only because app unit tests verify provider exception handling",
            appBuild.contains("testImplementation(libs.sun.mail.android)"),
        )
    }

    @Test
    fun appBuild_doesNotExportRoomSchemasWithoutOwningRoomEntities() {
        val appBuild = rootFile("app/build.gradle.kts").readText()
        val dataBuild = rootFile("core/data/build.gradle.kts").readText()
        val legacyAppSchemas = rootFile("app/schemas", mustBeFile = false)

        assertFalse(
            "app/build.gradle.kts should not configure Room schema export without Room entities",
            appBuild.contains("room.schemaLocation"),
        )
        assertFalse(
            "app/build.gradle.kts should not apply the Room compiler; core:data owns Room",
            appBuild.contains("ksp(libs.androidx.room.compiler)"),
        )
        assertTrue(
            "core:data should continue to export Room schemas for the database it owns",
            dataBuild.contains("room.schemaLocation"),
        )
        assertFalse(
            "app/schemas should not exist because core:data owns active Room schema exports",
            legacyAppSchemas.exists(),
        )
    }

    @Test
    fun manifest_doesNotRequestForegroundServicePermissionsWithoutForegroundService() {
        val manifest = rootFile("app/src/main/AndroidManifest.xml").readText()

        listOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        ).forEach { permission ->
            assertFalse(
                "AndroidManifest.xml should not request $permission without a foreground service",
                manifest.contains(permission),
            )
        }
    }

    private fun rootFile(relativePath: String, mustBeFile: Boolean = true): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { dir -> File(dir, "settings.gradle.kts").isFile }
            ?: error("Could not locate repository root from $start")
        val target = File(root, relativePath)

        if (!mustBeFile && !target.exists()) {
            return target
        }
        if (if (mustBeFile) target.isFile else target.exists()) {
            return target
        }
        error("Could not find $relativePath from repository root $root")
    }
}
