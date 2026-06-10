package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainValueParsingTest {

    @Test
    fun `message status parser handles legacy casing and invalid values`() {
        assertEquals(MessageStatus.SENT, MessageStatus.fromRaw("sent"))
        assertEquals(MessageStatus.APPROVED, MessageStatus.fromRaw(" APPROVED "))
        assertEquals(MessageStatus.UNKNOWN, MessageStatus.fromRaw("archived"))
        assertEquals(MessageStatus.UNKNOWN, MessageStatus.fromRaw(null))
    }

    @Test
    fun `message channel parser handles legacy casing and invalid values`() {
        assertEquals(MessageChannel.SMS, MessageChannel.fromRaw("sms"))
        assertEquals(MessageChannel.WHATSAPP, MessageChannel.fromRaw("WhatsApp"))
        assertEquals(MessageChannel.UNKNOWN, MessageChannel.fromRaw("telegram"))
        assertEquals(MessageChannel.UNKNOWN, MessageChannel.fromRaw(null))
    }

    @Test
    fun `approval mode parser handles legacy casing and invalid values`() {
        assertEquals(ApprovalMode.FULLY_AUTO, ApprovalMode.fromRaw("fully_auto"))
        assertEquals(ApprovalMode.SMART_APPROVE, ApprovalMode.fromRaw("SMART_APPROVE"))
        assertEquals(ApprovalMode.UNKNOWN, ApprovalMode.fromRaw("manual_only"))
        assertEquals(ApprovalMode.UNKNOWN, ApprovalMode.fromRaw(null))
    }

    @Test
    fun `event type parser handles legacy casing and invalid values`() {
        assertEquals(EventType.BIRTHDAY, EventType.fromRaw("birthday"))
        assertEquals(EventType.WORK_ANNIVERSARY, EventType.fromRaw("WORK_ANNIVERSARY"))
        assertEquals(EventType.UNKNOWN, EventType.fromRaw("holiday"))
        assertEquals(EventType.UNKNOWN, EventType.fromRaw(null))
    }
}
