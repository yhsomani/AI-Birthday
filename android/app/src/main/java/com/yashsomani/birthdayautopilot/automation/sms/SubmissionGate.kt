package com.yashsomani.birthdayautopilot.automation.sms

import android.content.Context
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes the short local decision boundary across coroutines and Android processes.
 *
 * The lock file contains no data and lives in no-backup app storage. Network waits must never be
 * executed while this gate is held.
 */
class SubmissionGate(context: Context) {
  private val lockFile = context.applicationContext.noBackupFilesDir
    .resolve("birthday-submission-gate-v1.lock")
    .toPath()

  suspend fun <T> withExclusiveBoundary(block: suspend () -> T): T = processMutex.withLock {
    withContext(Dispatchers.IO) {
      FileChannel.open(
        lockFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
      ).use { channel ->
        channel.lock().use {
          block()
        }
      }
    }
  }

  private companion object {
    val processMutex = Mutex()
  }
}
