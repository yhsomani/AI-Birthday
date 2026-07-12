package com.yashsomani.birthdayautopilot.automation.workers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.util.concurrent.ListenableFuture
import com.yashsomani.birthdayautopilot.MainApplication
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-29-compatible runtime coverage for the production WorkManager graph.
 *
 * The test WorkManager records the real requests while [HoldingWorkerFactory] prevents any
 * production worker body from running. Assertions therefore cover scheduling, receiver and
 * factory wiring without opening the protected database, contacting Google People, or touching
 * an SMS provider.
 */
@RunWith(AndroidJUnit4::class)
class AutomationWorkRuntimeInstrumentationTest {
  private lateinit var context: Context
  private lateinit var workManager: WorkManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    HoldingWorker.releaseAll()
    val executor = SynchronousExecutor()
    val configuration = Configuration.Builder()
      .setExecutor(executor)
      .setTaskExecutor(executor)
      .setWorkerFactory(HoldingWorkerFactory())
      .setMinimumLoggingLevel(Log.ERROR)
      .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    workManager = WorkManager.getInstance(context)
    AutomationScheduler.resetNetworkAttemptCoalescerForTests(context)
    assertNotNull(WorkManagerTestInitHelper.getTestDriver(context))
  }

  @After
  fun tearDown() {
    runCatching { workManager.cancelAllWork().result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
    AutomationScheduler.resetNetworkAttemptCoalescerForTests(context)
    HoldingWorker.releaseAll()
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun ensureScheduledCreatesTheCompleteProductionInventoryWithExactTimingAndConstraints() {
    AutomationScheduler.ensureScheduled(context)

    val reconcile = onlyUnique(RECONCILE_NAME)
    assertPeriodic(
      reconcile,
      ReconcileWorker::class.java,
      repeatHours = null,
      repeatMinutes = 15,
      flexHours = null,
      flexMinutes = 15,
      networkType = NetworkType.NOT_REQUIRED,
      requiredTags = setOf(AutomationScheduler.RECONCILE_TAG),
    )
    assertEquals(
      "PERIODIC",
      spec(reconcile).input.getString(AutomationScheduler.INPUT_TRIGGER),
    )

    val people = onlyUnique(AutomationScheduler.PEOPLE_SYNC_WORK_NAME)
    assertPeriodic(
      people,
      PeopleSyncWorker::class.java,
      repeatHours = 24,
      repeatMinutes = null,
      flexHours = 6,
      flexMinutes = null,
      networkType = NetworkType.CONNECTED,
      requiredTags = setOf(AutomationScheduler.PEOPLE_SYNC_TAG),
    )

    val retention = onlyUnique(AutomationScheduler.DATA_RETENTION_PERIODIC_WORK_NAME)
    assertPeriodic(
      retention,
      DataRetentionWorker::class.java,
      repeatHours = 24,
      repeatMinutes = null,
      flexHours = 24,
      flexMinutes = null,
      networkType = NetworkType.NOT_REQUIRED,
      requiredTags = setOf(AutomationScheduler.DATA_RETENTION_TAG),
    )

    val startupRetention = onlyUnique(AutomationScheduler.DATA_RETENTION_STARTUP_WORK_NAME)
    val startupSpec = spec(startupRetention)
    assertEquals(DataRetentionWorker::class.java.name, startupSpec.workerClassName)
    assertEquals(WorkInfo.State.RUNNING, startupRetention.state)
    assertEquals(0L, startupRetention.initialDelayMillis)
    assertEquals(null, startupRetention.periodicityInfo)
    assertTrue(startupRetention.tags.contains(AutomationScheduler.DATA_RETENTION_TAG))
    assertEquals(NetworkType.NOT_REQUIRED, startupRetention.constraints.requiredNetworkType)

    // AutomationScheduler also owns reconstruction through its delegated outcome scheduler.
    val evidence = onlyUnique(SMS_RECONSTRUCTION_NAME)
    assertPeriodic(
      evidence,
      SmsEvidenceWorker::class.java,
      repeatHours = null,
      repeatMinutes = 15,
      flexHours = null,
      flexMinutes = 15,
      networkType = NetworkType.NOT_REQUIRED,
      requiredTags = setOf("sms-evidence", "sms-reconstruction"),
    )
  }

  @Test
  fun repeatedEnsureUsesPeriodicUpdateAndKeepsTheInFlightStartupPrune() {
    AutomationScheduler.ensureScheduled(context)
    val periodicNames = listOf(
      RECONCILE_NAME,
      AutomationScheduler.PEOPLE_SYNC_WORK_NAME,
      AutomationScheduler.DATA_RETENTION_PERIODIC_WORK_NAME,
      SMS_RECONSTRUCTION_NAME,
    )
    val beforePeriodic = periodicNames.associateWith(::onlyUnique)
    val beforeStartup = onlyUnique(AutomationScheduler.DATA_RETENTION_STARTUP_WORK_NAME)

    AutomationScheduler.ensureScheduled(context)

    periodicNames.forEach { name ->
      val before = checkNotNull(beforePeriodic[name])
      val after = onlyUnique(name)
      assertEquals("UPDATE must retain the unique periodic identity for $name", before.id, after.id)
      assertEquals(
        "UPDATE must advance the generation for $name",
        before.generation + 1,
        after.generation,
      )
      assertEquals(1, unique(name).size)
    }
    val afterStartup = onlyUnique(AutomationScheduler.DATA_RETENTION_STARTUP_WORK_NAME)
    assertEquals(beforeStartup.id, afterStartup.id)
    assertEquals(beforeStartup.generation, afterStartup.generation)
    assertEquals(1, unique(AutomationScheduler.DATA_RETENTION_STARTUP_WORK_NAME).size)
  }

  @Test
  fun immediateLocalKeepPreservesTheFirstPendingTrigger() {
    AutomationScheduler.enqueueImmediateLocal(context, "BOOT_OR_CLOCK")
    val before = onlyUnique(LOCAL_RECONCILE_NAME)

    AutomationScheduler.enqueueImmediateLocal(context, "APP_REPLACED")

    val after = onlyUnique(LOCAL_RECONCILE_NAME)
    assertEquals(before.id, after.id)
    assertEquals(before.generation, after.generation)
    assertEquals("BOOT_OR_CLOCK", spec(after).input.getString(AutomationScheduler.INPUT_TRIGGER))
    assertEquals(ReconcileWorker::class.java.name, spec(after).workerClassName)
    assertTrue(after.tags.contains("birthday-local-reconcile"))
  }

  @Test
  fun accountAndContactChangesAreNotHiddenByAnInFlightForegroundReconcile() {
    AutomationScheduler.enqueueImmediateLocal(context, "FOREGROUND")
    val foreground = onlyUnique(LOCAL_RECONCILE_NAME)

    AutomationScheduler.enqueueAccountChange(context)
    val accountChange = onlyUnique(AutomationScheduler.STATE_CHANGE_RECONCILE_WORK_NAME)
    assertNotEquals(foreground.id, accountChange.id)
    assertEquals(
      "ACCOUNT_CHANGED",
      spec(accountChange).input.getString(AutomationScheduler.INPUT_TRIGGER),
    )

    AutomationScheduler.enqueueContactChange(context)

    val stateChanges = unique(AutomationScheduler.STATE_CHANGE_RECONCILE_WORK_NAME)
      .filterNot { it.state.isFinished }
    assertEquals(2, stateChanges.size)
    assertEquals(
      setOf("ACCOUNT_CHANGED", "CONTACTS_CHANGED"),
      stateChanges.map { spec(it).input.getString(AutomationScheduler.INPUT_TRIGGER) }.toSet(),
    )
    stateChanges.forEach { info ->
      assertEquals(ReconcileWorker::class.java.name, spec(info).workerClassName)
      assertTrue(info.tags.contains(AutomationScheduler.RECONCILE_TAG))
      assertTrue(info.tags.contains(AutomationScheduler.STATE_CHANGE_RECONCILE_TAG))
    }
    assertEquals(foreground.id, onlyUnique(LOCAL_RECONCILE_NAME).id)
  }

  @Test
  fun networkAttemptReplaceRecoversAClosedChainAndCoalescesToTheEarliestWake() {
    val operationKey = "birthday_${"a".repeat(64)}"
    val uniqueName = "birthday-attempt-v1-$operationKey"
    val runAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
    AutomationScheduler.scheduleNetworkAttempt(context, operationKey, runAt)
    val first = onlyUnfinishedUnique(uniqueName)
    assertEquals(WorkInfo.State.ENQUEUED, first.state)

    workManager.cancelUniqueWork(uniqueName).result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    AutomationScheduler.scheduleNetworkAttempt(context, operationKey, runAt)
    val replacement = onlyUnfinishedUnique(uniqueName)
    assertNotEquals(first.id, replacement.id)
    assertEquals(WorkInfo.State.ENQUEUED, replacement.state)

    AutomationScheduler.scheduleNetworkAttempt(
      context,
      operationKey,
      runAt + TimeUnit.MINUTES.toMillis(2),
    )
    AutomationScheduler.scheduleNetworkAttempt(context, operationKey, runAt)
    repeat(32) {
      AutomationScheduler.scheduleNetworkAttempt(
        context,
        operationKey,
        runAt + TimeUnit.MINUTES.toMillis(1),
      )
    }
    val unfinished = unique(uniqueName).filterNot { it.state.isFinished }
    assertEquals(1, unfinished.size)
    assertEquals(WorkInfo.State.ENQUEUED, unfinished.single().state)
    unfinished.forEach { info ->
      val workSpec = spec(info)
      assertEquals(ReconcileWorker::class.java.name, workSpec.workerClassName)
      assertEquals("NEXT_WINDOW", workSpec.input.getString(AutomationScheduler.INPUT_TRIGGER))
      assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
      assertTrue(info.tags.contains("birthday-network-attempt"))
      assertTrue(info.initialDelayMillis in 1..TimeUnit.MINUTES.toMillis(5))
      assertEquals(
        runAt,
        workSpec.input.getLong(AutomationScheduler.INPUT_ATTEMPT_RUN_AT_MILLIS, -1),
      )
    }

    val active = unfinished.single()
    val activeInput = spec(active).input
    AutomationScheduler.consumeNetworkAttempt(context, activeInput)
    val earlierWhileActive = runAt - TimeUnit.MINUTES.toMillis(1)
    AutomationScheduler.scheduleNetworkAttempt(context, operationKey, earlierWhileActive)
    assertEquals(
      "an active worker must not be cancelled by a concurrent wake request",
      active.id,
      onlyUnfinishedUnique(uniqueName).id,
    )

    AutomationScheduler.completeNetworkAttempt(context, activeInput)
    val deferred = onlyUnfinishedUnique(uniqueName)
    assertNotEquals(active.id, deferred.id)
    assertEquals(
      earlierWhileActive,
      spec(deferred).input.getLong(AutomationScheduler.INPUT_ATTEMPT_RUN_AT_MILLIS, -1),
    )
  }

  @Test
  fun productionWorkerFactoryCreatesEveryCoreAutomationWorker() {
    val application = context.applicationContext as MainApplication
    val factory = application.workManagerConfiguration.workerFactory
    assertTrue(factory is BirthdayWorkerFactory)

    val reconcile = TestListenableWorkerBuilder<ReconcileWorker>(context)
      .setWorkerFactory(factory)
      .build()
    val people = TestListenableWorkerBuilder<PeopleSyncWorker>(context)
      .setWorkerFactory(factory)
      .build()
    val retention = TestListenableWorkerBuilder<DataRetentionWorker>(context)
      .setWorkerFactory(factory)
      .build()

    assertEquals(ReconcileWorker::class.java, reconcile.javaClass)
    assertEquals(PeopleSyncWorker::class.java, people.javaClass)
    assertEquals(DataRetentionWorker::class.java, retention.javaClass)
  }

  @Test
  fun lifecycleReceiverIsPrivateEnabledAndRegisteredForRecoverySignals() {
    val component = ComponentName(context, AutomationReconcileReceiver::class.java)
    val receiverInfo = context.packageManager.getReceiverInfo(component, 0)
    assertTrue(receiverInfo.enabled)
    assertFalse(receiverInfo.exported)
    assertEquals(
      PackageManager.PERMISSION_GRANTED,
      context.packageManager.checkPermission(
        android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
        context.packageName,
      ),
    )

    RECOVERY_ACTIONS.keys.forEach { action ->
      val matches = context.packageManager.queryBroadcastReceivers(
        Intent(action).setPackage(context.packageName),
        0,
      )
      assertTrue(
        "$action must resolve to AutomationReconcileReceiver",
        matches.any { it.activityInfo?.name == AutomationReconcileReceiver::class.java.name },
      )
    }
  }

  @Test
  fun receiverMapsBootTimezoneAndPackageReplacementToBoundedLocalReconciliation() {
    val receiver = AutomationReconcileReceiver()

    RECOVERY_ACTIONS.forEach { (action, expectedTrigger) ->
      receiver.onReceive(context, Intent(action))
      val info = onlyUnfinishedUnique(LOCAL_RECONCILE_NAME)
      val workSpec = spec(info)
      assertEquals(ReconcileWorker::class.java.name, workSpec.workerClassName)
      assertEquals(expectedTrigger, workSpec.input.getString(AutomationScheduler.INPUT_TRIGGER))
      assertEquals(NetworkType.NOT_REQUIRED, workSpec.constraints.requiredNetworkType)
      assertTrue(info.tags.contains(AutomationScheduler.RECONCILE_TAG))
      assertTrue(info.tags.contains("birthday-local-reconcile"))

      workManager.cancelUniqueWork(LOCAL_RECONCILE_NAME).result
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      assertTrue(unique(LOCAL_RECONCILE_NAME).all { it.state.isFinished })
    }
  }

  @Test
  fun receiverIgnoresMissingAndUnrecognizedActions() {
    val receiver = AutomationReconcileReceiver()

    receiver.onReceive(context, Intent())
    receiver.onReceive(context, Intent("com.example.FORGED_RECOVERY_SIGNAL"))

    assertTrue(unique(LOCAL_RECONCILE_NAME).isEmpty())
  }

  private fun assertPeriodic(
    info: WorkInfo,
    workerClass: Class<out ListenableWorker>,
    repeatHours: Long?,
    repeatMinutes: Long?,
    flexHours: Long?,
    flexMinutes: Long?,
    networkType: NetworkType,
    requiredTags: Set<String>,
  ) {
    val periodicity = checkNotNull(info.periodicityInfo)
    val expectedRepeat = repeatHours?.let(TimeUnit.HOURS::toMillis)
      ?: TimeUnit.MINUTES.toMillis(checkNotNull(repeatMinutes))
    val expectedFlex = flexHours?.let(TimeUnit.HOURS::toMillis)
      ?: TimeUnit.MINUTES.toMillis(checkNotNull(flexMinutes))
    assertEquals(expectedRepeat, periodicity.repeatIntervalMillis)
    assertEquals(expectedFlex, periodicity.flexIntervalMillis)
    assertEquals(networkType, info.constraints.requiredNetworkType)
    assertTrue(info.tags.containsAll(requiredTags))
    assertEquals(workerClass.name, spec(info).workerClassName)
  }

  private fun onlyUnique(name: String): WorkInfo = unique(name).single()

  private fun onlyUnfinishedUnique(name: String): WorkInfo =
    unique(name).filterNot { it.state.isFinished }.single()

  private fun unique(name: String): List<WorkInfo> =
    workManager.getWorkInfosForUniqueWork(name).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

  private fun spec(info: WorkInfo): WorkSpec {
    val implementation = workManager as WorkManagerImpl
    return checkNotNull(implementation.workDatabase.workSpecDao().getWorkSpec(info.id.toString()))
  }

  private class HoldingWorkerFactory : WorkerFactory() {
    override fun createWorker(
      appContext: Context,
      workerClassName: String,
      workerParameters: WorkerParameters,
    ): ListenableWorker = HoldingWorker(appContext, workerParameters)
  }

  private class HoldingWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
  ) : ListenableWorker(appContext, workerParameters) {
    override fun startWork(): ListenableFuture<Result> = CallbackToFutureAdapter.getFuture {
      completer: CallbackToFutureAdapter.Completer<Result> ->
      synchronized(completers) { completers += completer }
      "holding-work-$id"
    }

    companion object {
      private val completers = mutableListOf<CallbackToFutureAdapter.Completer<Result>>()

      fun releaseAll() {
        val pending = synchronized(completers) {
          completers.toList().also { completers.clear() }
        }
        pending.forEach { it.set(Result.success()) }
      }
    }
  }

  private companion object {
    const val TIMEOUT_SECONDS = 10L
    const val RECONCILE_NAME = "birthday-reconcile-v1"
    const val LOCAL_RECONCILE_NAME = "birthday-local-reconcile-v1"
    const val SMS_RECONSTRUCTION_NAME = "sms-outcome-reconstruction-v1"
    val RECOVERY_ACTIONS = linkedMapOf(
      Intent.ACTION_BOOT_COMPLETED to "BOOT_OR_CLOCK",
      Intent.ACTION_TIMEZONE_CHANGED to "BOOT_OR_CLOCK",
      Intent.ACTION_MY_PACKAGE_REPLACED to "APP_REPLACED",
    )
  }
}
