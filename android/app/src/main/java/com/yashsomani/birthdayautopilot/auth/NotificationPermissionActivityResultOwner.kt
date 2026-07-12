package com.yashsomani.birthdayautopilot.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal enum class NotificationPermissionResult {
  GRANTED,
  DENIED,
  SETTINGS_REQUIRED,
  UNAVAILABLE,
}

internal fun interface NotificationPermissionLauncher {
  suspend fun request(): NotificationPermissionResult
}

internal class NotificationPermissionStateStore(context: Context) {
  private val baseFile =
    File(context.applicationContext.noBackupFilesDir, "birthday-notification-permission-v1")
  private val legacyBackupFile = File(baseFile.path + ".bak")
  private val file = AtomicFile(baseFile)

  fun wasRequested(): Boolean {
    return synchronized(FILE_LOCK) {
      if (!atomicExists()) return@synchronized false
      // Only markRequested creates this file. Any existing unreadable or malformed value therefore
      // fails closed to settings rather than allowing a second system prompt after process death.
      true
    }
  }

  fun markRequested(): Boolean {
    return synchronized(FILE_LOCK) {
      // Activity recreation may temporarily leave two owners alive. The first durable claim wins;
      // a second owner must route to settings instead of presenting another system prompt.
      if (atomicExists()) return@synchronized false
      val stream = try {
        file.startWrite()
      } catch (_: Exception) {
        return@synchronized false
      }
      try {
        stream.write("1\n".toByteArray(StandardCharsets.US_ASCII))
        stream.fd.sync()
        file.finishWrite(stream)
        true
      } catch (_: Exception) {
        file.failWrite(stream)
        false
      }
    }
  }

  private fun atomicExists(): Boolean = baseFile.exists() || legacyBackupFile.exists()

  private companion object {
    val FILE_LOCK = Any()
  }
}

internal class NotificationPermissionActivityResultOwner(
  private val activity: ComponentActivity,
  private val permissionRequired: () -> Boolean = { Build.VERSION.SDK_INT >= 33 },
  private val permissionGranted: (Context) -> Boolean = Companion::isGranted,
) : NotificationPermissionLauncher {
  private val gate = SingleResolutionRequestGate()
  private val state = NotificationPermissionStateStore(activity.applicationContext)
  private var pending:
    kotlinx.coroutines.CancellableContinuation<NotificationPermissionResult>? = null
  private val launcher = activity.registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    val continuation = pending ?: return@registerForActivityResult
    pending = null
    if (!gate.finish() || !continuation.isActive) return@registerForActivityResult
    continuation.resume(
      when {
        granted && !AndroidAttentionNotifier.settingsRequired(activity) ->
          NotificationPermissionResult.GRANTED
        granted -> NotificationPermissionResult.SETTINGS_REQUIRED
        !ActivityCompat.shouldShowRequestPermissionRationale(
          activity,
          POST_NOTIFICATIONS_PERMISSION,
        ) -> NotificationPermissionResult.SETTINGS_REQUIRED
        else -> NotificationPermissionResult.DENIED
      },
    )
  }

  override suspend fun request(): NotificationPermissionResult =
    withContext(Dispatchers.Main.immediate) {
      if (!permissionRequired() || permissionGranted(activity)) {
        return@withContext if (AndroidAttentionNotifier.settingsRequired(activity)) {
          NotificationPermissionResult.SETTINGS_REQUIRED
        } else {
          NotificationPermissionResult.GRANTED
        }
      }
      if (state.wasRequested()) {
        return@withContext NotificationPermissionResult.SETTINGS_REQUIRED
      }
      if (
        activity.isFinishing ||
        activity.isDestroyed ||
        !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
        !gate.begin()
      ) return@withContext NotificationPermissionResult.UNAVAILABLE
      if (!state.markRequested()) {
        gate.finish()
        return@withContext if (state.wasRequested()) {
          NotificationPermissionResult.SETTINGS_REQUIRED
        } else {
          NotificationPermissionResult.UNAVAILABLE
        }
      }
      suspendCancellableCoroutine { continuation ->
        pending = continuation
        continuation.invokeOnCancellation {
          activity.runOnUiThread {
            if (pending === continuation) {
              pending = null
              gate.finish()
            }
          }
        }
        try {
          launcher.launch(POST_NOTIFICATIONS_PERMISSION)
        } catch (_: RuntimeException) {
          if (pending === continuation) {
            pending = null
            gate.finish()
            if (continuation.isActive) {
              continuation.resume(NotificationPermissionResult.UNAVAILABLE)
            }
          }
        }
      }
    }

  fun onDestroy() {
    val continuation = pending
    pending = null
    gate.destroy()
    if (continuation?.isActive == true) {
      continuation.resume(NotificationPermissionResult.UNAVAILABLE)
    }
  }

  companion object {
    private const val POST_NOTIFICATIONS_PERMISSION =
      "android.permission.POST_NOTIFICATIONS"

    fun status(context: Context): String {
      if (Build.VERSION.SDK_INT < 33 || isGranted(context)) {
        return if (AndroidAttentionNotifier.settingsRequired(context)) {
          "settings-required"
        } else {
          "granted"
        }
      }
      return if (NotificationPermissionStateStore(context).wasRequested()) {
        "settings-required"
      } else {
        "not-requested"
      }
    }

    private fun isGranted(context: Context): Boolean =
      ContextCompat.checkSelfPermission(
        context,
        POST_NOTIFICATIONS_PERMISSION,
      ) == PackageManager.PERMISSION_GRANTED
  }
}
