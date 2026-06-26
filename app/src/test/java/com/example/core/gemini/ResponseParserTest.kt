package com.example.core.gemini

import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
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
                "relationship_type": "FRIEND",
                "relationship_subtype": "CLOSE",
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
    fun `parseContactClassification normalizes canonical enum fields`() {
        val json = """
            {
                "relationship_type": "best friend",
                "confidence": 0.88,
                "language": "HI",
                "formality": "semi-formal",
                "communication_style": "professional"
            }
        """.trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("BEST_FRIEND", result.type)
        assertEquals(0.88, result.confidence, 0.001)
        assertEquals("hi", result.language)
        assertEquals("SEMI_FORMAL", result.formality)
        assertEquals("PROFESSIONAL", result.communicationStyle)
    }

    @Test
    fun `parseContactClassification accepts legacy type and communication style fields`() {
        val json = """
            {
                "type": "colleague",
                "subtype": "school",
                "communicationStyle": "funny"
            }
        """.trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("COLLEAGUE", result.type)
        assertEquals("school", result.subtype)
        assertEquals("FUNNY", result.communicationStyle)
    }

    @Test
    fun `parseContactClassification defaults missing style with telemetry`() {
        StructuredLogger.clearForTests()
        val json = """{"relationship_type": "FRIEND"}""".trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("WARM", result.communicationStyle)
        val entry = StructuredLogger.getRecent(1).single()
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Classification response missing communication style; using default", entry.message)
        assertEquals("WARM", entry.extras["default"])
    }

    @Test
    fun `parseContactClassification defaults unsupported style with telemetry`() {
        StructuredLogger.clearForTests()
        val json = """{"relationship_type": "FRIEND", "communication_style": "EXPRESSIVE"}""".trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("WARM", result.communicationStyle)
        val entry = StructuredLogger.getRecent(1).single()
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("Classification response had unsupported communication style; using default", entry.message)
        assertEquals("WARM", entry.extras["default"])
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
    fun `parseContactClassification accepts fenced json`() {
        val json = """
            ```JSON
            {
                "type": "COLLEAGUE",
                "confidence": 0.7,
                "language": "en",
                "formality": "FORMAL"
            }
            ```
        """.trimIndent()

        val result = ResponseParser.parseContactClassification(json)

        assertEquals("COLLEAGUE", result.type)
        assertEquals(0.7, result.confidence, 0.001)
        assertEquals("FORMAL", result.formality)
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
        assertEquals(MessageVariantParseStatus.SUCCESS, variants.parseMetadata.status)
        assertEquals(MessageVariantFallbackReason.NONE, variants.parseMetadata.fallbackReason)
    }

    @Test
    fun `parseMessageVariants returns fallback on malformed json`() {
        val variants = ResponseParser.parseMessageVariants("garbage")

        assertEquals("Wishing you a very happy birthday! Hope you have a wonderful day!", variants.short)
        assertEquals("standard", variants.recommended)
        assertTrue(variants.isUsingFallback)
        assertEquals(MessageVariantParseStatus.FALLBACK, variants.parseMetadata.status)
        assertEquals(MessageVariantFallbackReason.MALFORMED_JSON, variants.parseMetadata.fallbackReason)
    }

    @Test
    fun `parseMessageVariants returns anniversary fallback for malformed anniversary response`() {
        val variants = ResponseParser.parseMessageVariants("garbage", eventType = "ANNIVERSARY")

        assertEquals("Happy Anniversary! Wishing you both a lifetime of love and happiness.", variants.standard)
        assertEquals("standard", variants.recommended)
        assertTrue(variants.isUsingFallback)
    }

    @Test
    fun `parseMessageVariants returns work anniversary fallback for malformed work anniversary response`() {
        val variants = ResponseParser.parseMessageVariants("garbage", eventType = "WORK_ANNIVERSARY")

        assertEquals("Congratulations on your work anniversary! Thank you for your hard work and dedication.", variants.standard)
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
        assertEquals(MessageVariantFallbackReason.ERROR_PAYLOAD, variants.parseMetadata.fallbackReason)
    }

    @Test
    fun `parseGiftSuggestions accepts plain fenced json array`() {
        val suggestions = ResponseParser.parseGiftSuggestions(
            """
            ```
            [
                {"name":"Book","reason":"Loves reading","estimatedCostInr":500}
            ]
            ```
            """.trimIndent(),
        )

        assertEquals(1, suggestions.size)
        assertEquals("Book", suggestions.first().name)
        assertEquals(500, suggestions.first().estimatedCostInr)
    }

    @Test
    fun `parseGiftSuggestions accepts wrapped suggestions array`() {
        val suggestions = ResponseParser.parseGiftSuggestions(
            """
            {
                "suggestions": [
                    {"name":"Coffee kit","reason":"Enjoys coffee","estimatedCostInr":1200}
                ]
            }
            """.trimIndent(),
        )

        assertEquals(1, suggestions.size)
        assertEquals("Coffee kit", suggestions.first().name)
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
        assertEquals(MessageVariantParseStatus.FALLBACK, variants.parseMetadata.status)
        assertEquals(MessageVariantFallbackReason.MALFORMED_JSON, variants.parseMetadata.fallbackReason)
    }
}
