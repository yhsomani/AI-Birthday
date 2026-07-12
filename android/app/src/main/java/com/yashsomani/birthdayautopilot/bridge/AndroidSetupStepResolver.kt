package com.yashsomani.birthdayautopilot.bridge

import com.yashsomani.birthdayautopilot.storage.database.ContactSyncStateEntity
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness

/**
 * Keeps onboarding limited to the irreversible first-import boundary.
 *
 * Once an authoritative generation exists, a refresh failure, expired Google authorization, or
 * an in-progress replacement generation must not trap a returning user in setup. The application
 * shell owns those recoverable states and exposes them through contacts/readiness projections.
 */
internal object AndroidSetupStepResolver {
  fun resolve(
    eligibilitySupported: Boolean,
    identityReady: Boolean,
    syncState: ContactSyncStateEntity?,
  ): String {
    if (!eligibilitySupported) return "compatibility"
    if (!identityReady) return "google-account"
    val state = syncState ?: return "contacts-disclosure"

    if (state.activeGeneration != null) return "complete"
    if (state.freshness == SyncFreshness.AUTH_ACTION_REQUIRED) return "contacts-disclosure"
    if (state.stagingGeneration != null) return "sync-summary"
    return if (state.lastAttemptMillis == null) "contacts-disclosure" else "sync-summary"
  }
}
