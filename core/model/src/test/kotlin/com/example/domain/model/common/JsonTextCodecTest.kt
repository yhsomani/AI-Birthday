package com.example.domain.model.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTextCodecTest {
    @Test
    fun `parseStringArray reads escaped string arrays and rejects malformed input`() {
        assertEquals(
            listOf("tea", "line\nbreak", "quote\"mark"),
            JsonTextCodec.parseStringArray("""["tea","line\nbreak","quote\"mark"]"""),
        )
        assertTrue(JsonTextCodec.parseStringArray("not json").isEmpty())
        assertTrue(JsonTextCodec.parseStringArray("""["ok", 1]""").isEmpty())
    }

    @Test
    fun `hasStringArrayContent detects valid arrays and preserves legacy loose signals`() {
        assertTrue(JsonTextCodec.hasStringArrayContent("""["tea"]"""))
        assertTrue(JsonTextCodec.hasStringArrayContent("legacy value"))
        assertTrue(JsonTextCodec.hasStringArrayContent("""[1]"""))

        assertEquals(1, JsonTextCodec.countStringArrayItems("""["tea"]"""))
        assertEquals(0, JsonTextCodec.countStringArrayItems("""[1]"""))
        assertFalse(JsonTextCodec.hasStringArrayContent("""[]"""))
        assertFalse(JsonTextCodec.hasStringArrayContent("""[ ]"""))
        assertFalse(JsonTextCodec.hasStringArrayContent(" "))
        assertFalse(JsonTextCodec.hasStringArrayContent(null))
    }

    @Test
    fun `readStringField decodes escaped nonblank values`() {
        val raw = """{"phase":"Moved to \"Pune\"","blank":"   ","count":42,"negative":-7}"""

        assertEquals("Moved to \"Pune\"", JsonTextCodec.readStringField(raw, "phase"))
        assertNull(JsonTextCodec.readStringField(raw, "blank"))
        assertNull(JsonTextCodec.readStringField(raw, "missing"))
        assertEquals(42, JsonTextCodec.readIntField(raw, "count"))
        assertEquals(-7, JsonTextCodec.readIntField(raw, "negative"))
        assertNull(JsonTextCodec.readIntField(raw, "phase"))
    }

    @Test
    fun `encodeStringArray and encodeObject escape values deterministically`() {
        assertEquals(
            """["tea","quote\"mark","line\nbreak"]""",
            JsonTextCodec.encodeStringArray(listOf("tea", "quote\"mark", "line\nbreak")),
        )
        assertEquals(
            """{"name":"Asha","count":2,"active":true,"tags":["tea","music"],"missing":null}""",
            JsonTextCodec.encodeObject(
                listOf(
                    "name" to "Asha",
                    "count" to 2,
                    "active" to true,
                    "tags" to listOf("tea", "music"),
                    "missing" to null,
                )
            ),
        )
    }
}
