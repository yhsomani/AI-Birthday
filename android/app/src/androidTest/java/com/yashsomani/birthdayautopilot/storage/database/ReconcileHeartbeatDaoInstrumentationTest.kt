package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconcileHeartbeatDaoInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var dao: SafetyLedgerDao

  @Before
  fun setUp() = runBlocking {
    database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      BirthdayDatabase::class.java,
    ).build()
    database.birthdayDao().initializeIfAbsent("callback-generation")
    dao = database.safetyLedgerDao()
    dao.insertAccount(
      AccountRecordEntity(
        accountId = ACCOUNT_ID,
        activeSlot = 1,
        googleSubjectHash = "2".repeat(64),
        firebaseUid = "firebase-uid",
        displayEmail = null,
        localeTag = "en-IN",
        state = AccountRecordState.ACTIVE,
        revision = 0,
        createdAtMillis = 1_000,
        updatedAtMillis = 1_000,
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun newerWorkerOwnsTheOnlyFinishableHeartbeatLease() = runBlocking {
    val first = requireNotNull(dao.beginReconcileHeartbeat(ACCOUNT_ID, 1_000))
    assertEquals(0L, first.revision)
    assertEquals(
      ReconcileHeartbeatSnapshot(
        ReconcileHeartbeatStatus.RUNNING,
        ReconcileHeartbeatPolicy.RUNNING_SAFE_CODE,
        1_000,
      ),
      ReconcileHeartbeatPolicy.snapshot(dao.getReadinessState(ACCOUNT_ID)),
    )

    val second = requireNotNull(dao.beginReconcileHeartbeat(ACCOUNT_ID, 2_000))
    assertEquals(1L, second.revision)
    assertFalse(
      dao.finishReconcileHeartbeat(
        first,
        ReconcileHeartbeatStatus.FAILED,
        "STALE_WORKER_FAILURE",
        3_000,
      ),
    )
    assertTrue(
      dao.finishReconcileHeartbeat(
        second,
        ReconcileHeartbeatStatus.SUCCEEDED,
        "network-offline",
        2_500,
      ),
    )

    val stored = requireNotNull(dao.getReadinessState(ACCOUNT_ID))
    assertEquals(2L, stored.revision)
    assertEquals(
      ReconcileHeartbeatSnapshot(
        ReconcileHeartbeatStatus.SUCCEEDED,
        "NETWORK_OFFLINE",
        2_500,
      ),
      ReconcileHeartbeatPolicy.snapshot(stored),
    )
  }

  @Test
  fun finishNeverMovesTheHeartbeatBeforeItsRecordedStart() = runBlocking {
    val lease = requireNotNull(dao.beginReconcileHeartbeat(ACCOUNT_ID, 5_000))

    assertTrue(
      dao.finishReconcileHeartbeat(
        lease,
        ReconcileHeartbeatStatus.RETRYING,
        ReconcileHeartbeatPolicy.RETRY_SAFE_CODE,
        4_000,
      ),
    )

    assertEquals(
      5_000L,
      requireNotNull(dao.getReadinessState(ACCOUNT_ID)).evaluatedAtMillis,
    )
  }

  private companion object {
    val ACCOUNT_ID = "a_${"a".repeat(64)}"
  }
}
