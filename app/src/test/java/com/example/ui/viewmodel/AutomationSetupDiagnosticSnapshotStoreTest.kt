package com.example.ui.viewmodel

import android.app.Application
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.repository.DiagnosticSnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationSetupDiagnosticSnapshotStoreTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var diagnosticSnapshotRepository: DiagnosticSnapshotRepository

    @Test
    fun `loadRecentPersistedHealthSnapshot returns recent non-ok health snapshot`() = runTest {
        val snapshot = healthSnapshot(
            status = DiagnosticSnapshotStatus.WARNING,
            createdAtMs = 1_000L,
        )
        coEvery {
            diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
        } returns snapshot
        val store = AutomationSetupDiagnosticSnapshotStore(diagnosticSnapshotRepository)

        assertEquals(snapshot, store.loadRecentPersistedHealthSnapshot(nowMs = 1_001L))
    }

    @Test
    fun `loadRecentPersistedHealthSnapshot ignores ok or stale health snapshots`() = runTest {
        coEvery {
            diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
        } returns healthSnapshot(
            status = DiagnosticSnapshotStatus.OK,
            createdAtMs = 1_000L,
        )
        val store = AutomationSetupDiagnosticSnapshotStore(diagnosticSnapshotRepository)

        assertNull(store.loadRecentPersistedHealthSnapshot(nowMs = 1_001L))

        coEvery {
            diagnosticSnapshotRepository.getLatestBySource(DiagnosticSnapshotSource.HEALTH_MONITOR)
        } returns healthSnapshot(
            status = DiagnosticSnapshotStatus.WARNING,
            createdAtMs = 1_000L,
        )

        assertNull(store.loadRecentPersistedHealthSnapshot(nowMs = 1_000L + EIGHT_DAYS_MS))
    }

    @Test
    fun `toDiagnosticSnapshot redacts summary and check payload`() {
        val store = AutomationSetupDiagnosticSnapshotStore(diagnosticSnapshotRepository)

        val snapshot = with(store) { aiDoctorReport().toDiagnosticSnapshot() }

        assertEquals(DiagnosticSnapshotSource.AI_DOCTOR, snapshot.source)
        assertEquals(DiagnosticSnapshotStatus.ACTION_REQUIRED, snapshot.status)
        assertFalse(snapshot.summary.contains("aarav@example.com"))
        assertFalse(snapshot.checksJson.contains("aarav@example.com"))
        assertFalse(snapshot.summary.contains("ya29.secret-token"))
        assertFalse(snapshot.checksJson.contains("ya29.secret-token"))
        assertTrue(snapshot.summary.contains("[REDACTED_EMAIL]"))
        assertTrue(snapshot.checksJson.contains("Bearer [REDACTED]"))
        assertNotNull(snapshot.id)
    }

    @Test
    fun `recordAiDoctorSnapshot skips duplicate report payloads`() = runTest {
        coEvery { diagnosticSnapshotRepository.record(any()) } returns Unit
        val store = AutomationSetupDiagnosticSnapshotStore(diagnosticSnapshotRepository)
        val report = aiDoctorReport()

        store.recordAiDoctorSnapshot(report)
        store.recordAiDoctorSnapshot(report)

        coVerify(exactly = 1) { diagnosticSnapshotRepository.record(any()) }
    }

    @Test
    fun `recordAiDoctorSnapshot records changed report payloads`() = runTest {
        coEvery { diagnosticSnapshotRepository.record(any()) } returns Unit
        val store = AutomationSetupDiagnosticSnapshotStore(diagnosticSnapshotRepository)

        store.recordAiDoctorSnapshot(aiDoctorReport())
        store.recordAiDoctorSnapshot(aiDoctorReport(summaryTitle = "AI issue changed"))

        coVerify(exactly = 2) { diagnosticSnapshotRepository.record(any()) }
    }

    private fun aiDoctorReport(
        summaryTitle: String = "AI issue for aarav@example.com",
    ): AiDoctorReport {
        return AiDoctorReport(
            summary = AiDoctorSummary(
                title = summaryTitle,
                detail = "Authorization=Bearer ya29.secret-token",
                status = ReadinessStatus.ACTION_REQUIRED,
            ),
            checks = listOf(
                ReadinessCheck(
                    title = "Gemini",
                    detail = "Unexpected aarav@example.com Authorization=Bearer ya29.secret-token",
                    status = ReadinessStatus.ACTION_REQUIRED,
                    action = AiDoctorAction.TEST_AI,
                    actionLabel = "Test AI",
                    group = ReadinessGroup.REQUIRED,
                ),
            ),
            recommendedFix = null,
            setupProgress = SetupProgressSummary(
                completedSteps = 0,
                totalSteps = 1,
                actionRequiredCount = 1,
            ),
            setupActionReadiness = RelationshipActionReadinessPolicy.fromSetupCandidates(emptyList()),
        )
    }

    private fun healthSnapshot(
        status: DiagnosticSnapshotStatus,
        createdAtMs: Long,
    ): DiagnosticSnapshot {
        return DiagnosticSnapshot(
            id = DiagnosticSnapshotId("health"),
            source = DiagnosticSnapshotSource.HEALTH_MONITOR,
            status = status,
            summary = "Health",
            checksJson = "{}",
            createdAtMs = createdAtMs,
        )
    }

    private companion object {
        const val EIGHT_DAYS_MS = 8L * 24 * 60 * 60 * 1000
    }
}
