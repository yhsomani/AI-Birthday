package com.example

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
    fun startupIdeaDocs_areExplicitlyArchivedWhenPresent() {
        val startupIdeaDir = rootFile("docs/startup-idea", mustBeFile = false)
        if (!startupIdeaDir.exists()) return

        val markdownFiles = startupIdeaDir.listFiles { file -> file.extension == "md" }.orEmpty()
        assertFalse("startup-idea docs should be removed or explicitly marked archived", markdownFiles.isEmpty())

        markdownFiles.forEach { file ->
            val text = file.readText()
            assertTrue(
                "${file.name} must not look like an active implementation contract",
                text.contains("Archived reference note") &&
                    text.contains("not the implemented RelateAI Android product"),
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

    private fun rootFile(relativePath: String, mustBeFile: Boolean = true): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { dir -> File(dir, "settings.gradle.kts").isFile }
            ?: error("Could not locate repository root from $start")
        val target = File(root, relativePath)

        if (if (mustBeFile) target.isFile else target.exists()) {
            return target
        }
        error("Could not find $relativePath from repository root $root")
    }
}
