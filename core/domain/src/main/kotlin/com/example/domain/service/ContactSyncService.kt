package com.example.domain.service

import com.example.core.db.entities.ContactEntity

interface ContactSyncService {
    suspend fun fetchGoogleContacts(forceRefresh: Boolean = false): List<ContactEntity>
    suspend fun fetchDeviceContacts(): List<ContactEntity>
}

class DeviceContactsPermissionDeniedException(
    message: String = "Device contacts permission is missing."
) : SecurityException(message)
