package com.example.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.db.dao.ActivityLogDao
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DiagnosticSnapshotDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.GiftHistoryDao
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.dao.MessageFeedbackDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.StyleProfileDao
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.DiagnosticSnapshotEntity
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.StructuredLogger
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        ContactEntity::class,
        EventEntity::class,
        PendingMessageEntity::class,
        SentMessageEntity::class,
        StyleProfileEntity::class,
        MemoryNoteEntity::class,
        GiftHistoryEntity::class,
        StyleProfileHistoryEntity::class,
        ActivityLogEntity::class,
        MessageFeedbackEntity::class,
        DispatchAttemptEntity::class,
        DiagnosticSnapshotEntity::class,
    ],
    version = 16,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun eventDao(): EventDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun sentMessageDao(): SentMessageDao
    abstract fun styleProfileDao(): StyleProfileDao
    abstract fun memoryNoteDao(): MemoryNoteDao
    abstract fun giftHistoryDao(): GiftHistoryDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun messageFeedbackDao(): MessageFeedbackDao
    abstract fun dispatchAttemptDao(): DispatchAttemptDao
    abstract fun diagnosticSnapshotDao(): DiagnosticSnapshotDao

    companion object {
        private const val TAG = "AppDatabase"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = AppDatabaseMigrations.MIGRATION_2_3
        val MIGRATION_3_4 = AppDatabaseMigrations.MIGRATION_3_4
        val MIGRATION_4_5 = AppDatabaseMigrations.MIGRATION_4_5
        val MIGRATION_5_6 = AppDatabaseMigrations.MIGRATION_5_6
        val MIGRATION_6_7 = AppDatabaseMigrations.MIGRATION_6_7
        val MIGRATION_7_8 = AppDatabaseMigrations.MIGRATION_7_8
        val MIGRATION_8_9 = AppDatabaseMigrations.MIGRATION_8_9
        val MIGRATION_9_10 = AppDatabaseMigrations.MIGRATION_9_10
        val MIGRATION_10_11 = AppDatabaseMigrations.MIGRATION_10_11
        val MIGRATION_11_12 = AppDatabaseMigrations.MIGRATION_11_12
        val MIGRATION_12_13 = AppDatabaseMigrations.MIGRATION_12_13
        val MIGRATION_13_14 = AppDatabaseMigrations.MIGRATION_13_14
        val MIGRATION_14_15 = AppDatabaseMigrations.MIGRATION_14_15
        val MIGRATION_15_16 = AppDatabaseMigrations.MIGRATION_15_16

        fun closeAndResetInstance() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        db.close()
                    } catch (e: Exception) {
                        StructuredLogger.e(TAG, "Error closing database", e)
                    }
                }
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val quarantineResult = LegacyDatabaseQuarantine.quarantineIfPlaintext(context.applicationContext)
                if (quarantineResult.quarantined) {
                    StructuredLogger.w(
                        TAG,
                        "Quarantined legacy unencrypted database",
                        extras = mapOf("directoryPresent" to (quarantineResult.directory != null).toString()),
                    )
                    try {
                        SecurePrefs(context.applicationContext).setLegacyUnencryptedDbQuarantined(true)
                    } catch (e: Exception) {
                        StructuredLogger.e(TAG, "Failed to persist legacy DB quarantine notice", e)
                    }
                }

                val passphrase = DatabaseKeyDerivation.deriveKey(context)
                val factory = SupportFactory(passphrase, null, false)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "relateai.db"
                )
                .openHelperFactory(factory)
                .addMigrations(*AppDatabaseMigrations.ALL)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
