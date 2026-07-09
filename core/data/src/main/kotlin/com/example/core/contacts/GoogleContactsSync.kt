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
                        person.toContactSyncRecord()?.let(contacts::add)
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
