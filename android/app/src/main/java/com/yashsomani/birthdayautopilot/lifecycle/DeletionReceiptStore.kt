package com.yashsomani.birthdayautopilot.lifecycle

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets

internal data class DurableDeletionReceipt(
  val operationId: String,
  val receiptId: String,
  val state: State,
  val updatedAtMillis: Long,
  val completedAtMillis: Long?,
  val cleanupAtMillis: Long?,
) {
  enum class State { PENDING, COMPLETED }
}

internal sealed interface DeletionReceiptLookup {
  data object None : DeletionReceiptLookup
  data object Unavailable : DeletionReceiptLookup
  data class Present(val receipt: DurableDeletionReceipt) : DeletionReceiptLookup
}

private sealed interface ReceiptJournalSnapshot {
  data object Absent : ReceiptJournalSnapshot
  data object Unreadable : ReceiptJournalSnapshot
  data object Expired : ReceiptJournalSnapshot
  data class Present(val receipt: DurableDeletionReceipt) : ReceiptJournalSnapshot
}

/**
 * Independent, backup-excluded bearer-receipt journal. Pending and corrupt records never expire;
 * an exact verified completion is retained for one year, then atomically reduced to a content-free
 * expiry marker so neither the raw bearer nor its operation identity can be resurrected.
 */
internal class DeletionReceiptStore(context: Context) {
  private val baseFile =
    File(context.applicationContext.noBackupFilesDir, "birthday-deletion-receipt-v1")
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)

  fun lookup(nowMillis: Long): DeletionReceiptLookup = synchronized(FILE_LOCK) {
    if (nowMillis < 0) return DeletionReceiptLookup.Unavailable
    when (val snapshot = read()) {
      ReceiptJournalSnapshot.Absent,
      ReceiptJournalSnapshot.Expired,
      -> DeletionReceiptLookup.None
      ReceiptJournalSnapshot.Unreadable -> DeletionReceiptLookup.Unavailable
      is ReceiptJournalSnapshot.Present -> {
        val receipt = snapshot.receipt
        if (
          receipt.state == DurableDeletionReceipt.State.COMPLETED &&
          nowMillis >= checkNotNull(receipt.cleanupAtMillis)
        ) {
          if (writeExpired()) DeletionReceiptLookup.None
          else DeletionReceiptLookup.Present(receipt)
        } else {
          DeletionReceiptLookup.Present(receipt)
        }
      }
    }
  }

  /** Lifecycle-owned lookup defers expiry until its matching operation journal is sanitized. */
  fun lookupWithoutExpiry(): DeletionReceiptLookup = synchronized(FILE_LOCK) {
    when (val snapshot = read()) {
      ReceiptJournalSnapshot.Absent,
      ReceiptJournalSnapshot.Expired,
      -> DeletionReceiptLookup.None
      ReceiptJournalSnapshot.Unreadable -> DeletionReceiptLookup.Unavailable
      is ReceiptJournalSnapshot.Present -> DeletionReceiptLookup.Present(snapshot.receipt)
    }
  }

  fun expireCompleted(receipt: DurableDeletionReceipt, nowMillis: Long): Boolean =
    synchronized(FILE_LOCK) {
      if (
        nowMillis < 0 ||
        receipt.state != DurableDeletionReceipt.State.COMPLETED ||
        nowMillis < (receipt.cleanupAtMillis ?: return false)
      ) return false
      val current = (read() as? ReceiptJournalSnapshot.Present)?.receipt ?: return false
      if (current != receipt) return false
      writeExpired()
    }

  /** Retires exact terminal evidence before a genuinely different operation can be persisted. */
  fun retireCompleted(receipt: DurableDeletionReceipt): Boolean = synchronized(FILE_LOCK) {
    if (receipt.state != DurableDeletionReceipt.State.COMPLETED) return false
    val current = (read() as? ReceiptJournalSnapshot.Present)?.receipt ?: return false
    current == receipt && writeExpired()
  }

  /** True only for a never-created slot; false includes the terminal expiry marker. */
  fun migrationAllowed(): Boolean? = synchronized(FILE_LOCK) {
    when (read()) {
      ReceiptJournalSnapshot.Absent -> true
      ReceiptJournalSnapshot.Unreadable -> null
      else -> false
    }
  }

  fun capture(operation: DurablePrivacyOperation): Boolean = synchronized(FILE_LOCK) {
    if (
      operation.action != "delete-account" ||
      !operation.localDataErased ||
      operation.requestId == null
    ) return false
    when (operation.remoteDeletionComplete) {
      false -> putPending(
        operation.id,
        operation.requestId,
        operation.updatedAtMillis,
      )
      true -> {
        val completedAt = operation.completedAtMillis ?: return false
        val current = read()
        if (current is ReceiptJournalSnapshot.Present &&
          current.receipt.operationId == operation.id &&
          current.receipt.receiptId == operation.requestId &&
          current.receipt.state == DurableDeletionReceipt.State.COMPLETED
        ) {
          current.receipt.completedAtMillis == completedAt
        } else {
          if (!putPending(operation.id, operation.requestId, operation.updatedAtMillis)) return false
          complete(operation.requestId, completedAt) != null
        }
      }
      null -> false
    }
  }

  fun putPending(
    operationId: String,
    receiptId: String,
    updatedAtMillis: Long,
  ): Boolean = synchronized(FILE_LOCK) {
    val pending = DurableDeletionReceipt(
      operationId,
      receiptId,
      DurableDeletionReceipt.State.PENDING,
      updatedAtMillis,
      null,
      null,
    )
    if (!valid(pending)) return false
    when (val current = read()) {
      ReceiptJournalSnapshot.Unreadable -> false
      is ReceiptJournalSnapshot.Present -> when (current.receipt.state) {
        DurableDeletionReceipt.State.COMPLETED -> if (
          current.receipt.operationId == operationId &&
          current.receipt.receiptId == receiptId
        ) {
          // Preserve the stronger terminal evidence when a stale local-erased operation replays
          // the exact same reviewed deletion after receipt completion.
          true
        } else {
          // A different operation must first pass LifecycleStateStore's reviewed receipt-first
          // retirement boundary. This low-level slot never infers that authorization itself.
          false
        }
        DurableDeletionReceipt.State.PENDING -> if (
          current.receipt.operationId == operationId &&
          current.receipt.receiptId == receiptId
        ) {
          if (updatedAtMillis <= current.receipt.updatedAtMillis) true else write(pending)
        } else {
          false
        }
      }
      ReceiptJournalSnapshot.Absent,
      ReceiptJournalSnapshot.Expired,
      -> write(pending)
    }
  }

  fun complete(receiptId: String, completedAtMillis: Long): DurableDeletionReceipt? =
    synchronized(FILE_LOCK) {
      if (completedAtMillis < 0) return null
      val cleanupAtMillis = safeAdd(completedAtMillis, COMPLETED_RETENTION_MILLIS) ?: return null
      val current = (read() as? ReceiptJournalSnapshot.Present)?.receipt ?: return null
      if (current.receiptId != receiptId) return null
      if (current.state == DurableDeletionReceipt.State.COMPLETED) {
        return current.takeIf { current.completedAtMillis == completedAtMillis }
      }
      val completed = current.copy(
        state = DurableDeletionReceipt.State.COMPLETED,
        updatedAtMillis = completedAtMillis,
        completedAtMillis = completedAtMillis,
        cleanupAtMillis = cleanupAtMillis,
      )
      completed.takeIf { write(it) }
    }

  fun markExpiredIfAbsent(): Boolean = synchronized(FILE_LOCK) {
    when (read()) {
      ReceiptJournalSnapshot.Absent -> writeExpired()
      ReceiptJournalSnapshot.Expired -> true
      else -> false
    }
  }

  private fun read(): ReceiptJournalSnapshot {
    if (!atomicExists()) return ReceiptJournalSnapshot.Absent
    return try {
      val bytes = file.openRead().use { stream ->
        if (!baseFile.isFile || baseFile.length() !in 1L..1024L) {
          return ReceiptJournalSnapshot.Unreadable
        }
        stream.readBytes()
      }
      val lines = bytes.toString(StandardCharsets.US_ASCII).lines()
      if (lines.lastOrNull()?.isNotEmpty() != false || lines.firstOrNull() != "1") {
        return ReceiptJournalSnapshot.Unreadable
      }
      if (lines.size == 3 && lines[1] == "EXPIRED") {
        return ReceiptJournalSnapshot.Expired
      }
      if (lines.size != 8) return ReceiptJournalSnapshot.Unreadable
      val state = when (lines[1]) {
        "PENDING" -> DurableDeletionReceipt.State.PENDING
        "COMPLETED" -> DurableDeletionReceipt.State.COMPLETED
        else -> return ReceiptJournalSnapshot.Unreadable
      }
      val receipt = DurableDeletionReceipt(
        operationId = lines[2],
        receiptId = lines[3],
        state = state,
        updatedAtMillis = lines[4].toLongOrNull() ?: return ReceiptJournalSnapshot.Unreadable,
        completedAtMillis = lines[5].ifEmpty { null }?.toLongOrNull(),
        cleanupAtMillis = lines[6].ifEmpty { null }?.toLongOrNull(),
      )
      if (valid(receipt)) ReceiptJournalSnapshot.Present(receipt)
      else ReceiptJournalSnapshot.Unreadable
    } catch (_: Exception) {
      ReceiptJournalSnapshot.Unreadable
    }
  }

  private fun write(receipt: DurableDeletionReceipt): Boolean {
    if (!valid(receipt)) return false
    if (atomicExists() && read() == ReceiptJournalSnapshot.Unreadable) return false
    val bytes = buildString {
      append("1\n")
      append(receipt.state.name).append('\n')
      append(receipt.operationId).append('\n')
      append(receipt.receiptId).append('\n')
      append(receipt.updatedAtMillis).append('\n')
      append(receipt.completedAtMillis?.toString().orEmpty()).append('\n')
      append(receipt.cleanupAtMillis?.toString().orEmpty()).append('\n')
    }.toByteArray(StandardCharsets.US_ASCII)
    return atomicWrite(bytes)
  }

  private fun writeExpired(): Boolean {
    if (atomicExists() && read() == ReceiptJournalSnapshot.Unreadable) return false
    return atomicWrite("1\nEXPIRED\n".toByteArray(StandardCharsets.US_ASCII))
  }

  private fun atomicWrite(bytes: ByteArray): Boolean {
    val stream = try {
      file.startWrite()
    } catch (_: Exception) {
      bytes.fill(0)
      return false
    }
    return try {
      stream.write(bytes)
      stream.fd.sync()
      file.finishWrite(stream)
      true
    } catch (_: Exception) {
      file.failWrite(stream)
      false
    } finally {
      bytes.fill(0)
    }
  }

  private fun valid(receipt: DurableDeletionReceipt): Boolean {
    if (
      !OPERATION_ID.matches(receipt.operationId) ||
      !UUID.matches(receipt.receiptId) ||
      receipt.updatedAtMillis < 0
    ) return false
    return when (receipt.state) {
      DurableDeletionReceipt.State.PENDING ->
        receipt.completedAtMillis == null && receipt.cleanupAtMillis == null
      DurableDeletionReceipt.State.COMPLETED -> {
        val completedAt = receipt.completedAtMillis ?: return false
        val cleanupAt = receipt.cleanupAtMillis ?: return false
        completedAt >= 0 &&
          safeAdd(completedAt, COMPLETED_RETENTION_MILLIS) == cleanupAt &&
          receipt.updatedAtMillis == completedAt
      }
    }
  }

  private fun atomicExists(): Boolean = baseFile.exists() || legacyBackupFile.exists()

  private fun safeAdd(left: Long, right: Long): Long? =
    if (left > Long.MAX_VALUE - right) null else left + right

  internal companion object {
    val FILE_LOCK = Any()
    val OPERATION_ID = Regex("^privacy_[a-f0-9]{32}$")
    val UUID = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
    const val COMPLETED_RETENTION_MILLIS = 365L * 24 * 60 * 60 * 1_000

    fun isExpired(completedAtMillis: Long, nowMillis: Long): Boolean =
      completedAtMillis >= 0 &&
        nowMillis >= 0 &&
        completedAtMillis <= Long.MAX_VALUE - COMPLETED_RETENTION_MILLIS &&
        nowMillis >= completedAtMillis + COMPLETED_RETENTION_MILLIS
  }
}
