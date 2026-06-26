package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogStatusTest {
    @Test
    fun `fromRaw normalizes persisted activity log statuses`() {
        assertEquals(ActivityLogStatus.OPEN, ActivityLogStatus.fromRaw(" open "))
        assertEquals(ActivityLogStatus.RESOLVED, ActivityLogStatus.fromRaw("resolved"))
        assertEquals(ActivityLogStatus.UNKNOWN, ActivityLogStatus.fromRaw("IGNORED"))
    }
}
