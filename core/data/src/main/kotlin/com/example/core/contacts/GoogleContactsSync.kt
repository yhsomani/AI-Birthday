package com.example.core.contacts

import android.accounts.AccountManager
import android.content.Context
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.SensitiveLogRedactor
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.contact.ContactSyncRecord
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException

class GoogleContactsSync(
    private val context: Context,
    private val client: OkHttpClient,
    private val requestFactory: PeopleConnectionsRequestFactory,
) {
    private companion object {
        const val TAG = "GoogleContactsSync"
    }

    private suspend fun getValidToken(prefs: SecurePrefs): String? = withContext(Dispatchers.IO) {
        val existing = prefs.getGoogleOAuthToken()
        StructuredLogger.d(
            TAG,
            "Google contacts token cache checked",
            mapOf("cachedTokenPresent" to existing.isNotEmpty().toString()),
        )
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                val email = account.email
                StructuredLogger.d(
                    TAG,
                    "Signed-in Google account checked",
                    mapOf("emailPresent" to (email != null).toString()),
                )
                
                val contactsScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts.readonly")
                if (!GoogleSignIn.hasPermissions(account, contactsScope)) {
                    StructuredLogger.w(TAG, "Required Google Contacts scope is not granted")
                    throw SecurityException("Required Google Contacts access permission is not granted. Please sign out and sign in again, ensuring you check the box to grant contact access.")
                }
                
                val googleAccount = account.account ?: email?.let { android.accounts.Account(it, "com.google") }
                if (googleAccount != null) {
                    // Try GoogleAuthUtil first
                    try {
                        val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                            context,
                            googleAccount,
                            "oauth2:https://www.googleapis.com/auth/contacts.readonly"
                        )
                        if (!token.isNullOrEmpty()) {
                            StructuredLogger.d(TAG, "GoogleAuthUtil token retrieved successfully")
                            if (token != existing) {
                                prefs.setGoogleOAuthToken(token)
                            }
                            return@withContext token
                        }
                    } catch (e: Exception) {
                        StructuredLogger.w(TAG, "GoogleAuthUtil token retrieval failed; trying AccountManager", e)
                    }

                    // Fallback to AccountManager
                    StructuredLogger.d(TAG, "Attempting fallback to AccountManager")
                    val am = AccountManager.get(context)
                    val future = am.getAuthToken(
                        googleAccount,
                        "oauth2:https://www.googleapis.com/auth/contacts.readonly",
                        null,
                        false,
                        null,
                        null
                    )
                    val bundle = future.result
                    val freshToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                    if (freshToken != null) {
                        StructuredLogger.d(TAG, "AccountManager token retrieved successfully")
                        if (freshToken != existing) {
                            prefs.setGoogleOAuthToken(freshToken)
                        }
                        return@withContext freshToken
                    }
                } else {
                    StructuredLogger.w(TAG, "Google account handle is unavailable")
                }
            } else {
                StructuredLogger.w(TAG, "GoogleSignIn returned no signed-in account")
            }
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "Token fetch or refresh failed; using cached token if available", e)
        }
        StructuredLogger.d(
            TAG,
            "Returning cached Google contacts token",
            mapOf("cachedTokenPresent" to existing.isNotEmpty().toString()),
        )
        return@withContext existing.ifEmpty { null }
    }

    suspend fun fetchAll(forceRefresh: Boolean = false): List<ContactSyncRecord> = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs(context)
        val token = getValidToken(prefs)
        if (token == null) {
            StructuredLogger.w(TAG, "Google contacts token is unavailable; aborting fetch")
            throw IllegalStateException("Google account token is missing or expired. Please sign in again.")
        }

        val baseFields = "names,nicknames,emailAddresses,phoneNumbers,birthdays,events,organizations,memberships,relations,addresses,photos,biographies"
        val syncToken = if (forceRefresh) "" else prefs.getSyncToken()
        val contacts = mutableListOf<ContactSyncRecord>()
        
        var pageToken: String? = null
        var lastNextSyncToken = ""
        
        try {
            do {
                StructuredLogger.d(
                    TAG,
                    "Requesting People API connections page",
                    mapOf(
                        "incrementalSync" to syncToken.isNotEmpty().toString(),
                        "continuation" to (!pageToken.isNullOrEmpty()).toString(),
                    ),
                )
                
                val request = requestFactory.build(
                    oauthToken = token,
                    personFields = baseFields,
                    syncToken = syncToken,
                    pageToken = pageToken,
                )
                    
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.body?.close()
                    val safeError = SensitiveLogRedactor.googleContactsHttpErrorSummary(response.code)
                    StructuredLogger.e(TAG, "Google contacts fetch failed", extras = mapOf("summary" to safeError))
                    if (response.code == 400 && syncToken.isNotEmpty()) {
                        StructuredLogger.w(TAG, "Sync token expired or parameter mismatch; clearing sync token and performing full sync")
                        prefs.setSyncToken("")
                        return@withContext fetchAll(forceRefresh = true)
                    }
                    throw IOException(safeError)
                }
                
                val jsonStr = response.body?.string() ?: break
                StructuredLogger.d(
                    TAG,
                    "Retrieved Google contacts page JSON response",
                    mapOf("length" to jsonStr.length.toString()),
                )
                val jsonObj = JSONObject(jsonStr)
                
                val connections = jsonObj.optJSONArray("connections")
                if (connections != null) {
                    for (i in 0 until connections.length()) {
                        val person = connections.getJSONObject(i)
                        val resourceName = person.optString("resourceName")
                        if (resourceName.isEmpty()) continue
                        
                        val metadata = person.optJSONObject("metadata")
                        val isDeleted = metadata?.optBoolean("deleted", false) ?: false
                        
                        if (isDeleted) {
                            contacts.add(ContactSyncRecord(
                                id = resourceName,
                                googleContactId = resourceName,
                                displayName = "",
                                isDeleted = true
                            ))
                        } else {
                            // parse name
                            var displayName = ""
                            val names = person.optJSONArray("names")
                            if (names != null && names.length() > 0) {
                                displayName = names.getJSONObject(0).optString("displayName", "")
                            }
                            
                            if (displayName.isEmpty()) {
                                val nicknames = person.optJSONArray("nicknames")
                                if (nicknames != null && nicknames.length() > 0) {
                                    displayName = nicknames.getJSONObject(0).optString("value", "")
                                }
                            }
                            
                            if (displayName.isEmpty()) {
                                val phoneNumbers = person.optJSONArray("phoneNumbers")
                                if (phoneNumbers != null && phoneNumbers.length() > 0) {
                                    displayName = phoneNumbers.getJSONObject(0).optString("value", "")
                                }
                            }
                            
                            if (displayName.isEmpty()) {
                                val emailAddresses = person.optJSONArray("emailAddresses")
                                if (emailAddresses != null && emailAddresses.length() > 0) {
                                    displayName = emailAddresses.getJSONObject(0).optString("value", "")
                                }
                            }
                            
                            if (displayName.isEmpty()) {
                                displayName = "Unnamed Contact"
                            }
                            
                            var phone: String? = null
                            val phoneNumbers = person.optJSONArray("phoneNumbers")
                            if (phoneNumbers != null && phoneNumbers.length() > 0) {
                                phone = phoneNumbers.getJSONObject(0).optString("value")
                            }
                            
                            var email: String? = null
                            val emailAddresses = person.optJSONArray("emailAddresses")
                            if (emailAddresses != null && emailAddresses.length() > 0) {
                                email = emailAddresses.getJSONObject(0).optString("value")
                            }
                            
                            var company: String? = null
                            var jobTitle: String? = null
                            val orgs = person.optJSONArray("organizations")
                            if (orgs != null && orgs.length() > 0) {
                                company = orgs.getJSONObject(0).optString("name")
                                jobTitle = orgs.getJSONObject(0).optString("title")
                            }
                            
                            var bDay: Int? = null
                            var bMonth: Int? = null
                            var bYear: Int? = null
                            val birthdays = person.optJSONArray("birthdays")
                            if (birthdays != null && birthdays.length() > 0) {
                                val date = birthdays.getJSONObject(0).optJSONObject("date")
                                if (date != null) {
                                    bDay = if(date.has("day")) date.getInt("day") else null
                                    bMonth = if(date.has("month")) date.getInt("month") else null
                                    bYear = if(date.has("year")) date.getInt("year") else null
                                }
                            }
                            
                            var aDay: Int? = null
                            var aMonth: Int? = null
                            var wDay: Int? = null
                            var wMonth: Int? = null
                            var wYear: Int? = null
                            val events = person.optJSONArray("events")
                            if (events != null) {
                                for (j in 0 until events.length()) {
                                    val evt = events.getJSONObject(j)
                                    val type = evt.optString("type")
                                    val evtDate = evt.optJSONObject("date")
                                    if (evtDate != null) {
                                        if (type == "anniversary") {
                                            aDay = if(evtDate.has("day")) evtDate.getInt("day") else null
                                            aMonth = if(evtDate.has("month")) evtDate.getInt("month") else null
                                        } else if (type == "work_anniversary") {
                                            wDay = if(evtDate.has("day")) evtDate.getInt("day") else null
                                            wMonth = if(evtDate.has("month")) evtDate.getInt("month") else null
                                            wYear = if(evtDate.has("year")) evtDate.getInt("year") else null
                                        }
                                    }
                                }
                            }
                            
                            var contactGroup: String? = null
                            val memberships = person.optJSONArray("memberships")
                            if (memberships != null && memberships.length() > 0) {
                                for (k in 0 until memberships.length()) {
                                    val membership = memberships.getJSONObject(k)
                                    val cgMembership = membership.optJSONObject("contactGroupMembership")
                                    if (cgMembership != null) {
                                        val resourceNameVal = cgMembership.optString("contactGroupResourceName", "")
                                        if (resourceNameVal.isNotEmpty()) {
                                            contactGroup = resourceNameVal.removePrefix("contactGroups/")
                                            break
                                        }
                                    }
                                }
                            }
            
                            var relationsJson = "[]"
                            val relationsArray = person.optJSONArray("relations")
                            if (relationsArray != null && relationsArray.length() > 0) {
                                val relationsList = mutableListOf<Map<String, String>>()
                                for (k in 0 until relationsArray.length()) {
                                    val rel = relationsArray.getJSONObject(k)
                                    val personName = rel.optString("person", "")
                                    val relType = rel.optString("type", "")
                                    if (personName.isNotEmpty()) {
                                        relationsList += mapOf("person" to personName, "type" to relType)
                                    }
                                }
                                if (relationsList.isNotEmpty()) {
                                    relationsJson = org.json.JSONArray(relationsList.map {
                                        org.json.JSONObject().apply {
                                            put("person", it["person"])
                                            put("type", it["type"])
                                        }
                                    }).toString()
                                }
                            }
                            
                            var nickname: String? = null
                            val nicknames = person.optJSONArray("nicknames")
                            if (nicknames != null && nicknames.length() > 0) {
                                nickname = nicknames.getJSONObject(0).optString("value")
                            }
                            
                            var address: String? = null
                            val addresses = person.optJSONArray("addresses")
                            if (addresses != null && addresses.length() > 0) {
                                address = addresses.getJSONObject(0).optString("formattedValue")
                            }
                            
                            var photoUrl: String? = null
                            val photos = person.optJSONArray("photos")
                            if (photos != null && photos.length() > 0) {
                                photoUrl = photos.getJSONObject(0).optString("url")
                            }
                            
                            var notesText = ""
                            val biographies = person.optJSONArray("biographies")
                            if (biographies != null && biographies.length() > 0) {
                                notesText = biographies.getJSONObject(0).optString("value", "")
                            }
            
                            contacts.add(ContactSyncRecord(
                                id = resourceName,
                                googleContactId = resourceName,
                                displayName = displayName,
                                nickname = nickname,
                                primaryPhone = phone,
                                primaryEmail = email,
                                company = company,
                                jobTitle = jobTitle,
                                birthdayDay = bDay,
                                birthdayMonth = bMonth,
                                birthdayYear = bYear,
                                anniversaryDay = aDay,
                                anniversaryMonth = aMonth,
                                workStartDay = wDay,
                                workStartMonth = wMonth,
                                workStartYear = wYear,
                                contactGroup = contactGroup,
                                relationsJson = relationsJson,
                                address = address,
                                profilePhotoUri = photoUrl,
                                notesText = notesText
                            ))
                        }
                    }
                }
                
                val nextToken = jsonObj.optString("nextPageToken", "")
                pageToken = if (nextToken.isNotEmpty()) nextToken else null
                val nextSyncToken = jsonObj.optString("nextSyncToken", "")
                if (nextSyncToken.isNotEmpty()) {
                    lastNextSyncToken = nextSyncToken
                }
            } while (!pageToken.isNullOrEmpty())
            
            if (lastNextSyncToken.isNotEmpty()) {
                prefs.setSyncToken(lastNextSyncToken)
            }
        } catch (e: Exception) {
            StructuredLogger.e(TAG, "Failed to fetch Google contacts", e)
            throw e
        }

        return@withContext contacts
    }
}
