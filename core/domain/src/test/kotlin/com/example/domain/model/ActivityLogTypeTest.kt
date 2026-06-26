package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTypeTest {
    @Test
    fun `fromRaw normalizes persisted activity log types`() {
        assertEquals(ActivityLogType.MESSAGE, ActivityLogType.fromRaw(" message "))
        assertEquals(ActivityLogType.EVENT, ActivityLogType.fromRaw("event"))
        assertEquals(ActivityLogType.AI, ActivityLogType.fromRaw("AI"))
        assertEquals(ActivityLogType.ANALYTICS, ActivityLogType.fromRaw("analytics"))
        assertEquals(ActivityLogType.BACKUP, ActivityLogType.fromRaw("backup"))
        assertEquals(ActivityLogType.SYNC, ActivityLogType.fromRaw("sync"))
        assertEquals(ActivityLogType.SETTINGS, ActivityLogType.fromRaw("settings"))
        assertEquals(ActivityLogType.DISPATCH, ActivityLogType.fromRaw("dispatch"))
        assertEquals(ActivityLogType.UNKNOWN, ActivityLogType.fromRaw("NOTICE"))
    }
}
