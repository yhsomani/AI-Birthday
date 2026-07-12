package com.yashsomani.birthdayautopilot.auth

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * A weak, resume-scoped Activity source for APIs that must present Google-owned foreground UI.
 * Pausing or destroying the Activity removes it, so a background worker can never open a prompt.
 */
internal object ForegroundActivityRegistry {
  private val lock = Any()
  private var resumedActivity = WeakReference<Activity>(null)
  private var resolutionLauncher = WeakReference<AuthorizationResolutionLauncher>(null)
  private var telephonyPermissionLauncher = WeakReference<TelephonyPermissionLauncher>(null)
  private var notificationPermissionLauncher = WeakReference<NotificationPermissionLauncher>(null)

  fun onResumed(
    activity: Activity,
    launcher: AuthorizationResolutionLauncher,
    permissionLauncher: TelephonyPermissionLauncher,
    notificationLauncher: NotificationPermissionLauncher,
  ) {
    synchronized(lock) {
      resumedActivity = WeakReference(activity)
      resolutionLauncher = WeakReference(launcher)
      telephonyPermissionLauncher = WeakReference(permissionLauncher)
      notificationPermissionLauncher = WeakReference(notificationLauncher)
    }
  }

  fun onPaused(activity: Activity) {
    synchronized(lock) {
      if (resumedActivity.get() === activity) {
        resumedActivity.clear()
        resolutionLauncher.clear()
        telephonyPermissionLauncher.clear()
        notificationPermissionLauncher.clear()
      }
    }
  }

  fun current(): Activity? = synchronized(lock) {
    resumedActivity.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
  }

  /**
   * Linearizes a short foreground-only platform boundary against onPause. If pause wins, the
   * block never runs; if this block wins, pause waits until the boundary has returned.
   */
  fun <T> withCurrentActivity(block: (Activity) -> T): T? = synchronized(lock) {
    val activity = resumedActivity.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
      ?: return@synchronized null
    block(activity)
  }

  fun currentResolutionLauncher(): AuthorizationResolutionLauncher? = synchronized(lock) {
    val activity = resumedActivity.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    if (activity == null) null else resolutionLauncher.get()
  }

  fun currentTelephonyPermissionLauncher(): TelephonyPermissionLauncher? = synchronized(lock) {
    val activity = resumedActivity.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    if (activity == null) null else telephonyPermissionLauncher.get()
  }

  fun currentNotificationPermissionLauncher(): NotificationPermissionLauncher? = synchronized(lock) {
    val activity = resumedActivity.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    if (activity == null) null else notificationPermissionLauncher.get()
  }
}
