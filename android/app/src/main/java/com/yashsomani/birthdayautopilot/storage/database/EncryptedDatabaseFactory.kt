package com.yashsomani.birthdayautopilot.storage.database

import android.content.Context
import androidx.room.Room
import com.yashsomani.birthdayautopilot.core.crypto.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object EncryptedDatabaseFactory {
  fun create(context: Context): BirthdayDatabase {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath(BirthdayDatabase.DATABASE_NAME)
    val passphrase = DatabaseKeyManager(appContext)
      .getOrCreatePassphrase(databaseAlreadyExists = databaseFile.exists())

    System.loadLibrary("sqlcipher")
    val openHelperFactory = SupportOpenHelperFactory(passphrase)
    return try {
      Room.databaseBuilder(
        appContext,
        BirthdayDatabase::class.java,
        BirthdayDatabase.DATABASE_NAME,
      )
        .openHelperFactory(openHelperFactory)
        .build()
        .also { database -> database.openHelper.writableDatabase }
    } finally {
      passphrase.fill(0)
    }
  }
}
