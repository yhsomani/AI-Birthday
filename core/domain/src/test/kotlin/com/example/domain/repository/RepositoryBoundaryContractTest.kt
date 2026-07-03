package com.example.domain.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class RepositoryBoundaryContractTest {

    @Test
    fun repositoriesWithPureContracts_doNotExposeRoomEntityTypes() {
        listOf(
            "core/domain/src/main/kotlin/com/example/domain/repository/ActivityLogRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/MemoryNoteRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/GiftHistoryRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/MessageFeedbackRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/StyleProfileRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/ContactRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/MessageRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/DispatchAttemptRepository.kt",
            "core/domain/src/main/kotlin/com/example/domain/repository/EventRepository.kt",
        ).forEach { relativePath ->
            val source = rootFile(relativePath).readText()

            assertFalse(
                "$relativePath should expose pure domain models, not Room entities.",
                source.contains("com.example.core.db.entities") ||
                    source.contains("ActivityLogEntity") ||
                    source.contains("MemoryNoteEntity") ||
                    source.contains("GiftHistoryEntity") ||
                    source.contains("MessageFeedbackEntity") ||
                    source.contains("StyleProfileEntity") ||
                    source.contains("StyleProfileHistoryEntity") ||
                    source.contains("ContactEntity") ||
                    source.contains("PendingMessageEntity") ||
                    source.contains("SentMessageEntity") ||
                    source.contains("DispatchAttemptEntity") ||
                    source.contains("EventEntity"),
            )
        }
    }

    @Test
    fun contactRepository_doesNotExposePersistenceTypesOrUnusedRawEntityApis() {
        val source = rootFile(
            "core/domain/src/main/kotlin/com/example/domain/repository/ContactRepository.kt",
        ).readText()

        assertFalse(
            "ContactRepository should expose domain contact models, not unused DAO/raw entity APIs.",
            source.contains("com.example.core.db.entities") ||
                source.contains("com.example.core.db.dao") ||
                source.contains("ContactEntity") ||
                source.contains("RelationshipTypeCount") ||
                source.contains("getAllSync") ||
                source.contains("getById") ||
                source.contains("upsert(contact") ||
                source.contains("update(contact") ||
                source.contains("getContactsForRevival") ||
                source.contains("countByRelationshipType") ||
                source.contains("getTopByHealthScore") ||
                source.contains("getBottomByHealthScore") ||
                source.contains("fun getAll(): Flow<List<ContactEntity>>") ||
                source.contains("delete(contact: ContactEntity)"),
        )
    }

    @Test
    fun messageRepository_doesNotExposeMessageRoomEntities() {
        val source = rootFile(
            "core/domain/src/main/kotlin/com/example/domain/repository/MessageRepository.kt",
        ).readText()

        assertFalse(
            "MessageRepository message APIs should expose pure records, not Room entities.",
            source.contains("PendingMessageEntity") ||
                source.contains("SentMessageEntity") ||
                source.contains("fun getAllPending(): Flow<List<com.example.core.db.entities") ||
                source.contains("suspend fun getAllPendingSync(): List<com.example.core.db.entities") ||
                source.contains("suspend fun getPendingById(id: String): com.example.core.db.entities") ||
                source.contains("suspend fun insertPending(message: com.example.core.db.entities") ||
                source.contains("fun getAllSent(): Flow<List<com.example.core.db.entities") ||
                source.contains("suspend fun getSentByContact(contactId: String, limit: Int): List<com.example.core.db.entities") ||
                source.contains("fun getSentByContactFlow(contactId: String, limit: Int): Flow<List<com.example.core.db.entities") ||
                source.contains("suspend fun insertSent(message: com.example.core.db.entities"),
        )
    }

    @Test
    fun pureRepositoryPersistenceMappers_doNotLiveInDomainModule() {
        listOf(
            "core/domain/src/main/kotlin/com/example/domain/activity/ActivityLogMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/diagnostic/DiagnosticSnapshotMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/gift/GiftHistoryMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/memory/MemoryNoteMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/message/MessageFeedbackMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/style/StyleProfileMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/dispatch/DispatchAttemptMappers.kt",
            "core/domain/src/main/kotlin/com/example/domain/contact/ContactMappers.kt",
        ).forEach { relativePath ->
            assertFalse(
                "$relativePath should stay in core:data because it maps Room entities.",
                rootFileOrNull(relativePath)?.isFile == true,
            )
        }
    }

    @Test
    fun eventMappers_doNotDependOnRoomEntities() {
        val source = rootFile(
            "core/domain/src/main/kotlin/com/example/domain/event/EventMappers.kt",
        ).readText()

        assertFalse(
            "Domain event mappers should only map pure event models; EventEntity mapping belongs in core:data.",
            source.contains("com.example.core.db.entities") ||
                source.contains("EventEntity") ||
                source.contains("toEventEntity") ||
                source.contains("toOccasions"),
        )
    }

    @Test
    fun domainMainSources_doNotImportAndroidOrPersistenceFrameworks() {
        val contactRepositoryFile = rootFile(
            "core/domain/src/main/kotlin/com/example/domain/repository/ContactRepository.kt",
        )
        val sourceRoot = requireNotNull(
            contactRepositoryFile.parentFile?.parentFile?.parentFile?.parentFile?.parentFile,
        ) { "Could not resolve core/domain/src/main/kotlin from $contactRepositoryFile" }

        sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .forEach { file ->
                val source = file.readText()
                val relativePath = file.toRelativeString(sourceRoot)
                assertFalse(
                    "$relativePath should stay platform and persistence neutral.",
                    source.contains("import android.") ||
                        source.contains("import androidx.") ||
                        source.contains("com.example.core.db.entities") ||
                        source.contains("com.example.core.db.dao"),
                )
            }
    }

    private fun rootFile(relativePath: String): File {
        return requireNotNull(rootFileOrNull(relativePath)) { "Could not find $relativePath" }
    }

    private fun rootFileOrNull(relativePath: String): File? {
        val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { dir -> File(dir, "settings.gradle.kts").isFile }
            ?: error("Could not locate repository root from $start")
        val target = File(root, relativePath)
        return target.takeIf { it.isFile }
    }
}
