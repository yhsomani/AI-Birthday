package com.yashsomani.birthdayautopilot.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.automation.workers.SchedulerStartupStateStore
import com.yashsomani.birthdayautopilot.automation.workers.SchedulerStartupStatus
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReliabilityStateStoresInstrumentationTest {
  private lateinit var context: Context
  private lateinit var originalSchedulerStatus: SchedulerStartupStatus
  private lateinit var originalDenial: TelephonyPermanentDenial

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    originalSchedulerStatus = SchedulerStartupStateStore(context).status()
    originalDenial = TelephonyPermissionDenialStore(context).reconcile(false, false)
    SchedulerStartupStateStore.resetProcessStatusForTests()
    schedulerFiles().forEach(File::delete)
    assertTrue(TelephonyPermissionDenialStore(context).clearForTests())
  }

  @After
  fun tearDown() {
    SchedulerStartupStateStore.resetProcessStatusForTests()
    schedulerFiles().forEach(File::delete)
    if (originalSchedulerStatus != SchedulerStartupStatus.UNKNOWN) {
      SchedulerStartupStateStore(context).record(originalSchedulerStatus)
    } else {
      SchedulerStartupStateStore.resetProcessStatusForTests()
    }
    val denial = TelephonyPermissionDenialStore(context)
    denial.clearForTests()
    if (originalDenial.blocksPhoneState() && originalDenial != TelephonyPermanentDenial.UNKNOWN) {
      denial.markPhoneStatePermanent()
    }
    if (originalDenial.blocksSms() && originalDenial != TelephonyPermanentDenial.UNKNOWN) {
      denial.markSmsPermanent()
    }
  }

  @Test
  fun schedulerStartupEvidenceSurvivesAStoreRecreation() {
    val store = SchedulerStartupStateStore(context)
    assertEquals(SchedulerStartupStatus.UNKNOWN, store.status())
    assertTrue(store.record(SchedulerStartupStatus.FAILED))
    SchedulerStartupStateStore.resetProcessStatusForTests()
    assertEquals(SchedulerStartupStatus.FAILED, SchedulerStartupStateStore(context).status())
    assertTrue(store.record(SchedulerStartupStatus.READY))
    SchedulerStartupStateStore.resetProcessStatusForTests()
    assertEquals(SchedulerStartupStatus.READY, SchedulerStartupStateStore(context).status())
  }

  @Test
  fun permanentTelephonyDenialsRemainExactAndSettingsGrantsClearThem() {
    val store = TelephonyPermissionDenialStore(context)
    assertTrue(store.markPhoneStatePermanent())
    assertEquals(
      TelephonyPermanentDenial.PHONE_STATE,
      TelephonyPermissionDenialStore(context).reconcile(false, false),
    )
    assertTrue(store.markSmsPermanent())
    assertEquals(TelephonyPermanentDenial.BOTH, store.reconcile(false, false))

    assertEquals(TelephonyPermanentDenial.SMS, store.reconcile(true, false))
    assertEquals(TelephonyPermanentDenial.NONE, store.reconcile(true, true))
    assertEquals(
      TelephonyPermanentDenial.NONE,
      TelephonyPermissionDenialStore(context).reconcile(false, false),
    )
  }

  private fun schedulerFiles(): List<File> {
    val base = File(context.noBackupFilesDir, "birthday-scheduler-startup-v1")
    return listOf(base, File(base.path + ".bak"), File(base.path + ".new"))
  }
}
