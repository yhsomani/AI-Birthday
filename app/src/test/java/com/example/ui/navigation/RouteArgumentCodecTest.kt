package com.example.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RouteArgumentCodecTest {

    @Test
    fun encodeDecode_roundTripsPathSensitiveValues() {
        val values = listOf(
            "Aarav Somani",
            "family/friend",
            "A+B",
            "100% match",
            "emoji-like #1 & symbols",
        )

        values.forEach { value ->
            val encoded = RouteArgumentCodec.encode(value)

            assertFalse(encoded.contains("/"))
            assertEquals(value, RouteArgumentCodec.decode(encoded))
        }
    }

    @Test
    fun createRoute_encodesContactAndMessageRefsAsPathSegments() {
        val route = Screen.WishPreview.createRoute(
            contactId = "people/c123 + 100%",
            messageRef = "pending/message #1",
        )

        assertEquals("wish/people%2Fc123%20%2B%20100%25/pending%2Fmessage%20%231", route)
        assertEquals("people/c123 + 100%", RouteArgumentCodec.decode(route.split("/")[1]))
        assertEquals("pending/message #1", RouteArgumentCodec.decode(route.split("/")[2]))
    }

    @Test
    fun decode_preservesAlreadyDecodedPlusAndInvalidPercentInput() {
        assertEquals("A+B", RouteArgumentCodec.decode("A+B"))
        assertEquals("100% match", RouteArgumentCodec.decode("100% match"))
    }
}
