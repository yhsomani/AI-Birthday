package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.service.ContactSyncService
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
    private val contactSyncService: ContactSyncService
) {
    suspend operator fun invoke(): SyncOutcome {
        val googleContacts = contactSyncService.fetchGoogleContacts()
        val deviceContacts = contactSyncService.fetchDeviceContacts()
        val merged = mergeContacts(deviceContacts, googleContacts)

        var inserted = 0
        var updated = 0
        merged.forEach { contact ->
            val existing = contactRepository.getById(contact.id)
            contactRepository.upsert(contact)
            if (existing == null) inserted++ else updated++
        }

        return SyncOutcome(googleCount = googleContacts.size, deviceCount = deviceContacts.size, inserted = inserted, updated = updated)
    }

    private fun mergeContacts(
        device: List<ContactEntity>,
        google: List<ContactEntity>
    ): List<ContactEntity> {
        val byName = mutableMapOf<String, ContactEntity>()
        device.forEach { byName[it.name] = it }
        google.forEach { g ->
            val existing = byName[g.name]
            byName[g.name] = if (existing != null) {
                existing.copy(
                    googleContactId = g.googleContactId ?: existing.googleContactId,
                    primaryEmail = g.primaryEmail ?: existing.primaryEmail
                )
            } else g
        }
        return byName.values.toList()
    }

    data class SyncOutcome(
        val googleCount: Int,
        val deviceCount: Int,
        val inserted: Int,
        val updated: Int
    )
}
