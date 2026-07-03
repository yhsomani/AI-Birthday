package com.example.core.contacts

import android.content.Context
import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.service.ContactSyncService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class ContactSyncServiceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val peopleConnectionsRequestFactory: PeopleConnectionsRequestFactory,
) : ContactSyncService {

    override suspend fun fetchGoogleContacts(forceRefresh: Boolean): List<ContactSyncRecord> {
        val googleSync = GoogleContactsSync(
            context = context,
            client = okHttpClient,
            requestFactory = peopleConnectionsRequestFactory,
        )
        return googleSync.fetchAll(forceRefresh)
    }

    override suspend fun fetchDeviceContacts(): List<ContactSyncRecord> {
        return DeviceContactsReader(context).readContacts()
    }
}
