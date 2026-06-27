package com.example.domain.service

import com.example.domain.model.contact.ContactSyncRecord

interface ContactSyncService {
    suspend fun fetchGoogleContacts(forceRefresh: Boolean = false): List<ContactSyncRecord>
    suspend fun fetchDeviceContacts(): List<ContactSyncRecord>
}

class DeviceContactsPermissionDeniedException(
    message: String = "Device contacts permission is missing."
) : SecurityException(message)
