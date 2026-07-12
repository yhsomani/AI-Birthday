package com.yashsomani.birthdayautopilot.automation.workers

import com.yashsomani.birthdayautopilot.attention.AttentionClassificationPolicy

internal object WorkerAttentionPolicy {
  fun successful(
    primarySafeCode: String,
    additionalSafeCodes: Iterable<String> = emptyList(),
  ): List<String> = (additionalSafeCodes + primarySafeCode)
    .distinct()
    .filter { AttentionClassificationPolicy.classify(it) != null }

  fun failure(safeCode: String, terminal: Boolean): String? = safeCode.takeIf {
    terminal && AttentionClassificationPolicy.classify(it) != null
  }
}
