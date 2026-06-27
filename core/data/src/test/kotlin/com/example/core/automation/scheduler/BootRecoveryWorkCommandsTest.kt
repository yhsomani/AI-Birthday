package com.example.core.automation.scheduler

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.example.core.automation.workers.DailyTriggerWorker
import com.example.core.automation.workers.RevivalWorker
import com.example.core.automation.workers.StyleAnalysisWorker
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRecoveryWorkCommandsTest {
    @Test
    fun bootRecoveryRecurringWorkCommands_returnsStableBootRecoverySet() {
        assertEquals(
            listOf(
                BootRecoveryRecurringWorkCommand.DAILY_TRIGGER,
                BootRecoveryRecurringWorkCommand.REVIVAL,
                BootRecoveryRecurringWorkCommand.STYLE_ANALYSIS,
            ),
            bootRecoveryRecurringWorkCommands(),
        )
    }

    @Test
    fun isBootRecoveryRecurringWorkActive_onlyTreatsEnqueuedAndRunningAsActive() {
        val activeStates = WorkInfo.State.values()
            .filter { it.isBootRecoveryRecurringWorkActive() }
            .toSet()

        assertEquals(
            setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING),
            activeStates,
        )
    }

    @Test
    fun toPeriodicWorkRequest_buildsDailyTriggerRequest() {
        assertPeriodicWorkRequest(
            command = BootRecoveryRecurringWorkCommand.DAILY_TRIGGER,
            workerClassName = DailyTriggerWorker::class.java.name,
            intervalMs = TimeUnit.HOURS.toMillis(24),
            networkType = NetworkType.NOT_REQUIRED,
        )
    }

    @Test
    fun toPeriodicWorkRequest_buildsRevivalRequest() {
        assertPeriodicWorkRequest(
            command = BootRecoveryRecurringWorkCommand.REVIVAL,
            workerClassName = RevivalWorker::class.java.name,
            intervalMs = TimeUnit.DAYS.toMillis(7),
            networkType = NetworkType.NOT_REQUIRED,
        )
    }

    @Test
    fun toPeriodicWorkRequest_buildsStyleAnalysisRequest() {
        assertPeriodicWorkRequest(
            command = BootRecoveryRecurringWorkCommand.STYLE_ANALYSIS,
            workerClassName = StyleAnalysisWorker::class.java.name,
            intervalMs = TimeUnit.DAYS.toMillis(14),
            networkType = NetworkType.CONNECTED,
        )
    }

    private fun assertPeriodicWorkRequest(
        command: BootRecoveryRecurringWorkCommand,
        workerClassName: String,
        intervalMs: Long,
        networkType: NetworkType,
    ) {
        val request = command.toPeriodicWorkRequest()
        val workSpec = request.workSpec

        assertEquals(workerClassName, workSpec.workerClassName)
        assertTrue(request.tags.contains(command.tag))
        assertEquals(intervalMs, workSpec.intervalDuration)
        assertTrue(workSpec.constraints.requiresBatteryNotLow())
        assertTrue(workSpec.constraints.requiresStorageNotLow())
        assertEquals(networkType, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), workSpec.backoffDelayDuration)
    }
}
