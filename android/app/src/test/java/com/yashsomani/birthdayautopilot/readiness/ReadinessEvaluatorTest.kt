package com.yashsomani.birthdayautopilot.readiness

import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.core.model.SafeReasonCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessEvaluatorTest {
  private val evaluator = ReadinessEvaluator()

  @Test
  fun `unknown external values fail every affected gate closed`() {
    val decision = evaluator.evaluate(readyInputs().copy(networkAvailable = null))

    assertTrue(SafeReasonCode.NETWORK_UNAVAILABLE in decision.testBlockers)
    assertTrue(SafeReasonCode.NETWORK_UNAVAILABLE in decision.activationBlockers)
    assertTrue(SafeReasonCode.NETWORK_UNAVAILABLE in decision.birthdayBlockers)
  }

  @Test
  fun `foreground test excludes background predicates`() {
    val decision = evaluator.evaluate(
      readyInputs().copy(
        backgroundRestricted = true,
        dozeAllowlisted = false,
        unusedAppRestrictionDisabled = false,
        dataSaverAllowsBackground = false,
        lowPowerStandbySafe = false,
      ),
    )

    assertTrue(decision.isReady(ReadinessGate.TEST))
    assertFalse(decision.isReady(ReadinessGate.ACTIVATION))
    assertFalse(decision.isReady(ReadinessGate.BIRTHDAY))
  }

  @Test
  fun `birthday requires automation active mode`() {
    val paused = evaluator.evaluate(readyInputs())
    val active = evaluator.evaluate(readyInputs().copy(accountMode = AccountMode.AUTOMATION_ACTIVE))

    assertTrue(SafeReasonCode.AUTOMATION_PAUSED in paused.birthdayBlockers)
    assertFalse(SafeReasonCode.AUTOMATION_PAUSED in active.birthdayBlockers)
  }

  private fun readyInputs() = ReadinessInputs(
    distributionVerified = true,
    accountMode = AccountMode.TEST_ONLY,
    contactsFresh = true,
    approvalsReady = true,
    passingTestReceipt = true,
    networkAvailable = true,
    coordinationAvailable = true,
    smsPermissionGranted = true,
    simReady = true,
    backgroundRestricted = false,
    dozeAllowlisted = true,
    unusedAppRestrictionDisabled = true,
    dataSaverAllowsBackground = true,
    lowPowerStandbySafe = true,
    clockTrusted = true,
    resetSafetyClear = true,
  )
}
