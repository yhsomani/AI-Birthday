package com.yashsomani.birthdayautopilot.automation.orchestration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalRecordState
import com.yashsomani.birthdayautopilot.storage.database.ApprovalSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.AutomationPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ContactPhoneEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotState
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.PhoneRecordState
import com.yashsomani.birthdayautopilot.storage.database.PolicyRecordState
import com.yashsomani.birthdayautopilot.storage.database.RecipientEnrollmentState
import com.yashsomani.birthdayautopilot.storage.database.RecipientPolicyEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetBlockedDateEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomationOrchestrationDaoInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var dao: AutomationOrchestrationDao

  @Before
  fun setUp() = runBlocking {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      BirthdayDatabase::class.java,
    ).build()
    database.birthdayDao().initializeIfAbsent(CALLBACK_GENERATION)
    database.safetyLedgerDao().insertAccount(
      AccountRecordEntity(
        accountId = ACCOUNT_ID,
        activeSlot = 1,
        googleSubjectHash = "subject-hash",
        firebaseUid = "firebase-uid",
        displayEmail = null,
        localeTag = "en-IN",
        state = AccountRecordState.ACTIVE,
        revision = 0,
        createdAtMillis = 1_000,
        updatedAtMillis = 1_000,
      ),
    )
    dao = database.automationOrchestrationDao()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun registrationReplayRefreshesProjectionWithoutChangingTheBlockerRevision() = runBlocking {
    assertNotNull(dao.ensureLocalInstallation(pendingInstallation()))
    assertTrue(
      dao.applyRegistration(
        accountId = ACCOUNT_ID,
        installationId = INSTALLATION_ID,
        installationState = InstallationRecordState.ACTIVE,
        serverMode = AccountMode.TEST_ONLY,
        localMode = AccountMode.TEST_ONLY,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 20_000,
        serverNowMillis = 2_000,
        coordination = coordination(revision = 0, leaseUntilMillis = 20_000),
        reset = reset(),
        blockedDate = blockedDate(),
        deviceWallMillis = 2_000,
      ),
    )
    val firstControl = checkNotNull(dao.control())
    assertEquals(1, firstControl.blockerRevision)
    assertEquals(AccountMode.TEST_ONLY.name, firstControl.accountMode)
    assertEquals(1L, dao.localInstallation()?.senderEpoch)
    assertEquals(CALLBACK_GENERATION, dao.callbackCounterGeneration())

    assertTrue(
      dao.applyRegistration(
        accountId = ACCOUNT_ID,
        installationId = INSTALLATION_ID,
        installationState = InstallationRecordState.ACTIVE,
        serverMode = AccountMode.TEST_ONLY,
        localMode = AccountMode.TEST_ONLY,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 25_000,
        serverNowMillis = 3_000,
        coordination = coordination(revision = 1, leaseUntilMillis = 25_000),
        reset = reset(),
        blockedDate = blockedDate(),
        deviceWallMillis = 3_000,
      ),
    )
    val replayControl = checkNotNull(dao.control())
    assertEquals(firstControl.blockerRevision, replayControl.blockerRevision)
    assertTrue(replayControl.revision > firstControl.revision)
    assertEquals(25_000L, dao.localInstallation()?.ownerLeaseUntilMillis)
  }

  @Test
  fun durableIdentityMismatchFailsClosedWithoutReplacingThePersistedBinding() = runBlocking {
    val persisted = checkNotNull(dao.ensureLocalInstallation(pendingInstallation()))
    val rotatedCandidate = pendingInstallation().copy(
      installationId = "fedcba9876543210fedcba9876543210",
      callbackGeneration = "0123456789abcdef0123456789abcdef",
    )

    assertNull(dao.ensureLocalInstallation(rotatedCandidate))
    assertEquals(persisted.installationId, dao.localInstallation()?.installationId)
    assertEquals(persisted.callbackGeneration, dao.callbackCounterGeneration())
  }

  @Test
  fun exactPermitLookupAndBootRecoveryPaginationCannotBeStarvedByOlderRows() = runBlocking {
    registerInstallation()
    repeat(RECOVERY_ROW_COUNT) { insertRawArmReconcilingPermit(it) }

    val target = dao.recoverablePermitForOperation(
      OperationPurpose.TEST,
      "operation-${RECOVERY_ROW_COUNT - 1}",
    )
    assertEquals("permit-${RECOVERY_ROW_COUNT - 1}", target?.permitId)

    val recoveredIds = mutableListOf<String>()
    var hasCursor = false
    var afterUpdatedAtMillis = Long.MIN_VALUE
    var afterPermitId = ""
    while (true) {
      val page = dao.bootLostArmReconcilingPermits(
        currentBootCount = 2,
        currentElapsedRealtimeMillis = 2_000,
        hasCursor = hasCursor,
        afterUpdatedAtMillis = afterUpdatedAtMillis,
        afterPermitId = afterPermitId,
        limit = RECOVERY_PAGE_LIMIT,
      )
      if (page.isEmpty()) break
      recoveredIds += page.map { it.permitId }
      val last = page.last()
      afterUpdatedAtMillis = last.updatedAtMillis
      afterPermitId = last.permitId
      hasCursor = true
      if (page.size < RECOVERY_PAGE_LIMIT) break
    }

    assertEquals(RECOVERY_ROW_COUNT, recoveredIds.size)
    assertEquals(RECOVERY_ROW_COUNT, recoveredIds.toSet().size)
    assertTrue(recoveredIds.contains("permit-${RECOVERY_ROW_COUNT - 1}"))
  }

  @Test
  fun planningCursorReturnsEligibleContactsBeyondTheFirstThousand() = runBlocking {
    val ledger = database.safetyLedgerDao()
    ledger.insertAutomationPolicy(activePolicy())
    repeat(PLANNING_ROW_COUNT) { index -> insertPlanningMaterial(index) }

    val collected = mutableListOf<String>()
    var hasCursor = false
    var afterUpcomingBucket = 0
    var afterMonthDay = 0
    var afterContactId = ""
    while (true) {
      val page = dao.planningSeeds(
        horizonStartDate = "2026-01-01",
        horizonEndDate = "2027-02-05",
        currentTimeZoneId = "Asia/Kolkata",
        currentMonthDay = 101,
        hasCursor = hasCursor,
        afterUpcomingBucket = afterUpcomingBucket,
        afterMonthDay = afterMonthDay,
        afterContactId = afterContactId,
        limit = PLANNING_PAGE_LIMIT,
      )
      if (page.isEmpty()) break
      collected += page.map { it.contactId }
      val last = page.last()
      val monthDay = last.birthdayMonth * 100 + last.birthdayDay
      afterUpcomingBucket = if (monthDay >= 101) 0 else 1
      afterMonthDay = monthDay
      afterContactId = last.contactId
      hasCursor = true
      if (page.size < PLANNING_PAGE_LIMIT) break
    }

    assertEquals(PLANNING_ROW_COUNT, collected.size)
    assertEquals(PLANNING_ROW_COUNT, collected.toSet().size)
    assertTrue(collected.contains(contactId(PLANNING_ROW_COUNT - 1)))
  }

  @Test
  fun resetDateIsReleasedOnlyStrictlyAfterItsTrustedServerBoundary() = runBlocking {
    dao.ensureLocalInstallation(pendingInstallation())
    dao.applyRegistration(
      accountId = ACCOUNT_ID,
      installationId = INSTALLATION_ID,
      installationState = InstallationRecordState.ACTIVE,
      serverMode = AccountMode.TEST_ONLY,
      localMode = AccountMode.TEST_ONLY,
      senderEpoch = 1,
      resetGeneration = 1,
      ownerLeaseUntilMillis = 20_000,
      serverNowMillis = 2_000,
      coordination = coordination(revision = 0, leaseUntilMillis = 20_000),
      reset = reset(),
      blockedDate = blockedDate(),
      deviceWallMillis = 2_000,
    )
    assertFalse(dao.releaseExpiredResetDates(ACCOUNT_ID, 5_000, 5_000))
    assertEquals(ResetSafetyStatus.BLOCKED, dao.resetSafety(ACCOUNT_ID)?.status)
    assertTrue(dao.releaseExpiredResetDates(ACCOUNT_ID, 5_001, 5_001))
    assertEquals(ResetSafetyStatus.CLEAR, dao.resetSafety(ACCOUNT_ID)?.status)
  }

  @Test
  fun registrationReplayCannotReblockAnAlreadyReleasedResetGeneration() = runBlocking {
    dao.ensureLocalInstallation(pendingInstallation())
    assertTrue(
      dao.applyRegistration(
        accountId = ACCOUNT_ID,
        installationId = INSTALLATION_ID,
        installationState = InstallationRecordState.ACTIVE,
        serverMode = AccountMode.TEST_ONLY,
        localMode = AccountMode.TEST_ONLY,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 20_000,
        serverNowMillis = 2_000,
        coordination = coordination(revision = 0, leaseUntilMillis = 20_000),
        reset = reset(),
        blockedDate = blockedDate(),
        deviceWallMillis = 2_000,
      ),
    )
    assertTrue(dao.releaseExpiredResetDates(ACCOUNT_ID, 5_001, 5_001))
    val released = checkNotNull(dao.resetSafety(ACCOUNT_ID))
    assertEquals(ResetSafetyStatus.CLEAR, released.status)

    // A repeated authoritative registration for the same generation may refresh the lease and
    // projection, but its BLOCKED-shaped transport projection must not replace local reset truth.
    assertTrue(
      dao.applyRegistration(
        accountId = ACCOUNT_ID,
        installationId = INSTALLATION_ID,
        installationState = InstallationRecordState.ACTIVE,
        serverMode = AccountMode.TEST_ONLY,
        localMode = AccountMode.TEST_ONLY,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 25_000,
        serverNowMillis = 6_000,
        coordination = coordination(revision = 1, leaseUntilMillis = 25_000),
        reset = reset(),
        blockedDate = blockedDate(),
        deviceWallMillis = 6_000,
      ),
    )

    val replayed = checkNotNull(dao.resetSafety(ACCOUNT_ID))
    assertEquals(ResetSafetyStatus.CLEAR, replayed.status)
    assertEquals(released.revision, replayed.revision)
    assertTrue(dao.releaseExpiredResetDates(ACCOUNT_ID, 6_000, 6_000))
  }

  @Test
  fun authoritativeTransferAndDeletionModesAtomicallyBlockTheLocalSender() = runBlocking {
    dao.ensureLocalInstallation(pendingInstallation())
    assertTrue(
      dao.applyRegistration(
        accountId = ACCOUNT_ID,
        installationId = INSTALLATION_ID,
        installationState = InstallationRecordState.STANDBY,
        serverMode = AccountMode.AUTOMATION_ACTIVE,
        localMode = AccountMode.STANDBY,
        senderEpoch = null,
        resetGeneration = 1,
        ownerLeaseUntilMillis = null,
        serverNowMillis = 2_000,
        coordination = coordination(revision = 0, leaseUntilMillis = 20_000).copy(
          mode = AccountMode.AUTOMATION_ACTIVE,
          activeInstallationId = OTHER_ACTIVE_INSTALLATION_ID,
          senderEpoch = 7,
        ),
        reset = reset(),
        blockedDate = blockedDate(),
        deviceWallMillis = 2_000,
      ),
    )

    val registeredControl = checkNotNull(dao.control())
    assertFalse(
      dao.applyRemoteLifecycleMode(
        accountId = ACCOUNT_ID,
        localInstallationId = INSTALLATION_ID,
        mode = AccountMode.TRANSFER_PENDING,
        activeInstallationId = OTHER_ACTIVE_INSTALLATION_ID,
        senderEpoch = 7,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 20_000,
        nextArmNotBeforeMillis = 0,
        latestIssuedSubmitNotAfterMillis = 0,
        birthdayAutomationNotBeforeMillis = 4_000,
        transferTargetInstallationId = "not-the-local-installation",
        transferDrainUntilMillis = 5_000,
        deletionDrainUntilMillis = null,
        serverObservedAtMillis = 3_000,
        deviceWallMillis = 3_000,
      ),
    )
    assertEquals(AccountMode.STANDBY, dao.localInstallation()?.accountMode)
    assertEquals(registeredControl.blockerRevision, dao.control()?.blockerRevision)

    assertTrue(
      dao.applyRemoteLifecycleMode(
        accountId = ACCOUNT_ID,
        localInstallationId = INSTALLATION_ID,
        mode = AccountMode.TRANSFER_PENDING,
        activeInstallationId = OTHER_ACTIVE_INSTALLATION_ID,
        senderEpoch = 7,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 20_000,
        nextArmNotBeforeMillis = 0,
        latestIssuedSubmitNotAfterMillis = 0,
        birthdayAutomationNotBeforeMillis = 4_000,
        transferTargetInstallationId = INSTALLATION_ID,
        transferDrainUntilMillis = 5_000,
        deletionDrainUntilMillis = null,
        serverObservedAtMillis = 3_000,
        deviceWallMillis = 3_000,
      ),
    )
    val transferLocal = checkNotNull(dao.localInstallation())
    val transferCoordination = checkNotNull(dao.coordinationState(ACCOUNT_ID))
    val transferControl = checkNotNull(dao.control())
    assertEquals(AccountMode.TRANSFER_PENDING, transferLocal.accountMode)
    assertEquals(InstallationRecordState.STANDBY, transferLocal.state)
    assertNull(transferLocal.senderEpoch)
    assertEquals(AccountMode.TRANSFER_PENDING, transferCoordination.mode)
    assertEquals(5_000L, transferCoordination.transferDrainUntilMillis)
    assertNull(transferCoordination.deletionDrainUntilMillis)
    assertEquals(AccountMode.TRANSFER_PENDING.name, transferControl.accountMode)
    assertFalse(transferControl.automationDesired)
    assertEquals(7L, transferControl.activeInstallationEpoch)
    assertTrue(transferControl.blockerRevision > registeredControl.blockerRevision)

    assertTrue(
      dao.applyRemoteDeletionFence(
        accountId = ACCOUNT_ID,
        localInstallationId = INSTALLATION_ID,
        senderEpoch = 7,
        resetGeneration = 1,
        deletionDrainUntilMillis = 6_000,
        serverObservedAtMillis = 4_000,
        deviceWallMillis = 4_000,
      ),
    )
    val deletingCoordination = checkNotNull(dao.coordinationState(ACCOUNT_ID))
    val deletingControl = checkNotNull(dao.control())
    assertEquals(AccountMode.DELETING, dao.localInstallation()?.accountMode)
    assertEquals(AccountMode.DELETING, deletingCoordination.mode)
    assertNull(deletingCoordination.transferDrainUntilMillis)
    assertEquals(6_000L, deletingCoordination.deletionDrainUntilMillis)
    assertEquals(AccountMode.DELETING.name, deletingControl.accountMode)
    assertFalse(deletingControl.automationDesired)
    assertTrue(deletingControl.blockerRevision > transferControl.blockerRevision)
  }

  private suspend fun registerInstallation() {
    dao.ensureLocalInstallation(pendingInstallation())
    check(
      dao.applyRegistration(
        accountId = ACCOUNT_ID,
        installationId = INSTALLATION_ID,
        installationState = InstallationRecordState.ACTIVE,
        serverMode = AccountMode.TEST_ONLY,
        localMode = AccountMode.TEST_ONLY,
        senderEpoch = 1,
        resetGeneration = 1,
        ownerLeaseUntilMillis = 20_000,
        serverNowMillis = 2_000,
        coordination = coordination(revision = 0, leaseUntilMillis = 20_000),
        reset = reset(),
        blockedDate = blockedDate(),
        deviceWallMillis = 2_000,
      ),
    )
  }

  private fun insertRawArmReconcilingPermit(index: Int) {
    database.openHelper.writableDatabase.execSQL(
      """
      INSERT INTO coordination_permits_v2(
        permitId, accountId, installationId, senderEpoch, resetGeneration,
        purpose, operationId, attemptNumber, payloadHash, opaqueClaimId,
        opaqueDestinationGuardId, claimRequestId, armRequestId, state, armDispatched,
        armStartBlockerRevision, claimExpiresAtMillis, maxPossibleSubmitNotAfterMillis,
        unresolvedArmCutoffMillis, trustedServerNowMillis, requestStartElapsedMillis, bootCount,
        serverSubmitNotAfterMillis, effectiveSubmitNotAfterMillis, noWriteReason, revision,
        createdAtMillis, updatedAtMillis, barrierConsumedAtMillis, retentionUntilMillis
      ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.trimIndent(),
      arrayOf<Any?>(
        "permit-$index",
        ACCOUNT_ID,
        INSTALLATION_ID,
        1L,
        1L,
        OperationPurpose.TEST.name,
        "operation-$index",
        1,
        "payload-$index",
        "claim-$index",
        null,
        "claim-request-$index",
        "arm-request-$index",
        "ARM_RECONCILING",
        1,
        0L,
        10_000L,
        11_000L,
        11_000L,
        2_000L,
        1_000L,
        1,
        null,
        null,
        null,
        0L,
        index.toLong(),
        index.toLong(),
        null,
        20_000L,
      ),
    )
  }

  private suspend fun insertPlanningMaterial(index: Int) {
    val ledger = database.safetyLedgerDao()
    val contactId = contactId(index)
    val phoneId = "phone-$index"
    val approvalId = "approval-$index"
    ledger.insertContactSnapshot(
      ContactSnapshotEntity(
        contactId = contactId,
        accountId = ACCOUNT_ID,
        peopleResourceName = "people/$index",
        sourceFingerprint = "source-$index",
        sourceEtag = null,
        displayName = "Person $index",
        safeGivenName = "Person",
        birthdayMonth = 12,
        birthdayDay = 31,
        birthdayYear = null,
        leapDayPolicy = null,
        state = ContactSnapshotState.ACTIVE,
        syncGeneration = "generation",
        materialRevision = 1,
        sourceUpdatedAtMillis = 1_000,
        syncedAtMillis = 1_000,
        deletedAtMillis = null,
      ),
    )
    ledger.insertContactPhone(
      ContactPhoneEntity(
        phoneId = phoneId,
        contactId = contactId,
        sourceFingerprint = "phone-source-$index",
        rawNumber = "+9190000${index.toString().padStart(5, '0')}",
        normalizedE164 = "+9190000${index.toString().padStart(5, '0')}",
        destinationFingerprint = "destination-$index",
        maskedDisplay = "•••• ${index.toString().padStart(4, '0').takeLast(4)}",
        typeLabel = "mobile",
        regionCode = "IN",
        isSmsCapableType = true,
        state = PhoneRecordState.READY,
        materialRevision = 1,
        updatedAtMillis = 1_000,
      ),
    )
    ledger.insertApprovalSnapshot(
      ApprovalSnapshotEntity(
        approvalId = approvalId,
        accountId = ACCOUNT_ID,
        contactId = contactId,
        phoneId = phoneId,
        schemaVersion = 1,
        contactMaterialRevision = 1,
        phoneMaterialRevision = 1,
        policyId = POLICY_ID,
        policyRevision = 1,
        normalizedPhoneE164 = "+9190000${index.toString().padStart(5, '0')}",
        destinationFingerprint = "destination-$index",
        maskedPhoneDisplay = "•••• ${index.toString().padStart(4, '0').takeLast(4)}",
        exactMessage = "Happy birthday Person!",
        sourceTemplateId = null,
        sourceTemplateVersion = "template-v1",
        placeholderMode = "GIVEN_NAME",
        birthdayMonth = 12,
        birthdayDay = 31,
        leapDayPolicy = null,
        windowStartMinute = 540,
        windowEndMinute = 600,
        graceEndMinute = null,
        latePolicy = "STRICT_END",
        simPolicyKind = "FIXED",
        resolvedSubscriptionId = 1,
        segmentCount = 1,
        messageEncoding = "GSM_7",
        orderedPartsHash = "parts-$index",
        carrierCostDisclosureVersion = "carrier-v1",
        consentDisclosureVersion = "consent-v1",
        contentHash = "content-$index",
        state = ApprovalRecordState.ACTIVE,
        approvedAtMillis = 1_000,
        invalidatedAtMillis = null,
        invalidationReason = null,
      ),
    )
    ledger.insertRecipientPolicy(
      RecipientPolicyEntity(
        contactId = contactId,
        chosenPhoneId = phoneId,
        state = RecipientEnrollmentState.ENABLED,
        explicitEnrollmentEventId = "enrollment-$index",
        blockReason = null,
        approvalId = approvalId,
        revision = 1,
        enabledAtMillis = 1_000,
        updatedAtMillis = 1_000,
      ),
    )
  }

  private fun activePolicy() = AutomationPolicyEntity(
    policyId = POLICY_ID,
    accountId = ACCOUNT_ID,
    revision = 1,
    state = PolicyRecordState.ACTIVE,
    timeZoneId = "Asia/Kolkata",
    windowStartMinute = 540,
    windowEndMinute = 600,
    graceEndMinute = null,
    latePolicy = "STRICT_END",
    dailyCap = 20,
    simPolicyKind = "FIXED",
    resolvedSubscriptionId = 1,
    roamingAllowed = false,
    policyVersion = "policy-v1",
    createdAtMillis = 1_000,
    invalidatedAtMillis = null,
    invalidationReason = null,
  )

  private fun contactId(index: Int): String = "contact-${index.toString().padStart(4, '0')}"

  private fun pendingInstallation() = InstallationBindingEntity(
    installationId = INSTALLATION_ID,
    accountId = ACCOUNT_ID,
    localSlot = 1,
    callbackGeneration = CALLBACK_GENERATION,
    state = InstallationRecordState.STANDBY,
    accountMode = AccountMode.STANDBY,
    senderEpoch = null,
    resetGeneration = 1,
    ownerLeaseUntilMillis = null,
    appVersionCode = 1,
    distributionChannel = "DEV",
    signingCertificateSha256 = "unverified",
    lastVerifiedServerMillis = null,
    revision = 0,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
  )

  private fun coordination(revision: Long, leaseUntilMillis: Long) = CoordinationStateEntity(
    accountId = ACCOUNT_ID,
    mode = AccountMode.TEST_ONLY,
    activeInstallationId = INSTALLATION_ID,
    senderEpoch = 1,
    resetGeneration = 1,
    continuityGeneration = 1,
    ownerLeaseUntilMillis = leaseUntilMillis,
    nextArmNotBeforeMillis = 0,
    latestIssuedSubmitNotAfterMillis = 0,
    birthdayAutomationNotBeforeMillis = 4_000,
    transferDrainUntilMillis = null,
    deletionDrainUntilMillis = null,
    lastSuccessfulCoordinationMillis = 2_000,
    lastSafeCode = null,
    revision = revision,
    updatedAtMillis = 2_000,
  )

  private fun reset() = ResetSafetyEntity(
    resetSafetyId = RESET_ID,
    accountId = ACCOUNT_ID,
    resetGeneration = 1,
    resetAtMillis = 2_000,
    resetLocalDate = "2026-07-12",
    resetTimeZoneId = "Asia/Kolkata",
    birthdayAutomationNotBeforeMillis = 4_000,
    status = ResetSafetyStatus.BLOCKED,
    overflowBlocked = false,
    revision = 0,
    updatedAtMillis = 2_000,
  )

  private fun blockedDate() = ResetBlockedDateEntity(
    blockedDateId = "blocked-date",
    resetSafetyId = RESET_ID,
    civilDate = "2026-07-12",
    releaseAfterTrustedServerMillis = 5_000,
    observedAtMillis = 2_000,
  )

  private companion object {
    const val ACCOUNT_ID = "account"
    const val INSTALLATION_ID = "0123456789abcdef0123456789abcdef"
    const val OTHER_ACTIVE_INSTALLATION_ID = "fedcba9876543210fedcba9876543210"
    const val CALLBACK_GENERATION = "abcdef0123456789abcdef0123456789"
    const val RESET_ID = "reset-1"
    const val RECOVERY_PAGE_LIMIT = 32
    const val RECOVERY_ROW_COUNT = 65
    const val PLANNING_PAGE_LIMIT = 1_000
    const val PLANNING_ROW_COUNT = 1_001
    const val POLICY_ID = "policy"
  }
}
