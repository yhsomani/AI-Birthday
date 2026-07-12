package com.yashsomani.birthdayautopilot.lifecycle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionRouteStore
import com.yashsomani.birthdayautopilot.auth.NotificationPermissionStateStore
import com.yashsomani.birthdayautopilot.people.StablePrivateId
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSnapshotState
import com.yashsomani.birthdayautopilot.storage.database.ContactSyncStateEntity
import com.yashsomani.birthdayautopilot.storage.database.ConsentDecision
import com.yashsomani.birthdayautopilot.storage.database.ConsentKind
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidLifecycleDurabilityInstrumentationTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    files().forEach(File::delete)
  }

  @After
  fun tearDown() {
    files().forEach(File::delete)
  }

  @Test
  fun deletionReceiptSurvivesControllerRecreationWithoutIdentityOrPrivateContent() {
    val store = LifecycleStateStore(context)
    val receipt = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(store.putOperation(receipt))

    val afterProcessDeath = LifecycleStateStore(context).latestOperation()
    assertEquals(receipt, afterProcessDeath)
    val raw = lifecycleFile().readText(Charsets.US_ASCII)
    assertFalse(raw.contains("@"))
    assertFalse(raw.contains("firebase-uid"))
    assertFalse(raw.contains("google-subject"))
    assertFalse(raw.contains("message"))
  }

  @Test
  fun transferDrainAndStableOperationIdentitySurviveControllerRecreation() {
    val operation = DurablePrivacyOperation(
      id = "transfer_0123456789abcdef0123456789abcdef",
      action = "sender-transfer",
      state = "remote-draining",
      reason = "transfer-pending",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      transferActiveInstallationId = ACTIVE_INSTALLATION_ID,
      transferTargetInstallationId = TARGET_INSTALLATION_ID,
      transferSenderEpoch = 11,
      transferResetGeneration = 4,
    )
    assertTrue(LifecycleStateStore(context).putOperation(operation))
    assertEquals(operation, LifecycleStateStore(context).latestOperation())
  }

  @Test
  fun notificationTapIdentitySurvivesProcessDeathAndIsConsumedExactlyOnce() {
    val issued = AndroidAttentionRouteStore(context).issueTapIdentity()
    assertNotNull(issued)
    assertFalse(AndroidAttentionRouteStore(context).acceptTapIdentity("forged"))
    assertTrue(AndroidAttentionRouteStore(context).acceptTapIdentity(checkNotNull(issued)))

    val route = AndroidAttentionRouteStore(context).consumeRouteId()
    assertNotNull(route)
    assertTrue(UUID.matches(checkNotNull(route)))
    assertNull(AndroidAttentionRouteStore(context).consumeRouteId())
    assertFalse(AndroidAttentionRouteStore(context).acceptTapIdentity(checkNotNull(issued)))
  }

  @Test
  fun corruptNotificationPromptMarkerFailsClosedToAlreadyRequested() {
    assertFalse(NotificationPermissionStateStore(context).wasRequested())
    notificationPermissionFile().writeText("corrupt\n", Charsets.US_ASCII)
    assertTrue(NotificationPermissionStateStore(context).wasRequested())
  }

  @Test
  fun malformedLifecycleJournalBlocksOverwriteAndSurfacesUnavailableProjections() {
    lifecycleFile().writeText("malformed\n", Charsets.US_ASCII)
    val before = lifecycleFile().readBytes()
    val store = LifecycleStateStore(context)
    assertEquals(LifecycleJournalStatus.UNREADABLE, store.journalStatus())
    assertNull(store.latestOperation())
    assertFalse(store.setActivityVisibilityCutoffMillis(123))
    assertFalse(
      store.putOperation(
        DurablePrivacyOperation(
          id = "privacy_0123456789abcdef0123456789abcdef",
          action = "clear-activity",
          state = "queued",
          reason = null,
          updatedAtMillis = 1,
          completedAtMillis = null,
        ),
      ),
    )
    assertArrayEquals(before, lifecycleFile().readBytes())

    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 4_000 })
      assertTrue(controller.lifecycleJournalUnreadable())
      assertEquals("unavailable", controller.currentOperationPayload().getString("kind"))
      assertEquals("unavailable", controller.latestDeletionReceiptPayload().getString("kind"))
      assertEquals(
        "unavailable",
        controller.senderTransferOperationProjectionPayload().getString("kind"),
      )
    } finally {
      database.close()
    }
  }

  @Test
  fun truncatedLifecycleJournalRemainsUnreadableAcrossProcessRecreation() {
    val operation = DurablePrivacyOperation(
      id = "transfer_0123456789abcdef0123456789abcdef",
      action = "sender-transfer",
      state = "remote-draining",
      reason = "transfer-pending",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      transferActiveInstallationId = ACTIVE_INSTALLATION_ID,
      transferTargetInstallationId = TARGET_INSTALLATION_ID,
      transferSenderEpoch = 11,
      transferResetGeneration = 4,
    )
    assertTrue(LifecycleStateStore(context).putOperation(operation))
    val valid = lifecycleFile().readBytes()
    lifecycleFile().writeBytes(valid.copyOf(valid.size / 2))
    val truncated = lifecycleFile().readBytes()

    repeat(2) {
      val afterProcessDeath = LifecycleStateStore(context)
      assertEquals(LifecycleJournalStatus.UNREADABLE, afterProcessDeath.journalStatus())
      assertNull(afterProcessDeath.latestOperation())
      assertFalse(afterProcessDeath.putOperation(operation))
      assertArrayEquals(truncated, lifecycleFile().readBytes())
    }
  }

  @Test
  fun oversizedLifecycleJournalFailsClosedWithoutReplacement() {
    val oversized = ByteArray(8_193) { 'x'.code.toByte() }
    lifecycleFile().writeBytes(oversized)
    val store = LifecycleStateStore(context)
    assertEquals(LifecycleJournalStatus.UNREADABLE, store.journalStatus())
    assertFalse(store.setActivityVisibilityCutoffMillis(1))
    assertArrayEquals(oversized, lifecycleFile().readBytes())
  }

  @Test
  fun backupOnlyLifecycleJournalIsRecoveredBeforeItCanLookAbsent() {
    val receipt = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(LifecycleStateStore(context).putOperation(receipt))
    val backup = File(lifecycleFile().path + ".bak")
    assertTrue(lifecycleFile().renameTo(backup))
    assertFalse(lifecycleFile().exists())

    val afterProcessDeath = LifecycleStateStore(context)
    assertEquals(LifecycleJournalStatus.READABLE, afterProcessDeath.journalStatus())
    assertEquals(receipt, afterProcessDeath.latestOperation())
    assertTrue(lifecycleFile().exists())
  }

  @Test
  fun syntacticallyValidButImpossibleDeletionCompletionIsUnreadable() {
    lifecycleFile().writeText(
      listOf(
        "3",
        "0",
        "privacy_0123456789abcdef0123456789abcdef",
        "delete-account",
        "complete",
        "",
        "2000",
        "2000",
        "",
        "",
        "",
        "",
        "",
        "0",
        "",
        "",
        "",
        "",
        "",
        "",
      ).joinToString("\n"),
      Charsets.US_ASCII,
    )
    val store = LifecycleStateStore(context)
    assertEquals(LifecycleJournalStatus.UNREADABLE, store.journalStatus())
    assertNull(store.latestOperation())
  }

  @Test
  fun deletionWriteAheadWipeMarkerSurvivesDeathAndCompletesIdempotently() {
    val marker = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = false,
      remoteDeletionComplete = false,
      localWipeStarted = true,
      wipeInstallationId = ACTIVE_INSTALLATION_ID,
      wipeCallbackGeneration = TARGET_INSTALLATION_ID,
    )
    assertTrue(LifecycleStateStore(context).putOperation(marker))

    val afterDeath = LifecycleStateStore(context)
    val pending = checkNotNull(afterDeath.pendingLocalWipe())
    assertEquals(marker.id, pending.operationId)
    assertEquals(ACTIVE_INSTALLATION_ID, pending.installationId)
    val receipt = checkNotNull(afterDeath.completeRecoveredLocalWipe(pending, 4_000))
    assertTrue(receipt.localDataErased)
    assertEquals(false, receipt.remoteDeletionComplete)
    assertNull(LifecycleStateStore(context).pendingLocalWipe())

    // A stale recovery proof cannot mutate the terminal local receipt a second time.
    assertNull(LifecycleStateStore(context).completeRecoveredLocalWipe(pending, 5_000))
  }

  @Test
  fun strictRemoteDeletionCompletionSurvivesDeathAndProjectsContentFreeEvidence() {
    val pending = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(LifecycleStateStore(context).putOperation(pending))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database)
      assertNull(controller.completeAccountDeletionReceipt(OTHER_REQUEST_ID, 4_000))
      val completed = checkNotNull(
        controller.completeAccountDeletionReceipt(REQUEST_ID, 4_000),
      )
      assertEquals(DurableDeletionReceipt.State.COMPLETED, completed.state)
      assertEquals(4_000L, completed.completedAtMillis)

      val afterDeath = AndroidLifecycleController(context, database, wallClockMillis = { 5_000 })
        .latestDeletionReceiptPayload()
      assertEquals("complete", afterDeath.getString("kind"))
      assertEquals("delete-account", afterDeath.getString("action"))
      assertTrue(afterDeath.getBoolean("localDataErased"))
      assertTrue(afterDeath.getBoolean("remoteDeletionComplete"))
      assertTrue(afterDeath.getBoolean("externalSmsCopiesNotErased"))
      assertEquals(7, afterDeath.length())
      assertNull(LifecycleStateStore(context).pendingLocalWipe())
    } finally {
      database.close()
    }
  }

  @Test
  fun independentReceiptCompletesAfterMainOperationLossAndExpiresAtOneYearBoundary() {
    val pending = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    val store = LifecycleStateStore(context)
    assertTrue(store.putOperation(pending))
    lifecycleFile().delete()
    File(lifecycleFile().path + ".bak").delete()
    assertNull(LifecycleStateStore(context).latestOperation())

    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val before = AndroidLifecycleController(context, database, wallClockMillis = { 4_000 })
        .latestDeletionReceiptPayload()
      assertEquals("unavailable", before.getString("kind"))
      assertEquals("coordination-unavailable", before.getString("reason"))
      assertFalse(before.toString().contains(REQUEST_ID))

      val completedAt = 10_000L
      assertNotNull(
        AndroidLifecycleController(context, database, wallClockMillis = { completedAt })
          .completeAccountDeletionReceipt(REQUEST_ID, completedAt),
      )
      assertNull(LifecycleStateStore(context).latestOperation())
      val cleanupAt = completedAt + DeletionReceiptStore.COMPLETED_RETENTION_MILLIS
      val retained = AndroidLifecycleController(
        context,
        database,
        wallClockMillis = { cleanupAt - 1 },
      ).latestDeletionReceiptPayload()
      assertEquals("complete", retained.getString("kind"))
      assertFalse(retained.toString().contains(REQUEST_ID))
      assertEquals(
        "none",
        AndroidLifecycleController(context, database, wallClockMillis = { cleanupAt })
          .latestDeletionReceiptPayload()
          .getString("kind"),
      )
      val expiredMarker = deletionReceiptFile().readText(Charsets.US_ASCII)
      assertEquals("1\nEXPIRED\n", expiredMarker)
      assertFalse(expiredMarker.contains(REQUEST_ID))
      assertEquals(
        "none",
        AndroidLifecycleController(context, database, wallClockMillis = { cleanupAt + 1 })
          .latestDeletionReceiptPayload()
          .getString("kind"),
      )
    } finally {
      database.close()
    }
  }

  @Test
  fun pendingReceiptNeverExpiresRejectsReplacementAndCompletedAllowsNewPending() {
    val store = DeletionReceiptStore(context)
    assertTrue(store.putPending(PRIVACY_ID, REQUEST_ID, 1_000))
    assertFalse(
      LifecycleStateStore(context).putOperation(
        DurablePrivacyOperation(
          id = OTHER_PRIVACY_ID,
          action = "clear-activity",
          state = "complete",
          reason = null,
          updatedAtMillis = 2_000,
          completedAtMillis = 2_000,
        ),
      ),
    )
    assertTrue(store.lookup(Long.MAX_VALUE) is DeletionReceiptLookup.Present)
    assertFalse(store.putPending(OTHER_PRIVACY_ID, OTHER_REQUEST_ID, 2_000))
    assertEquals(REQUEST_ID, (store.lookup(3_000) as DeletionReceiptLookup.Present).receipt.receiptId)

    assertNotNull(store.complete(REQUEST_ID, 4_000))
    assertTrue(store.putPending(PRIVACY_ID, REQUEST_ID, 5_000))
    val preserved = (store.lookup(5_000) as DeletionReceiptLookup.Present).receipt
    assertEquals(DurableDeletionReceipt.State.COMPLETED, preserved.state)
    assertEquals(4_000L, preserved.completedAtMillis)
    assertFalse(store.putPending(OTHER_PRIVACY_ID, OTHER_REQUEST_ID, 5_000))
    assertTrue(store.retireCompleted(preserved))
    assertTrue(store.putPending(OTHER_PRIVACY_ID, OTHER_REQUEST_ID, 5_000))
    val replacement = (store.lookup(6_000) as DeletionReceiptLookup.Present).receipt
    assertEquals(OTHER_REQUEST_ID, replacement.receiptId)
    assertEquals(DurableDeletionReceipt.State.PENDING, replacement.state)
  }

  @Test
  fun completedReceiptRetiresBeforeANewDeletionOperationWrite() {
    val accepted = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    val store = LifecycleStateStore(context)
    assertTrue(store.putOperation(accepted))
    assertNotNull(DeletionReceiptStore(context).complete(REQUEST_ID, 4_000))

    val later = DurablePrivacyOperation(
      id = OTHER_PRIVACY_ID,
      action = "delete-account",
      state = "queued",
      reason = null,
      updatedAtMillis = 5_000,
      completedAtMillis = null,
    )
    assertTrue(store.putOperation(later))
    assertEquals(later, store.latestOperation())
    assertEquals(DeletionReceiptLookup.None, store.deletionReceiptLookup(5_000))
    assertEquals("1\nEXPIRED\n", deletionReceiptFile().readText(Charsets.US_ASCII))
  }

  @Test
  fun completedReceiptRejectsSameOperationLifecycleDowngrade() {
    val completed = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "complete",
      reason = null,
      updatedAtMillis = 4_000,
      completedAtMillis = 4_000,
      requestId = REQUEST_ID,
      localDataErased = true,
      remoteDeletionComplete = true,
    )
    val store = LifecycleStateStore(context)
    assertTrue(store.putOperation(completed))
    val stale = completed.copy(
      state = "remote-pending",
      reason = "coordination-unavailable",
      updatedAtMillis = 5_000,
      completedAtMillis = null,
      remoteDeletionComplete = false,
      deletionLocalWipeFallback = true,
      recoveryBindingSalt = RECOVERY_SALT,
      recoveryFirebaseUidHash = RECOVERY_UID_HASH,
      recoveryGoogleSubjectHash = RECOVERY_SUBJECT_HASH,
    )
    assertFalse(store.putOperation(stale))
    assertEquals(completed, store.latestOperation())
    val receipt = store.deletionReceiptLookup(5_000) as DeletionReceiptLookup.Present
    assertEquals(DurableDeletionReceipt.State.COMPLETED, receipt.receipt.state)
  }

  @Test
  fun ordinaryIdentityBoundaryRecoversAfterCrashBetweenSanitizationAndReceiptRetirement() {
    val completed = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "complete",
      reason = null,
      updatedAtMillis = 4_000,
      completedAtMillis = 4_000,
      requestId = REQUEST_ID,
      localDataErased = true,
      remoteDeletionComplete = true,
    )
    val store = LifecycleStateStore(context)
    assertTrue(store.putOperation(completed))
    val receipt = (store.deletionReceiptLookup(4_000) as DeletionReceiptLookup.Present).receipt
    assertEquals(DurableDeletionReceipt.State.COMPLETED, receipt.state)

    // Models process death after the exact main record was sanitized and before receipt retirement.
    lifecycleFile().delete()
    File(lifecycleFile().path + ".bak").delete()
    assertEquals(DurableDeletionReceipt.State.COMPLETED, receipt.state)
    assertTrue(LifecycleStateStore(context).prepareForOrdinaryAccountIdentity())
    assertNull(LifecycleStateStore(context).latestOperation())
    assertEquals(
      DeletionReceiptLookup.None,
      LifecycleStateStore(context).deletionReceiptLookup(5_000),
    )
  }

  @Test
  fun ordinaryIdentityBoundaryPreservesCompletedReceiptWhenMainJournalIsUnreadable() {
    val completed = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "complete",
      reason = null,
      updatedAtMillis = 4_000,
      completedAtMillis = 4_000,
      requestId = REQUEST_ID,
      localDataErased = true,
      remoteDeletionComplete = true,
    )
    assertTrue(LifecycleStateStore(context).putOperation(completed))
    lifecycleFile().writeText("malformed\n", Charsets.US_ASCII)
    assertFalse(LifecycleStateStore(context).prepareForOrdinaryAccountIdentity())
    val preserved = DeletionReceiptStore(context).lookupWithoutExpiry()
      as DeletionReceiptLookup.Present
    assertEquals(DurableDeletionReceipt.State.COMPLETED, preserved.receipt.state)
    assertEquals(REQUEST_ID, preserved.receipt.receiptId)
  }

  @Test
  fun expiredCompletedReceiptAlsoSanitizesItsMatchingLifecycleOperation() {
    val pending = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(LifecycleStateStore(context).putOperation(pending))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val completedAt = 10_000L
      val controller = AndroidLifecycleController(
        context,
        database,
        wallClockMillis = { completedAt },
      )
      assertNotNull(controller.completeAccountDeletionReceipt(REQUEST_ID, completedAt))
      assertEquals("complete", LifecycleStateStore(context).latestOperation()?.state)

      val cleanupAt = completedAt + DeletionReceiptStore.COMPLETED_RETENTION_MILLIS
      val expired = AndroidLifecycleController(
        context,
        database,
        wallClockMillis = { cleanupAt },
      )
      assertEquals("none", expired.latestDeletionReceiptPayload().getString("kind"))
      assertEquals("none", expired.currentOperationPayload().getString("kind"))
      assertNull(LifecycleStateStore(context).latestOperation())
      val lifecycleRaw = lifecycleFile().readText(Charsets.US_ASCII)
      val receiptRaw = deletionReceiptFile().readText(Charsets.US_ASCII)
      assertFalse(lifecycleRaw.contains(PRIVACY_ID))
      assertFalse(lifecycleRaw.contains(REQUEST_ID))
      assertFalse(receiptRaw.contains(PRIVACY_ID))
      assertFalse(receiptRaw.contains(REQUEST_ID))
      assertEquals("1\nEXPIRED\n", receiptRaw)
    } finally {
      database.close()
    }
  }

  @Test
  fun expirySanitizesStaleOperationWhenReceiptCompletionReconciliationWasInterrupted() {
    val staleOperation = deletionFallbackOperation().copy(
      state = "remote-pending",
      reason = "coordination-unavailable",
      updatedAtMillis = 2_000,
    )
    assertTrue(LifecycleStateStore(context).putOperation(staleOperation))
    val completedAt = 10_000L
    assertNotNull(DeletionReceiptStore(context).complete(REQUEST_ID, completedAt))

    // Models a process death after the receipt write and before controller reconciliation.
    val beforeExpiry = checkNotNull(LifecycleStateStore(context).latestOperation())
    assertEquals("remote-pending", beforeExpiry.state)
    assertFalse(beforeExpiry.remoteDeletionComplete == true)
    assertEquals(RECOVERY_UID_HASH, beforeExpiry.recoveryFirebaseUidHash)

    val cleanupAt = completedAt + DeletionReceiptStore.COMPLETED_RETENTION_MILLIS
    assertEquals(
      DeletionReceiptLookup.None,
      LifecycleStateStore(context).deletionReceiptLookup(cleanupAt),
    )
    assertNull(LifecycleStateStore(context).latestOperation())
    val lifecycleRaw = lifecycleFile().readText(Charsets.US_ASCII)
    val receiptRaw = deletionReceiptFile().readText(Charsets.US_ASCII)
    listOf(
      PRIVACY_ID,
      REQUEST_ID,
      RECOVERY_SALT,
      RECOVERY_UID_HASH,
      RECOVERY_SUBJECT_HASH,
    ).forEach { privateValue ->
      assertFalse(lifecycleRaw.contains(privateValue))
      assertFalse(receiptRaw.contains(privateValue))
    }
    assertEquals("1\nEXPIRED\n", receiptRaw)
  }

  @Test
  fun receiptBackupRecoversAndTruncationRemainsFailClosed() {
    val store = DeletionReceiptStore(context)
    assertTrue(store.putPending(PRIVACY_ID, REQUEST_ID, 1_000))
    val backup = File(deletionReceiptFile().path + ".bak")
    assertTrue(deletionReceiptFile().renameTo(backup))
    assertTrue(DeletionReceiptStore(context).lookup(2_000) is DeletionReceiptLookup.Present)

    val valid = deletionReceiptFile().readBytes()
    deletionReceiptFile().writeBytes(valid.copyOf(valid.size / 2))
    val unreadable = DeletionReceiptStore(context)
    assertEquals(DeletionReceiptLookup.Unavailable, unreadable.lookup(3_000))
    assertFalse(unreadable.putPending(OTHER_PRIVACY_ID, OTHER_REQUEST_ID, 4_000))
    assertEquals(DeletionReceiptLookup.Unavailable, unreadable.lookup(Long.MAX_VALUE))
  }

  @Test
  fun legacyMainJournalMigratesPendingReceiptIntoIndependentSlot() {
    val pending = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(LifecycleStateStore(context).putOperation(pending))
    deletionReceiptFile().delete()
    File(deletionReceiptFile().path + ".bak").delete()
    File(deletionReceiptFile().path + ".new").delete()

    val migrated = LifecycleStateStore(context).deletionReceiptLookup(4_000)
      as DeletionReceiptLookup.Present
    assertEquals(REQUEST_ID, migrated.receipt.receiptId)
    assertTrue(deletionReceiptFile().isFile)
  }

  @Test
  fun deletionV4BearerIsBoundBeforeDispatchAndReplayedAfterProcessDeath() {
    val queued = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "queued",
      reason = null,
      updatedAtMillis = 1_000,
      completedAtMillis = null,
      requestId = null,
    )
    assertTrue(LifecycleStateStore(context).putOperation(queued))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val plan = PrivacyActionPlan(PRIVACY_ID, "delete-account", true, true)
      val first = checkNotNull(
        AndroidLifecycleController(context, database, wallClockMillis = { 2_000 })
          .deletionRequestId(plan),
      )
      assertTrue(V4_UUID.matches(first))
      assertEquals(first, LifecycleStateStore(context).latestOperation()?.requestId)

      val afterAmbiguousCallAndDeath = checkNotNull(
        AndroidLifecycleController(context, database, wallClockMillis = { 3_000 })
          .deletionRequestId(plan),
      )
      assertEquals(first, afterAmbiguousCallAndDeath)
      assertEquals(first, LifecycleStateStore(context).latestOperation()?.requestId)
    } finally {
      database.close()
    }
  }

  @Test
  fun completedReceiptReconcilesAStaleMainOperationAfterPartialCommit() {
    val pending = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(LifecycleStateStore(context).putOperation(pending))
    assertNotNull(DeletionReceiptStore(context).complete(REQUEST_ID, 4_000))
    assertEquals("remote-draining", LifecycleStateStore(context).latestOperation()?.state)

    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val lookup = AndroidLifecycleController(context, database, wallClockMillis = { 5_000 })
        .deletionReceiptLookup()
      assertEquals(
        DurableDeletionReceipt.State.COMPLETED,
        (lookup as DeletionReceiptLookup.Present).receipt.state,
      )
      val reconciled = checkNotNull(LifecycleStateStore(context).latestOperation())
      assertEquals("complete", reconciled.state)
      assertEquals(true, reconciled.remoteDeletionComplete)
    } finally {
      database.close()
    }
  }

  @Test
  fun releaseWipeMarkerRequiresExactDurableRequestBinding() {
    val valid = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "sign-out-wipe",
      state = "local-wiping",
      reason = null,
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteRequestInstallationId = ACTIVE_INSTALLATION_ID,
      remoteRequestSenderEpoch = 11,
      remoteRequestResetGeneration = 4,
      localWipeStarted = true,
      wipeInstallationId = ACTIVE_INSTALLATION_ID,
      wipeCallbackGeneration = TARGET_INSTALLATION_ID,
      senderReleaseRecoverySalt = SENDER_RECOVERY_SALT,
      senderReleaseRecoveryFirebaseUidHash = SENDER_RECOVERY_UID_HASH,
      senderReleaseRecoveryGoogleSubjectHash = SENDER_RECOVERY_SUBJECT_HASH,
    )
    assertTrue(LifecycleStateStore(context).putOperation(valid))
    assertNotNull(LifecycleStateStore(context).pendingLocalWipe())

    files().forEach(File::delete)
    assertFalse(
      LifecycleStateStore(context).putOperation(
        valid.copy(
          remoteRequestInstallationId = null,
          remoteRequestSenderEpoch = null,
          remoteRequestResetGeneration = null,
        ),
      ),
    )
  }

  @Test
  fun unreadableJournalCanOnlyBeReplacedByExplicitAuthoritativeRecovery() {
    lifecycleFile().writeText("corrupt\n", Charsets.US_ASCII)
    val ordinary = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "disconnect-contacts",
      state = "local-wiping",
      reason = null,
      updatedAtMillis = 2_000,
      completedAtMillis = null,
    )
    val store = LifecycleStateStore(context)
    assertFalse(store.putOperation(ordinary))
    assertFalse(store.repairUnreadableWithAuthoritativeOperation(ordinary))

    val recovered = ordinary.copy(authoritativeRecoveryKind = "contact-reset")
    assertTrue(store.repairUnreadableWithAuthoritativeOperation(recovered))
    assertEquals(LifecycleJournalStatus.READABLE, LifecycleStateStore(context).journalStatus())
    assertEquals(recovered, LifecycleStateStore(context).latestOperation())
    assertFalse(
      LifecycleStateStore(context).repairUnreadableWithAuthoritativeOperation(
        recovered.copy(updatedAtMillis = 3_000),
      ),
    )
  }

  @Test
  fun senderReleaseBindingIsImmutableAndWipeMarkerRequiresTerminalProof() {
    val store = LifecycleStateStore(context)
    val operation = DurablePrivacyOperation(
      id = "privacy_0123456789abcdef0123456789abcdef",
      action = "sign-out-wipe",
      state = "queued",
      reason = null,
      updatedAtMillis = 1_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
    )
    assertTrue(store.putOperation(operation))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(
        context,
        database,
        activityProvider = { null },
      )
      val plan = PrivacyActionPlan(operation.id, operation.action, true, true)
      assertNotNull(
        controller.persistReleaseRequestBinding(
          plan,
          account(),
          ACTIVE_INSTALLATION_ID,
          11,
          4,
        ),
      )
      assertNull(
        controller.persistReleaseRequestBinding(
          plan,
          account(),
          ACTIVE_INSTALLATION_ID,
          12,
          4,
        ),
      )
      assertNull(
        controller.markLocalWipeStarted(
          plan,
          ACTIVE_INSTALLATION_ID,
          TARGET_INSTALLATION_ID,
        ),
      )

      controller.markCoordinatedOperationInProgress(
        plan,
        drainUntilMillis = 3_000,
        serverObservedAtMillis = 2_000,
        acceptedAtElapsedMillis = 400,
        acceptedBootCount = 7,
      )
      controller.markCoordinatedOperationCompleted(plan)
      val marker = controller.markLocalWipeStarted(
        plan,
        ACTIVE_INSTALLATION_ID,
        TARGET_INSTALLATION_ID,
      )
      assertNotNull(marker)
      assertTrue(checkNotNull(marker).localWipeStarted)
      assertEquals(REQUEST_ID, marker.requestId)
    } finally {
      database.close()
    }
  }

  @Test
  fun localFirstSenderWipeRecoveryStaysRemotePendingAndDropsOldCallbackCapability() {
    val marker = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "sign-out-wipe",
      state = "local-wiping",
      reason = null,
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteRequestInstallationId = ACTIVE_INSTALLATION_ID,
      remoteRequestSenderEpoch = 11,
      remoteRequestResetGeneration = 4,
      localWipeStarted = true,
      wipeInstallationId = ACTIVE_INSTALLATION_ID,
      wipeCallbackGeneration = TARGET_INSTALLATION_ID,
      senderReleaseRecoverySalt = SENDER_RECOVERY_SALT,
      senderReleaseRecoveryFirebaseUidHash = SENDER_RECOVERY_UID_HASH,
      senderReleaseRecoveryGoogleSubjectHash = SENDER_RECOVERY_SUBJECT_HASH,
    )
    assertTrue(LifecycleStateStore(context).putOperation(marker))
    val proof = checkNotNull(LifecycleStateStore(context).pendingLocalWipe())

    val pending = checkNotNull(
      LifecycleStateStore(context).completeRecoveredLocalWipe(proof, 3_000),
    )
    assertEquals("remote-pending", pending.state)
    assertTrue(pending.localDataErased)
    assertFalse(pending.localWipeStarted)
    assertNull(pending.wipeInstallationId)
    assertNull(pending.wipeCallbackGeneration)
    assertNull(LifecycleStateStore(context).pendingLocalWipe())
    assertFalse(lifecycleFile().readText(Charsets.US_ASCII).contains(TARGET_INSTALLATION_ID))

    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 4_000 })
      val projection = controller.currentOperationPayload()
      assertEquals("remote-pending", projection.getString("kind"))
      assertFalse(projection.toString().contains(REQUEST_ID))
      assertFalse(projection.toString().contains(SENDER_RECOVERY_SALT))

      val completed = checkNotNull(
        controller.completeSenderReleaseRemoteCleanup(
          PrivacyActionPlan(PRIVACY_ID, "sign-out-wipe", true, true),
        ),
      )
      assertEquals("complete", completed.state)
      assertTrue(completed.localDataErased)
      assertNull(completed.requestId)
      assertNull(completed.remoteRequestInstallationId)
      assertNull(completed.senderReleaseRecoverySalt)
      assertNull(completed.senderReleaseRecoveryFirebaseUidHash)
      assertNull(completed.senderReleaseRecoveryGoogleSubjectHash)
    } finally {
      database.close()
    }
  }

  @Test
  fun contactPayloadIsPurgedBeforeRemoteResetAndCompletionRemainsTruthful() =
    kotlinx.coroutines.runBlocking {
      val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
      try {
        database.birthdayDao().initializeIfAbsent("callback-generation")
        val ledger = database.safetyLedgerDao()
        ledger.insertAccount(account())
        ledger.putContactSyncState(
          ContactSyncStateEntity(
            accountId = ACCOUNT_ID,
            activeGeneration = "generation",
            stagingGeneration = null,
            syncToken = "private-token",
            parametersHash = "parameters",
            freshness = SyncFreshness.FRESH,
            lastFullSuccessMillis = 1_000,
            lastIncrementalSuccessMillis = null,
            lastAttemptMillis = 1_000,
            lastErrorCode = null,
            revision = 1,
          ),
        )
        ledger.insertContactSnapshot(
          ContactSnapshotEntity(
            contactId = "c_${"b".repeat(64)}",
            accountId = ACCOUNT_ID,
            peopleResourceName = "people/private",
            sourceFingerprint = "source",
            sourceEtag = "private-etag",
            displayName = "Private Person",
            safeGivenName = "Private",
            birthdayMonth = 7,
            birthdayDay = 12,
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
        assertTrue(
          LifecycleStateStore(context).putOperation(
            DurablePrivacyOperation(
              id = PRIVACY_ID,
              action = "disconnect-contacts",
              state = "pausing",
              reason = null,
              updatedAtMillis = 2_000,
              completedAtMillis = null,
              requestId = REQUEST_ID,
            ),
          ),
        )
        val controller = AndroidLifecycleController(context, database, wallClockMillis = { 3_000 })
        val plan = PrivacyActionPlan(PRIVACY_ID, "disconnect-contacts", true, true)

        val local = controller.purgeContactDerivedState(plan, ACCOUNT_ID)
        assertTrue(local.localDataErased)
        assertEquals("remote-pending", local.state)
        database.openHelper.readableDatabase.query(
          "SELECT COUNT(*) FROM contact_snapshots_v2 WHERE accountId = ?",
          arrayOf(ACCOUNT_ID),
        ).use { cursor ->
          assertTrue(cursor.moveToFirst())
          assertEquals(0, cursor.getInt(0))
        }
        assertEquals(
          SyncFreshness.AUTH_ACTION_REQUIRED,
          database.peopleSyncDao().contactSyncState(ACCOUNT_ID)?.freshness,
        )
        val disclosure = checkNotNull(
          database.configurationDao().latestConsentReceipt(
            ACCOUNT_ID,
            ConsentKind.CONTACTS_DISCLOSURE,
          ),
        )
        assertEquals(ConsentDecision.REVOKED, disclosure.decision)

        val completed = checkNotNull(controller.markContactResetRemoteCompleted(plan))
        assertEquals("complete", completed.state)
        assertTrue(completed.localDataErased)
      } finally {
        database.close()
      }
    }

  @Test
  fun localDeletionFallbackSurvivesWipeAndProjectsRemoteUnknownWithoutPrivateBinding() {
    val store = LifecycleStateStore(context)
    val marker = deletionFallbackOperation(localDataErased = false).copy(
      state = "local-wiping",
      localWipeStarted = true,
      wipeInstallationId = ACTIVE_INSTALLATION_ID,
      wipeCallbackGeneration = TARGET_INSTALLATION_ID,
    )
    assertTrue(store.putOperation(marker))
    val pending = checkNotNull(LifecycleStateStore(context).pendingLocalWipe())

    val completed = checkNotNull(
      LifecycleStateStore(context).completeRecoveredLocalWipe(pending, 4_000),
    )
    assertEquals("remote-pending", completed.state)
    assertTrue(completed.localDataErased)
    assertTrue(completed.deletionLocalWipeFallback)
    assertFalse(completed.deletionRetryAllowed)
    assertTrue(
      LifecycleStateStore(context).deletionReceiptLookup(4_000) is
        DeletionReceiptLookup.Present,
    )

    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val payload = AndroidLifecycleController(context, database, wallClockMillis = { 4_000 })
        .latestDeletionReceiptPayload()
      assertEquals("remote-unknown", payload.getString("kind"))
      assertFalse(payload.getBoolean("sameAccountRetryAvailable"))
      assertFalse(payload.toString().contains(REQUEST_ID))
      assertFalse(payload.toString().contains(RECOVERY_SALT))
      assertFalse(payload.toString().contains(RECOVERY_UID_HASH))
      assertFalse(payload.toString().contains(RECOVERY_SUBJECT_HASH))
    } finally {
      database.close()
    }
  }

  @Test
  fun pendingReceiptWithoutAnExactReadableLifecycleOperationProjectsUnavailable() {
    assertTrue(DeletionReceiptStore(context).putPending(PRIVACY_ID, REQUEST_ID, 1_000))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val missing = AndroidLifecycleController(context, database, wallClockMillis = { 2_000 })
        .latestDeletionReceiptPayload()
      assertEquals("unavailable", missing.getString("kind"))
      assertEquals("coordination-unavailable", missing.getString("reason"))
      assertFalse(missing.toString().contains(REQUEST_ID))
    } finally {
      database.close()
    }
  }

  @Test
  fun pendingReceiptWithMismatchedLifecycleOperationProjectsUnavailable() {
    assertTrue(
      LifecycleStateStore(context).putOperation(
        DurablePrivacyOperation(
          id = OTHER_PRIVACY_ID,
          action = "clear-activity",
          state = "complete",
          reason = null,
          updatedAtMillis = 3_000,
          completedAtMillis = 3_000,
        ),
      ),
    )
    assertTrue(DeletionReceiptStore(context).putPending(PRIVACY_ID, REQUEST_ID, 2_000))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val mismatched = AndroidLifecycleController(context, database, wallClockMillis = { 4_000 })
        .latestDeletionReceiptPayload()
      assertEquals("unavailable", mismatched.getString("kind"))
      assertEquals("coordination-unavailable", mismatched.getString("reason"))
    } finally {
      database.close()
    }
  }

  @Test
  fun corruptLifecyclePreventsInProgressEvidencePersistenceAndProjectsUnavailable() {
    assertTrue(LifecycleStateStore(context).putOperation(deletionFallbackOperation()))
    lifecycleFile().writeText("malformed\n", Charsets.US_ASCII)
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 4_000 })
      assertNull(
        controller.setDeletionRecoveryStatus(
          retryAllowed = false,
          inProgressObserved = true,
        ),
      )
      val corrupt = controller.latestDeletionReceiptPayload()
      assertEquals("unavailable", corrupt.getString("kind"))
      assertEquals("coordination-unavailable", corrupt.getString("reason"))
    } finally {
      database.close()
    }
  }

  @Test
  fun observedInProgressDeletionCannotDowngradeToUnknownOrEnableReplay() {
    assertTrue(LifecycleStateStore(context).putOperation(deletionFallbackOperation()))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 5_000 })
      val observed = checkNotNull(
        controller.setDeletionRecoveryStatus(
          retryAllowed = false,
          inProgressObserved = true,
        ),
      )
      assertTrue(observed.deletionInProgressObserved)
      assertEquals("remote-draining", controller.latestDeletionReceiptPayload().getString("kind"))

      val afterWeakStatus = checkNotNull(
        controller.setDeletionRecoveryStatus(
          retryAllowed = true,
          inProgressObserved = false,
        ),
      )
      assertTrue(afterWeakStatus.deletionInProgressObserved)
      assertFalse(afterWeakStatus.deletionRetryAllowed)
      assertEquals("remote-draining", controller.latestDeletionReceiptPayload().getString("kind"))
    } finally {
      database.close()
    }
  }

  @Test
  fun acceptedDeletionDrainRemainsStrongerThanLaterStatusResponses() {
    val accepted = DurablePrivacyOperation(
      id = PRIVACY_ID,
      action = "delete-account",
      state = "remote-draining",
      reason = "firebase-account-deleting",
      updatedAtMillis = 2_000,
      completedAtMillis = null,
      requestId = REQUEST_ID,
      remoteDrainUntilMillis = 3_000,
      serverObservedAtMillis = 2_000,
      acceptedAtElapsedMillis = 400,
      acceptedBootCount = 7,
      localDataErased = true,
      remoteDeletionComplete = false,
    )
    assertTrue(LifecycleStateStore(context).putOperation(accepted))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 5_000 })
      assertEquals(
        accepted,
        controller.setDeletionRecoveryStatus(
          retryAllowed = false,
          inProgressObserved = true,
        ),
      )
      assertEquals(
        accepted,
        controller.setDeletionRecoveryStatus(
          retryAllowed = true,
          inProgressObserved = false,
        ),
      )
      assertEquals("remote-draining", controller.latestDeletionReceiptPayload().getString("kind"))
    } finally {
      database.close()
    }
  }

  @Test
  fun recoveryBindingClearsOnlyAfterAuthoritativeAcceptanceIsDurablyWritten() {
    val fallback = deletionFallbackOperation().copy(deletionRetryAllowed = true)
    assertTrue(LifecycleStateStore(context).putOperation(fallback))
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 5_000 })
      val accepted = checkNotNull(
        controller.markDeletionRecoveryAccepted(
          requestId = REQUEST_ID,
          drainUntilMillis = 6_000,
          serverObservedAtMillis = 5_000,
          acceptedAtElapsedMillis = 500,
          acceptedBootCount = 7,
        ),
      )
      assertFalse(accepted.deletionLocalWipeFallback)
      assertFalse(accepted.deletionRetryAllowed)
      assertNull(accepted.recoveryBindingSalt)
      assertNull(accepted.recoveryFirebaseUidHash)
      assertNull(accepted.recoveryGoogleSubjectHash)
      assertEquals(accepted, LifecycleStateStore(context).latestOperation())
    } finally {
      database.close()
    }
  }

  @Test
  fun failedAcceptancePersistenceRetainsRecoveryBindingAndRetry() {
    val fallback = deletionFallbackOperation().copy(deletionRetryAllowed = true)
    assertTrue(LifecycleStateStore(context).putOperation(fallback))
    File(deletionReceiptFile().path + ".bak").delete()
    deletionReceiptFile().writeText("malformed\n", Charsets.US_ASCII)
    assertEquals(
      DeletionReceiptLookup.Unavailable,
      DeletionReceiptStore(context).lookupWithoutExpiry(),
    )
    val database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    try {
      val controller = AndroidLifecycleController(context, database, wallClockMillis = { 5_000 })
      assertNull(
        controller.markDeletionRecoveryAccepted(
          requestId = REQUEST_ID,
          drainUntilMillis = 6_000,
          serverObservedAtMillis = 5_000,
          acceptedAtElapsedMillis = 500,
          acceptedBootCount = 7,
        ),
      )
      val retained = checkNotNull(LifecycleStateStore(context).latestOperation())
      assertTrue(retained.deletionLocalWipeFallback)
      assertTrue(retained.deletionRetryAllowed)
      assertEquals(RECOVERY_SALT, retained.recoveryBindingSalt)
      assertEquals(RECOVERY_UID_HASH, retained.recoveryFirebaseUidHash)
      assertEquals(RECOVERY_SUBJECT_HASH, retained.recoveryGoogleSubjectHash)
    } finally {
      database.close()
    }
  }

  private fun deletionFallbackOperation(
    localDataErased: Boolean = true,
  ) = DurablePrivacyOperation(
    id = PRIVACY_ID,
    action = "delete-account",
    state = if (localDataErased) "remote-pending" else "local-wiping",
    reason = if (localDataErased) "coordination-unavailable" else null,
    updatedAtMillis = 3_000,
    completedAtMillis = null,
    requestId = REQUEST_ID,
    localDataErased = localDataErased,
    remoteDeletionComplete = false,
    deletionLocalWipeFallback = true,
    recoveryBindingSalt = RECOVERY_SALT,
    recoveryFirebaseUidHash = RECOVERY_UID_HASH,
    recoveryGoogleSubjectHash = RECOVERY_SUBJECT_HASH,
    deletionRetryAllowed = false,
  )

  private fun account() = AccountRecordEntity(
    accountId = ACCOUNT_ID,
    activeSlot = 1,
    googleSubjectHash = StablePrivateId.hash("GoogleSubject.v1", GOOGLE_SUBJECT),
    firebaseUid = FIREBASE_UID,
    displayEmail = "private@example.invalid",
    localeTag = "en-IN",
    state = AccountRecordState.ACTIVE,
    revision = 1,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
  )

  private fun files(): List<File> = listOf(
    lifecycleFile(),
    File(lifecycleFile().path + ".bak"),
    File(lifecycleFile().path + ".new"),
    deletionReceiptFile(),
    File(deletionReceiptFile().path + ".bak"),
    File(deletionReceiptFile().path + ".new"),
    File(context.noBackupFilesDir, "birthday-attention-routes-v1"),
    File(context.noBackupFilesDir, "birthday-attention-routes-v1.bak"),
    File(context.noBackupFilesDir, "birthday-attention-routes-v1.new"),
    notificationPermissionFile(),
    File(notificationPermissionFile().path + ".bak"),
    File(notificationPermissionFile().path + ".new"),
  )

  private fun lifecycleFile() =
    File(context.noBackupFilesDir, "birthday-lifecycle-state-v1")

  private fun deletionReceiptFile() =
    File(context.noBackupFilesDir, "birthday-deletion-receipt-v1")

  private fun notificationPermissionFile() =
    File(context.noBackupFilesDir, "birthday-notification-permission-v1")

  private companion object {
    const val REQUEST_ID = "00000000-0000-4000-8000-000000000001"
    const val OTHER_REQUEST_ID = "00000000-0000-4000-8000-000000000002"
    const val PRIVACY_ID = "privacy_0123456789abcdef0123456789abcdef"
    const val OTHER_PRIVACY_ID = "privacy_fedcba9876543210fedcba9876543210"
    const val ACTIVE_INSTALLATION_ID = "0123456789abcdef0123456789abcdef"
    const val TARGET_INSTALLATION_ID = "fedcba9876543210fedcba9876543210"
    const val ACCOUNT_ID = "a_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val FIREBASE_UID = "firebase-uid-0123456789"
    const val GOOGLE_SUBJECT = "123456789012345678901"
    val RECOVERY_SALT = "1".repeat(64)
    val RECOVERY_UID_HASH = "2".repeat(64)
    val RECOVERY_SUBJECT_HASH = "3".repeat(64)
    val SENDER_RECOVERY_SALT = "4".repeat(64)
    val SENDER_RECOVERY_UID_HASH = "5".repeat(64)
    val SENDER_RECOVERY_SUBJECT_HASH = "6".repeat(64)
    val UUID = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
    val V4_UUID = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
  }
}
