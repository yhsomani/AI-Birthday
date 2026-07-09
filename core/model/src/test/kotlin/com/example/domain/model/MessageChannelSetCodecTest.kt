package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageChannelSetCodecTest {
    @Test
    fun `parse maps quoted channel tokens and ignores casing`() {
        val channels = MessageChannelSetCodec.parse("[\"WHATSAPP\", \"sms\"]")

        assertEquals(setOf(MessageChannel.WHATSAPP, MessageChannel.SMS), channels)
    }

    @Test
    fun `parse ignores unknown duplicate and unquoted values`() {
        val channels = MessageChannelSetCodec.parse("[\"SMS\", \"TELEGRAM\", \"sms\", \"UNKNOWN\", EMAIL]")

        assertEquals(setOf(MessageChannel.SMS), channels)
        assertTrue(MessageChannelSetCodec.parse("SMS,EMAIL").isEmpty())
        assertTrue(MessageChannelSetCodec.parse("").isEmpty())
        assertTrue(MessageChannelSetCodec.parse(null).isEmpty())
    }

    @Test
    fun `toJsonArray writes deterministic known channel arrays`() {
        val json = MessageChannelSetCodec.toJsonArray(
            listOf(MessageChannel.WHATSAPP, MessageChannel.UNKNOWN, MessageChannel.SMS, MessageChannel.SMS),
        )

        assertEquals("[\"SMS\",\"WHATSAPP\"]", json)
    }
}
