package com.yashsomani.birthdayautopilot.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttentionClassificationPolicyTest {
  @Test
  fun reviewedStableCodesUseTheirExplicitCategoryAndSeverity() {
    assertEquals(
      AttentionClassification(AttentionCategory.TRANSFER, 2),
      AttentionClassificationPolicy.classify("SENDER_TRANSFER_PENDING"),
    )
    assertEquals(
      AttentionClassification(AttentionCategory.COORDINATION, 1),
      AttentionClassificationPolicy.classify("COORDINATION_NETWORK_UNAVAILABLE"),
    )
    assertEquals(
      AttentionClassification(AttentionCategory.SIM, 2),
      AttentionClassificationPolicy.classify("SMS_DEADLINE_OR_SIM_CHANGED"),
    )
  }

  @Test
  fun unknownOrSubstringRelatedCodesNeverNotify() {
    assertNull(AttentionClassificationPolicy.classify("GOOGLE_PLAY_SERVICES_MISSING"))
    assertNull(AttentionClassificationPolicy.classify("SOME_SIMILAR_SERVICE_FAILURE"))
    assertNull(AttentionClassificationPolicy.classify("sender_transfer_pending"))
    assertNull(AttentionClassificationPolicy.classify("RECONCILE_IDLE"))
    assertNull(AttentionClassificationPolicy.classify(""))
  }
}
