package com.example.core.contacts

import okhttp3.HttpUrl

internal object PeopleConnectionsRequestUrl {
    private const val SCHEME = "https"
    private const val HOST = "people.googleapis.com"
    private const val CONNECTIONS_PATH = "v1/people/me/connections"

    fun build(
        personFields: String,
        syncToken: String,
        pageToken: String?,
    ): String {
        val builder = HttpUrl.Builder()
            .scheme(SCHEME)
            .host(HOST)
            .addPathSegments(CONNECTIONS_PATH)
            .addQueryParameter("personFields", personFields)
            .addQueryParameter("pageSize", "1000")

        if (syncToken.isNotEmpty()) {
            builder.addQueryParameter("syncToken", syncToken)
        } else {
            builder.addQueryParameter("requestSyncToken", "true")
        }

        if (!pageToken.isNullOrEmpty()) {
            builder.addQueryParameter("pageToken", pageToken)
        }

        return builder.build().toString()
    }
}
