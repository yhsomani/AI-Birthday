package com.example.tools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperScriptsTest {

    @Test
    fun extractStringsScript_isRepoRootAware() {
        val script = sourceFile("scripts/extract_strings.sh").readText()

        assertFalse("extract_strings.sh must not hardcode /workspace", script.contains("/workspace"))
        assertTrue("extract_strings.sh should discover the git repository root", script.contains("git rev-parse --show-toplevel"))
        assertTrue("extract_strings.sh should use rg for portable source scanning", script.contains("rg -n"))
        assertTrue("extract_strings.sh should exclude build outputs", script.contains("!**/build/**"))
    }

    private fun sourceFile(rootRelativePath: String): File {
        return listOf(
            File(rootRelativePath),
            File("../$rootRelativePath"),
        ).firstOrNull { it.exists() }
            ?: error("Could not locate source file $rootRelativePath from ${File(".").absolutePath}")
    }
}
