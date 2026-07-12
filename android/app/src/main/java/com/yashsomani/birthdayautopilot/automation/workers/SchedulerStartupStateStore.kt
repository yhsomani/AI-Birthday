package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets

internal enum class SchedulerStartupStatus {
  READY,
  FAILED,
  UNKNOWN,
}

internal fun interface SchedulerStartupStateRecorder {
  fun record(status: SchedulerStartupStatus): Boolean
}

/** Backup-excluded, content-free evidence that the production scheduler inventory was installed. */
internal class SchedulerStartupStateStore(context: Context) : SchedulerStartupStateRecorder {
  private val baseFile = File(
    context.applicationContext.noBackupFilesDir,
    "birthday-scheduler-startup-v1",
  )
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)

  fun status(): SchedulerStartupStatus {
    val processStatus = PROCESS_STATUS
    if (processStatus != SchedulerStartupStatus.UNKNOWN) return processStatus
    return synchronized(FILE_LOCK) {
      if (!baseFile.exists() && !legacyBackupFile.exists()) {
        return@synchronized SchedulerStartupStatus.UNKNOWN
      }
      val value = try {
        file.openRead().use { stream ->
          val buffer = ByteArray(MAX_BYTES + 1)
          var total = 0
          while (total < buffer.size) {
            val count = stream.read(buffer, total, buffer.size - total)
            if (count < 0) break
            if (count == 0) break
            total += count
          }
          if (total <= 0 || total > MAX_BYTES || stream.read() != -1) {
            return@synchronized SchedulerStartupStatus.FAILED
          }
          String(buffer, 0, total, StandardCharsets.US_ASCII)
        }
      } catch (_: Exception) {
        return@synchronized SchedulerStartupStatus.FAILED
      }
      when (value) {
        READY_VALUE -> SchedulerStartupStatus.READY
        FAILED_VALUE -> SchedulerStartupStatus.FAILED
        else -> SchedulerStartupStatus.FAILED
      }
    }
  }

  override fun record(status: SchedulerStartupStatus): Boolean {
    val value = when (status) {
      SchedulerStartupStatus.READY -> READY_VALUE
      SchedulerStartupStatus.FAILED, SchedulerStartupStatus.UNKNOWN -> FAILED_VALUE
    }
    // Never allow an older durable READY value to mask a failed write in this process.
    PROCESS_STATUS = SchedulerStartupStatus.FAILED
    val persisted = synchronized(FILE_LOCK) {
      val stream = try {
        file.startWrite()
      } catch (_: Exception) {
        return@synchronized false
      }
      try {
        stream.write(value.toByteArray(StandardCharsets.US_ASCII))
        stream.fd.sync()
        file.finishWrite(stream)
        true
      } catch (_: Exception) {
        try {
          file.failWrite(stream)
        } catch (_: Exception) {
          // The process-local FAILED projection remains authoritative.
        }
        false
      }
    }
    if (persisted) {
      PROCESS_STATUS = if (status == SchedulerStartupStatus.READY) {
        SchedulerStartupStatus.READY
      } else {
        SchedulerStartupStatus.FAILED
      }
    }
    return persisted
  }

  internal companion object {
    const val READY_VALUE = "READY\n"
    const val FAILED_VALUE = "FAILED\n"
    const val MAX_BYTES = 16
    val FILE_LOCK = Any()
    @Volatile
    // ContentProviders (including WorkManager) can run before Application.onCreate. A fresh
    // process therefore starts blocked and never trusts READY evidence from a previous process.
    private var PROCESS_STATUS = SchedulerStartupStatus.FAILED

    fun resetProcessStatusForTests() {
      PROCESS_STATUS = SchedulerStartupStatus.UNKNOWN
    }
  }
}

/** Keeps app startup available while recording any incomplete scheduler installation fail-closed. */
internal object SchedulerStartupCoordinator {
  fun initialize(
    recorder: SchedulerStartupStateRecorder,
    schedule: () -> Unit,
  ): SchedulerStartupStatus {
    // Invalidate any earlier READY evidence before WorkManager can start an existing job in this
    // process. READY is projected again only after the complete scheduler inventory is installed.
    if (!recordSafely(recorder, SchedulerStartupStatus.FAILED)) {
      return SchedulerStartupStatus.FAILED
    }
    val scheduled = try {
      schedule()
      true
    } catch (_: Exception) {
      false
    } catch (_: LinkageError) {
      false
    }
    if (!scheduled) return SchedulerStartupStatus.FAILED
    return if (recordSafely(recorder, SchedulerStartupStatus.READY)) {
      SchedulerStartupStatus.READY
    } else {
      SchedulerStartupStatus.FAILED
    }
  }

  private fun recordSafely(
    recorder: SchedulerStartupStateRecorder,
    status: SchedulerStartupStatus,
  ): Boolean = try {
    recorder.record(status)
  } catch (_: Exception) {
    false
  } catch (_: LinkageError) {
    false
  }
}
