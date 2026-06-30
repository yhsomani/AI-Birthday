package com.example.core.automation.scheduler

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRecoveryIntentActionTest {

    @Test
    fun isScheduleRecoveryIntentAction_acceptsActionsThatCanInvalidateScheduledAutomation() {
        assertTrue(isScheduleRecoveryIntentAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(isScheduleRecoveryIntentAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(isScheduleRecoveryIntentAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(isScheduleRecoveryIntentAction(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun isScheduleRecoveryIntentAction_rejectsUnrelatedActions() {
        assertFalse(isScheduleRecoveryIntentAction(null))
        assertFalse(isScheduleRecoveryIntentAction(Intent.ACTION_AIRPLANE_MODE_CHANGED))
    }
}
