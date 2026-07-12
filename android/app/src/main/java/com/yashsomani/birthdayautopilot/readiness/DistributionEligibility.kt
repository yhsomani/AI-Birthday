package com.yashsomani.birthdayautopilot.readiness

enum class EligibilityKind {
  SUPPORTED,
  LIMITED,
  UNSUPPORTED,
}

enum class EligibilityReason(val wireCode: String) {
  PLATFORM_UNSUPPORTED("platform-unsupported"),
  NO_TELEPHONY("no-telephony"),
  RESTRICTED_PROFILE("restricted-profile"),
  DISTRIBUTION_CHANNEL_UNAPPROVED("distribution-channel-unapproved"),
  INSTALLER_ALLOWLIST_MISSING("installer-allowlist-missing"),
  GOOGLE_PLAY_SERVICES_MISSING("google-play-services-missing"),
  NO_ACTIVE_SIM("no-active-sim"),
  NETWORK_OFFLINE("network-offline"),
  BACKGROUND_RESTRICTED("background-restricted"),
  DOZE_EXEMPTION_MISSING("doze-exemption-missing"),
  UNUSED_APP_RESTRICTIONS_UNSAFE("unused-app-restrictions-unsafe"),
  DATA_SAVER_RESTRICTED("data-saver-restricted"),
  LOW_POWER_STANDBY_UNSAFE("low-power-standby-unsafe"),
}

data class DistributionSignals(
  val apiCertified: Boolean?,
  val telephonyMessagingAvailable: Boolean?,
  val restrictedProfile: Boolean?,
  val restrictedSmsBuildApproved: Boolean?,
  val evidenceCurrent: Boolean?,
  val signingCertificateMatches: Boolean?,
  val installerMatches: Boolean?,
  val playServicesAvailable: Boolean?,
  val simReady: Boolean?,
  val networkValidated: Boolean?,
  val backgroundRestricted: Boolean?,
  val dozeAllowlisted: Boolean?,
  val unusedAppRestrictionsDisabled: Boolean?,
  val dataSaverAllowsBackground: Boolean?,
  val lowPowerStandbySafe: Boolean?,
)

data class EligibilityDecision(
  val kind: EligibilityKind,
  val primaryReason: EligibilityReason?,
  val otherReasons: List<EligibilityReason>,
  val distributionVerified: Boolean,
) {
  init {
    require((kind == EligibilityKind.SUPPORTED) == (primaryReason == null))
    require(primaryReason !in otherReasons)
  }
}

class DistributionEligibilityEvaluator {
  fun evaluate(signals: DistributionSignals): EligibilityDecision {
    val unsupported = buildList {
      requireTrue(
        signals.restrictedSmsBuildApproved,
        EligibilityReason.DISTRIBUTION_CHANNEL_UNAPPROVED,
      )
      requireTrue(signals.evidenceCurrent, EligibilityReason.DISTRIBUTION_CHANNEL_UNAPPROVED)
      requireTrue(
        signals.signingCertificateMatches,
        EligibilityReason.DISTRIBUTION_CHANNEL_UNAPPROVED,
      )
      requireTrue(signals.installerMatches, EligibilityReason.INSTALLER_ALLOWLIST_MISSING)
      requireTrue(signals.apiCertified, EligibilityReason.PLATFORM_UNSUPPORTED)
      requireTrue(signals.telephonyMessagingAvailable, EligibilityReason.NO_TELEPHONY)
      requireFalse(signals.restrictedProfile, EligibilityReason.RESTRICTED_PROFILE)
    }.distinct()

    val distributionVerified = unsupported.none { reason ->
      reason in DISTRIBUTION_REASONS
    }

    if (unsupported.isNotEmpty()) {
      return EligibilityDecision(
        kind = EligibilityKind.UNSUPPORTED,
        primaryReason = unsupported.first(),
        otherReasons = unsupported.drop(1),
        distributionVerified = distributionVerified,
      )
    }

    val limited = buildList {
      requireTrue(
        signals.playServicesAvailable,
        EligibilityReason.GOOGLE_PLAY_SERVICES_MISSING,
      )
      requireTrue(signals.simReady, EligibilityReason.NO_ACTIVE_SIM)
      requireTrue(signals.networkValidated, EligibilityReason.NETWORK_OFFLINE)
      requireFalse(signals.backgroundRestricted, EligibilityReason.BACKGROUND_RESTRICTED)
      requireTrue(signals.dozeAllowlisted, EligibilityReason.DOZE_EXEMPTION_MISSING)
      requireTrue(
        signals.unusedAppRestrictionsDisabled,
        EligibilityReason.UNUSED_APP_RESTRICTIONS_UNSAFE,
      )
      requireTrue(
        signals.dataSaverAllowsBackground,
        EligibilityReason.DATA_SAVER_RESTRICTED,
      )
      requireTrue(
        signals.lowPowerStandbySafe,
        EligibilityReason.LOW_POWER_STANDBY_UNSAFE,
      )
    }.distinct()

    return if (limited.isEmpty()) {
      EligibilityDecision(
        kind = EligibilityKind.SUPPORTED,
        primaryReason = null,
        otherReasons = emptyList(),
        distributionVerified = true,
      )
    } else {
      EligibilityDecision(
        kind = EligibilityKind.LIMITED,
        primaryReason = limited.first(),
        otherReasons = limited.drop(1),
        distributionVerified = true,
      )
    }
  }

  private fun MutableList<EligibilityReason>.requireTrue(
    value: Boolean?,
    reason: EligibilityReason,
  ) {
    if (value != true) add(reason)
  }

  private fun MutableList<EligibilityReason>.requireFalse(
    value: Boolean?,
    reason: EligibilityReason,
  ) {
    if (value != false) add(reason)
  }

  private companion object {
    val DISTRIBUTION_REASONS = setOf(
      EligibilityReason.DISTRIBUTION_CHANNEL_UNAPPROVED,
      EligibilityReason.INSTALLER_ALLOWLIST_MISSING,
    )
  }
}
