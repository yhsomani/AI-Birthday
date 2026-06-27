package com.example.core.contacts

import android.content.Context
import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.service.ContactSyncService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactSyncServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactSyncService {

    override suspend fun fetchGoogleContacts(forceRefresh: Boolean): List<ContactSyncRecord> {
        val googleSync = GoogleContactsSync(context)
        return googleSync.fetchAll(forceRefresh)
    }

    override suspend fun fetchDeviceContacts(): List<ContactSyncRecord> {
        return DeviceContactsReader(context).readContacts()
    }
}
