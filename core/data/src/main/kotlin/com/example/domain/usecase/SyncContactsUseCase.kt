package com.example.domain.usecase

import android.content.Context
import com.example.contacts.DeviceContactsReader
import com.example.contacts.GoogleContactsSync
import com.example.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(): SyncOutcome {
        val googleSync = GoogleContactsSync(context)
        val deviceReader = DeviceContactsReader(context)

        val googleContacts = googleSync.fetchAll()
        val deviceContacts = deviceReader.readAll()
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
        device: List<com.example.core.db.entities.ContactEntity>,
        google: List<com.example.core.db.entities.ContactEntity>
    ): List<com.example.core.db.entities.ContactEntity> {
        val byName = mutableMapOf<String, com.example.core.db.entities.ContactEntity>()
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
