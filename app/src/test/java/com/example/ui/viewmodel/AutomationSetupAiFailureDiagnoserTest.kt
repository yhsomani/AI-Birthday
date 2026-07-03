package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupAiFailureDiagnoserTest {

    private lateinit var context: Context
    private lateinit var diagnoser: AutomationSetupAiFailureDiagnoser

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        diagnoser = AutomationSetupAiFailureDiagnoser(context)
    }

    @Test
    fun `diagnose classifies common provider failures`() {
        assertEquals(
            context.getString(R.string.automation_setup_ai_error_quota),
            diagnoser.diagnose("429 quota exhausted"),
        )
        assertEquals(
            context.getString(R.string.automation_setup_ai_error_auth),
            diagnoser.diagnose("API key permission denied 403"),
        )
        assertEquals(
            context.getString(R.string.automation_setup_ai_error_network),
            diagnoser.diagnose("Network timeout unavailable"),
        )
        assertEquals(
            context.getString(R.string.automation_setup_ai_error_json),
            diagnoser.diagnose("empty response could not parse JSON"),
        )
        assertEquals(
            context.getString(R.string.automation_setup_ai_error_circuit),
            diagnoser.diagnose("circuit breaker open"),
        )
    }

    @Test
    fun `diagnose redacts sensitive fallback text`() {
        val message = diagnoser.diagnose(
            "Unexpected user=aarav@example.com Authorization=Bearer ya29.secret-token phone=+91 98765 43210",
        )

        assertFalse(message.contains("aarav@example.com"))
        assertFalse(message.contains("ya29.secret-token"))
        assertFalse(message.contains("+91 98765 43210"))
        assertTrue(message.contains("[REDACTED_EMAIL]"))
    }
}
