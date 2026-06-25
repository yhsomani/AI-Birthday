package com.example.core.contacts

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleConnectionsRequestUrlTest {

    @Test
    fun `build encodes sync and page tokens as query parameter values`() {
        val syncToken = "sync a+b=c&bad=true?x"
        val pageToken = "page/one+two&syncToken=wrong"

        val url = PeopleConnectionsRequestUrl.build(
            personFields = "names,emailAddresses",
            syncToken = syncToken,
            pageToken = pageToken,
        ).toHttpUrl()

        assertEquals("https", url.scheme)
        assertEquals("people.googleapis.com", url.host)
        assertEquals("v1/people/me/connections", url.encodedPath.removePrefix("/"))
        assertEquals("names,emailAddresses", url.queryParameter("personFields"))
        assertEquals("1000", url.queryParameter("pageSize"))
        assertEquals(syncToken, url.queryParameter("syncToken"))
        assertEquals(pageToken, url.queryParameter("pageToken"))
        assertNull(url.queryParameter("bad"))
        assertEquals(1, url.queryParameterValues("syncToken").size)
    }

    @Test
    fun `build requests sync token when no sync token exists`() {
        val url = PeopleConnectionsRequestUrl.build(
            personFields = "names",
            syncToken = "",
            pageToken = null,
        ).toHttpUrl()

        assertEquals("true", url.queryParameter("requestSyncToken"))
        assertNull(url.queryParameter("syncToken"))
        assertNull(url.queryParameter("pageToken"))
    }
}
