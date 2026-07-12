package com.yashsomani.birthdayautopilot.lifecycle

/** Prevents a crash-restored recovery session from becoming an ordinary app/account session. */
internal object SenderReleaseRecoveryStartupPolicy {
  fun requiresIdentitySessionClear(operation: DurablePrivacyOperation?): Boolean = operation?.let {
    it.action in setOf("sign-out-wipe", "wipe-local-data") &&
      it.localDataErased &&
      it.state !in setOf("complete", "failed")
  } == true
}
