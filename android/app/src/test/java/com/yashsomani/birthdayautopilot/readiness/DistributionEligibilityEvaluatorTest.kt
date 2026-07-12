package com.yashsomani.birthdayautopilot.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionEligibilityEvaluatorTest {
  private val evaluator = DistributionEligibilityEvaluator()

  @Test
  fun `approved healthy device is supported`() {
    val decision = evaluator.evaluate(healthySignals())

    assertEquals(EligibilityKind.SUPPORTED, decision.kind)
    assertEquals(null, decision.primaryReason)
    assertTrue(decision.distributionVerified)
  }

  @Test
  fun `unknown installer fails distribution closed`() {
    val decision = evaluator.evaluate(healthySignals().copy(installerMatches = null))

    assertEquals(EligibilityKind.UNSUPPORTED, decision.kind)
    assertEquals(EligibilityReason.INSTALLER_ALLOWLIST_MISSING, decision.primaryReason)
    assertFalse(decision.distributionVerified)
  }

  @Test
  fun `unapproved build and stale evidence collapse to one safe reason`() {
    val decision = evaluator.evaluate(
      healthySignals().copy(
        restrictedSmsBuildApproved = false,
        evidenceCurrent = false,
        signingCertificateMatches = false,
      ),
    )

    assertEquals(EligibilityKind.UNSUPPORTED, decision.kind)
    assertEquals(EligibilityReason.DISTRIBUTION_CHANNEL_UNAPPROVED, decision.primaryReason)
    assertTrue(decision.otherReasons.isEmpty())
    assertFalse(decision.distributionVerified)
  }

  @Test
  fun `missing sim and unsafe background are limited after distribution is proven`() {
    val decision = evaluator.evaluate(
      healthySignals().copy(
        simReady = false,
        backgroundRestricted = true,
        dozeAllowlisted = false,
      ),
    )

    assertEquals(EligibilityKind.LIMITED, decision.kind)
    assertEquals(EligibilityReason.NO_ACTIVE_SIM, decision.primaryReason)
    assertEquals(
      listOf(
        EligibilityReason.BACKGROUND_RESTRICTED,
        EligibilityReason.DOZE_EXEMPTION_MISSING,
      ),
      decision.otherReasons,
    )
    assertTrue(decision.distributionVerified)
  }

  @Test
  fun `foreground test allows only background execution limitations`() {
    val backgroundOnly = evaluator.evaluate(
      healthySignals().copy(
        backgroundRestricted = true,
        dozeAllowlisted = false,
        unusedAppRestrictionsDisabled = false,
        dataSaverAllowsBackground = false,
        lowPowerStandbySafe = false,
      ),
    )
    val missingSim = evaluator.evaluate(healthySignals().copy(simReady = false))
    val offline = evaluator.evaluate(healthySignals().copy(networkValidated = false))
    val missingPlayServices = evaluator.evaluate(
      healthySignals().copy(playServicesAvailable = false),
    )

    assertTrue(backgroundOnly.allowsForegroundTest())
    assertFalse(missingSim.allowsForegroundTest())
    assertFalse(offline.allowsForegroundTest())
    assertFalse(missingPlayServices.allowsForegroundTest())
  }

  @Test
  fun `unknown restricted profile is unsupported`() {
    val decision = evaluator.evaluate(healthySignals().copy(restrictedProfile = null))

    assertEquals(EligibilityKind.UNSUPPORTED, decision.kind)
    assertEquals(EligibilityReason.RESTRICTED_PROFILE, decision.primaryReason)
  }

  private fun healthySignals() = DistributionSignals(
    apiCertified = true,
    telephonyMessagingAvailable = true,
    restrictedProfile = false,
    restrictedSmsBuildApproved = true,
    evidenceCurrent = true,
    signingCertificateMatches = true,
    installerMatches = true,
    playServicesAvailable = true,
    simReady = true,
    networkValidated = true,
    backgroundRestricted = false,
    dozeAllowlisted = true,
    unusedAppRestrictionsDisabled = true,
    dataSaverAllowsBackground = true,
    lowPowerStandbySafe = true,
  )
}
