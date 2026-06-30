package com.example.domain.usecase

import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.service.ContactSyncService
import com.example.domain.service.DeviceContactsPermissionDeniedException
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
    fun `invoke with empty fetched contacts inserts nothing`() = runTest {
        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns emptyList()
        coEvery { contactSyncService.fetchDeviceContacts() } returns emptyList()
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(0, outcome.googleCount)
        assertEquals(0, outcome.deviceCount)
        assertEquals(0, outcome.inserted)
        coVerify(exactly = 0) { contactRepository.upsert(any()) }
        coVerify { discoverEventsUseCase() }
    }

    @Test
    fun `invoke processes google contacts from provider`() = runTest {
        val googleContact = ContactSyncRecord(
            id = "g1",
            displayName = "Alice",
            googleContactId = "google_1",
            primaryEmail = "alice@gmail.com",
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns listOf(googleContact)
        coEvery { contactSyncService.fetchDeviceContacts() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(1, outcome.googleCount)
        assertEquals(0, outcome.deviceCount)
        assertEquals(1, outcome.inserted)
        coVerify { contactRepository.upsert(match { it.name == "Alice" && it.googleContactId == "google_1" && it.primaryEmail == "alice@gmail.com" }) }
        coVerify { discoverEventsUseCase() }
    }

    @Test
    fun `invoke imports device contacts when Google has no contacts`() = runTest {
        val deviceContact = ContactSyncRecord(
            id = "device_1",
            displayName = "Devika Rao",
            primaryPhone = "+91 99999 00000",
            contactGroup = "Device",
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns emptyList()
        coEvery { contactSyncService.fetchDeviceContacts() } returns listOf(deviceContact)
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(0, outcome.googleCount)
        assertEquals(1, outcome.deviceCount)
        assertEquals(1, outcome.inserted)
        coVerify { contactRepository.upsert(match { it.id == "device_1" && it.contactGroup == "Device" }) }
    }

    @Test
    fun `invoke keeps device contacts when Google sync fails`() = runTest {
        val deviceContact = ContactSyncRecord(
            id = "device_2",
            displayName = "Rohan Mehta",
            primaryEmail = "rohan@example.com",
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } throws RuntimeException("auth expired")
        coEvery { contactSyncService.fetchDeviceContacts() } returns listOf(deviceContact)
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(0, outcome.googleCount)
        assertEquals(1, outcome.deviceCount)
        assertEquals(1, outcome.inserted)
        coVerify { preferencesRepository.setLastSyncError("Google sync failed; imported 1 device contacts.") }
        coVerify { contactRepository.upsert(match { it.id == "device_2" }) }
    }

    @Test
    fun `invoke returns device permission outcome when phone contacts permission is denied`() = runTest {
        val googleContact = ContactSyncRecord(
            id = "google_1",
            displayName = "Anaya Shah",
            googleContactId = "people/c1",
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns listOf(googleContact)
        coEvery { contactSyncService.fetchDeviceContacts() } throws DeviceContactsPermissionDeniedException()
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(1, outcome.googleCount)
        assertEquals(0, outcome.deviceCount)
        assertEquals(true, outcome.deviceContactsPermissionDenied)
        coVerify {
            preferencesRepository.setLastSyncError(SyncContactsUseCase.DEVICE_CONTACTS_PERMISSION_ERROR)
        }
        coVerify { contactRepository.upsert(match { it.id == "google_1" }) }
    }

    @Test
    fun `invoke merges duplicate Google and device contacts while keeping Google identity`() = runTest {
        val googleContact = ContactSyncRecord(
            id = "google_1",
            displayName = "Anaya Shah",
            googleContactId = "people/c1",
            primaryEmail = "anaya@example.com",
        )
        val deviceContact = ContactSyncRecord(
            id = "device_1",
            displayName = "Anaya Shah",
            primaryEmail = "ANAYA@example.com",
            primaryPhone = "+91 88888 77777",
            birthdayDay = 9,
            birthdayMonth = 4,
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns listOf(googleContact)
        coEvery { contactSyncService.fetchDeviceContacts() } returns listOf(deviceContact)
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        val outcome = useCase()

        assertEquals(1, outcome.googleCount)
        assertEquals(1, outcome.deviceCount)
        assertEquals(1, outcome.inserted)
        coVerify {
            contactRepository.upsert(match {
                it.id == "google_1" &&
                    it.googleContactId == "people/c1" &&
                    it.primaryPhone == "+91 88888 77777" &&
                    it.birthdayDay == 9 &&
                    it.birthdayMonth == 4
            })
        }
    }

    @Test
    fun `invoke maps relationship from semantic contact group`() = runTest {
        val googleContact = ContactSyncRecord(
            id = "google_2",
            displayName = "Maya Nair",
            relationshipType = "UNKNOWN",
            contactGroup = "College Friends",
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns listOf(googleContact)
        coEvery { contactSyncService.fetchDeviceContacts() } returns emptyList()
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        useCase()

        coVerify {
            contactRepository.upsert(match {
                it.id == "google_2" &&
                    it.relationshipType == "FRIEND"
            })
        }
    }

    @Test
    fun `invoke leaves generic device group unknown for AI classification`() = runTest {
        val deviceContact = ContactSyncRecord(
            id = "device_3",
            displayName = "Ishaan Roy",
            relationshipType = "UNKNOWN",
            contactGroup = "Device",
        )

        coEvery { contactSyncService.fetchGoogleContacts(any()) } returns emptyList()
        coEvery { contactSyncService.fetchDeviceContacts() } returns listOf(deviceContact)
        coEvery { contactRepository.getAllSync() } returns emptyList()
        coEvery { contactRepository.getById(any()) } returns null

        useCase()

        coVerify {
            contactRepository.upsert(match {
                it.id == "device_3" &&
                    it.relationshipType == "UNKNOWN"
            })
        }
    }
}
