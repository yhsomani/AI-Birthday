package com.yashsomani.birthdayautopilot.approvals

import com.yashsomani.birthdayautopilot.messages.NativeSmsPlan
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanFailure
import com.yashsomani.birthdayautopilot.messages.NativeSmsPlanResult
import com.yashsomani.birthdayautopilot.messages.SmsPlatformPlanSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class SmsApprovalPlanFailure {
  INVALID_SEGMENT_CAP,
  PLATFORM_PLAN_REJECTED,
  SEGMENT_CAP_EXCEEDED,
  PLAN_BINDING_REJECTED,
  SNAPSHOT_PLAN_MALFORMED,
  APPROVED_PLAN_CHANGED,
}

sealed interface SmsApprovalPlanResult {
  data class Prepared(
    val segmentPlan: ApprovedSegmentPlan,
    val nativePlan: NativeSmsPlan,
  ) : SmsApprovalPlanResult {
    override fun toString(): String =
      "SmsApprovalPlanResult.Prepared(segmentCount=${nativePlan.segmentCount}, privateFields=<redacted>)"
  }

  data class Rejected(
    val reason: SmsApprovalPlanFailure,
    val platformReason: NativeSmsPlanFailure? = null,
  ) : SmsApprovalPlanResult
}

sealed interface SmsPreSendPlanResult {
  /** Ephemeral verified parts for the sole later SMS gateway; this class itself cannot send. */
  data class Verified(val nativePlan: NativeSmsPlan) : SmsPreSendPlanResult {
    override fun toString(): String =
      "SmsPreSendPlanResult.Verified(segmentCount=${nativePlan.segmentCount}, privateFields=<redacted>)"
  }

  data class Rejected(
    val reason: SmsApprovalPlanFailure,
    val platformReason: NativeSmsPlanFailure? = null,
  ) : SmsPreSendPlanResult
}

/**
 * Binds Android's real, subscription-specific segmentation into an immutable approval and checks
 * the exact same values immediately before server Arm. Full snapshot/content validation and SIM
 * activity checks remain mandatory callers of this narrow adapter.
 */
class AndroidSmsApprovalPlanner(private val platform: SmsPlatformPlanSource) {
  fun prepareForApproval(
    exactText: String,
    resolvedSubscriptionId: Int,
    approvedSegmentCap: Int,
  ): SmsApprovalPlanResult {
    if (approvedSegmentCap !in 1..MAX_SEGMENT_CAP) {
      return SmsApprovalPlanResult.Rejected(SmsApprovalPlanFailure.INVALID_SEGMENT_CAP)
    }
    val nativePlan = when (val result = platform.plan(exactText, resolvedSubscriptionId)) {
      is NativeSmsPlanResult.Planned -> result.plan
      is NativeSmsPlanResult.Rejected -> return SmsApprovalPlanResult.Rejected(
        SmsApprovalPlanFailure.PLATFORM_PLAN_REJECTED,
        result.reason,
      )
    }
    if (nativePlan.segmentCount > approvedSegmentCap) {
      return SmsApprovalPlanResult.Rejected(SmsApprovalPlanFailure.SEGMENT_CAP_EXCEEDED)
    }
    val approved = ApprovedSegmentPlan.bind(
      exactText = exactText,
      encoding = nativePlan.encoding,
      orderedParts = nativePlan.orderedParts,
      approvedSegmentCap = approvedSegmentCap,
    ) ?: return SmsApprovalPlanResult.Rejected(SmsApprovalPlanFailure.PLAN_BINDING_REJECTED)

    return SmsApprovalPlanResult.Prepared(approved, nativePlan)
  }

  fun revalidateBeforeArm(snapshot: ImmutableApprovalSnapshot): SmsPreSendPlanResult {
    if (
      snapshot.segmentCount !in 1..MAX_SEGMENT_CAP ||
      snapshot.resolvedSubscriptionId < 0 ||
      !SHA256.matches(snapshot.orderedPartsHash)
    ) return SmsPreSendPlanResult.Rejected(SmsApprovalPlanFailure.SNAPSHOT_PLAN_MALFORMED)

    val nativePlan = when (
      val result = platform.plan(snapshot.exactText, snapshot.resolvedSubscriptionId)
    ) {
      is NativeSmsPlanResult.Planned -> result.plan
      is NativeSmsPlanResult.Rejected -> return SmsPreSendPlanResult.Rejected(
        SmsApprovalPlanFailure.PLATFORM_PLAN_REJECTED,
        result.reason,
      )
    }
    if (nativePlan.segmentCount > MAX_SEGMENT_CAP) {
      return SmsPreSendPlanResult.Rejected(SmsApprovalPlanFailure.SEGMENT_CAP_EXCEEDED)
    }
    val current = ApprovedSegmentPlan.bind(
      exactText = snapshot.exactText,
      encoding = nativePlan.encoding,
      orderedParts = nativePlan.orderedParts,
      approvedSegmentCap = MAX_SEGMENT_CAP,
    ) ?: return SmsPreSendPlanResult.Rejected(SmsApprovalPlanFailure.PLAN_BINDING_REJECTED)

    if (
      current.segmentCount != snapshot.segmentCount ||
      current.encoding != snapshot.messageEncoding ||
      !constantTimeEquals(current.orderedPartsHash, snapshot.orderedPartsHash)
    ) return SmsPreSendPlanResult.Rejected(SmsApprovalPlanFailure.APPROVED_PLAN_CHANGED)

    return SmsPreSendPlanResult.Verified(nativePlan)
  }

  private fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(
      left.toByteArray(StandardCharsets.US_ASCII),
      right.toByteArray(StandardCharsets.US_ASCII),
    )

  private companion object {
    const val MAX_SEGMENT_CAP = 2
    val SHA256 = Regex("^[0-9a-f]{64}$")
  }
}
