package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.domain.contact.toEntities
import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.repository.ContactRepository
import com.example.domain.service.ContactSyncService
import com.example.domain.service.DeviceContactsPermissionDeniedException
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
        var googleContacts = emptyList<ContactSyncRecord>()
        var googleError: String? = null
        try {
            googleContacts = contactSyncService.fetchGoogleContacts(forceRefresh)
            preferencesRepository.setLastSyncError(null)
        } catch (e: Exception) {
            android.util.Log.e("SyncContactsUseCase", "Google contacts sync failed", e)
            googleError = e.message ?: "Failed to fetch Google contacts"
            preferencesRepository.setLastSyncError(googleError)
        }

        var deviceContacts = emptyList<ContactSyncRecord>()
        var deviceError: String? = null
        var deviceContactsPermissionDenied = false
        try {
            deviceContacts = contactSyncService.fetchDeviceContacts()
        } catch (e: DeviceContactsPermissionDeniedException) {
            android.util.Log.w("SyncContactsUseCase", "Device contacts permission denied", e)
            deviceContactsPermissionDenied = true
            deviceError = e.message ?: DEVICE_CONTACTS_PERMISSION_ERROR
        } catch (e: Exception) {
            android.util.Log.e("SyncContactsUseCase", "Device contacts sync failed", e)
            deviceError = e.message ?: "Failed to fetch device contacts"
        }

        if (googleError != null && deviceContacts.isEmpty()) {
            throw Exception("Google contacts sync failed: $googleError")
        }
        if (googleError == null && deviceError == null) {
            preferencesRepository.setLastSyncError(null)
        } else if (googleError != null) {
            preferencesRepository.setLastSyncError("Google sync failed; imported ${deviceContacts.size} device contacts.")
        } else if (deviceContactsPermissionDenied) {
            preferencesRepository.setLastSyncError(DEVICE_CONTACTS_PERMISSION_ERROR)
        } else if (deviceError != null) {
            preferencesRepository.setLastSyncError(deviceError)
        }

        val merged = mergeContacts(googleContacts.toEntities(), deviceContacts.toEntities())
            .map { it.withRelationshipFromContactGroup() }

        var inserted = 0
        var updated = 0
        merged.forEach { contact ->
            val existing = contactRepository.getById(contact.id)
            contactRepository.upsert(contact)
            if (existing == null) inserted++ else updated++
        }

        // Run event discovery immediately so events are available in the database
        discoverEventsUseCase()

        return SyncOutcome(
            googleCount = googleContacts.size,
            deviceCount = deviceContacts.size,
            inserted = inserted,
            updated = updated,
            deviceContactsPermissionDenied = deviceContactsPermissionDenied,
        )
    }

    private fun mergeContacts(
        googleContacts: List<ContactEntity>,
        deviceContacts: List<ContactEntity>,
    ): List<ContactEntity> {
        val mergedContacts = mutableListOf<ContactEntity>()
        val keyToIndex = linkedMapOf<String, Int>()

        fun registerKeys(index: Int, contact: ContactEntity) {
            contact.mergeKeys().forEach { key ->
                keyToIndex.putIfAbsent(key, index)
            }
        }

        googleContacts.forEach { contact ->
            val index = mergedContacts.size
            mergedContacts += contact
            registerKeys(index, contact)
        }
        deviceContacts.forEach { deviceContact ->
            val existingIndex = deviceContact.mergeKeys().firstNotNullOfOrNull { keyToIndex[it] }
            if (existingIndex == null) {
                val index = mergedContacts.size
                mergedContacts += deviceContact
                registerKeys(index, deviceContact)
            } else {
                val merged = mergedContacts[existingIndex].mergeMissingFrom(deviceContact)
                mergedContacts[existingIndex] = merged
                registerKeys(existingIndex, merged)
            }
        }
        return mergedContacts
    }

    private fun ContactEntity.mergeKeys(): List<String> {
        val keys = mutableListOf<String>()
        val phone = primaryPhone?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }
        if (phone != null) keys += "phone:$phone"
        val email = primaryEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (email != null) keys += "email:$email"
        keys += "name:${name.trim().lowercase()}"
        return keys
    }

    private fun ContactEntity.mergeMissingFrom(fallback: ContactEntity): ContactEntity {
        return copy(
            primaryPhone = primaryPhone ?: fallback.primaryPhone,
            secondaryPhone = secondaryPhone ?: fallback.secondaryPhone,
            primaryEmail = primaryEmail ?: fallback.primaryEmail,
            company = company ?: fallback.company,
            jobTitle = jobTitle ?: fallback.jobTitle,
            profilePhotoUri = profilePhotoUri ?: fallback.profilePhotoUri,
            birthdayDay = birthdayDay ?: fallback.birthdayDay,
            birthdayMonth = birthdayMonth ?: fallback.birthdayMonth,
            birthdayYear = birthdayYear ?: fallback.birthdayYear,
            anniversaryDay = anniversaryDay ?: fallback.anniversaryDay,
            anniversaryMonth = anniversaryMonth ?: fallback.anniversaryMonth,
            anniversaryYear = anniversaryYear ?: fallback.anniversaryYear,
            workStartDay = workStartDay ?: fallback.workStartDay,
            workStartMonth = workStartMonth ?: fallback.workStartMonth,
            workStartYear = workStartYear ?: fallback.workStartYear,
            contactGroup = contactGroup ?: fallback.contactGroup,
        )
    }

    private fun ContactEntity.withRelationshipFromContactGroup(): ContactEntity {
        val group = contactGroup?.trim()?.takeIf { it.isNotBlank() } ?: return this
        if (relationshipType != "UNKNOWN" || group.equals("device", ignoreCase = true)) {
            return this
        }

        val relationship = when {
            group.contains("family", ignoreCase = true) -> "FAMILY"
            group.contains("coworker", ignoreCase = true) ||
                group.contains("work", ignoreCase = true) ||
                group.contains("colleague", ignoreCase = true) -> "WORK"
            group.contains("friend", ignoreCase = true) -> "FRIEND"
            else -> "ACQUAINTANCE"
        }
        return copy(relationshipType = relationship)
    }

    data class SyncOutcome(
        val googleCount: Int,
        val deviceCount: Int,
        val inserted: Int,
        val updated: Int,
        val deviceContactsPermissionDenied: Boolean = false,
    )

    companion object {
        const val DEVICE_CONTACTS_PERMISSION_ERROR =
            "Device contacts permission is missing. Allow contacts access to import phone contacts."
    }
}
