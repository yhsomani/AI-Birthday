package com.example.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityLabelsRegressionTest {

    @Test
    fun iconOnlyActionsInCleanedScreens_haveScreenReaderLabels() {
        val offenders = CLEANED_ACTION_SOURCES.flatMap { path ->
            val file = sourceFile(path)
            val text = file.readText()
            listOf("IconButton", "FloatingActionButton").flatMap { callName ->
                findActionBlocks(text, callName).filter { block ->
                    !block.body.contains("contentDescription =") ||
                        block.body.contains(Regex("contentDescription\\s*=\\s*null"))
                }.map { block ->
                    "${file.path}:${lineNumber(text, block.offset)} $callName lacks a non-null contentDescription"
                }
            }
        }

        assertTrue(
            "Icon-only actions in cleaned screens must expose screen reader labels:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    private fun findActionBlocks(text: String, callName: String): List<ActionBlock> {
        val blocks = mutableListOf<ActionBlock>()
        var searchFrom = 0
        val needle = "$callName("
        while (true) {
            val callStart = text.indexOf(needle, startIndex = searchFrom)
            if (callStart == -1) break
            val paramsEnd = findMatchingDelimiter(
                text = text,
                start = callStart + callName.length,
                open = '(',
                close = ')',
            )
            if (paramsEnd == -1) {
                searchFrom = callStart + needle.length
                continue
            }
            val bodyStart = text.indexOf('{', startIndex = paramsEnd)
            if (bodyStart == -1) {
                searchFrom = paramsEnd + 1
                continue
            }
            val bodyEnd = findMatchingDelimiter(text, bodyStart, open = '{', close = '}')
            if (bodyEnd == -1) {
                searchFrom = bodyStart + 1
                continue
            }
            blocks += ActionBlock(offset = callStart, body = text.substring(bodyStart, bodyEnd + 1))
            searchFrom = bodyEnd + 1
        }
        return blocks
    }

    private fun findMatchingDelimiter(
        text: String,
        start: Int,
        open: Char,
        close: Char,
    ): Int {
        if (start !in text.indices || text[start] != open) return -1
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                open -> depth += 1
                close -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun sourceFile(rootRelativePath: String): File {
        return listOf(
            File(rootRelativePath),
            File("../$rootRelativePath"),
            File(rootRelativePath.removePrefix("app/")),
        ).firstOrNull { it.exists() }
            ?: error("Could not locate source file $rootRelativePath from ${File(".").absolutePath}")
    }

    private fun lineNumber(text: String, offset: Int): Int {
        return text.substring(0, offset).count { it == '\n' } + 1
    }

    private data class ActionBlock(
        val offset: Int,
        val body: String,
    )

    private companion object {
        val CLEANED_ACTION_SOURCES = listOf(
            "app/src/main/java/com/example/ui/components/SyncErrorCard.kt",
            "app/src/main/java/com/example/ui/screens/analytics/AnalyticsScreen.kt",
            "app/src/main/java/com/example/ui/screens/backup/BackupRestoreScreen.kt",
            "app/src/main/java/com/example/ui/screens/contacts/ContactDetailScreen.kt",
            "app/src/main/java/com/example/ui/screens/contacts/ContactListScreen.kt",
            "app/src/main/java/com/example/ui/screens/events/EventsScreen.kt",
            "app/src/main/java/com/example/ui/screens/giftadvisor/GiftAdvisorScreen.kt",
            "app/src/main/java/com/example/ui/screens/home/HomeScreen.kt",
            "app/src/main/java/com/example/ui/screens/memoryvault/MemoryVaultScreen.kt",
            "app/src/main/java/com/example/ui/screens/messages/MessagesScreen.kt",
            "app/src/main/java/com/example/ui/screens/stylecoach/StyleCoachScreen.kt",
            "app/src/main/java/com/example/ui/screens/wish/WishPreviewScreen.kt",
        )
    }
}
