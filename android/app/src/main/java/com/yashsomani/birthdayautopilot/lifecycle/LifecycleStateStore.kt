package com.yashsomani.birthdayautopilot.lifecycle

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets

internal data class DurablePrivacyOperation(
  val id: String,
  val action: String,
  val state: String,
  val reason: String?,
  val updatedAtMillis: Long,
  val completedAtMillis: Long?,
  val requestId: String? = null,
  val remoteDrainUntilMillis: Long? = null,
  val serverObservedAtMillis: Long? = null,
  val acceptedAtElapsedMillis: Long? = null,
  val acceptedBootCount: Int? = null,
  val localDataErased: Boolean = false,
  val remoteDeletionComplete: Boolean? = null,
  val transferActiveInstallationId: String? = null,
  val transferTargetInstallationId: String? = null,
  val transferSenderEpoch: Long? = null,
  val transferResetGeneration: Long? = null,
  val remoteRequestInstallationId: String? = null,
  val remoteRequestSenderEpoch: Long? = null,
  val remoteRequestResetGeneration: Long? = null,
  val localWipeStarted: Boolean = false,
  val wipeInstallationId: String? = null,
  val wipeCallbackGeneration: String? = null,
  val authoritativeRecoveryKind: String? = null,
  val deletionLocalWipeFallback: Boolean = false,
  val recoveryBindingSalt: String? = null,
  val recoveryFirebaseUidHash: String? = null,
  val recoveryGoogleSubjectHash: String? = null,
  val deletionRetryAllowed: Boolean = false,
  val deletionInProgressObserved: Boolean = false,
  val remoteAccessRevoked: Boolean = false,
  val senderReleaseRecoverySalt: String? = null,
  val senderReleaseRecoveryFirebaseUidHash: String? = null,
  val senderReleaseRecoveryGoogleSubjectHash: String? = null,
)

internal data class PendingLocalWipe(
  val operationId: String,
  val action: String,
  val installationId: String,
  val callbackGeneration: String,
)

internal enum class LifecycleJournalStatus {
  ABSENT,
  READABLE,
  UNREADABLE,
}

/** Backup-excluded, credential-free, content-minimized recovery journal that survives Room teardown. */
internal class LifecycleStateStore(context: Context) {
  private val baseFile =
    File(context.applicationContext.noBackupFilesDir, "birthday-lifecycle-state-v1")
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)
  private val deletionReceiptStore = DeletionReceiptStore(context)

  fun activityVisibilityCutoffMillis(): Long = synchronized(FILE_LOCK) {
    read()?.first ?: 0L
  }

  fun journalStatus(): LifecycleJournalStatus = synchronized(FILE_LOCK) {
    when {
      !atomicExists() -> LifecycleJournalStatus.ABSENT
      read() != null -> LifecycleJournalStatus.READABLE
      else -> LifecycleJournalStatus.UNREADABLE
    }
  }

  fun setActivityVisibilityCutoffMillis(value: Long): Boolean = synchronized(FILE_LOCK) {
    if (value < 0) return false
    val current = read()
    if (atomicExists() && current == null) return false
    write(value, current?.second)
  }

  fun operation(id: String): DurablePrivacyOperation? = synchronized(FILE_LOCK) {
    read()?.second?.takeIf { it.id == id }
  }

  fun latestOperation(): DurablePrivacyOperation? = synchronized(FILE_LOCK) { read()?.second }

  fun deletionReceiptLookup(nowMillis: Long): DeletionReceiptLookup = synchronized(FILE_LOCK) {
    when (val direct = deletionReceiptStore.lookupWithoutExpiry()) {
      DeletionReceiptLookup.Unavailable -> direct
      is DeletionReceiptLookup.Present -> {
        if (
          direct.receipt.state == DurableDeletionReceipt.State.COMPLETED &&
          nowMillis >= checkNotNull(direct.receipt.cleanupAtMillis)
        ) {
          expireCompletedReceipt(direct.receipt, nowMillis)
        } else {
          direct
        }
      }
      DeletionReceiptLookup.None -> when (deletionReceiptStore.migrationAllowed()) {
        false -> DeletionReceiptLookup.None
        null -> DeletionReceiptLookup.Unavailable
        true -> {
          val snapshot = read()
          if (snapshot == null) {
            if (atomicExists()) DeletionReceiptLookup.Unavailable else DeletionReceiptLookup.None
          } else {
            val legacy = snapshot.second?.takeIf {
              it.action == "delete-account" &&
                it.localDataErased &&
                it.requestId != null &&
                it.remoteDeletionComplete != null
            }
            if (legacy == null) {
              DeletionReceiptLookup.None
            } else if (
              legacy.remoteDeletionComplete == true &&
              DeletionReceiptStore.isExpired(checkNotNull(legacy.completedAtMillis), nowMillis)
            ) {
              if (
                write(snapshot.first, null) &&
                deletionReceiptStore.markExpiredIfAbsent()
              ) DeletionReceiptLookup.None else DeletionReceiptLookup.Unavailable
            } else if (deletionReceiptStore.capture(legacy)) {
              deletionReceiptLookup(nowMillis)
            } else {
              DeletionReceiptLookup.Unavailable
            }
          }
        }
      }
    }
  }

  /** Independent slot only; used to authorize a narrowly scoped explicit lifecycle repair. */
  fun independentDeletionReceiptLookup(): DeletionReceiptLookup =
    deletionReceiptStore.lookupWithoutExpiry()

  private fun expireCompletedReceipt(
    receipt: DurableDeletionReceipt,
    nowMillis: Long,
  ): DeletionReceiptLookup {
    val snapshot = read()
      ?: return DeletionReceiptLookup.Unavailable
    val operation = snapshot.second
    val matchingLocalErasedOperation = operation?.let {
      it.id == receipt.operationId &&
        it.action == "delete-account" &&
        it.requestId == receipt.receiptId &&
        it.localDataErased
    } == true
    // The receipt completion and lifecycle reconciliation are separate crash-safe writes. A
    // process can therefore reach retention expiry with the receipt completed while the matching
    // operation is still remote-pending/draining and still contains its raw request/binding data.
    // Sanitize that exact operation before making receipt expiry terminal.
    if (matchingLocalErasedOperation && !write(snapshot.first, null)) {
      return DeletionReceiptLookup.Unavailable
    }
    return if (deletionReceiptStore.expireCompleted(receipt, nowMillis)) {
      DeletionReceiptLookup.None
    } else {
      DeletionReceiptLookup.Unavailable
    }
  }

  fun completeDeletionReceipt(
    receiptId: String,
    completedAtMillis: Long,
  ): DurableDeletionReceipt? = deletionReceiptStore.complete(receiptId, completedAtMillis)

  /**
   * A completed account A must be proven exclusive and its exact lifecycle record sanitized before
   * retiring the receipt and allowing any ordinary Google/Firebase sign-in for account B.
   */
  fun prepareForOrdinaryAccountIdentity(): Boolean = synchronized(FILE_LOCK) {
    val snapshot = read()
    if (snapshot == null && atomicExists()) return false
    val receipt = when (val lookup = deletionReceiptStore.lookupWithoutExpiry()) {
      DeletionReceiptLookup.None -> null
      DeletionReceiptLookup.Unavailable -> return false
      is DeletionReceiptLookup.Present -> when (lookup.receipt.state) {
        DurableDeletionReceipt.State.PENDING -> return false
        DurableDeletionReceipt.State.COMPLETED -> lookup.receipt
      }
    }
    val operation = snapshot?.second
    if (receipt != null) {
      val exactLocalErasedDeletion = operation?.let {
        it.id == receipt.operationId &&
          it.action == "delete-account" &&
          it.requestId == receipt.receiptId &&
          it.localDataErased
      } == true
      if (operation != null && !exactLocalErasedDeletion) return false
      if (exactLocalErasedDeletion && !write(checkNotNull(snapshot).first, null)) return false
      return deletionReceiptStore.retireCompleted(receipt)
    }

    val terminalDeletionWithoutReceipt = operation?.let {
      it.action == "delete-account" &&
        it.state == "complete" &&
        it.localDataErased &&
        it.remoteDeletionComplete == true
    } == true
    return !terminalDeletionWithoutReceipt || write(checkNotNull(snapshot).first, null)
  }

  fun pendingLocalWipe(): PendingLocalWipe? = synchronized(FILE_LOCK) {
    read()?.second?.takeIf { operation ->
      operation.localWipeStarted &&
        !operation.localDataErased &&
        operation.state != "complete"
    }?.let { operation ->
      PendingLocalWipe(
        operation.id,
        operation.action,
        checkNotNull(operation.wipeInstallationId),
        checkNotNull(operation.wipeCallbackGeneration),
      )
    }
  }

  fun completeRecoveredLocalWipe(
    proof: PendingLocalWipe,
    completedAtMillis: Long,
  ): DurablePrivacyOperation? = synchronized(FILE_LOCK) {
    if (completedAtMillis < 0) return null
    val snapshot = read() ?: return null
    val current = snapshot.second?.takeIf {
      it.id == proof.operationId &&
        it.action == proof.action &&
        it.localWipeStarted &&
        it.wipeInstallationId == proof.installationId &&
        it.wipeCallbackGeneration == proof.callbackGeneration &&
        !(it.action == "delete-account" && it.localDataErased) &&
        !(it.action != "delete-account" && it.state == "complete")
    } ?: return null
    val completed = if (current.action == "delete-account") {
      val fallbackWithoutAcceptedDrain = current.deletionLocalWipeFallback &&
        current.remoteDrainUntilMillis == null
      current.copy(
        state = if (fallbackWithoutAcceptedDrain) "remote-pending" else "remote-draining",
        reason = if (fallbackWithoutAcceptedDrain) {
          "coordination-unavailable"
        } else {
          "firebase-account-deleting"
        },
        updatedAtMillis = completedAtMillis,
        completedAtMillis = null,
        localDataErased = true,
        remoteDeletionComplete = false,
      )
    } else if (current.authoritativeRecoveryKind == "sender-release") {
      current.copy(
        state = "complete",
        reason = null,
        updatedAtMillis = completedAtMillis,
        completedAtMillis = completedAtMillis,
        localDataErased = true,
        localWipeStarted = false,
        wipeInstallationId = null,
        wipeCallbackGeneration = null,
        remoteRequestInstallationId = null,
        remoteRequestSenderEpoch = null,
        remoteRequestResetGeneration = null,
        authoritativeRecoveryKind = null,
      )
    } else {
      current.copy(
        state = "remote-pending",
        reason = "coordination-unavailable",
        updatedAtMillis = completedAtMillis,
        completedAtMillis = null,
        localDataErased = true,
        localWipeStarted = false,
        wipeInstallationId = null,
        wipeCallbackGeneration = null,
      )
    }
    if (completed.action == "delete-account" && !deletionReceiptStore.capture(completed)) {
      return null
    }
    completed.takeIf { write(snapshot.first, it) }
  }

  /** The only path permitted to replace an unreadable journal. */
  fun repairUnreadableWithAuthoritativeOperation(
    operation: DurablePrivacyOperation,
    activityVisibilityCutoffMillis: Long = Long.MAX_VALUE,
  ): Boolean = synchronized(FILE_LOCK) {
    if (
      journalStatus() != LifecycleJournalStatus.UNREADABLE ||
      operation.authoritativeRecoveryKind == null ||
      !valid(operation) ||
      activityVisibilityCutoffMillis < 0 ||
      !prepareReceiptForOperation(operation)
    ) return false
    write(activityVisibilityCutoffMillis, operation, allowUnreadableReplacement = true)
  }

  fun putOperation(operation: DurablePrivacyOperation): Boolean = synchronized(FILE_LOCK) {
    if (!valid(operation)) return false
    if (!prepareReceiptForOperation(operation)) return false
    if (
      operation.action == "delete-account" &&
      operation.localDataErased &&
      !deletionReceiptStore.capture(operation)
    ) return false
    write(activityVisibilityCutoffMillis(), operation)
  }

  /** Must run while FILE_LOCK is held; exact old main data is sanitized before receipt retirement. */
  private fun prepareReceiptForOperation(operation: DurablePrivacyOperation): Boolean {
    return when (val lookup = deletionReceiptStore.lookupWithoutExpiry()) {
      DeletionReceiptLookup.None -> true
      DeletionReceiptLookup.Unavailable -> false
      is DeletionReceiptLookup.Present -> {
        val receipt = lookup.receipt
        val exactOperation = receipt.operationId == operation.id &&
          receipt.receiptId == operation.requestId
        when (receipt.state) {
          DurableDeletionReceipt.State.PENDING -> exactOperation
          DurableDeletionReceipt.State.COMPLETED -> if (exactOperation) {
            operation.action == "delete-account" &&
              operation.state == "complete" &&
              operation.localDataErased &&
              operation.remoteDeletionComplete == true
          } else {
            val snapshot = read() ?: return false
            val current = snapshot.second
            val exactCurrentReceipt = current?.let {
              it.id == receipt.operationId &&
                it.action == "delete-account" &&
                it.requestId == receipt.receiptId &&
                it.localDataErased
            } == true
            if (current != null && !exactCurrentReceipt) return false
            if (exactCurrentReceipt && !write(snapshot.first, null)) return false
            deletionReceiptStore.retireCompleted(receipt)
          }
        }
      }
    }
  }

  private fun read(): Pair<Long, DurablePrivacyOperation?>? {
    if (!atomicExists()) return 0L to null
    return try {
      val bytes = file.openRead().use { stream ->
        if (!baseFile.isFile || baseFile.length() !in 1L..8192L) return null
        stream.readBytes()
      }
      val lines = bytes.toString(StandardCharsets.US_ASCII).lines()
      if (lines.lastOrNull()?.isNotEmpty() != false) return null
      if (lines.firstOrNull() == "1") return readVersionOne(lines)
      if (lines.firstOrNull() == "2") return readVersionTwo(lines)
      if (lines.firstOrNull() == "3") return readVersionThree(lines)
      if (lines.firstOrNull() == "6") return readVersionSix(lines)
      if (lines.firstOrNull() == "7") return readVersionSeven(lines)
      val hasAuthoritativeRecovery = lines.firstOrNull() == "5"
      if (
        (!hasAuthoritativeRecovery && lines.firstOrNull() != "4") ||
        lines.size != if (hasAuthoritativeRecovery) 27 else 26
      ) return null
      val cutoff = lines[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
      if (lines[2].isEmpty()) return (cutoff to null).takeIf {
        lines.subList(3, lines.lastIndex).all(String::isEmpty)
      }
      for (index in listOf(7, 9, 10, 11, 17, 18, 20, 21)) {
        if (lines[index].isNotEmpty() && lines[index].toLongOrNull() == null) return null
      }
      if (lines[12].isNotEmpty() && lines[12].toIntOrNull() == null) return null
      val remoteDeletionComplete = when (lines[14]) {
        "" -> null
        "0" -> false
        "1" -> true
        else -> return null
      }
      val decoded = DurablePrivacyOperation(
        id = lines[2],
        action = lines[3],
        state = lines[4],
        reason = lines[5].ifEmpty { null },
        updatedAtMillis = lines[6].toLongOrNull() ?: return null,
        completedAtMillis = lines[7].ifEmpty { null }?.toLongOrNull(),
        requestId = lines[8].ifEmpty { null },
        remoteDrainUntilMillis = lines[9].ifEmpty { null }?.toLongOrNull(),
        serverObservedAtMillis = lines[10].ifEmpty { null }?.toLongOrNull(),
        acceptedAtElapsedMillis = lines[11].ifEmpty { null }?.toLongOrNull(),
        acceptedBootCount = lines[12].ifEmpty { null }?.toIntOrNull(),
        localDataErased = lines[13].decodeBoolean() ?: return null,
        remoteDeletionComplete = remoteDeletionComplete,
        transferActiveInstallationId = lines[15].ifEmpty { null },
        transferTargetInstallationId = lines[16].ifEmpty { null },
        transferSenderEpoch = lines[17].ifEmpty { null }?.toLongOrNull(),
        transferResetGeneration = lines[18].ifEmpty { null }?.toLongOrNull(),
        remoteRequestInstallationId = lines[19].ifEmpty { null },
        remoteRequestSenderEpoch = lines[20].ifEmpty { null }?.toLongOrNull(),
        remoteRequestResetGeneration = lines[21].ifEmpty { null }?.toLongOrNull(),
        localWipeStarted = lines[22].decodeBoolean() ?: return null,
        wipeInstallationId = lines[23].ifEmpty { null },
        wipeCallbackGeneration = lines[24].ifEmpty { null },
        authoritativeRecoveryKind = if (hasAuthoritativeRecovery) {
          lines[25].ifEmpty { null }
        } else {
          null
        },
      )
      val operation = if (
        !hasAuthoritativeRecovery &&
        decoded.action in setOf("sign-out-wipe", "wipe-local-data") &&
        decoded.state == "local-wiping" &&
        decoded.localWipeStarted
      ) {
        decoded.copy(
          requestId = null,
          authoritativeRecoveryKind = "sender-release",
        )
      } else {
        decoded
      }
      if (!valid(operation)) null else cutoff to operation
    } catch (_: Exception) {
      null
    }
  }

  private fun write(
    cutoff: Long,
    operation: DurablePrivacyOperation?,
    allowUnreadableReplacement: Boolean = false,
  ): Boolean {
    if (cutoff < 0 || operation?.let { !valid(it) } == true) return false
    // An existing but unreadable journal may be the sole surviving deletion/transfer receipt.
    // Never replace it with an apparently clean state; only an authenticated repair flow may do
    // that after reconciling the authoritative server lifecycle.
    if (!allowUnreadableReplacement && atomicExists() && read() == null) return false
    val bytes = buildString {
      append("7\n")
      append(cutoff).append('\n')
      append(operation?.id.orEmpty()).append('\n')
      append(operation?.action.orEmpty()).append('\n')
      append(operation?.state.orEmpty()).append('\n')
      append(operation?.reason.orEmpty()).append('\n')
      append(operation?.updatedAtMillis?.toString().orEmpty()).append('\n')
      append(operation?.completedAtMillis?.toString().orEmpty()).append('\n')
      append(operation?.requestId.orEmpty()).append('\n')
      append(operation?.remoteDrainUntilMillis?.toString().orEmpty()).append('\n')
      append(operation?.serverObservedAtMillis?.toString().orEmpty()).append('\n')
      append(operation?.acceptedAtElapsedMillis?.toString().orEmpty()).append('\n')
      append(operation?.acceptedBootCount?.toString().orEmpty()).append('\n')
      append(operation?.localDataErased?.let { if (it) "1" else "0" }.orEmpty()).append('\n')
      append(operation?.remoteDeletionComplete?.let { if (it) "1" else "0" }.orEmpty()).append('\n')
      append(operation?.transferActiveInstallationId.orEmpty()).append('\n')
      append(operation?.transferTargetInstallationId.orEmpty()).append('\n')
      append(operation?.transferSenderEpoch?.toString().orEmpty()).append('\n')
      append(operation?.transferResetGeneration?.toString().orEmpty()).append('\n')
      append(operation?.remoteRequestInstallationId.orEmpty()).append('\n')
      append(operation?.remoteRequestSenderEpoch?.toString().orEmpty()).append('\n')
      append(operation?.remoteRequestResetGeneration?.toString().orEmpty()).append('\n')
      append(operation?.localWipeStarted?.let { if (it) "1" else "0" }.orEmpty()).append('\n')
      append(operation?.wipeInstallationId.orEmpty()).append('\n')
      append(operation?.wipeCallbackGeneration.orEmpty()).append('\n')
      append(operation?.authoritativeRecoveryKind.orEmpty()).append('\n')
      append(
        operation?.deletionLocalWipeFallback?.let { if (it) "1" else "0" }.orEmpty(),
      ).append('\n')
      append(operation?.recoveryBindingSalt.orEmpty()).append('\n')
      append(operation?.recoveryFirebaseUidHash.orEmpty()).append('\n')
      append(operation?.recoveryGoogleSubjectHash.orEmpty()).append('\n')
      append(operation?.deletionRetryAllowed?.let { if (it) "1" else "0" }.orEmpty()).append('\n')
      append(
        operation?.deletionInProgressObserved?.let { if (it) "1" else "0" }.orEmpty(),
      ).append('\n')
      append(operation?.remoteAccessRevoked?.let { if (it) "1" else "0" }.orEmpty()).append('\n')
      append(operation?.senderReleaseRecoverySalt.orEmpty()).append('\n')
      append(operation?.senderReleaseRecoveryFirebaseUidHash.orEmpty()).append('\n')
      append(operation?.senderReleaseRecoveryGoogleSubjectHash.orEmpty()).append('\n')
    }.toByteArray(StandardCharsets.US_ASCII)
    val stream = try {
      file.startWrite()
    } catch (_: Exception) {
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

  private fun valid(operation: DurablePrivacyOperation): Boolean =
    OPAQUE.matches(operation.id) &&
      operation.action in OPERATION_ACTIONS &&
      operation.state in OPERATION_STATES &&
      operation.reason?.let { it in SAFE_REASONS } != false &&
      operation.updatedAtMillis >= 0 &&
      operation.completedAtMillis?.let { it >= 0 } != false &&
      operation.requestId?.let(UUID::matches) != false &&
      operation.remoteDrainUntilMillis?.let { it >= 0 } != false &&
      operation.serverObservedAtMillis?.let { it >= 0 } != false &&
      operation.acceptedAtElapsedMillis?.let { it >= 0 } != false &&
      operation.acceptedBootCount?.let { it >= 0 } != false &&
      validAcceptedDrain(operation) &&
      (operation.state != "complete" || operation.completedAtMillis != null) &&
      validDeletionState(operation) &&
      validTransferBinding(operation) &&
      validRemoteRequestBinding(operation) &&
      validLocalWipe(operation) &&
      validAuthoritativeRecovery(operation) &&
      validDeletionRecovery(operation) &&
      validRemoteAccessRevocation(operation) &&
      validSenderReleaseRecovery(operation)

  private fun validAcceptedDrain(operation: DurablePrivacyOperation): Boolean {
    val values = listOf(
      operation.remoteDrainUntilMillis,
      operation.serverObservedAtMillis,
      operation.acceptedAtElapsedMillis,
      operation.acceptedBootCount,
    )
    val allAbsent = values.all { it == null }
    val allPresent = values.all { it != null }
    if (!allAbsent && !allPresent) return false
    if (allPresent && operation.requestId == null) return false
    return operation.state != "remote-draining" ||
      operation.action !in setOf("delete-account", "sender-transfer") ||
      allPresent
  }

  private fun validTransferBinding(operation: DurablePrivacyOperation): Boolean {
    val active = operation.transferActiveInstallationId
    val target = operation.transferTargetInstallationId
    val epoch = operation.transferSenderEpoch
    val reset = operation.transferResetGeneration
    if (operation.action != "sender-transfer") {
      return active == null && target == null && epoch == null && reset == null
    }
    return operation.requestId != null &&
      active?.let(INSTALLATION_ID::matches) == true &&
      target?.let(INSTALLATION_ID::matches) == true &&
      active != target &&
      epoch != null && epoch > 0 &&
      reset != null && reset > 0
  }

  private fun validRemoteRequestBinding(operation: DurablePrivacyOperation): Boolean {
    val installation = operation.remoteRequestInstallationId
    val epoch = operation.remoteRequestSenderEpoch
    val reset = operation.remoteRequestResetGeneration
    if (installation == null && epoch == null && reset == null) return true
    return (operation.requestId != null ||
      operation.authoritativeRecoveryKind == "sender-release") &&
      operation.action in setOf("sign-out-wipe", "wipe-local-data") &&
      installation?.let(INSTALLATION_ID::matches) == true &&
      epoch != null && epoch > 0 &&
      reset != null && reset > 0
  }

  private fun validLocalWipe(operation: DurablePrivacyOperation): Boolean {
    val installation = operation.wipeInstallationId
    val callback = operation.wipeCallbackGeneration
    if (!operation.localWipeStarted) return installation == null && callback == null
    if (
      operation.action !in setOf("delete-account", "sign-out-wipe", "wipe-local-data") ||
      installation?.let(INSTALLATION_ID::matches) != true ||
      callback?.let(INSTALLATION_ID::matches) != true
    ) return false
    return if (operation.action == "delete-account") {
      operation.requestId != null && when (operation.remoteDeletionComplete) {
        false -> if (operation.deletionLocalWipeFallback) {
          operation.state in setOf("local-wiping", "remote-pending", "remote-draining")
        } else {
          operation.remoteDrainUntilMillis != null && operation.state == "remote-draining"
        }
        true -> operation.localDataErased && operation.state == "complete"
        null -> false
      }
    } else {
      validRemoteRequestBinding(operation) &&
        operation.remoteRequestInstallationId != null &&
        operation.state == "local-wiping" &&
        !operation.localDataErased
    }
  }

  private fun validDeletionState(operation: DurablePrivacyOperation): Boolean {
    if (operation.action != "delete-account") {
      if (operation.remoteDeletionComplete != null) return false
      return when (operation.action) {
        "disconnect-contacts", "revoke-google-access" ->
          !operation.localDataErased || operation.state in setOf(
            "local-wiping",
            "remote-pending",
            "remote-draining",
            "verifying",
            "complete",
          )
        "sign-out-wipe", "wipe-local-data" ->
          !operation.localDataErased || (
            !operation.localWipeStarted &&
              operation.state in setOf("remote-pending", "remote-draining", "complete")
            )
        else -> !operation.localDataErased
      }
    }
    return when (operation.remoteDeletionComplete) {
      null -> !operation.localDataErased && operation.state != "complete"
      false -> if (operation.deletionLocalWipeFallback) {
        operation.state in setOf("local-wiping", "remote-pending", "remote-draining") &&
          operation.requestId != null &&
          operation.completedAtMillis == null &&
          (!operation.localDataErased || operation.state != "local-wiping")
      } else {
        operation.state == "remote-draining" &&
          operation.requestId != null &&
          operation.completedAtMillis == null
      }
      true -> operation.state == "complete" &&
        operation.requestId != null &&
        operation.localDataErased &&
        operation.completedAtMillis != null &&
        !operation.deletionLocalWipeFallback
    }
  }

  private fun validDeletionRecovery(operation: DurablePrivacyOperation): Boolean {
    val salt = operation.recoveryBindingSalt
    val firebaseUidHash = operation.recoveryFirebaseUidHash
    val googleSubjectHash = operation.recoveryGoogleSubjectHash
    if (!operation.deletionLocalWipeFallback) {
      return salt == null && firebaseUidHash == null && googleSubjectHash == null &&
        !operation.deletionRetryAllowed && !operation.deletionInProgressObserved
    }
    if (
      operation.action != "delete-account" ||
      operation.remoteDeletionComplete != false ||
      operation.requestId?.let(DELETION_REQUEST_ID::matches) != true ||
      salt == null ||
      firebaseUidHash == null ||
      googleSubjectHash == null
    ) return false
    val proof = DeletionRecoveryBindingProof(salt, firebaseUidHash, googleSubjectHash)
    return DeletionRecoveryBindingPolicy.valid(proof) &&
      !(operation.deletionRetryAllowed && operation.deletionInProgressObserved) &&
      (!operation.deletionRetryAllowed || operation.state in setOf("local-wiping", "remote-pending")) &&
      (!operation.deletionInProgressObserved || operation.localDataErased)
  }

  private fun validRemoteAccessRevocation(operation: DurablePrivacyOperation): Boolean =
    !operation.remoteAccessRevoked || (
      operation.action == "revoke-google-access" &&
        operation.localDataErased &&
        operation.state in setOf("verifying", "complete")
      )

  private fun validSenderReleaseRecovery(operation: DurablePrivacyOperation): Boolean {
    val salt = operation.senderReleaseRecoverySalt
    val firebaseUidHash = operation.senderReleaseRecoveryFirebaseUidHash
    val googleSubjectHash = operation.senderReleaseRecoveryGoogleSubjectHash
    val allAbsent = salt == null && firebaseUidHash == null && googleSubjectHash == null
    if (operation.action !in setOf("sign-out-wipe", "wipe-local-data")) return allAbsent
    val proof = if (allAbsent) {
      null
    } else {
      SenderReleaseRecoveryBindingProof(
        salt = salt ?: return false,
        firebaseUidHash = firebaseUidHash ?: return false,
        googleSubjectHash = googleSubjectHash ?: return false,
      ).takeIf(SenderReleaseRecoveryBindingPolicy::valid) ?: return false
    }
    if (operation.state == "complete") return proof == null
    if (operation.authoritativeRecoveryKind == "sender-release") return proof == null
    if (operation.localDataErased || operation.localWipeStarted) return proof != null
    return true
  }

  private fun validAuthoritativeRecovery(operation: DurablePrivacyOperation): Boolean = when (
    operation.authoritativeRecoveryKind
  ) {
    null -> true
    "contact-reset" ->
      operation.action in setOf("disconnect-contacts", "revoke-google-access") &&
        operation.state in setOf("local-wiping", "complete") &&
        operation.requestId == null &&
        operation.remoteDrainUntilMillis == null &&
        operation.remoteRequestInstallationId == null
    "sender-release" ->
      operation.action in setOf("sign-out-wipe", "wipe-local-data") &&
        operation.state in setOf("local-wiping", "complete") &&
        operation.requestId == null &&
        operation.remoteDrainUntilMillis == null &&
        operation.remoteRequestInstallationId != null
    else -> false
  }

  private fun readVersionSix(lines: List<String>): Pair<Long, DurablePrivacyOperation?>? {
    if (lines.size != 33) return null
    val cutoff = lines[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    if (lines[2].isEmpty()) return (cutoff to null).takeIf {
      lines.subList(3, lines.lastIndex).all(String::isEmpty)
    }
    for (index in listOf(7, 9, 10, 11, 17, 18, 20, 21)) {
      if (lines[index].isNotEmpty() && lines[index].toLongOrNull() == null) return null
    }
    if (lines[12].isNotEmpty() && lines[12].toIntOrNull() == null) return null
    val remoteDeletionComplete = when (lines[14]) {
      "" -> null
      "0" -> false
      "1" -> true
      else -> return null
    }
    val decoded = DurablePrivacyOperation(
      id = lines[2],
      action = lines[3],
      state = lines[4],
      reason = lines[5].ifEmpty { null },
      updatedAtMillis = lines[6].toLongOrNull() ?: return null,
      completedAtMillis = lines[7].ifEmpty { null }?.toLongOrNull(),
      requestId = lines[8].ifEmpty { null },
      remoteDrainUntilMillis = lines[9].ifEmpty { null }?.toLongOrNull(),
      serverObservedAtMillis = lines[10].ifEmpty { null }?.toLongOrNull(),
      acceptedAtElapsedMillis = lines[11].ifEmpty { null }?.toLongOrNull(),
      acceptedBootCount = lines[12].ifEmpty { null }?.toIntOrNull(),
      localDataErased = lines[13].decodeBoolean() ?: return null,
      remoteDeletionComplete = remoteDeletionComplete,
      transferActiveInstallationId = lines[15].ifEmpty { null },
      transferTargetInstallationId = lines[16].ifEmpty { null },
      transferSenderEpoch = lines[17].ifEmpty { null }?.toLongOrNull(),
      transferResetGeneration = lines[18].ifEmpty { null }?.toLongOrNull(),
      remoteRequestInstallationId = lines[19].ifEmpty { null },
      remoteRequestSenderEpoch = lines[20].ifEmpty { null }?.toLongOrNull(),
      remoteRequestResetGeneration = lines[21].ifEmpty { null }?.toLongOrNull(),
      localWipeStarted = lines[22].decodeBoolean() ?: return null,
      wipeInstallationId = lines[23].ifEmpty { null },
      wipeCallbackGeneration = lines[24].ifEmpty { null },
      authoritativeRecoveryKind = lines[25].ifEmpty { null },
      deletionLocalWipeFallback = lines[26].decodeBoolean() ?: return null,
      recoveryBindingSalt = lines[27].ifEmpty { null },
      recoveryFirebaseUidHash = lines[28].ifEmpty { null },
      recoveryGoogleSubjectHash = lines[29].ifEmpty { null },
      deletionRetryAllowed = lines[30].decodeBoolean() ?: return null,
      deletionInProgressObserved = lines[31].decodeBoolean() ?: return null,
    )
    // Version six could create a destructive-wipe marker only after authoritative sender release
    // completed. Preserve that stronger legacy fact while removing its obsolete bearer request.
    val operation = if (
      decoded.action in setOf("sign-out-wipe", "wipe-local-data") &&
      decoded.state == "local-wiping" &&
      decoded.localWipeStarted
    ) {
      decoded.copy(
        requestId = null,
        authoritativeRecoveryKind = "sender-release",
      )
    } else {
      decoded
    }
    return (cutoff to operation).takeIf { valid(operation) }
  }

  private fun readVersionSeven(lines: List<String>): Pair<Long, DurablePrivacyOperation?>? {
    if (lines.size != 37) return null
    val cutoff = lines[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    if (lines[2].isEmpty()) return (cutoff to null).takeIf {
      lines.subList(3, lines.lastIndex).all(String::isEmpty)
    }
    for (index in listOf(7, 9, 10, 11, 17, 18, 20, 21)) {
      if (lines[index].isNotEmpty() && lines[index].toLongOrNull() == null) return null
    }
    if (lines[12].isNotEmpty() && lines[12].toIntOrNull() == null) return null
    val remoteDeletionComplete = when (lines[14]) {
      "" -> null
      "0" -> false
      "1" -> true
      else -> return null
    }
    val operation = DurablePrivacyOperation(
      id = lines[2],
      action = lines[3],
      state = lines[4],
      reason = lines[5].ifEmpty { null },
      updatedAtMillis = lines[6].toLongOrNull() ?: return null,
      completedAtMillis = lines[7].ifEmpty { null }?.toLongOrNull(),
      requestId = lines[8].ifEmpty { null },
      remoteDrainUntilMillis = lines[9].ifEmpty { null }?.toLongOrNull(),
      serverObservedAtMillis = lines[10].ifEmpty { null }?.toLongOrNull(),
      acceptedAtElapsedMillis = lines[11].ifEmpty { null }?.toLongOrNull(),
      acceptedBootCount = lines[12].ifEmpty { null }?.toIntOrNull(),
      localDataErased = lines[13].decodeBoolean() ?: return null,
      remoteDeletionComplete = remoteDeletionComplete,
      transferActiveInstallationId = lines[15].ifEmpty { null },
      transferTargetInstallationId = lines[16].ifEmpty { null },
      transferSenderEpoch = lines[17].ifEmpty { null }?.toLongOrNull(),
      transferResetGeneration = lines[18].ifEmpty { null }?.toLongOrNull(),
      remoteRequestInstallationId = lines[19].ifEmpty { null },
      remoteRequestSenderEpoch = lines[20].ifEmpty { null }?.toLongOrNull(),
      remoteRequestResetGeneration = lines[21].ifEmpty { null }?.toLongOrNull(),
      localWipeStarted = lines[22].decodeBoolean() ?: return null,
      wipeInstallationId = lines[23].ifEmpty { null },
      wipeCallbackGeneration = lines[24].ifEmpty { null },
      authoritativeRecoveryKind = lines[25].ifEmpty { null },
      deletionLocalWipeFallback = lines[26].decodeBoolean() ?: return null,
      recoveryBindingSalt = lines[27].ifEmpty { null },
      recoveryFirebaseUidHash = lines[28].ifEmpty { null },
      recoveryGoogleSubjectHash = lines[29].ifEmpty { null },
      deletionRetryAllowed = lines[30].decodeBoolean() ?: return null,
      deletionInProgressObserved = lines[31].decodeBoolean() ?: return null,
      remoteAccessRevoked = lines[32].decodeBoolean() ?: return null,
      senderReleaseRecoverySalt = lines[33].ifEmpty { null },
      senderReleaseRecoveryFirebaseUidHash = lines[34].ifEmpty { null },
      senderReleaseRecoveryGoogleSubjectHash = lines[35].ifEmpty { null },
    )
    return (cutoff to operation).takeIf { valid(operation) }
  }

  private fun readVersionThree(lines: List<String>): Pair<Long, DurablePrivacyOperation?>? {
    if (lines.size != 20) return null
    val cutoff = lines[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    if (lines[2].isEmpty()) return (cutoff to null).takeIf {
      lines.subList(3, 19).all(String::isEmpty)
    }
    for (index in listOf(7, 9, 10, 11, 17, 18)) {
      if (lines[index].isNotEmpty() && lines[index].toLongOrNull() == null) return null
    }
    if (lines[12].isNotEmpty() && lines[12].toIntOrNull() == null) return null
    val remoteDeletionComplete = when (lines[14]) {
      "" -> null
      "0" -> false
      "1" -> true
      else -> return null
    }
    val operation = DurablePrivacyOperation(
      id = lines[2],
      action = lines[3],
      state = lines[4],
      reason = lines[5].ifEmpty { null },
      updatedAtMillis = lines[6].toLongOrNull() ?: return null,
      completedAtMillis = lines[7].ifEmpty { null }?.toLongOrNull(),
      requestId = lines[8].ifEmpty { null },
      remoteDrainUntilMillis = lines[9].ifEmpty { null }?.toLongOrNull(),
      serverObservedAtMillis = lines[10].ifEmpty { null }?.toLongOrNull(),
      acceptedAtElapsedMillis = lines[11].ifEmpty { null }?.toLongOrNull(),
      acceptedBootCount = lines[12].ifEmpty { null }?.toIntOrNull(),
      localDataErased = lines[13].decodeBoolean() ?: return null,
      remoteDeletionComplete = remoteDeletionComplete,
      transferActiveInstallationId = lines[15].ifEmpty { null },
      transferTargetInstallationId = lines[16].ifEmpty { null },
      transferSenderEpoch = lines[17].ifEmpty { null }?.toLongOrNull(),
      transferResetGeneration = lines[18].ifEmpty { null }?.toLongOrNull(),
    )
    return (cutoff to operation).takeIf { valid(operation) }
  }

  private fun readVersionTwo(lines: List<String>): Pair<Long, DurablePrivacyOperation?>? {
    if (lines.size != 16) return null
    val cutoff = lines[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    if (lines[2].isEmpty()) return (cutoff to null).takeIf {
      lines.subList(3, 15).all(String::isEmpty)
    }
    for (index in listOf(7, 9, 10, 11)) {
      if (lines[index].isNotEmpty() && lines[index].toLongOrNull() == null) return null
    }
    if (lines[12].isNotEmpty() && lines[12].toIntOrNull() == null) return null
    val remoteDeletionComplete = when (lines[14]) {
      "" -> null
      "0" -> false
      "1" -> true
      else -> return null
    }
    val operation = DurablePrivacyOperation(
      id = lines[2],
      action = lines[3],
      state = lines[4],
      reason = lines[5].ifEmpty { null },
      updatedAtMillis = lines[6].toLongOrNull() ?: return null,
      completedAtMillis = lines[7].ifEmpty { null }?.toLongOrNull(),
      requestId = lines[8].ifEmpty { null },
      remoteDrainUntilMillis = lines[9].ifEmpty { null }?.toLongOrNull(),
      serverObservedAtMillis = lines[10].ifEmpty { null }?.toLongOrNull(),
      acceptedAtElapsedMillis = lines[11].ifEmpty { null }?.toLongOrNull(),
      acceptedBootCount = lines[12].ifEmpty { null }?.toIntOrNull(),
      localDataErased = lines[13].decodeBoolean() ?: return null,
      remoteDeletionComplete = remoteDeletionComplete,
    )
    // Version two never persisted the immutable transfer binding, so such an operation cannot be
    // safely resumed. Privacy/deletion operations remain fully migratable.
    if (operation.action == "sender-transfer") return null
    return (cutoff to operation).takeIf { valid(operation) }
  }

  private fun readVersionOne(lines: List<String>): Pair<Long, DurablePrivacyOperation?>? {
    if (lines.size != 9) return null
    val cutoff = lines[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    if (lines[2].isEmpty()) return (cutoff to null).takeIf {
      lines.subList(3, 8).all(String::isEmpty)
    }
    val operation = DurablePrivacyOperation(
      id = lines[2],
      action = lines[3],
      state = lines[4],
      reason = lines[5].ifEmpty { null },
      updatedAtMillis = lines[6].toLongOrNull() ?: return null,
      completedAtMillis = lines[7].ifEmpty { null }?.toLongOrNull(),
    )
    return (cutoff to operation).takeIf { valid(operation) }
  }

  private fun String.decodeBoolean(): Boolean? = when (this) {
    "0" -> false
    "1" -> true
    else -> null
  }

  private fun atomicExists(): Boolean = baseFile.exists() || legacyBackupFile.exists()

  internal companion object {
    private val FILE_LOCK = Any()
    val OPAQUE = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    val PRIVACY_ACTIONS = setOf(
      "disconnect-contacts",
      "revoke-google-access",
      "sign-out-retain",
      "sign-out-wipe",
      "delete-account",
      "wipe-local-data",
      "clear-gemini-templates",
      "clear-activity",
    )
    val OPERATION_ACTIONS = PRIVACY_ACTIONS + "sender-transfer"
    val OPERATION_STATES = setOf(
      "queued",
      "pausing",
      "remote-draining",
      "local-wiping",
      "remote-pending",
      "verifying",
      "complete",
      "failed",
    )
    private val UUID = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
    private val DELETION_REQUEST_ID = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
    private val INSTALLATION_ID = Regex("^[a-f0-9]{32}$")
    val SAFE_REASONS = setOf(
      "account-reconnect-required",
      "coordination-unavailable",
      "firebase-account-deleting",
      "internal-contract-invalid",
      "network-offline",
      "policy-suspended",
      "transfer-pending",
    )
  }
}
