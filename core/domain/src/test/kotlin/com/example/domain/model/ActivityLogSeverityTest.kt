package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogSeverityTest {
    @Test
    fun `fromRaw normalizes persisted activity log severities`() {
        assertEquals(ActivityLogSeverity.INFO, ActivityLogSeverity.fromRaw(" info "))
        assertEquals(ActivityLogSeverity.WARNING, ActivityLogSeverity.fromRaw("warning"))
        assertEquals(ActivityLogSeverity.ERROR, ActivityLogSeverity.fromRaw("ERROR"))
        assertEquals(ActivityLogSeverity.UNKNOWN, ActivityLogSeverity.fromRaw("NOTICE"))
    }
}
