package com.example.core.resilience

import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.repository.DiagnosticSnapshotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthMonitorDiagnosticRecorderTest {

    @Before
    fun setUp() {
        HealthMonitor.clearForTests()
    }

    @After
    fun tearDown() {
        HealthMonitor.clearForTests()
    }

    @Test
    fun `start persists redacted warning snapshot when HealthMonitor records an error`() = runTest {
        val repository = RecordingDiagnosticSnapshotRepository()
        val recorder = HealthMonitorDiagnosticRecorder(repository)
        recorder.start(this)

        HealthMonitor.recordError(
            context = "Gemini user=aarav@example.com",
            error = "Authorization=Bearer ya29.secret-token phone=+91 98765 43210",
        )
        advanceUntilIdle()

        val snapshot = repository.recorded.single()
        assertEquals(DiagnosticSnapshotSource.HEALTH_MONITOR, snapshot.source)
        assertEquals(DiagnosticSnapshotStatus.WARNING, snapshot.status)
        assertFalse(snapshot.summary.contains("aarav@example.com"))
        assertFalse(snapshot.checksJson.contains("aarav@example.com"))
        assertFalse(snapshot.summary.contains("ya29.secret-token"))
        assertFalse(snapshot.checksJson.contains("ya29.secret-token"))
        assertFalse(snapshot.checksJson.contains("+91 98765 43210"))
        assertTrue(snapshot.checksJson.contains("[REDACTED_EMAIL]"))
        assertTrue(snapshot.checksJson.contains("Bearer [REDACTED]"))
        assertTrue(snapshot.checksJson.contains("[REDACTED_PHONE]"))
    }

    private class RecordingDiagnosticSnapshotRepository : DiagnosticSnapshotRepository {
        val recorded = mutableListOf<DiagnosticSnapshot>()

        override suspend fun record(snapshot: DiagnosticSnapshot) {
            recorded += snapshot
        }

        override suspend fun getRecent(limit: Int): List<DiagnosticSnapshot> = recorded.take(limit)

        override suspend fun getLatestBySource(
            source: DiagnosticSnapshotSource,
        ): DiagnosticSnapshot? = recorded.lastOrNull { it.source == source }
    }
}
