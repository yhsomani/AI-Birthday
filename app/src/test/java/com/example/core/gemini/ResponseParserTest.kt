package com.example.core.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])

class ResponseParserTest {

    @Test
    fun `parseContactClassification returns expected fields`() {
        val json = """
            {
                "type": "FRIEND",
                "subtype": "CLOSE",
                "confidence": 0.95,
                "language": "en",
                "formality": "CASUAL",
                "communication_style": "WARM"
            }
        """.trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("FRIEND", result.type)
        assertEquals("CLOSE", result.subtype)
        assertEquals(0.95, result.confidence, 0.001)
        assertEquals("en", result.language)
        assertEquals("CASUAL", result.formality)
        assertEquals("WARM", result.communicationStyle)
    }

    @Test
    fun `parseContactClassification returns defaults on malformed json`() {
        val result = ResponseParser.parseContactClassification("not json")

        assertEquals("UNKNOWN", result.type)
        assertNull(result.subtype)
        assertEquals(0.0, result.confidence, 0.001)
        assertEquals("en", result.language)
        assertEquals("CASUAL", result.formality)
        assertEquals("WARM", result.communicationStyle)
    }

    @Test
    fun `parseContactClassification handles null subtype`() {
        val json = """{"type": "ACQUAINTANCE"}""".trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("ACQUAINTANCE", result.type)
        assertNull(result.subtype)
    }

    @Test
    fun `parseMessageVariants returns all variants`() {
        val json = """
            {
                "short": "Happy B-day!",
                "standard": "Happy birthday!",
                "long": "Have an amazing birthday!",
                "formal": "Warmest birthday wishes",
                "funny": "You're not old!",
                "emotional": "You mean so much to me",
                "recommended": "standard"
            }
        """.trimIndent()

        val variants = ResponseParser.parseMessageVariants(json)

        assertEquals("Happy B-day!", variants.short)
        assertEquals("Happy birthday!", variants.standard)
        assertEquals("Have an amazing birthday!", variants.long)
        assertEquals("Warmest birthday wishes", variants.formal)
        assertEquals("You're not old!", variants.funny)
        assertEquals("You mean so much to me", variants.emotional)
        assertEquals("standard", variants.recommended)
    }

    @Test
    fun `parseMessageVariants returns fallback on malformed json`() {
        val variants = ResponseParser.parseMessageVariants("garbage")

        assertEquals("Wishing you a very happy birthday! Hope you have a wonderful day!", variants.short)
        assertEquals("standard", variants.recommended)
        assertTrue(variants.isUsingFallback)
    }

    @Test
    fun `parseMessageVariants fills missing fields with defaults`() {
        val json = """{"short": "Hey!"}""".trimIndent()

        val variants = ResponseParser.parseMessageVariants(json)

        assertEquals("Hey!", variants.short)
        assertEquals("Wishing you a very happy birthday! Hope you have a wonderful day!", variants.standard)
        assertEquals("standard", variants.recommended)
        assertFalse(variants.isUsingFallback)
    }

    @Test
    fun `parseMessageVariants accepts fenced json and all six variants`() {
        val json = """
            ```json
            {
                "short": "Short wish",
                "standard": "Standard wish",
                "long": "Long wish",
                "formal": "Formal wish",
                "funny": "Funny wish",
                "emotional": "Emotional wish",
                "recommended": "emotional"
            }
            ```
        """.trimIndent()

        val variants = ResponseParser.parseMessageVariants(json)

        assertEquals("Short wish", variants.short)
        assertEquals("Standard wish", variants.standard)
        assertEquals("Long wish", variants.long)
        assertEquals("Formal wish", variants.formal)
        assertEquals("Funny wish", variants.funny)
        assertEquals("Emotional wish", variants.emotional)
        assertEquals("emotional", variants.recommended)
        assertFalse(variants.isUsingFallback)
    }

    @Test
    fun `parseMessageVariants resets invalid recommended to standard`() {
        val json = """
            {
                "short": "Short",
                "standard": "Standard",
                "recommended": "wild"
            }
        """.trimIndent()

        val variants = ResponseParser.parseMessageVariants(json)

        assertEquals("standard", variants.recommended)
        assertEquals("Standard", variants.get(variants.recommended))
    }

    @Test
    fun `parseMessageVariants treats error json as fallback`() {
        val variants = ResponseParser.parseMessageVariants("""{"error":"quota exceeded"}""")

        assertEquals("standard", variants.recommended)
        assertTrue(variants.isUsingFallback)
        assertEquals("Wishing you a very happy birthday! Hope you have a wonderful day!", variants.standard)
    }

    @Test
    fun `MessageVariants get returns correct variant`() {
        val variants = MessageVariants("s", "std", "l", "f", "fu", "e", "formal")

        assertEquals("s", variants.get("short"))
        assertEquals("std", variants.get("standard"))
        assertEquals("l", variants.get("long"))
        assertEquals("f", variants.get("formal"))
        assertEquals("fu", variants.get("funny"))
        assertEquals("e", variants.get("emotional"))
        assertEquals("std", variants.get("unknown"))
    }

    @Test
    fun `MessageVariants fromFallback creates uniform variants`() {
        val variants = MessageVariants.fromFallback("hello")

        assertEquals("hello", variants.short)
        assertEquals("hello", variants.standard)
        assertEquals("hello", variants.long)
        assertEquals("hello", variants.formal)
        assertEquals("hello", variants.funny)
        assertEquals("hello", variants.emotional)
        assertEquals("standard", variants.recommended)
    }
}
