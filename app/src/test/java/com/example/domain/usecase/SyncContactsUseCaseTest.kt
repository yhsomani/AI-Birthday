package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.service.ContactSyncService
import com.example.domain.service.PreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncContactsUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val contactSyncService: ContactSyncService = mockk(relaxed = true)
    private val discoverEventsUseCase: DiscoverEventsUseCase = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)

    private val useCase = SyncContactsUseCase(
        contactRepository,
        contactSyncService,
        discoverEventsUseCase,
        preferencesRepository
    )

    @Test
    fun `invoke in guest mode with empty merged contacts inserts mock contacts`() = runTest {
        coEvery { contactSyncService.fetchGoogleContacts() } returns emptyList()
        coEvery { preferencesRepository.isGuestMode() } returns true
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(0, outcome.googleCount)
        assertEquals(0, outcome.deviceCount)
        assertEquals(4, outcome.inserted) // Mock contacts list size is 4
        coVerify(exactly = 4) { contactRepository.upsert(any()) }
        coVerify { discoverEventsUseCase() }
    }

    @Test
    fun `invoke processes google contacts and deletes mock contacts if not in guest mode`() = runTest {
        val googleContact = ContactEntity(id = "g1", name = "Alice", googleContactId = "google_1", primaryEmail = "alice@gmail.com")
        val existingMock = ContactEntity(id = "mock_amit", name = "Amit")

        coEvery { contactSyncService.fetchGoogleContacts() } returns listOf(googleContact)
        coEvery { preferencesRepository.isGuestMode() } returns false
        coEvery { contactRepository.getAllSync() } returns listOf(existingMock)
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(1, outcome.googleCount)
        assertEquals(0, outcome.deviceCount)
        assertEquals(1, outcome.inserted)
        coVerify { contactRepository.delete(existingMock) }
        coVerify { contactRepository.upsert(match { it.name == "Alice" && it.googleContactId == "google_1" && it.primaryEmail == "alice@gmail.com" }) }
        coVerify { discoverEventsUseCase() }
    }
}
