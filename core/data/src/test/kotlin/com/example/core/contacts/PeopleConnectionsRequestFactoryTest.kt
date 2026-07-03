package com.example.core.contacts

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleConnectionsRequestFactoryTest {
    private val factory = PeopleConnectionsRequestFactory()

    @Test
    fun `build creates authorized People connections request with encoded tokens`() {
        val request = factory.build(
            oauthToken = "oauth-token",
            personFields = "names,emailAddresses",
            syncToken = "sync a+b=c&bad=true?x",
            pageToken = "page/one+two&syncToken=wrong",
        )
        val url = request.url.toString().toHttpUrl()

        assertEquals("Bearer oauth-token", request.header("Authorization"))
        assertEquals("https", url.scheme)
        assertEquals("people.googleapis.com", url.host)
        assertEquals("v1/people/me/connections", url.encodedPath.removePrefix("/"))
        assertEquals("names,emailAddresses", url.queryParameter("personFields"))
        assertEquals("sync a+b=c&bad=true?x", url.queryParameter("syncToken"))
        assertEquals("page/one+two&syncToken=wrong", url.queryParameter("pageToken"))
        assertNull(url.queryParameter("bad"))
    }

    @Test
    fun `build requests sync token when no cached sync token exists`() {
        val request = factory.build(
            oauthToken = "oauth-token",
            personFields = "names",
            syncToken = "",
            pageToken = null,
        )

        assertEquals("true", request.url.queryParameter("requestSyncToken"))
        assertNull(request.url.queryParameter("syncToken"))
        assertNull(request.url.queryParameter("pageToken"))
    }
}
