package com.example.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.AppDatabase
import com.example.core.db.entities.ContactEntity
import com.example.core.prefs.SecurePrefs
import com.example.domain.service.BackupFailureReason
import com.example.domain.service.BackupOperationResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class BackupServiceImplTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var service: BackupServiceImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = BackupServiceImpl(context, database, SecurePrefs(context))
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "backup-test.enc").delete()
    }

    @Test
    fun importBackup_unsupportedVersionReturnsStableFailure() = runTest {
        val uri = encryptedFixture("""{"version":2}""")

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Failure)
        assertEquals(
            BackupFailureReason.UNSUPPORTED_VERSION,
            (result as BackupOperationResult.Failure).reason,
        )
    }

    @Test
    fun importBackup_databaseFailureRollsBackEarlierWrites() = runTest {
        val uri = encryptedFixture(
            """
            {
              "version": 1,
              "contacts": [
                { "id": "contact_1", "name": "Alice" }
              ],
              "events": [
                {
                  "id": "event_1",
                  "contactId": "missing_contact",
                  "type": "BIRTHDAY",
                  "dayOfMonth": 1,
                  "month": 1,
                  "nextOccurrenceMs": 1700000000000
                }
              ]
            }
            """.trimIndent()
        )

        val result = service.importBackup(uri, PASSPHRASE)

        assertTrue(result is BackupOperationResult.Failure)
        assertEquals(
            BackupFailureReason.DATABASE_ERROR,
            (result as BackupOperationResult.Failure).reason,
        )
        assertNull(database.contactDao().getById("contact_1"))
    }

    @Test
    fun exportBackup_returnsFileNameAndSize() = runTest {
        database.contactDao().upsert(ContactEntity(id = "contact_1", name = "Alice"))

        val result = service.exportBackup(outputUri = null, passphrase = PASSPHRASE)

        assertTrue(result is BackupOperationResult.Success)
        val value = (result as BackupOperationResult.Success).value
        assertTrue(value.fileName.startsWith("relateai_backup_"))
        assertTrue(value.sizeBytes > 0)
    }

    private fun encryptedFixture(json: String): Uri {
        val file = File(context.filesDir, "backup-test.enc")
        file.writeText(BackupEncryption.encrypt(json, PASSPHRASE))
        return Uri.fromFile(file)
    }

    private companion object {
        const val PASSPHRASE = "Abc12345!"
    }
}
