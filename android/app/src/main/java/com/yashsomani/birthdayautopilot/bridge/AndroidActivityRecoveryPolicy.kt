package com.yashsomani.birthdayautopilot.bridge

/** Keeps historical Activity records separate from live, state-bound repair affordances. */
internal object AndroidActivityRecoveryPolicy {
  fun route(
    kind: String,
    reason: String?,
    currentIssueCodes: Set<String>,
    automationEffective: String,
  ): String? = when {
    kind == "paused" && automationEffective == "paused-repair" -> "automation"
    kind == "approval-invalidated" && "approval-invalid" in currentIssueCodes -> "people"
    kind == "transfer" &&
      (automationEffective == "transfer-pending" || "transfer-pending" in currentIssueCodes) ->
      "settings"
    reason != null && reason in currentIssueCodes -> "attention"
    else -> null
  }
}
