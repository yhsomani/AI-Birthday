package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.service.AiService
import com.example.domain.service.ContactClassificationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifyContactUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val aiService: AiService = mockk(relaxed = true)
    private val useCase = ClassifyContactUseCase(contactRepository, aiService)

    @Test
    fun `invoke with missing contact returns ContactNotFound`() = runTest {
        coEvery { contactRepository.getById("c1") } returns null

        val result = useCase("c1")

        assertEquals(ClassifyContactUseCase.ClassificationOutcome.ContactNotFound, result)
    }

    @Test
    fun `invoke with already classified contact returns AlreadyClassified`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "John Doe",
            relationshipType = "FAMILY"
        )
        coEvery { contactRepository.getById("c1") } returns contact

        val result = useCase("c1")

        assertTrue(result is ClassifyContactUseCase.ClassificationOutcome.AlreadyClassified)
        assertEquals("FAMILY", (result as ClassifyContactUseCase.ClassificationOutcome.AlreadyClassified).type)
        coVerify(exactly = 0) { aiService.classifyContact(any()) }
    }

    @Test
    fun `invoke with unclassified contact performs classification and saves result`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "John Doe",
            relationshipType = "UNKNOWN"
        )
        val classificationResult = ContactClassificationResult(
            type = "FRIEND",
            subtype = "CLOSE",
            language = "en",
            formality = "CASUAL",
            communicationStyle = "EXPRESSIVE",
            confidence = 0.95
        )
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { aiService.classifyContact(contact) } returns classificationResult

        val result = useCase("c1")

        assertTrue(result is ClassifyContactUseCase.ClassificationOutcome.Classified)
        val classified = result as ClassifyContactUseCase.ClassificationOutcome.Classified
        assertEquals("FRIEND", classified.type)
        assertEquals(0.95, classified.confidence, 0.001)

        coVerify {
            contactRepository.updateClassification(
                id = "c1",
                type = "FRIEND",
                subtype = "CLOSE",
                lang = "en",
                formality = "CASUAL",
                style = "EXPRESSIVE"
            )
        }
    }
}
