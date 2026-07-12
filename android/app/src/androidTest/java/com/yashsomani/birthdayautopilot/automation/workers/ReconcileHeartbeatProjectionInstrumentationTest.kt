package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.configuration.AndroidConfigurationController
import com.yashsomani.birthdayautopilot.lifecycle.AndroidLifecycleController
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordState
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatStatus
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconcileHeartbeatProjectionInstrumentationTest {
  private lateinit var context: Context
  private lateinit var database: BirthdayDatabase

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    lifecycleFiles().forEach(File::delete)
    database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    database.birthdayDao().initializeIfAbsent("callback-generation")
    database.safetyLedgerDao().insertAccount(
      AccountRecordEntity(
        accountId = ACCOUNT_ID,
        activeSlot = 1,
        googleSubjectHash = "2".repeat(64),
        firebaseUid = "firebase-uid",
        displayEmail = null,
        localeTag = "en-IN",
        state = AccountRecordState.ACTIVE,
        revision = 0,
        createdAtMillis = HEARTBEAT_AT,
        updatedAtMillis = HEARTBEAT_AT,
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
    lifecycleFiles().forEach(File::delete)
  }

  @Test
  fun homeAndDiagnosticsProjectTheLatestFinishedWorkerHeartbeat() = runBlocking {
    val ledger = database.safetyLedgerDao()
    val completed = requireNotNull(ledger.beginReconcileHeartbeat(ACCOUNT_ID, HEARTBEAT_AT - 100))
    assertTrue(
      ledger.finishReconcileHeartbeat(
        completed,
        ReconcileHeartbeatStatus.SUCCEEDED,
        "AUTOMATION_RECONCILED",
        HEARTBEAT_AT,
      ),
    )
    val expectedInstant = Instant.ofEpochMilli(HEARTBEAT_AT).toString()

    val home = AndroidConfigurationController(
      context,
      database,
      RecurrencePlanner(),
    ).homePayload(
      automation = JSONObject().put("platform", "android"),
      contactsSync = JSONObject().put("kind", "never-synced"),
    )
    assertEquals(expectedInstant, home.getString("schedulerHeartbeatAt"))

    val diagnostics = AndroidLifecycleController(
      context,
      database,
      appStandbyBucketCode = { "app-standby-bucket-active" },
    ).diagnosticsPayload()
    assertEquals(expectedInstant, diagnostics.getString("schedulerHeartbeatAt"))
    assertTrue(
      (0 until diagnostics.getJSONArray("capabilityCodes").length()).any {
        diagnostics.getJSONArray("capabilityCodes").getString(it) ==
          "app-standby-bucket-active"
      },
    )

    val failed = requireNotNull(ledger.beginReconcileHeartbeat(ACCOUNT_ID, HEARTBEAT_AT + 1_000))
    assertTrue(
      ledger.finishReconcileHeartbeat(
        failed,
        ReconcileHeartbeatStatus.FAILED,
        "RECONCILE_ATTEMPTS_EXHAUSTED",
        HEARTBEAT_AT + 2_000,
      ),
    )
    val failedDiagnostics = AndroidLifecycleController(context, database).diagnosticsPayload()
    val capabilityCodes = failedDiagnostics.getJSONArray("capabilityCodes")
    assertTrue(
      (0 until capabilityCodes.length()).any {
        capabilityCodes.getString(it) == "scheduler-delayed"
      },
    )
  }

  private fun lifecycleFiles(): List<File> {
    val journal = File(context.noBackupFilesDir, "birthday-lifecycle-state-v1")
    val receipt = File(context.noBackupFilesDir, "birthday-deletion-receipt-v1")
    return listOf(
      journal,
      File(journal.path + ".bak"),
      File(journal.path + ".new"),
      receipt,
      File(receipt.path + ".bak"),
      File(receipt.path + ".new"),
    )
  }

  private companion object {
    val ACCOUNT_ID = "a_${"a".repeat(64)}"
    const val HEARTBEAT_AT = 1_800_000_000_000L
  }
}
