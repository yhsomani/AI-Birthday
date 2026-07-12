package com.yashsomani.birthdayautopilot.approvals

import com.yashsomani.birthdayautopilot.messages.NativeSmsPlan
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanFailure
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanResult
import com.yashsomani.birthdayautopilot.messages.SmsEncoding
import com.yashsomani.birthdayautopilot.messages.SmsPlatformPlanSource
import com.yashsomani.birthdayautopilot.messages.TemplatePlaceholderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSmsApprovalPlannerTest {
  @Test
  fun `approval binds the exact native encoding boundaries and subscription`() {
    val text = "a".repeat(161)
    val seen = mutableListOf<Pair<Int, String>>()
    val planner = AndroidSmsApprovalPlanner { exactText, subscriptionId ->
      seen += subscriptionId to exactText
      planned(text, SmsEncoding.GSM_7, 153)
    }

    val result = planner.prepareForApproval(text, resolvedSubscriptionId = 8, approvedSegmentCap = 2)
      as SmsApprovalPlanResult.Prepared

    assertEquals(listOf(8 to text), seen)
    assertEquals(2, result.segmentPlan.segmentCount)
    assertEquals(SmsEncoding.GSM_7, result.segmentPlan.encoding)
    assertEquals(64, result.segmentPlan.orderedPartsHash.length)
    assertEquals(text, result.nativePlan.orderedParts.joinToString(""))
    assertFalse(result.toString().contains(text))
  }

  @Test
  fun `approval respects a lowered cap and rejects malformed cap`() {
    val text = "a".repeat(161)
    val planner = AndroidSmsApprovalPlanner { _, _ -> planned(text, SmsEncoding.GSM_7, 153) }

    assertApprovalRejected(
      planner.prepareForApproval(text, 1, approvedSegmentCap = 1),
      SmsApprovalPlanFailure.SEGMENT_CAP_EXCEEDED,
    )
    listOf(0, 3, Int.MAX_VALUE).forEach { cap ->
      assertApprovalRejected(
        planner.prepareForApproval(text, 1, approvedSegmentCap = cap),
        SmsApprovalPlanFailure.INVALID_SEGMENT_CAP,
      )
    }
  }

  @Test
  fun `pre-Arm revalidation returns only exact approved native parts`() {
    val text = "a".repeat(161)
    val platform = MutablePlanSource(planned(text, SmsEncoding.GSM_7, 153))
    val planner = AndroidSmsApprovalPlanner(platform)
    val prepared = planner.prepareForApproval(text, 9, 2) as SmsApprovalPlanResult.Prepared
    val snapshot = snapshot(text, 9, prepared.segmentPlan)

    val result = planner.revalidateBeforeArm(snapshot) as SmsPreSendPlanResult.Verified

    assertEquals(text, result.nativePlan.orderedParts.joinToString(""))
    assertEquals(2, result.nativePlan.segmentCount)
    assertEquals(listOf(9, 9), platform.seenSubscriptions)
    assertFalse(result.toString().contains(text))
  }

  @Test
  fun `any native boundary encoding or part-count drift invalidates approval`() {
    val text = "a".repeat(161)
    val platform = MutablePlanSource(planned(text, SmsEncoding.GSM_7, 153))
    val planner = AndroidSmsApprovalPlanner(platform)
    val prepared = planner.prepareForApproval(text, 4, 2) as SmsApprovalPlanResult.Prepared
    val snapshot = snapshot(text, 4, prepared.segmentPlan)

    val changes = listOf(
      planned(text, SmsEncoding.GSM_7, 152),
      NativeSmsPlanResult.Planned(
        NativeSmsPlan(
          encoding = SmsEncoding.UNICODE,
          characterCount = text.length,
          encodingUnitCount = text.length,
          orderedParts = listOf(text.take(153), text.drop(153)),
        ),
      ),
      NativeSmsPlanResult.Planned(
        NativeSmsPlan(
          encoding = SmsEncoding.GSM_7,
          characterCount = text.length,
          encodingUnitCount = text.length,
          orderedParts = listOf(text),
        ),
      ),
    )

    changes.forEachIndexed { index, changed ->
      platform.result = changed
      val result = planner.revalidateBeforeArm(snapshot)
      assertPreSendRejected(
        result,
        if (index == 1 || index == 2) {
          SmsApprovalPlanFailure.PLAN_BINDING_REJECTED
        } else {
          SmsApprovalPlanFailure.APPROVED_PLAN_CHANGED
        },
        "case $index",
      )
    }
  }

  @Test
  fun `malformed snapshot plan and platform failures stay fail closed and content free`() {
    val text = "private birthday message"
    val native = NativeSmsPlan(
      encoding = SmsEncoding.GSM_7,
      characterCount = text.length,
      encodingUnitCount = text.length,
      orderedParts = listOf(text),
    )
    val platform = MutablePlanSource(NativeSmsPlanResult.Planned(native))
    val planner = AndroidSmsApprovalPlanner(platform)
    val prepared = planner.prepareForApproval(text, 2, 2) as SmsApprovalPlanResult.Prepared
    val valid = snapshot(text, 2, prepared.segmentPlan)

    val malformed = listOf(
      valid.copy(segmentCount = 0),
      valid.copy(segmentCount = 3),
      valid.copy(resolvedSubscriptionId = -1),
      valid.copy(orderedPartsHash = "not-a-hash"),
    )
    malformed.forEachIndexed { index, candidate ->
      assertPreSendRejected(
        planner.revalidateBeforeArm(candidate),
        SmsApprovalPlanFailure.SNAPSHOT_PLAN_MALFORMED,
        "case $index",
      )
    }

    platform.result = NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.PLATFORM_UNAVAILABLE)
    val rejected = planner.revalidateBeforeArm(valid)
    assertPreSendRejected(rejected, SmsApprovalPlanFailure.PLATFORM_PLAN_REJECTED)
    assertEquals(
      NativeSmsPlanFailure.PLATFORM_UNAVAILABLE,
      (rejected as SmsPreSendPlanResult.Rejected).platformReason,
    )
    assertFalse(rejected.toString().contains(text))
  }

  @Test
  fun `more than two native segments are never approved or revalidated`() {
    val text = "a".repeat(307)
    val native = NativeSmsPlanResult.Planned(
      NativeSmsPlan(
        encoding = SmsEncoding.GSM_7,
        characterCount = text.length,
        encodingUnitCount = text.length,
        orderedParts = listOf(text.take(153), text.substring(153, 306), text.drop(306)),
      ),
    )
    val planner = AndroidSmsApprovalPlanner { _, _ -> native }

    assertApprovalRejected(
      planner.prepareForApproval(text, 1, 2),
      SmsApprovalPlanFailure.SEGMENT_CAP_EXCEEDED,
    )
  }

  private fun planned(
    text: String,
    encoding: SmsEncoding,
    splitAt: Int,
  ): NativeSmsPlanResult = NativeSmsPlanResult.Planned(
    NativeSmsPlan(
      encoding = encoding,
      characterCount = text.codePointCount(0, text.length),
      encodingUnitCount = text.length,
      orderedParts = listOf(text.take(splitAt), text.drop(splitAt)),
    ),
  )

  private fun snapshot(
    text: String,
    subscriptionId: Int,
    plan: ApprovedSegmentPlan,
  ) = ImmutableApprovalSnapshot(
    schemaVersion = 1,
    recipientId = "recipient-1",
    normalizedPhoneE164 = "+919123456789",
    maskedPhoneDisplay = "•••• 6789",
    exactText = text,
    sourceTemplateVersion = "template-v1",
    placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
    birthdayMonth = 7,
    birthdayDay = 14,
    leapDayPolicy = null,
    windowStartMinuteOfDay = 540,
    windowEndMinuteOfDay = 660,
    graceEndMinuteOfDay = null,
    latePolicy = ApprovedLatePolicy.SAME_DAY_WINDOW_ONLY,
    simPolicyKind = ApprovedSimPolicyKind.EXPLICIT_SUBSCRIPTION,
    resolvedSubscriptionId = subscriptionId,
    segmentCount = plan.segmentCount,
    messageEncoding = plan.encoding,
    orderedPartsHash = plan.orderedPartsHash,
    carrierCostDisclosureVersion = "carrier-v1",
    consentDisclosureVersion = "consent-v1",
    approvedAtEpochMillis = 1_720_000_000_000,
    contentHash = "a".repeat(64),
  )

  private fun assertApprovalRejected(
    result: SmsApprovalPlanResult,
    reason: SmsApprovalPlanFailure,
  ) {
    assertTrue(result is SmsApprovalPlanResult.Rejected)
    assertEquals(reason, (result as SmsApprovalPlanResult.Rejected).reason)
  }

  private fun assertPreSendRejected(
    result: SmsPreSendPlanResult,
    reason: SmsApprovalPlanFailure,
    message: String? = null,
  ) {
    assertTrue(message, result is SmsPreSendPlanResult.Rejected)
    assertEquals(message, reason, (result as SmsPreSendPlanResult.Rejected).reason)
  }

  private class MutablePlanSource(var result: NativeSmsPlanResult) : SmsPlatformPlanSource {
    val seenSubscriptions = mutableListOf<Int>()

    override fun plan(exactText: String, subscriptionId: Int): NativeSmsPlanResult {
      seenSubscriptions += subscriptionId
      return result
    }
  }
}
