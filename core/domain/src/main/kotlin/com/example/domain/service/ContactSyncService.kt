package com.example.domain.service

import com.example.core.db.entities.ContactEntity

interface ContactSyncService {
    suspend fun fetchGoogleContacts(forceRefresh: Boolean = false): List<ContactEntity>
}
