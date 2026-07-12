package com.yashsomani.birthdayautopilot.automation.orchestration

import com.yashsomani.birthdayautopilot.storage.database.ClockTrustEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustStatus
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState

internal enum class ArmRecoveryAction {
  DISPATCH_ONCE,
  QUERY_EXACT_STATUS,
  QUERY_EXACT_STATUS_THEN_CLOSE,
  CLOSE_UNKNOWN,
  NONE,
}

/** Pure recovery policy: an already-dispatched Arm is never returned as a dispatch action. */
internal object ArmRecoveryPolicy {
  fun decide(
    permit: CoordinationPermitEntity,
    currentBootCount: Int?,
    currentElapsedRealtimeMillis: Long,
  ): ArmRecoveryAction {
    return when (permit.state) {
    CoordinationPermitState.CLOUD_CLAIMED -> if (
      !permit.armDispatched && permit.armRequestId == null
    ) {
      ArmRecoveryAction.DISPATCH_ONCE
    } else {
      ArmRecoveryAction.CLOSE_UNKNOWN
    }
    CoordinationPermitState.ARM_RECONCILING -> {
      if (
        !permit.armDispatched ||
        permit.armRequestId == null ||
        currentBootCount == null ||
        currentBootCount != permit.bootCount ||
        permit.requestStartElapsedMillis < 0 ||
        currentElapsedRealtimeMillis < permit.requestStartElapsedMillis
      ) return ArmRecoveryAction.CLOSE_UNKNOWN
      val remaining = subtractExactOrNull(
        permit.unresolvedArmCutoffMillis,
        permit.trustedServerNowMillis,
      ) ?: return ArmRecoveryAction.CLOSE_UNKNOWN
      if (remaining < 0) return ArmRecoveryAction.CLOSE_UNKNOWN
      val cutoffElapsed = addExactOrNull(permit.requestStartElapsedMillis, remaining)
        ?: return ArmRecoveryAction.CLOSE_UNKNOWN
      if (currentElapsedRealtimeMillis < cutoffElapsed) {
        ArmRecoveryAction.QUERY_EXACT_STATUS
      } else {
        ArmRecoveryAction.QUERY_EXACT_STATUS_THEN_CLOSE
      }
    }
    else -> ArmRecoveryAction.NONE
    }
  }

  private fun addExactOrNull(left: Long, right: Long): Long? = try {
    Math.addExact(left, right)
  } catch (_: ArithmeticException) {
    null
  }

  private fun subtractExactOrNull(left: Long, right: Long): Long? = try {
    Math.subtractExact(left, right)
  } catch (_: ArithmeticException) {
    null
  }
}

internal object TrustedTimeEstimator {
  fun estimate(
    trust: ClockTrustEntity?,
    currentElapsedRealtimeMillis: Long,
    currentBootCount: Int?,
  ): Long? {
    if (
      trust?.status != ClockTrustStatus.TRUSTED ||
      currentBootCount == null ||
      trust.trustedBootCount != currentBootCount ||
      currentElapsedRealtimeMillis < 0
    ) return null
    val server = trust.greatestTrustedServerMillis ?: return null
    val anchor = trust.lastElapsedRealtimeMillis ?: return null
    if (anchor < 0 || currentElapsedRealtimeMillis < anchor) return null
    return try {
      Math.addExact(server, Math.subtractExact(currentElapsedRealtimeMillis, anchor))
    } catch (_: ArithmeticException) {
      null
    }
  }
}
