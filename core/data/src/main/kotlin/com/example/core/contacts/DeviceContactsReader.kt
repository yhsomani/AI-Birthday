package com.example.core.contacts

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import com.example.core.db.entities.ContactEntity

class DeviceContactsReader(private val context: Context) {

    @SuppressLint("Range")
    fun readAll(): List<ContactEntity> {
        val contacts = mutableListOf<ContactEntity>()
        val contentResolver = context.contentResolver
        
        // Read Call Logs first to compute interaction frequency
        val callMap = mutableMapOf<String, Pair<Int, Long>>() // Number -> Pair(count, lastInteractionMs)
        try {
            val callCursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null, null, CallLog.Calls.DATE + " DESC"
            )
            
            callCursor?.use {
                val now = System.currentTimeMillis()
                val monthMs = 30L * 24 * 60 * 60 * 1000
                
                while (it.moveToNext()) {
                    val number = it.getString(it.getColumnIndex(CallLog.Calls.NUMBER)).replace(Regex("[^0-9+]"), "")
                    val dateMs = it.getLong(it.getColumnIndex(CallLog.Calls.DATE))
                    
                    val existing = callMap[number] ?: Pair(0, dateMs)
                    val newCount = if (now - dateMs <= monthMs) existing.first + 1 else existing.first
                    callMap[number] = Pair(newCount, Math.max(existing.second, dateMs))
                }
            }
        } catch (e: SecurityException) {
            Log.w("DeviceContactsReader", "Missing call log permission", e)
        }

        try {
            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                    val name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    val hasPhone = it.getInt(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                    var phone: String? = null
                    var interactionCount = 0
                    var lastInteractionDate: Long? = null

                    if (hasPhone) {
                        val pCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        if (pCursor != null && pCursor.moveToFirst()) {
                            phone = pCursor.getString(pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            pCursor.close()

                            val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
                            if (callMap.containsKey(cleanPhone)) {
                                interactionCount = callMap[cleanPhone]!!.first
                                lastInteractionDate = callMap[cleanPhone]!!.second
                            }
                        }
                    }

                    if (name != null) {
                        val health = if (interactionCount > 10) 90 else if (interactionCount > 3) 70 else if (interactionCount > 0) 50 else 20
                        contacts.add(ContactEntity(
                            id = id,
                            name = name,
                            primaryPhone = phone,
                            interactionFrequencyPerMonth = interactionCount.toFloat(),
                            lastInteractionDate = lastInteractionDate,
                            healthScore = health
                        ))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("DeviceContactsReader", "Missing contacts permission", e)
        }
        
        return contacts
    }
}
