package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageChannelTest {
    @Test
    fun `fromRaw normalizes known values and rejects unknown values`() {
        assertEquals(MessageChannel.SMS, MessageChannel.fromRaw(" sms "))
        assertEquals(MessageChannel.WHATSAPP, MessageChannel.fromRaw("WhatsApp"))
        assertEquals(MessageChannel.UNKNOWN, MessageChannel.fromRaw("telegram"))
        assertEquals(MessageChannel.UNKNOWN, MessageChannel.fromRaw(null))
    }

    @Test
    fun `orDefault replaces unknown values only`() {
        assertEquals(MessageChannel.EMAIL, MessageChannel.EMAIL.orDefault())
        assertEquals(MessageChannel.SMS, MessageChannel.UNKNOWN.orDefault())
        assertEquals(MessageChannel.WHATSAPP, MessageChannel.UNKNOWN.orDefault(MessageChannel.WHATSAPP))
    }
}
