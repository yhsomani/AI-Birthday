package com.example.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.domain.model.contact.ContactSyncRecord
import com.example.domain.service.DeviceContactsPermissionDeniedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceContactsReader(private val context: Context) {

    suspend fun readContacts(): List<ContactSyncRecord> = withContext(Dispatchers.IO) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw DeviceContactsPermissionDeniedException()
        }

        val rowsById = linkedMapOf<String, MutableDeviceContact>()
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA4,
        )
        val supportedMimeTypes = listOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        )
        val placeholders = supportedMimeTypes.joinToString(",") { "?" }
        val selection = "${ContactsContract.Data.MIMETYPE} IN ($placeholders)"

        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                supportedMimeTypes.toTypedArray(),
                "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val contactIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
                val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_URI)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val data1Index = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
                val data2Index = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
                val data4Index = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA4)

                while (cursor.moveToNext()) {
                    val rawId = cursor.getString(contactIdIndex).orEmpty()
                    if (rawId.isBlank()) continue
                    val row = rowsById.getOrPut(rawId) {
                        MutableDeviceContact(id = "device_$rawId")
                    }
                    row.name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: row.name
                    row.profilePhotoUri = cursor.getString(photoIndex)?.takeIf { it.isNotBlank() }
                        ?: row.profilePhotoUri

                    val mimeType = cursor.getString(mimeTypeIndex).orEmpty()
                    val data1 = cursor.getString(data1Index)?.takeIf { it.isNotBlank() }
                    val data2 = cursor.getIntOrNull(data2Index)
                    val data4 = cursor.getString(data4Index)?.takeIf { it.isNotBlank() }

                    when (mimeType) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            row.name = data1 ?: row.name
                        }
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            row.primaryPhone = row.primaryPhone ?: data1
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            row.primaryEmail = row.primaryEmail ?: data1
                        }
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val date = data1?.toContactDate()
                            if (date != null) {
                                when (data2) {
                                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> {
                                        row.birthdayDay = row.birthdayDay ?: date.day
                                        row.birthdayMonth = row.birthdayMonth ?: date.month
                                        row.birthdayYear = row.birthdayYear ?: date.year
                                    }
                                    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> {
                                        row.anniversaryDay = row.anniversaryDay ?: date.day
                                        row.anniversaryMonth = row.anniversaryMonth ?: date.month
                                        row.anniversaryYear = row.anniversaryYear ?: date.year
                                    }
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            row.company = row.company ?: data1
                            row.jobTitle = row.jobTitle ?: data4
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            throw DeviceContactsPermissionDeniedException(e.message ?: "Device contacts permission is missing.")
        }

        rowsById.values
            .filter { it.name.isNotBlank() }
            .map { it.toRecord() }
    }

    private data class MutableDeviceContact(
        val id: String,
        var name: String = "",
        var primaryPhone: String? = null,
        var primaryEmail: String? = null,
        var company: String? = null,
        var jobTitle: String? = null,
        var profilePhotoUri: String? = null,
        var birthdayDay: Int? = null,
        var birthdayMonth: Int? = null,
        var birthdayYear: Int? = null,
        var anniversaryDay: Int? = null,
        var anniversaryMonth: Int? = null,
        var anniversaryYear: Int? = null,
    ) {
        fun toRecord(): ContactSyncRecord = ContactSyncRecord(
            id = id,
            displayName = name,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            company = company,
            jobTitle = jobTitle,
            profilePhotoUri = profilePhotoUri,
            contactGroup = "Device",
            relationshipType = "UNKNOWN",
            birthdayDay = birthdayDay,
            birthdayMonth = birthdayMonth,
            birthdayYear = birthdayYear,
            anniversaryDay = anniversaryDay,
            anniversaryMonth = anniversaryMonth,
            anniversaryYear = anniversaryYear,
        )
    }

    private data class ContactDate(val day: Int, val month: Int, val year: Int?)

    private fun String.toContactDate(): ContactDate? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.removePrefix("--")
        val parts = normalized.split("-")
        return when (parts.size) {
            3 -> ContactDate(
                year = parts[0].toIntOrNull(),
                month = parts[1].toIntOrNull() ?: return null,
                day = parts[2].toIntOrNull() ?: return null,
            )
            2 -> ContactDate(
                year = null,
                month = parts[0].toIntOrNull() ?: return null,
                day = parts[1].toIntOrNull() ?: return null,
            )
            else -> null
        }?.takeIf { it.month in 1..12 && it.day in 1..31 }
    }

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (isNull(columnIndex)) null else getInt(columnIndex)
    }
}
