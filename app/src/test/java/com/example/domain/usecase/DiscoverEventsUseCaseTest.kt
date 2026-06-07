package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DiscoverEventsUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val useCase = DiscoverEventsUseCase(contactRepository, eventRepository)

    @Test
    fun `invoke with no contacts returns zero discovered events`() = runTest {
        coEvery { contactRepository.getAllSync() } returns emptyList()

        val outcome = useCase()

        assertEquals(0, outcome.contacts)
        assertEquals(0, outcome.events)
        coVerify(exactly = 0) { eventRepository.upsert(any()) }
    }

    @Test
    fun `invoke with contacts having birthday, anniversary, and work anniversary generates events`() = runTest {
        val today = Calendar.getInstance()
        val birthdayDay = today.get(Calendar.DAY_OF_MONTH)
        val birthdayMonth = today.get(Calendar.MONTH) + 1

        val contact = ContactEntity(
            id = "c1",
            name = "Alice",
            birthdayDay = birthdayDay,
            birthdayMonth = birthdayMonth,
            birthdayYear = 1990,
            anniversaryDay = 10,
            anniversaryMonth = 5,
            anniversaryYear = 2015,
            workStartDay = 1,
            workStartMonth = 9,
            workStartYear = 2020
        )

        coEvery { contactRepository.getAllSync() } returns listOf(contact)

        val outcome = useCase()

        assertEquals(1, outcome.contacts)
        assertEquals(3, outcome.events) // Birthday, Anniversary, Work Anniversary

        coVerify(exactly = 3) { eventRepository.upsert(any()) }
    }
}
