package com.yashsomani.birthdayautopilot.readiness

import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.core.model.SafeReasonCode

enum class ReadinessGate {
  TEST,
  ACTIVATION,
  BIRTHDAY,
}

data class ReadinessBlocker(
  val code: SafeReasonCode,
  val gates: Set<ReadinessGate>,
)

data class ReadinessInputs(
  val distributionVerified: Boolean?,
  val accountMode: AccountMode?,
  val contactsFresh: Boolean?,
  val approvalsReady: Boolean?,
  val passingTestReceipt: Boolean?,
  val networkAvailable: Boolean?,
  val coordinationAvailable: Boolean?,
  val schedulerReady: Boolean?,
  val smsPermissionGranted: Boolean?,
  val simReady: Boolean?,
  val backgroundRestricted: Boolean?,
  val dozeAllowlisted: Boolean?,
  val unusedAppRestrictionDisabled: Boolean?,
  val dataSaverAllowsBackground: Boolean?,
  val lowPowerStandbySafe: Boolean?,
  val clockTrusted: Boolean?,
  val resetSafetyClear: Boolean?,
)

data class ReadinessDecision(
  val testBlockers: List<SafeReasonCode>,
  val activationBlockers: List<SafeReasonCode>,
  val birthdayBlockers: List<SafeReasonCode>,
) {
  fun isReady(gate: ReadinessGate): Boolean = when (gate) {
    ReadinessGate.TEST -> testBlockers.isEmpty()
    ReadinessGate.ACTIVATION -> activationBlockers.isEmpty()
    ReadinessGate.BIRTHDAY -> birthdayBlockers.isEmpty()
  }
}

class ReadinessEvaluator {
  fun evaluate(input: ReadinessInputs): ReadinessDecision {
    val blockers = buildList {
      requireTrue(
        input.distributionVerified,
        SafeReasonCode.DISTRIBUTION_UNVERIFIED,
        ReadinessGate.entries.toSet(),
      )
      requireAccountModes(input.accountMode)
      requireTrue(
        input.contactsFresh,
        SafeReasonCode.CONTACTS_STALE,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.approvalsReady,
        SafeReasonCode.APPROVAL_REQUIRED,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.passingTestReceipt,
        SafeReasonCode.TEST_REQUIRED,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.networkAvailable,
        SafeReasonCode.NETWORK_UNAVAILABLE,
        ReadinessGate.entries.toSet(),
      )
      requireTrue(
        input.coordinationAvailable,
        SafeReasonCode.COORDINATION_UNAVAILABLE,
        ReadinessGate.entries.toSet(),
      )
      requireTrue(
        input.schedulerReady,
        SafeReasonCode.SCHEDULER_UNAVAILABLE,
        ReadinessGate.entries.toSet(),
      )
      requireTrue(
        input.smsPermissionGranted,
        SafeReasonCode.SMS_PERMISSION_MISSING,
        ReadinessGate.entries.toSet(),
      )
      requireTrue(
        input.simReady,
        SafeReasonCode.SIM_UNAVAILABLE,
        ReadinessGate.entries.toSet(),
      )
      requireFalse(
        input.backgroundRestricted,
        SafeReasonCode.BACKGROUND_RESTRICTED,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.dozeAllowlisted,
        SafeReasonCode.DOZE_NOT_ALLOWLISTED,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.unusedAppRestrictionDisabled,
        SafeReasonCode.UNUSED_APP_RESTRICTION,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.dataSaverAllowsBackground,
        SafeReasonCode.DATA_SAVER_RESTRICTED,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.lowPowerStandbySafe,
        SafeReasonCode.LOW_POWER_STANDBY_UNSAFE,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
      requireTrue(
        input.clockTrusted,
        SafeReasonCode.CLOCK_UNTRUSTED,
        ReadinessGate.entries.toSet(),
      )
      requireTrue(
        input.resetSafetyClear,
        SafeReasonCode.RESET_SAFETY_BLOCKED,
        setOf(ReadinessGate.ACTIVATION, ReadinessGate.BIRTHDAY),
      )
    }

    return ReadinessDecision(
      testBlockers = blockers.forGate(ReadinessGate.TEST),
      activationBlockers = blockers.forGate(ReadinessGate.ACTIVATION),
      birthdayBlockers = blockers.forGate(ReadinessGate.BIRTHDAY),
    )
  }

  private fun MutableList<ReadinessBlocker>.requireAccountModes(mode: AccountMode?) {
    if (mode == null) {
      add(ReadinessBlocker(SafeReasonCode.ACCOUNT_REQUIRED, ReadinessGate.entries.toSet()))
      return
    }
    if (mode !in setOf(AccountMode.TEST_ONLY, AccountMode.PAUSED_REPAIR)) {
      add(ReadinessBlocker(SafeReasonCode.ACCOUNT_REQUIRED, setOf(ReadinessGate.TEST)))
    }
    if (mode !in setOf(AccountMode.TEST_ONLY, AccountMode.PAUSED_REPAIR)) {
      add(ReadinessBlocker(SafeReasonCode.AUTOMATION_PAUSED, setOf(ReadinessGate.ACTIVATION)))
    }
    if (mode != AccountMode.AUTOMATION_ACTIVE) {
      add(ReadinessBlocker(SafeReasonCode.AUTOMATION_PAUSED, setOf(ReadinessGate.BIRTHDAY)))
    }
  }

  private fun MutableList<ReadinessBlocker>.requireTrue(
    value: Boolean?,
    code: SafeReasonCode,
    gates: Set<ReadinessGate>,
  ) {
    if (value != true) add(ReadinessBlocker(code, gates))
  }

  private fun MutableList<ReadinessBlocker>.requireFalse(
    value: Boolean?,
    code: SafeReasonCode,
    gates: Set<ReadinessGate>,
  ) {
    if (value != false) add(ReadinessBlocker(code, gates))
  }

  private fun List<ReadinessBlocker>.forGate(gate: ReadinessGate): List<SafeReasonCode> =
    asSequence()
      .filter { gate in it.gates }
      .map(ReadinessBlocker::code)
      .distinct()
      .toList()
}
