package com.yashsomani.birthdayautopilot.storage.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class Migration4To5InstrumentationTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    BirthdayDatabase::class.java,
  )

  @After
  fun cleanUp() {
    context.deleteDatabase(ACTIVE_DATABASE)
    context.deleteDatabase(TEST_ONLY_DATABASE)
  }

  @Test
  fun activeV4ControlBackfillsCompletedActivation() = runBlocking {
    createV4Control(ACTIVE_DATABASE, "AUTOMATION_ACTIVE", automationDesired = true)

    val database = openLatest(ACTIVE_DATABASE)
    try {
      assertTrue(checkNotNull(database.birthdayDao().getControl()).initialActivationCompleted)
    } finally {
      database.close()
    }
  }

  @Test
  fun testOnlyV4ControlRemainsConservativelyIncomplete() = runBlocking {
    createV4Control(TEST_ONLY_DATABASE, "TEST_ONLY", automationDesired = false)

    val database = openLatest(TEST_ONLY_DATABASE)
    try {
      assertFalse(checkNotNull(database.birthdayDao().getControl()).initialActivationCompleted)
    } finally {
      database.close()
    }
  }

  private fun createV4Control(databaseName: String, mode: String, automationDesired: Boolean) {
    helper.createDatabase(databaseName, 4).use { database ->
      database.execSQL(
        """
        INSERT INTO app_control(
          singletonId, revision, blockerRevision, accountMode, automationDesired,
          activeInstallationEpoch, lastTrustedServerMillis, lastTrustedElapsedMillis,
          trustedBootCount, resetSafetyState
        ) VALUES(1, 4, 9, ?, ?, 7, 1000, 100, 1, 'CLEAR')
        """.trimIndent(),
        arrayOf<Any>(mode, if (automationDesired) 1 else 0),
      )
    }
  }

  private fun openLatest(databaseName: String): BirthdayDatabase =
    Room.databaseBuilder(context, BirthdayDatabase::class.java, databaseName)
      .build()
      .also { it.openHelper.writableDatabase }

  private companion object {
    const val ACTIVE_DATABASE = "migration-4-5-active.db"
    const val TEST_ONLY_DATABASE = "migration-4-5-test-only.db"
  }
}
