package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.service.ContactSyncService
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronises contacts from Google People API and the device address book.
 * - Fetches Google contacts (with syncToken for incremental sync)
 * - Fetches device contacts
 * - Deduplicates by contact name (Google contacts win on conflicts)
 * - Upserts into the local database
 */
@Singleton
class SyncContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val contactSyncService: ContactSyncService,
    private val discoverEventsUseCase: DiscoverEventsUseCase,
    private val preferencesRepository: com.example.domain.service.PreferencesRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): SyncOutcome {
        val isGuest = preferencesRepository.isGuestMode()

        // Proactively clear mock contacts if the user is not in Guest Mode.
        // This prevents showing dummy data if the Google Contacts sync subsequently fails.
        if (!isGuest) {
            try {
                val allExisting = contactRepository.getAllSync()
                allExisting.forEach { existing ->
                    if (existing.id.startsWith("mock_")) {
                        contactRepository.delete(existing)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SyncContactsUseCase", "Failed to clear mock contacts", e)
            }
        }

        var googleContacts = emptyList<ContactEntity>()
        var googleError: String? = null
        try {
            googleContacts = contactSyncService.fetchGoogleContacts(forceRefresh)
            preferencesRepository.setLastSyncError(null)
        } catch (e: Exception) {
            android.util.Log.e("SyncContactsUseCase", "Google contacts sync failed", e)
            googleError = e.message ?: "Failed to fetch Google contacts"
            preferencesRepository.setLastSyncError(googleError)
        }

        if (googleError != null) {
            throw Exception("Google contacts sync failed: $googleError")
        }

        var merged = googleContacts

        if (merged.isEmpty() && isGuest) {
            merged = getMockContacts()
        }

        var inserted = 0
        var updated = 0
        merged.forEach { contact ->
            val existing = contactRepository.getById(contact.id)
            contactRepository.upsert(contact)
            if (existing == null) inserted++ else updated++
        }

        // Run event discovery immediately so events are available in the database
        discoverEventsUseCase()

        return SyncOutcome(googleCount = googleContacts.size, deviceCount = 0, inserted = inserted, updated = updated)
    }

    private fun getMockContacts(): List<ContactEntity> {
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dayAfterTomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
        val nextWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 5) }

        return listOf(
            ContactEntity(
                id = "mock_sneha",
                name = "Sneha Reddy",
                relationshipType = "FRIEND",
                birthdayDay = today.get(Calendar.DAY_OF_MONTH),
                birthdayMonth = today.get(Calendar.MONTH) + 1,
                birthdayYear = 1998,
                primaryPhone = "+919876543210",
                primaryEmail = "sneha.reddy@example.com",
                healthScore = 85,
                interactionFrequencyPerMonth = 12f,
                lastInteractionDate = System.currentTimeMillis() - 2 * 24 * 3600 * 1000L
            ),
            ContactEntity(
                id = "mock_priya",
                name = "Priya Patel",
                relationshipType = "FAMILY",
                birthdayDay = tomorrow.get(Calendar.DAY_OF_MONTH),
                birthdayMonth = tomorrow.get(Calendar.MONTH) + 1,
                birthdayYear = 1995,
                anniversaryDay = dayAfterTomorrow.get(Calendar.DAY_OF_MONTH),
                anniversaryMonth = dayAfterTomorrow.get(Calendar.MONTH) + 1,
                anniversaryYear = 2021,
                primaryPhone = "+919876543211",
                primaryEmail = "priya.patel@example.com",
                healthScore = 95,
                interactionFrequencyPerMonth = 22f,
                lastInteractionDate = System.currentTimeMillis() - 1 * 24 * 3600 * 1000L
            ),
            ContactEntity(
                id = "mock_amit",
                name = "Amit Verma",
                relationshipType = "WORK",
                workStartDay = nextWeek.get(Calendar.DAY_OF_MONTH),
                workStartMonth = nextWeek.get(Calendar.MONTH) + 1,
                workStartYear = 2018,
                primaryPhone = "+919876543212",
                primaryEmail = "amit.verma@example.com",
                healthScore = 60,
                interactionFrequencyPerMonth = 4f,
                lastInteractionDate = System.currentTimeMillis() - 8 * 24 * 3600 * 1000L
            ),
            ContactEntity(
                id = "mock_rahul",
                name = "Rahul Sharma",
                relationshipType = "FRIEND",
                birthdayDay = 15,
                birthdayMonth = 6,
                birthdayYear = 1996,
                primaryPhone = "+919876543213",
                primaryEmail = "rahul.sharma@example.com",
                healthScore = 40,
                interactionFrequencyPerMonth = 1f,
                lastInteractionDate = System.currentTimeMillis() - 25 * 24 * 3600 * 1000L
            )
        )
    }

    data class SyncOutcome(
        val googleCount: Int,
        val deviceCount: Int,
        val inserted: Int,
        val updated: Int
    )
}
