package com.example.core.contacts

import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.example.core.db.entities.ContactEntity
import com.example.core.prefs.SecurePrefs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class GoogleContactsSync(private val context: Context) {

    private suspend fun getValidToken(prefs: SecurePrefs): String? = withContext(Dispatchers.IO) {
        val existing = prefs.getGoogleOAuthToken()
        if (existing.isNotEmpty()) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    val am = AccountManager.get(context)
                    val future = am.getAuthToken(
                        account.account ?: return@withContext existing,
                        "oauth2:https://www.googleapis.com/auth/contacts.readonly",
                        null,
                        false,
                        null,
                        null
                    )
                    val bundle = future.result
                    val freshToken = bundle?.getString(AccountManager.KEY_AUTHTOKEN)
                    if (freshToken != null && freshToken != existing) {
                        prefs.setGoogleOAuthToken(freshToken)
                    }
                    return@withContext freshToken ?: existing
                }
            } catch (e: Exception) {
                Log.w("GoogleContactsSync", "Token refresh failed, using existing", e)
            }
        }
        return@withContext existing.ifEmpty { null }
    }

    suspend fun fetchAll(): List<ContactEntity> = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs(context)
        val token = getValidToken(prefs) ?: return@withContext emptyList()

        val baseFields = "names,emailAddresses,phoneNumbers,birthdays,events,organizations,memberships,relations"
        val syncToken = prefs.getSyncToken()
        val url = if (syncToken.isNotEmpty()) {
            "https://people.googleapis.com/v1/people/me/connections?personFields=$baseFields&syncToken=$syncToken"
        } else {
            "https://people.googleapis.com/v1/people/me/connections?personFields=$baseFields"
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
            
        val client = OkHttpClient()
        val contacts = mutableListOf<ContactEntity>()
        
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val jsonStr = response.body?.string() ?: return@withContext emptyList()
            val jsonObj = JSONObject(jsonStr)
            
            val connections = jsonObj.optJSONArray("connections") ?: return@withContext emptyList()
            
            for (i in 0 until connections.length()) {
                val person = connections.getJSONObject(i)
                val resourceName = person.optString("resourceName")
                
                val names = person.optJSONArray("names")
                if (names == null || names.length() == 0) continue
                val displayName = names.getJSONObject(0).optString("displayName")
                
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
                val orgs = person.optJSONArray("organizations")
                if (orgs != null && orgs.length() > 0) {
                    company = orgs.getJSONObject(0).optString("name")
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
                            val resourceName = cgMembership.optString("contactGroupResourceName", "")
                            if (resourceName.isNotEmpty()) {
                                contactGroup = resourceName.removePrefix("contactGroups/")
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

                contacts.add(ContactEntity(
                    id = resourceName,
                    googleContactId = resourceName,
                    name = displayName,
                    primaryPhone = phone,
                    primaryEmail = email,
                    company = company,
                    birthdayDay = bDay,
                    birthdayMonth = bMonth,
                    birthdayYear = bYear,
                    anniversaryDay = aDay,
                    anniversaryMonth = aMonth,
                    workStartDay = wDay,
                    workStartMonth = wMonth,
                    workStartYear = wYear,
                    contactGroup = contactGroup,
                    relationsJson = relationsJson
                ))
            }

            val nextSyncToken = jsonObj.optString("nextSyncToken", "")
            if (nextSyncToken.isNotEmpty()) {
                prefs.setSyncToken(nextSyncToken)
            }

        } catch (e: IOException) {
            Log.e("GoogleContactsSync", "Failed to fetch Google contacts", e)
        }

        return@withContext contacts
    }
}
