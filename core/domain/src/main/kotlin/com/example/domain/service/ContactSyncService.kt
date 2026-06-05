package com.example.domain.service

import com.example.core.db.entities.ContactEntity

interface ContactSyncService {
    suspend fun fetchGoogleContacts(): List<ContactEntity>
    suspend fun fetchDeviceContacts(): List<ContactEntity>
}
