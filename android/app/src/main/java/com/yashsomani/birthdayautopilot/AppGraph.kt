package com.yashsomani.birthdayautopilot

import android.content.Context
import android.os.SystemClock
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.yashsomani.birthdayautopilot.auth.AndroidContactsAuthorizationGateway
import com.yashsomani.birthdayautopilot.auth.AndroidGoogleIdentityCoordinator
import com.yashsomani.birthdayautopilot.auth.ForegroundActivityRegistry
import com.yashsomani.birthdayautopilot.auth.AndroidIdentityConfigurationResolver
import com.yashsomani.birthdayautopilot.auth.FirebaseAccountBindingProvider
import com.yashsomani.birthdayautopilot.auth.IdentityConfigurationResult
import com.yashsomani.birthdayautopilot.auth.LifecycleRepairAccountBindingPolicy
import com.yashsomani.birthdayautopilot.auth.NativeAccountBinding
import com.yashsomani.birthdayautopilot.auth.RoomIdentityAccountStore
import com.yashsomani.birthdayautopilot.auth.AuthorizationResolutionLauncher
import com.yashsomani.birthdayautopilot.auth.ResolutionLaunchResult
import com.yashsomani.birthdayautopilot.automation.orchestration.AndroidAutomationOrchestrator
import com.yashsomani.birthdayautopilot.automation.orchestration.AndroidAutomationTimeSource
import com.yashsomani.birthdayautopilot.automation.orchestration.AndroidFinalExternalGateSource
import com.yashsomani.birthdayautopilot.automation.orchestration.FirebaseAutomationCoordinationPort
import com.yashsomani.birthdayautopilot.automation.orchestration.NoBackupInstallationIdentityStore
import com.yashsomani.birthdayautopilot.automation.sms.AndroidSmsGateway
import com.yashsomani.birthdayautopilot.automation.sms.SubmissionGate
import com.yashsomani.birthdayautopilot.automation.workers.BirthdayWorkerFactory
import com.yashsomani.birthdayautopilot.coordination.ActiveRoomAccountBindingPredicate
import com.yashsomani.birthdayautopilot.coordination.FirebaseCoordinationRuntime
import com.yashsomani.birthdayautopilot.core.crypto.StorageKeyUnavailableException
import com.yashsomani.birthdayautopilot.core.crypto.DatabaseKeyManager
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleStateStore
import com.yashsomani.birthdayautopilot.lifecycle.DeletionRecoveryIdentitySessionGuard
import com.yashsomani.birthdayautopilot.lifecycle.DeletionRecoveryStartupPolicy
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleJournalStatus
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleRepairIdentityPolicy
import com.yashsomani.birthdayautopilot.gemini.AndroidGeminiSuggestionGateway
import com.yashsomani.birthdayautopilot.people.AndroidNetworkAvailability
import com.yashsomani.birthdayautopilot.people.AndroidPeopleSyncService
import com.yashsomani.birthdayautopilot.people.PeopleHttpTransport
import com.yashsomani.birthdayautopilot.people.PeopleSyncLimits
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.readiness.AndroidReadinessProbe
import com.yashsomani.birthdayautopilot.readiness.ReadinessEvaluator
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.EncryptedDatabaseFactory
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

private data class LifecycleRepairIdentityLease(
  val accountId: String,
  val expiresAtElapsedMillis: Long,
)

class AppGraph private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val lifecycleStateStore = LifecycleStateStore(appContext)
  private val installationIdentityStore = NoBackupInstallationIdentityStore(appContext)
  @Volatile private var lifecycleRepairIdentityLease: LifecycleRepairIdentityLease? = null
  private val lifecycleRepairSessionCleanupExecutor =
    Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(runnable, "LifecycleRepairSessionCleanup").apply { isDaemon = true }
    }
  private val startupLocalWipeRecoverySucceeded = recoverInterruptedLocalWipe()
  private val deletionRecoveryIdentitySessionGuard = DeletionRecoveryIdentitySessionGuard(
    boundaryRequired = ::deletionRecoveryIdentitySessionBoundaryRequired,
    clearSession = ::clearIdentitySession,
  )

  init {
    // This is deliberately eager: a process can die after recovery Firebase sign-in but before
    // replay/sign-out. Clear any restored SDK session before the bridge, workers, or account
    // binding predicates can observe it. Failure remains fail-closed in identitySessionMatches.
    deletionRecoveryIdentitySessionGuard.clearIfRequired()
  }

  val database: BirthdayDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    if (!startupLocalWipeRecoverySucceeded || lifecycleStateStore.pendingLocalWipe() != null) {
      throw StorageKeyUnavailableException("local-wipe-recovery-pending")
    }
    val callbackGeneration = installationIdentityStore.getOrCreate()?.callbackGeneration
      ?: throw StorageKeyUnavailableException("installation-identity-unavailable")
    EncryptedDatabaseFactory.create(appContext).also { database ->
      runBlocking {
        database.birthdayDao().initializeIfAbsent(callbackGeneration)
      }
    }
  }
  internal val peopleSyncDao by lazy { database.peopleSyncDao() }
  internal val automationOrchestrationDao by lazy { database.automationOrchestrationDao() }
  internal val safetyLedgerDao by lazy { database.safetyLedgerDao() }
  private val identityAccountStore by lazy { RoomIdentityAccountStore(peopleSyncDao) }
  internal val googleIdentityCoordinator by lazy {
    AndroidGoogleIdentityCoordinator(
      context = appContext,
      environment = BuildConfig.APP_ENV,
      activityProvider = ForegroundActivityRegistry::current,
      accountStore = identityAccountStore,
    )
  }
  private val peopleLimits by lazy { PeopleSyncLimits() }
  private val contactsAuthorizationGateway by lazy {
    AndroidContactsAuthorizationGateway(
      context = appContext,
      environment = BuildConfig.APP_ENV,
      activityProvider = ForegroundActivityRegistry::current,
      resolutionLauncher = AuthorizationResolutionLauncher { pendingIntent ->
        ForegroundActivityRegistry.currentResolutionLauncher()?.launch(pendingIntent)
          ?: ResolutionLaunchResult.Failed
      },
    )
  }
  internal val peopleSyncService by lazy {
    AndroidPeopleSyncService(
      dao = peopleSyncDao,
      authorizationGateway = contactsAuthorizationGateway,
      transport = PeopleHttpTransport(
        networkAvailability = AndroidNetworkAvailability(appContext),
        maxPageBytes = peopleLimits.maxPageBytes,
      ),
      limits = peopleLimits,
    )
  }
  internal val geminiSuggestionGateway by lazy {
    AndroidGeminiSuggestionGateway(appContext) {
      runBlocking { peopleSyncDao.activeAccount()?.let(::identitySessionMatches) == true }
    }
  }
  internal val coordinationRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    FirebaseCoordinationRuntime.resolve(
      context = appContext,
      environment = BuildConfig.APP_ENV,
      accountBindingPredicate = ActiveRoomAccountBindingPredicate {
        peopleSyncDao.activeAccount()
      },
    )
  }

  internal fun identitySessionMatches(account: AccountRecordEntity): Boolean {
    if (!deletionRecoveryIdentitySessionGuard.ordinaryIdentityUseAllowed()) return false
    return currentIdentitySessionMatches(account)
  }

  private fun currentIdentitySessionMatches(account: AccountRecordEntity): Boolean {
    val configuration = AndroidIdentityConfigurationResolver(appContext, BuildConfig.APP_ENV).resolve()
    if (configuration !is IdentityConfigurationResult.Ready) return false
    val binding = FirebaseAccountBindingProvider(configuration.configuration.firebaseApp).current()
      ?: return false
    return binding.firebaseUid == account.firebaseUid &&
      StablePrivateId.prefixed("a", "FirebaseAccount.v1", binding.firebaseUid) == account.accountId &&
      StablePrivateId.hash("GoogleSubject.v1", binding.googleSubject) == account.googleSubjectHash
  }

  /**
   * Grants only lifecycle repair a short, in-memory lease after an explicit foreground sign-in.
   * Ordinary setup and automation remain blocked by identitySessionMatches.
   */
  @Synchronized
  internal fun authorizeLifecycleRepairIdentitySession(account: AccountRecordEntity): Boolean {
    if (lifecycleRepairAccount() != account) return false
    if (!currentIdentitySessionMatches(account)) return false
    val now = SystemClock.elapsedRealtime().coerceAtLeast(0)
    val expiresAt = (now + LIFECYCLE_REPAIR_REAUTH_MILLIS).takeIf { it >= now }
      ?: Long.MAX_VALUE
    lifecycleRepairIdentityLease = LifecycleRepairIdentityLease(account.accountId, expiresAt)
    scheduleLifecycleRepairLeaseCleanup(checkNotNull(lifecycleRepairIdentityLease))
    return true
  }

  internal fun lifecycleRepairAccount(): AccountRecordEntity? {
    if (lifecycleStateStore.journalStatus() != LifecycleJournalStatus.UNREADABLE) return null
    val account = runCatching { runBlocking { peopleSyncDao.activeAccount() } }.getOrNull()
      ?.takeIf { it.state == AccountRecordState.ACTIVE && it.activeSlot == 1 }
    return account.takeIf {
      LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        lifecycleStateStore.independentDeletionReceiptLookup(),
        hasPreexistingActiveAccount = account != null,
      )
    }
  }

  internal fun matchesLifecycleRepairGoogleSubject(
    account: AccountRecordEntity,
    googleSubject: String,
  ): Boolean = LifecycleRepairAccountBindingPolicy.matchesGoogleSubject(
    account.googleSubjectHash,
    googleSubject,
  )

  internal fun matchesLifecycleRepairBinding(
    account: AccountRecordEntity,
    binding: NativeAccountBinding,
  ): Boolean = LifecycleRepairAccountBindingPolicy.matchesBinding(
    expectedAccountId = account.accountId,
    expectedFirebaseUid = account.firebaseUid,
    expectedGoogleSubjectHash = account.googleSubjectHash,
    binding = binding,
  )

  @Synchronized
  internal fun lifecycleRepairIdentitySessionMatches(account: AccountRecordEntity): Boolean {
    val lease = lifecycleRepairIdentityLease ?: return false
    if (lifecycleStateStore.journalStatus() != LifecycleJournalStatus.UNREADABLE) {
      lifecycleRepairIdentityLease = null
      return false
    }
    if (
      !LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        lifecycleStateStore.independentDeletionReceiptLookup(),
        hasPreexistingActiveAccount = true,
      ) ||
      lease.accountId != account.accountId ||
      SystemClock.elapsedRealtime().coerceAtLeast(0) > lease.expiresAtElapsedMillis ||
      !currentIdentitySessionMatches(account)
    ) {
      clearLifecycleRepairIdentitySession()
      return false
    }
    return true
  }

  @Synchronized
  internal fun consumeLifecycleRepairIdentitySession(account: AccountRecordEntity): Boolean {
    if (!lifecycleRepairIdentitySessionMatches(account)) return false
    lifecycleRepairIdentityLease = null
    return true
  }

  internal fun clearLifecycleRepairIdentitySession(): Boolean {
    val cleared = clearIdentitySession()
    if (!cleared) {
      lifecycleRepairIdentityLease?.let(::scheduleLifecycleRepairLeaseCleanupRetry)
        ?: scheduleUnleasedLifecycleRepairCleanup()
    }
    return cleared
  }

  private fun scheduleLifecycleRepairLeaseCleanup(lease: LifecycleRepairIdentityLease) {
    val delay = (lease.expiresAtElapsedMillis - SystemClock.elapsedRealtime().coerceAtLeast(0))
      .coerceAtLeast(0)
    lifecycleRepairSessionCleanupExecutor.schedule(
      { expireLifecycleRepairIdentityLease(lease) },
      delay,
      TimeUnit.MILLISECONDS,
    )
  }

  @Synchronized
  private fun expireLifecycleRepairIdentityLease(lease: LifecycleRepairIdentityLease) {
    if (lifecycleRepairIdentityLease != lease) return
    if (lifecycleStateStore.journalStatus() != LifecycleJournalStatus.UNREADABLE) {
      lifecycleRepairIdentityLease = null
      return
    }
    if (!clearIdentitySession()) {
      scheduleLifecycleRepairLeaseCleanupRetry(lease)
    }
  }

  private fun scheduleLifecycleRepairLeaseCleanupRetry(lease: LifecycleRepairIdentityLease) {
    lifecycleRepairSessionCleanupExecutor.schedule(
      { expireLifecycleRepairIdentityLease(lease) },
      LIFECYCLE_REPAIR_CLEANUP_RETRY_MILLIS,
      TimeUnit.MILLISECONDS,
    )
  }

  private fun scheduleUnleasedLifecycleRepairCleanup() {
    lifecycleRepairSessionCleanupExecutor.schedule(
      {
        if (
          lifecycleRepairIdentityLease == null &&
          !clearIdentitySession()
        ) scheduleUnleasedLifecycleRepairCleanup()
      },
      LIFECYCLE_REPAIR_CLEANUP_RETRY_MILLIS,
      TimeUnit.MILLISECONDS,
    )
  }
  val recurrencePlanner by lazy { RecurrencePlanner() }
  val readinessEvaluator by lazy { ReadinessEvaluator() }
  val androidReadinessProbe by lazy { AndroidReadinessProbe(appContext) }
  private val automationTimeSource by lazy { AndroidAutomationTimeSource(appContext) }
  internal val submissionGate by lazy { SubmissionGate(appContext) }
  internal val automationCoordinationPort by lazy {
    FirebaseAutomationCoordinationPort(coordinationRuntime)
  }
  private val smsGateway by lazy {
    AndroidSmsGateway(
      context = appContext,
      ledger = safetyLedgerDao,
      birthdayDao = database.birthdayDao(),
      submissionGate = submissionGate,
      readinessProbe = androidReadinessProbe,
    )
  }
  private val finalExternalGateSource by lazy {
    AndroidFinalExternalGateSource(
      context = appContext,
      ledger = safetyLedgerDao,
      orchestrationDao = automationOrchestrationDao,
      readinessProbe = androidReadinessProbe,
      accountSessionMatches = { accountId ->
        peopleSyncDao.activeAccount()
          ?.takeIf { it.accountId == accountId }
          ?.let(::identitySessionMatches) == true
      },
      timeSource = automationTimeSource,
    )
  }
  internal val automationOrchestrator by lazy {
    AndroidAutomationOrchestrator(
      context = appContext,
      dao = automationOrchestrationDao,
      ledger = safetyLedgerDao,
      coordination = automationCoordinationPort,
      recurrencePlanner = recurrencePlanner,
      readinessProbe = androidReadinessProbe,
      identitySessionMatches = { accountId ->
        peopleSyncDao.activeAccount()
          ?.takeIf { it.accountId == accountId }
          ?.let(::identitySessionMatches) == true
      },
      submissionGate = submissionGate,
      smsGateway = smsGateway,
      finalGateSource = finalExternalGateSource,
      timeSource = automationTimeSource,
      installationIdentityStore = installationIdentityStore,
    )
  }

  internal fun rotateInstallationIdentityAfterTeardown(
    expectedInstallationId: String,
    expectedCallbackGeneration: String,
  ) = installationIdentityStore.rotateAfterTeardown(
    expectedInstallationId,
    expectedCallbackGeneration,
  )

  /**
   * Completes a write-ahead local wipe after process death without opening the protected Room
   * database. The marker is created only after either an accepted server fence or the local
   * fail-closed pause boundary, plus native callback retirement and WorkManager cancellation.
   */
  private fun recoverInterruptedLocalWipe(): Boolean {
    val pending = lifecycleStateStore.pendingLocalWipe() ?: return true
    if (!clearIdentitySessionForRecoveredWipe()) return false

    val databaseFiles = protectedDatabaseFiles()
    if (databaseFiles.any(java.io.File::exists)) {
      runCatching { appContext.deleteDatabase(BirthdayDatabase.DATABASE_NAME) }
      databaseFiles.forEach { candidate ->
        if (candidate.exists()) runCatching { candidate.delete() }
      }
    }
    if (databaseFiles.any(java.io.File::exists)) return false
    if (runCatching { DatabaseKeyManager(appContext).clear() }.isFailure) return false

    val before = installationIdentityStore.currentOrNull() ?: return false
    val after = when {
      before.installationId == pending.installationId &&
        before.callbackGeneration == pending.callbackGeneration ->
        installationIdentityStore.rotateAfterTeardown(
          pending.installationId,
          pending.callbackGeneration,
        )
      before.installationId != pending.installationId &&
        before.callbackGeneration != pending.callbackGeneration -> before
      else -> null
    } ?: return false
    if (
      after.installationId == pending.installationId ||
      after.callbackGeneration == pending.callbackGeneration ||
      protectedDatabaseFiles().any(java.io.File::exists)
    ) return false
    return lifecycleStateStore.completeRecoveredLocalWipe(
      pending,
      System.currentTimeMillis().coerceAtLeast(0),
    ) != null
  }

  private fun clearIdentitySessionForRecoveredWipe(): Boolean {
    return clearIdentitySession()
  }

  internal fun deletionRecoveryIdentitySessionBoundaryRequired(): Boolean =
    DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
      receiptLookup = lifecycleStateStore.deletionReceiptLookup(
        System.currentTimeMillis().coerceAtLeast(0),
      ),
      journalStatus = lifecycleStateStore.journalStatus(),
      operation = lifecycleStateStore.latestOperation(),
    )

  /** Retries the fail-closed cleanup without changing or consuming the durable recovery receipt. */
  internal fun ensureDeletionRecoveryIdentitySessionCleared(): Boolean =
    deletionRecoveryIdentitySessionGuard.clearIfRequired()

  @Synchronized
  private fun clearIdentitySession(): Boolean {
    val configuration = AndroidIdentityConfigurationResolver(appContext, BuildConfig.APP_ENV).resolve()
    if (configuration !is IdentityConfigurationResult.Ready) return false
    val auth = FirebaseAuth.getInstance(configuration.configuration.firebaseApp)
    if (runCatching { auth.signOut() }.isFailure || auth.currentUser != null) return false
    val credentialsCleared = runCatching {
      runBlocking {
        CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
      }
    }.isSuccess
    if (credentialsCleared) lifecycleRepairIdentityLease = null
    return credentialsCleared
  }

  private fun protectedDatabaseFiles(): List<java.io.File> {
    val base = appContext.getDatabasePath(BirthdayDatabase.DATABASE_NAME)
    return listOf(base, java.io.File(base.path + "-wal"), java.io.File(base.path + "-shm"))
  }

  /**
   * Final destructive boundary. Callers must first pause, either drain or conservatively suppress
   * outstanding local work, retire callbacks, cancel WorkManager, and clear SDK sessions. The
   * lifecycle receipt lives in a separate no-backup file and remains readable after Room teardown.
   */
  @Synchronized
  internal fun eraseProtectedDatabaseAfterTeardown(
    expectedInstallationId: String,
    expectedCallbackGeneration: String,
  ): Boolean {
    val current = automationOrchestrationDao.localInstallationBlockingOrNull()
      ?: return false
    if (
      current.installationId != expectedInstallationId ||
      current.callbackGeneration != expectedCallbackGeneration
    ) return false
    return try {
      database.close()
      appContext.deleteDatabase(BirthdayDatabase.DATABASE_NAME)
      DatabaseKeyManager(appContext).clear()
      val rotated = installationIdentityStore.rotateAfterTeardown(
        expectedInstallationId,
        expectedCallbackGeneration,
      ) ?: return false
      rotated.installationId != expectedInstallationId &&
        rotated.callbackGeneration != expectedCallbackGeneration &&
        protectedDatabaseFiles().none(java.io.File::exists)
    } catch (_: RuntimeException) {
      false
    }
  }

  private fun com.yashsomani.birthdayautopilot.automation.orchestration.AutomationOrchestrationDao
    .localInstallationBlockingOrNull() = runCatching {
      runBlocking { localInstallation() }
    }.getOrNull()
  val workerFactory by lazy { BirthdayWorkerFactory(this) }

  companion object {
    private const val LIFECYCLE_REPAIR_REAUTH_MILLIS = 5 * 60 * 1_000L
    private const val LIFECYCLE_REPAIR_CLEANUP_RETRY_MILLIS = 30_000L
    @Volatile private var instance: AppGraph? = null

    fun get(context: Context): AppGraph = instance ?: synchronized(this) {
      instance ?: AppGraph(context).also { instance = it }
    }
  }
}
