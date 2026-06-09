package com.example.core.automation.workers

import androidx.work.BackoffPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDispatchWorkRequestsTest {
    @Test
    fun create_appliesDataSafetyConstraintsAndBackoff() {
        val request = MessageDispatchWorkRequests.create("event_1")
        val workSpec = request.workSpec

        assertEquals("event_1", workSpec.input.getString(MessageDispatchWorkRequests.KEY_EVENT_ID))
        assertFalse(workSpec.constraints.requiresBatteryNotLow())
        assertTrue(workSpec.constraints.requiresStorageNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
    }
}
