package com.example.domain.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class RepositoryBoundaryContractTest {

    @Test
    fun repositoriesWithPureContracts_doNotExposeRoomEntityTypes() {
        listOf(
            "core/domain/src/main/kotlin/com/example/domain/repository/MemoryNoteRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/GiftHistoryRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/MessageFeedbackRepository.kt",
        ).forEach { relativePath ->
            val source = rootFile(relativePath).readText()

            assertFalse(
                "$relativePath should expose pure domain models, not Room entities.",
                source.contains("com.example.core.db.entities") ||
                    source.contains("MemoryNoteEntity") ||
                    source.contains("GiftHistoryEntity") ||
                    source.contains("MessageFeedbackEntity"),
            )
        }
    }

    private fun rootFile(relativePath: String): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { dir -> File(dir, "settings.gradle.kts").isFile }
            ?: error("Could not locate repository root from $start")
        val target = File(root, relativePath)
        require(target.isFile) { "Could not find $relativePath from repository root $root" }
        return target
    }
}
