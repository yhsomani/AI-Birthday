package com.yashsomani.birthdayautopilot.automation.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.yashsomani.birthdayautopilot.automation.workers.AutomationScheduler
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executor

/** Hashes the device binding in memory; raw subscription identifiers are never persisted. */
internal enum class SubscriptionFingerprintQuality {
  FULL,
  PERMISSION_MISSING,
  UNAVAILABLE,
}

internal data class SubscriptionBindingFingerprint(
  val digest: String,
  val quality: SubscriptionFingerprintQuality,
)

internal object SubscriptionChangeFingerprint {
  /**
   * The subscription read is preceded by an explicit runtime permission check and catches a
   * concurrent revoke. Lint does not carry that guard through this fingerprint expression.
   */
  @SuppressLint("MissingPermission")
  fun read(context: Context): SubscriptionBindingFingerprint {
    val appContext = context.applicationContext
    var quality = SubscriptionFingerprintQuality.FULL
    val defaultBinding = try {
      SubscriptionManager.getDefaultSmsSubscriptionId().let { subscriptionId ->
        if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
          subscriptionId.toString()
        } else {
          "NONE"
        }
      }
    } catch (_: RuntimeException) {
      quality = SubscriptionFingerprintQuality.UNAVAILABLE
      "UNAVAILABLE"
    } catch (_: LinkageError) {
      quality = SubscriptionFingerprintQuality.UNAVAILABLE
      "UNAVAILABLE"
    }
    val activeBindings = if (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) ==
      PackageManager.PERMISSION_GRANTED
    ) {
      try {
        appContext.getSystemService(SubscriptionManager::class.java)
          .activeSubscriptionInfoList
          .orEmpty()
          .sortedBy { it.subscriptionId }
          .joinToString(";") { subscription ->
            listOf(
              subscription.subscriptionId,
              subscription.simSlotIndex,
              subscription.carrierId,
              subscription.mccString ?: "NONE",
              subscription.mncString ?: "NONE",
              subscription.isEmbedded,
              subscription.cardId,
            ).joinToString(":")
          }
      } catch (_: SecurityException) {
        quality = SubscriptionFingerprintQuality.UNAVAILABLE
        "UNAVAILABLE"
      } catch (_: RuntimeException) {
        quality = SubscriptionFingerprintQuality.UNAVAILABLE
        "UNAVAILABLE"
      } catch (_: LinkageError) {
        quality = SubscriptionFingerprintQuality.UNAVAILABLE
        "UNAVAILABLE"
      }
    } else {
      if (quality != SubscriptionFingerprintQuality.UNAVAILABLE) {
        quality = SubscriptionFingerprintQuality.PERMISSION_MISSING
      }
      "PERMISSION_MISSING"
    }
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(
        "BirthdayAutopilot.SubscriptionBinding.v1|$defaultBinding|$activeBindings"
          .toByteArray(StandardCharsets.UTF_8),
      )
      .joinToString(separator = "") { byte ->
        String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
      }
    return SubscriptionBindingFingerprint(digest, quality)
  }
}

/**
 * No-backup durable edge signal. A change generation remains pending until the Room invalidation
 * commits, so WorkManager coalescing or process death cannot hide an away-and-back SIM sequence.
 */
internal class SubscriptionChangeSignalStore(context: Context) {
  private val appContext = context.applicationContext
  private val file = AtomicFile(
    File(appContext.noBackupFilesDir, FILE_NAME),
  )
  private val legacyBackupFile = File(file.baseFile.path + ".bak")
  private val fallback = appContext.getSharedPreferences(FALLBACK_PREFERENCES, Context.MODE_PRIVATE)

  /** Establishes the first process baseline without invalidating; later differences are signals. */
  fun observe(fingerprint: SubscriptionBindingFingerprint): Long? = synchronized(processLock) {
    if (writeFailurePending()) return@synchronized FAIL_CLOSED_GENERATION
    when (val read = readState()) {
      ReadState.Missing -> {
        val generation = if (fingerprint.quality == SubscriptionFingerprintQuality.UNAVAILABLE) {
          1L
        } else {
          0L
        }
        if (writeState(State(fingerprint.digest, fingerprint.quality, generation, 0L))) {
          generation.takeIf { it > 0 }
        } else {
          markWriteFailure()
        }
      }
      ReadState.Corrupt -> {
        if (writeState(State(fingerprint.digest, fingerprint.quality, 1L, 0L))) {
          1L
        } else {
          markWriteFailure()
        }
      }
      is ReadState.Valid -> {
        val prior = read.state
        val pending = prior.generation.takeIf { it > prior.consumedGeneration }
        if (fingerprint.quality == SubscriptionFingerprintQuality.UNAVAILABLE) {
          val next = pending ?: incrementOrMax(prior.generation)
          if (writeState(prior.copy(
              fingerprint = fingerprint.digest,
              quality = fingerprint.quality,
              generation = next,
            ))) next else markWriteFailure()
        } else if (
          fingerprint.quality == SubscriptionFingerprintQuality.PERMISSION_MISSING ||
          (prior.quality == SubscriptionFingerprintQuality.PERMISSION_MISSING &&
            fingerprint.quality == SubscriptionFingerprintQuality.FULL && pending == null)
        ) {
          if (writeState(prior.copy(
              fingerprint = fingerprint.digest,
              quality = fingerprint.quality,
            ))) pending else markWriteFailure()
        } else if (
          prior.fingerprint == fingerprint.digest &&
          prior.quality == fingerprint.quality
        ) {
          read.state.generation.takeIf { it > read.state.consumedGeneration }
        } else {
          val next = incrementOrMax(read.state.generation)
          if (writeState(read.state.copy(
              fingerprint = fingerprint.digest,
              quality = fingerprint.quality,
              generation = next,
            ))) {
            next
          } else {
            markWriteFailure()
          }
        }
      }
    }
  }

  /** A public platform broadcast is already a confirmed edge, including the first observed edge. */
  fun recordConfirmedChange(fingerprint: SubscriptionBindingFingerprint): Long = synchronized(processLock) {
    if (writeFailurePending()) return@synchronized FAIL_CLOSED_GENERATION
    val prior = (readState() as? ReadState.Valid)?.state
    val generation = incrementOrMax(prior?.generation ?: 0L)
    if (!writeState(
      State(
        fingerprint = fingerprint.digest,
        quality = fingerprint.quality,
        generation = generation,
        consumedGeneration = prior?.consumedGeneration?.coerceAtMost(generation) ?: 0L,
      ),
    )) markWriteFailure() else generation
  }

  fun pendingGeneration(): Long? = synchronized(processLock) {
    if (writeFailurePending()) return@synchronized FAIL_CLOSED_GENERATION
    when (val read = readState()) {
      ReadState.Missing -> null
      ReadState.Corrupt -> markWriteFailure()
      is ReadState.Valid -> if (read.state.quality == SubscriptionFingerprintQuality.UNAVAILABLE) {
        FAIL_CLOSED_GENERATION
      } else {
        read.state.generation.takeIf { it > read.state.consumedGeneration }
      }
    }
  }

  // These synchronous commits are deliberate fail-closed durability barriers, not UI state.
  @SuppressLint("ApplySharedPref", "UseKtx")
  fun markConsumed(generation: Long): Boolean = synchronized(processLock) {
    if (generation == FAIL_CLOSED_GENERATION || writeFailurePending()) {
      val prior = (readState() as? ReadState.Valid)?.state
      val current = SubscriptionChangeFingerprint.read(appContext)
      if (current.quality == SubscriptionFingerprintQuality.UNAVAILABLE) return@synchronized false
      val repaired = State(
        fingerprint = current.digest,
        quality = current.quality,
        generation = prior?.generation ?: 0L,
        consumedGeneration = prior?.generation ?: 0L,
      )
      if (!writeState(repaired)) {
        markWriteFailure()
        return@synchronized false
      }
      if (!fallback.edit().remove(FALLBACK_KEY).commit()) return@synchronized false
      processWriteFailure = false
      return@synchronized true
    }
    val state = (readState() as? ReadState.Valid)?.state ?: return@synchronized false
    if (state.quality == SubscriptionFingerprintQuality.UNAVAILABLE) return@synchronized false
    if (generation <= state.consumedGeneration || generation > state.generation) return@synchronized false
    if (writeState(state.copy(consumedGeneration = generation))) {
      true
    } else {
      markWriteFailure()
      false
    }
  }

  private fun readState(): ReadState {
    if (!file.baseFile.exists() && !legacyBackupFile.exists()) return ReadState.Missing
    return try {
      DataInputStream(file.openRead().buffered()).use { input ->
        if (input.readInt() != MAGIC || input.readInt() != VERSION) return ReadState.Corrupt
        val state = State(
          fingerprint = input.readUTF(),
          quality = runCatching {
            SubscriptionFingerprintQuality.valueOf(input.readUTF())
          }.getOrNull() ?: return ReadState.Corrupt,
          generation = input.readLong(),
          consumedGeneration = input.readLong(),
        )
        if (
          !state.fingerprint.matches(FINGERPRINT) ||
          state.generation < 0 ||
          state.consumedGeneration !in 0..state.generation ||
          input.read() != -1
        ) ReadState.Corrupt else ReadState.Valid(state)
      }
    } catch (_: Exception) {
      ReadState.Corrupt
    }
  }

  private fun writeState(state: State): Boolean {
    val bytes = ByteArrayOutputStream().use { buffer ->
      DataOutputStream(buffer).use { output ->
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeUTF(state.fingerprint)
        output.writeUTF(state.quality.name)
        output.writeLong(state.generation)
        output.writeLong(state.consumedGeneration)
        output.flush()
      }
      buffer.toByteArray()
    }
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
      runCatching { file.failWrite(stream) }
      false
    } finally {
      bytes.fill(0)
    }
  }

  private fun incrementOrMax(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1

  private fun writeFailurePending(): Boolean =
    processWriteFailure || fallback.getBoolean(FALLBACK_KEY, false)

  // A process crash must not erase knowledge that the primary AtomicFile write failed.
  @SuppressLint("ApplySharedPref", "UseKtx")
  private fun markWriteFailure(): Long {
    processWriteFailure = true
    fallback.edit().putBoolean(FALLBACK_KEY, true).commit()
    return FAIL_CLOSED_GENERATION
  }

  private data class State(
    val fingerprint: String,
    val quality: SubscriptionFingerprintQuality,
    val generation: Long,
    val consumedGeneration: Long,
  )

  private sealed interface ReadState {
    data object Missing : ReadState
    data object Corrupt : ReadState
    data class Valid(val state: State) : ReadState
  }

  private companion object {
    const val FILE_NAME = "birthday-subscription-change-signal-v1"
    const val FALLBACK_PREFERENCES = "birthday-subscription-change-failure-v1"
    const val FALLBACK_KEY = "fail_closed"
    const val FAIL_CLOSED_GENERATION = Long.MAX_VALUE
    const val MAGIC = 0x42535331
    const val VERSION = 2
    val FINGERPRINT = Regex("^[a-f0-9]{64}$")
    val processLock = Any()
    @Volatile var processWriteFailure = false
  }
}

/** Process-lifetime active-subscription observer retained by AppGraph. */
internal class SubscriptionChangeObserver(
  context: Context,
  private val signalStore: SubscriptionChangeSignalStore,
) {
  private val appContext = context.applicationContext
  private val subscriptionManager = appContext.getSystemService(SubscriptionManager::class.java)
  private val callbackExecutor: Executor = ContextCompat.getMainExecutor(appContext)
  private val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
    override fun onSubscriptionsChanged() {
      observeAndSchedule()
    }
  }
  @Volatile private var started = false

  @Synchronized
  fun start(): Boolean {
    if (started) return true
    // This synchronous observation distinguishes the registration callback from a real edge.
    observeAndSchedule()
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        subscriptionManager.addOnSubscriptionsChangedListener(callbackExecutor, listener)
      } else {
        @Suppress("DEPRECATION")
        subscriptionManager.addOnSubscriptionsChangedListener(listener)
      }
      started = true
      true
    } catch (_: SecurityException) {
      false
    } catch (_: RuntimeException) {
      false
    } catch (_: LinkageError) {
      false
    }
  }

  private fun observeAndSchedule() {
    signalStore.observe(SubscriptionChangeFingerprint.read(appContext)) ?: return
    AutomationScheduler.enqueueSubscriptionChange(appContext)
  }
}
