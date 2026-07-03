package com.example.core.contacts

import javax.inject.Inject
import okhttp3.Request

class PeopleConnectionsRequestFactory @Inject constructor() {
    fun build(
        oauthToken: String,
        personFields: String,
        syncToken: String,
        pageToken: String?,
    ): Request {
        return Request.Builder()
            .url(
                PeopleConnectionsRequestUrl.build(
                    personFields = personFields,
                    syncToken = syncToken,
                    pageToken = pageToken,
                ),
            )
            .addHeader("Authorization", "Bearer $oauthToken")
            .build()
    }
}
