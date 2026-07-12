package com.yashsomani.birthdayautopilot.coordination

internal const val COORDINATION_CONTRACT_VERSION = 1
internal const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L

internal class CoordinationBinding private constructor(
  val ledgerGeneration: String,
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
  val appBuildNumber: Int,
  val policyVersion: Int,
  val distributionChannel: DistributionChannel,
) {
  internal fun payload(): Map<String, Any> = linkedMapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "ledgerGeneration" to ledgerGeneration,
    "installationId" to installationId,
    "senderEpoch" to senderEpoch,
    "resetGeneration" to resetGeneration,
    "appBuildNumber" to appBuildNumber,
    "policyVersion" to policyVersion,
    "distributionChannel" to distributionChannel.name,
  )

  override fun toString(): String = "CoordinationBinding(<redacted>)"

  companion object {
    internal fun create(
      ledgerGeneration: String,
      installationId: String,
      senderEpoch: Long,
      resetGeneration: Long,
      appBuildNumber: Int,
      policyVersion: Int,
      distributionChannel: DistributionChannel,
    ): RequestBuildResult<CoordinationBinding> {
      val invalid = CoordinationValuePolicy.validateBinding(
        ledgerGeneration,
        installationId,
        senderEpoch,
        resetGeneration,
        appBuildNumber,
        policyVersion,
      )
      return if (invalid == null) {
        RequestBuildResult.Ready(
          CoordinationBinding(
            ledgerGeneration,
            installationId,
            senderEpoch,
            resetGeneration,
            appBuildNumber,
            policyVersion,
            distributionChannel,
          ),
        )
      } else {
        RequestBuildResult.Invalid(invalid)
      }
    }
  }
}

internal sealed class PreparedCoordinationRequest<T>(
  val functionName: String,
  private val payload: Map<String, Any>,
) {
  internal fun callablePayload(): Map<String, Any> = payload

  override fun toString(): String = "${this::class.simpleName}(<redacted>)"
}

internal class RegistrationRequest internal constructor(
  val ledgerGeneration: String,
  val installationId: String,
  val appBuildNumber: Int,
  val policyVersion: Int,
  val distributionChannel: DistributionChannel,
) : PreparedCoordinationRequest<RegistrationOutcome>(
  functionName = CoordinationEndpointPolicy.REGISTER,
  payload = linkedMapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "ledgerGeneration" to ledgerGeneration,
    "installationId" to installationId,
    "appBuildNumber" to appBuildNumber,
    "policyVersion" to policyVersion,
    "distributionChannel" to distributionChannel.name,
  ),
)

internal class LeaseRequest internal constructor(
  val binding: CoordinationBinding,
  val purpose: CoordinationPurpose,
) : PreparedCoordinationRequest<LeaseOutcome>(
  functionName = CoordinationEndpointPolicy.RENEW_LEASE,
  payload = binding.payload() + ("purpose" to purpose.name),
)

internal class AccountModeRequest internal constructor(
  val binding: CoordinationBinding,
  val action: AccountModeAction,
  val testClaimId: String?,
  boundTestReceiptPrehash: String?,
  readinessContractVersion: Int?,
) : PreparedCoordinationRequest<AccountModeOutcome>(
  functionName = CoordinationEndpointPolicy.CHANGE_MODE,
  payload = when (action) {
    AccountModeAction.PAUSE_FOR_REPAIR -> binding.payload() + ("action" to action.name)
    AccountModeAction.ACTIVATE_AUTOMATION -> binding.payload() + mapOf(
      "action" to action.name,
      "testClaimId" to checkNotNull(testClaimId),
      "boundTestReceiptPrehash" to checkNotNull(boundTestReceiptPrehash),
      "readinessContractVersion" to checkNotNull(readinessContractVersion),
    )
  },
)

internal sealed class ClaimRequest<T>(
  functionName: String,
  payload: Map<String, Any>,
) : PreparedCoordinationRequest<T>(functionName, payload) {
  abstract val binding: CoordinationBinding
  abstract val purpose: CoordinationPurpose
  abstract val claimRequestId: String
}

internal class BirthdayClaimRequest internal constructor(
  override val binding: CoordinationBinding,
  override val claimRequestId: String,
  recipientPrehashAliases: List<String>,
  destinationPrehashAliases: List<String>,
) : ClaimRequest<ClaimOutcome>(
  functionName = CoordinationEndpointPolicy.CLAIM_OCCURRENCE,
  payload = binding.payload() + mapOf(
    "purpose" to CoordinationPurpose.BIRTHDAY.name,
    "claimRequestId" to claimRequestId,
    "recipientPrehashAliases" to recipientPrehashAliases.toList(),
    "destinationPrehashAliases" to destinationPrehashAliases.toList(),
  ),
) {
  override val purpose: CoordinationPurpose = CoordinationPurpose.BIRTHDAY
}

internal class TestClaimRequest internal constructor(
  override val binding: CoordinationBinding,
  val testRequestId: String,
  testConfigurationPrehash: String,
  testDestinationPrehash: String,
) : ClaimRequest<ClaimOutcome>(
  functionName = CoordinationEndpointPolicy.CLAIM_TEST,
  payload = binding.payload() + mapOf(
    "purpose" to CoordinationPurpose.TEST.name,
    "testRequestId" to testRequestId,
    "testConfigurationPrehash" to testConfigurationPrehash,
    "testDestinationPrehash" to testDestinationPrehash,
  ),
) {
  override val purpose: CoordinationPurpose = CoordinationPurpose.TEST
  override val claimRequestId: String = testRequestId
}

internal class ArmRequest internal constructor(
  val binding: CoordinationBinding,
  val purpose: CoordinationPurpose,
  val claimId: String,
  val armRequestId: String,
  val attempt: Int,
) : PreparedCoordinationRequest<ArmDecisionOutcome>(
  functionName = CoordinationEndpointPolicy.ARM_ATTEMPT,
  payload = binding.payload() + mapOf(
    "purpose" to purpose.name,
    "claimId" to claimId,
    "armRequestId" to armRequestId,
    "attempt" to attempt,
  ),
)

internal class RetryRequest internal constructor(
  val binding: CoordinationBinding,
  val claimId: String,
  val retryRequestId: String,
  val proof: RetryProof,
) : PreparedCoordinationRequest<RetryOutcome>(
  functionName = CoordinationEndpointPolicy.AUTHORIZE_RETRY,
  payload = binding.payload() + mapOf(
    "purpose" to CoordinationPurpose.BIRTHDAY.name,
    "claimId" to claimId,
    "retryRequestId" to retryRequestId,
    "proof" to proof.name,
  ),
)

internal class TestReportRequest internal constructor(
  val binding: CoordinationBinding,
  val testClaimId: String,
  val armRequestId: String,
  val result: TestReportResult,
) : PreparedCoordinationRequest<TestReportOutcome>(
  functionName = CoordinationEndpointPolicy.REPORT_TEST,
  payload = binding.payload() + mapOf(
    "purpose" to CoordinationPurpose.TEST.name,
    "testClaimId" to testClaimId,
    "armRequestId" to armRequestId,
    "result" to result.name,
  ),
)

internal class CompanionStatusRequest internal constructor(
  val ledgerGeneration: String,
) : PreparedCoordinationRequest<CompanionStatusOutcome>(
  functionName = CoordinationEndpointPolicy.COMPANION_STATUS,
  payload = mapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "ledgerGeneration" to ledgerGeneration,
  ),
)

internal class SenderTransferRequest internal constructor(
  val binding: CoordinationBinding,
  val targetInstallationId: String,
  functionName: String,
) : PreparedCoordinationRequest<SenderTransferOutcome>(
  functionName = functionName,
  payload = binding.payload() + ("targetInstallationId" to targetInstallationId),
)

internal class AccountDeletionRequest internal constructor(
  val requestId: String,
) : PreparedCoordinationRequest<AccountDeletionAcceptance>(
  functionName = CoordinationEndpointPolicy.REQUEST_ACCOUNT_DELETION,
  payload = mapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "requestId" to requestId,
  ),
)

internal class AccountDeletionReceiptRequest internal constructor(
  val receiptId: String,
) : PreparedCoordinationRequest<AccountDeletionReceiptOutcome>(
  functionName = CoordinationEndpointPolicy.ACCOUNT_DELETION_RECEIPT,
  payload = mapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "receiptId" to receiptId,
  ),
)

internal class ContactDerivedResetRequest internal constructor(
  val requestId: String,
) : PreparedCoordinationRequest<CoordinationOperationOutcome>(
  functionName = CoordinationEndpointPolicy.RESET_CONTACT_DERIVED_STATE,
  payload = mapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "requestId" to requestId,
  ),
)

internal class SenderReleaseRequest internal constructor(
  val requestId: String,
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
) : PreparedCoordinationRequest<CoordinationOperationOutcome>(
  functionName = CoordinationEndpointPolicy.RELEASE_ANDROID_SENDER,
  payload = mapOf(
    "contractVersion" to COORDINATION_CONTRACT_VERSION,
    "requestId" to requestId,
    "installationId" to installationId,
    "senderEpoch" to senderEpoch,
    "resetGeneration" to resetGeneration,
  ),
)

internal class CoordinationLifecycleStatusRequest internal constructor() :
  PreparedCoordinationRequest<CoordinationLifecycleStatusOutcome>(
    functionName = CoordinationEndpointPolicy.COORDINATION_LIFECYCLE_STATUS,
    payload = mapOf("contractVersion" to COORDINATION_CONTRACT_VERSION),
  )

internal object CoordinationRequestFactory {
  fun binding(
    ledgerGeneration: String,
    installationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
    appBuildNumber: Int,
    policyVersion: Int,
    distributionChannel: DistributionChannel,
  ): RequestBuildResult<CoordinationBinding> = CoordinationBinding.create(
    ledgerGeneration,
    installationId,
    senderEpoch,
    resetGeneration,
    appBuildNumber,
    policyVersion,
    distributionChannel,
  )

  fun registration(
    ledgerGeneration: String,
    installationId: String,
    appBuildNumber: Int,
    policyVersion: Int,
    distributionChannel: DistributionChannel,
  ): RequestBuildResult<RegistrationRequest> {
    val invalid = CoordinationValuePolicy.validateRegistration(
      ledgerGeneration,
      installationId,
      appBuildNumber,
      policyVersion,
    )
    return if (invalid == null) {
      RequestBuildResult.Ready(
        RegistrationRequest(
          ledgerGeneration,
          installationId,
          appBuildNumber,
          policyVersion,
          distributionChannel,
        ),
      )
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun lease(
    binding: CoordinationBinding,
    purpose: CoordinationPurpose,
  ): LeaseRequest = LeaseRequest(binding, purpose)

  fun pauseForRepair(binding: CoordinationBinding): AccountModeRequest = AccountModeRequest(
    binding,
    AccountModeAction.PAUSE_FOR_REPAIR,
    null,
    null,
    null,
  )

  fun activateAutomation(
    binding: CoordinationBinding,
    testClaimId: String,
    boundTestReceiptPrehash: String,
    readinessContractVersion: Int,
  ): RequestBuildResult<AccountModeRequest> {
    val invalid = when {
      !CoordinationValuePolicy.isOpaqueKey(testClaimId) -> RequestInvalidReason.CLAIM_ID
      !CoordinationValuePolicy.isSha256(boundTestReceiptPrehash) -> RequestInvalidReason.PREHASH
      !CoordinationValuePolicy.isPositiveVersion(readinessContractVersion) -> {
        RequestInvalidReason.READINESS_CONTRACT_VERSION
      }
      else -> null
    }
    return if (invalid == null) {
      RequestBuildResult.Ready(
        AccountModeRequest(
          binding,
          AccountModeAction.ACTIVATE_AUTOMATION,
          testClaimId,
          boundTestReceiptPrehash,
          readinessContractVersion,
        ),
      )
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun birthdayClaim(
    binding: CoordinationBinding,
    claimRequestId: String,
    recipientPrehashAliases: List<String>,
    destinationPrehashAliases: List<String>,
  ): RequestBuildResult<BirthdayClaimRequest> {
    val invalid = when {
      !CoordinationValuePolicy.isUuid(claimRequestId) -> RequestInvalidReason.REQUEST_ID
      !CoordinationValuePolicy.isPrehashAliases(recipientPrehashAliases) -> {
        RequestInvalidReason.PREHASH_ALIASES
      }
      !CoordinationValuePolicy.isPrehashAliases(destinationPrehashAliases) -> {
        RequestInvalidReason.PREHASH_ALIASES
      }
      else -> null
    }
    return if (invalid == null) {
      RequestBuildResult.Ready(
        BirthdayClaimRequest(
          binding,
          claimRequestId,
          recipientPrehashAliases,
          destinationPrehashAliases,
        ),
      )
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun testClaim(
    binding: CoordinationBinding,
    testRequestId: String,
    testConfigurationPrehash: String,
    testDestinationPrehash: String,
  ): RequestBuildResult<TestClaimRequest> {
    val invalid = when {
      !CoordinationValuePolicy.isUuid(testRequestId) -> RequestInvalidReason.REQUEST_ID
      !CoordinationValuePolicy.isSha256(testConfigurationPrehash) ||
        !CoordinationValuePolicy.isSha256(testDestinationPrehash) -> RequestInvalidReason.PREHASH
      else -> null
    }
    return if (invalid == null) {
      RequestBuildResult.Ready(
        TestClaimRequest(
          binding,
          testRequestId,
          testConfigurationPrehash,
          testDestinationPrehash,
        ),
      )
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun arm(
    binding: CoordinationBinding,
    purpose: CoordinationPurpose,
    claimId: String,
    armRequestId: String,
    attempt: Int,
  ): RequestBuildResult<ArmRequest> {
    val invalid = when {
      !CoordinationValuePolicy.isOpaqueKey(claimId) -> RequestInvalidReason.CLAIM_ID
      !CoordinationValuePolicy.isUuid(armRequestId) -> RequestInvalidReason.REQUEST_ID
      attempt !in 1..2 -> RequestInvalidReason.ATTEMPT
      else -> null
    }
    return if (invalid == null) {
      RequestBuildResult.Ready(ArmRequest(binding, purpose, claimId, armRequestId, attempt))
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun retry(
    binding: CoordinationBinding,
    claimId: String,
    retryRequestId: String,
    proof: RetryProof,
  ): RequestBuildResult<RetryRequest> = when {
    !CoordinationValuePolicy.isOpaqueKey(claimId) ->
      RequestBuildResult.Invalid(RequestInvalidReason.CLAIM_ID)
    !CoordinationValuePolicy.isUuid(retryRequestId) ->
      RequestBuildResult.Invalid(RequestInvalidReason.REQUEST_ID)
    else -> RequestBuildResult.Ready(RetryRequest(binding, claimId, retryRequestId, proof))
  }

  fun testReport(
    binding: CoordinationBinding,
    testClaimId: String,
    armRequestId: String,
    result: TestReportResult,
  ): RequestBuildResult<TestReportRequest> {
    val invalid = when {
      !CoordinationValuePolicy.isOpaqueKey(testClaimId) -> RequestInvalidReason.CLAIM_ID
      !CoordinationValuePolicy.isUuid(armRequestId) -> RequestInvalidReason.REQUEST_ID
      else -> null
    }
    return if (invalid == null) {
      RequestBuildResult.Ready(TestReportRequest(binding, testClaimId, armRequestId, result))
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun companionStatus(
    ledgerGeneration: String,
  ): RequestBuildResult<CompanionStatusRequest> = if (
    CoordinationValuePolicy.isLedgerGeneration(ledgerGeneration)
  ) {
    RequestBuildResult.Ready(CompanionStatusRequest(ledgerGeneration))
  } else {
    RequestBuildResult.Invalid(RequestInvalidReason.LEDGER_GENERATION)
  }

  fun beginSenderTransfer(
    binding: CoordinationBinding,
    targetInstallationId: String,
  ): RequestBuildResult<SenderTransferRequest> = senderTransfer(
    binding,
    targetInstallationId,
    CoordinationEndpointPolicy.BEGIN_SENDER_TRANSFER,
  )

  fun completeSenderTransfer(
    binding: CoordinationBinding,
    targetInstallationId: String,
  ): RequestBuildResult<SenderTransferRequest> = senderTransfer(
    binding,
    targetInstallationId,
    CoordinationEndpointPolicy.COMPLETE_SENDER_TRANSFER,
  )

  fun accountDeletion(requestId: String): RequestBuildResult<AccountDeletionRequest> =
    if (CoordinationValuePolicy.isUuidV4(requestId)) {
      RequestBuildResult.Ready(AccountDeletionRequest(requestId))
    } else {
      RequestBuildResult.Invalid(RequestInvalidReason.REQUEST_ID)
    }

  fun accountDeletionReceipt(
    receiptId: String,
  ): RequestBuildResult<AccountDeletionReceiptRequest> = if (
    CoordinationValuePolicy.isUuidV4(receiptId)
  ) {
    RequestBuildResult.Ready(AccountDeletionReceiptRequest(receiptId))
  } else {
    RequestBuildResult.Invalid(RequestInvalidReason.REQUEST_ID)
  }

  fun resetContactDerivedState(
    requestId: String,
  ): RequestBuildResult<ContactDerivedResetRequest> = if (
    CoordinationValuePolicy.isUuid(requestId)
  ) {
    RequestBuildResult.Ready(ContactDerivedResetRequest(requestId))
  } else {
    RequestBuildResult.Invalid(RequestInvalidReason.REQUEST_ID)
  }

  fun releaseAndroidSender(
    requestId: String,
    installationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
  ): RequestBuildResult<SenderReleaseRequest> {
    val invalid = when {
      !CoordinationValuePolicy.isUuid(requestId) -> RequestInvalidReason.REQUEST_ID
      !CoordinationValuePolicy.isInstallationId(installationId) -> {
        RequestInvalidReason.INSTALLATION_ID
      }
      !CoordinationValuePolicy.isSafePositiveLong(senderEpoch) -> RequestInvalidReason.SENDER_EPOCH
      !CoordinationValuePolicy.isSafePositiveLong(resetGeneration) -> {
        RequestInvalidReason.RESET_GENERATION
      }
      else -> null
    }
    return if (invalid == null) {
      RequestBuildResult.Ready(
        SenderReleaseRequest(
          requestId,
          installationId,
          senderEpoch,
          resetGeneration,
        ),
      )
    } else {
      RequestBuildResult.Invalid(invalid)
    }
  }

  fun coordinationLifecycleStatus(): CoordinationLifecycleStatusRequest =
    CoordinationLifecycleStatusRequest()

  private fun senderTransfer(
    binding: CoordinationBinding,
    targetInstallationId: String,
    functionName: String,
  ): RequestBuildResult<SenderTransferRequest> = if (
    CoordinationValuePolicy.isInstallationId(targetInstallationId) &&
    targetInstallationId != binding.installationId
  ) {
    RequestBuildResult.Ready(SenderTransferRequest(binding, targetInstallationId, functionName))
  } else {
    RequestBuildResult.Invalid(RequestInvalidReason.INSTALLATION_ID)
  }
}

internal object CoordinationValuePolicy {
  private val ledgerGenerationPattern = Regex("^[A-Za-z0-9._-]{8,64}$")
  private val installationIdPattern = Regex("^[a-f0-9]{32}$")
  private val sha256Pattern = Regex("^[a-f0-9]{64}$")
  private val opaqueKeyPattern = Regex("^[A-Za-z0-9._-]{10,80}$")
  private val uuidPattern = Regex(
    "^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
  )
  private val uuidV4Pattern = Regex(
    "^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
  )

  fun isLedgerGeneration(value: String): Boolean = ledgerGenerationPattern.matches(value)

  fun isInstallationId(value: String): Boolean = installationIdPattern.matches(value)

  fun isSha256(value: String): Boolean = sha256Pattern.matches(value)

  fun isOpaqueKey(value: String): Boolean = opaqueKeyPattern.matches(value)

  fun isUuid(value: String): Boolean = uuidPattern.matches(value)

  fun isUuidV4(value: String): Boolean = uuidV4Pattern.matches(value)

  fun isPositiveVersion(value: Int): Boolean = value > 0

  fun isSafePositiveLong(value: Long): Boolean = value in 1..MAX_SAFE_JSON_INTEGER

  fun isPrehashAliases(value: List<String>): Boolean =
    value.size in 1..2 && value.all(::isSha256)

  fun validateRegistration(
    ledgerGeneration: String,
    installationId: String,
    appBuildNumber: Int,
    policyVersion: Int,
  ): RequestInvalidReason? = when {
    !isLedgerGeneration(ledgerGeneration) -> RequestInvalidReason.LEDGER_GENERATION
    !isInstallationId(installationId) -> RequestInvalidReason.INSTALLATION_ID
    !isPositiveVersion(appBuildNumber) -> RequestInvalidReason.APP_BUILD_NUMBER
    !isPositiveVersion(policyVersion) -> RequestInvalidReason.POLICY_VERSION
    else -> null
  }

  fun validateBinding(
    ledgerGeneration: String,
    installationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
    appBuildNumber: Int,
    policyVersion: Int,
  ): RequestInvalidReason? = validateRegistration(
    ledgerGeneration,
    installationId,
    appBuildNumber,
    policyVersion,
  ) ?: when {
    !isSafePositiveLong(senderEpoch) -> RequestInvalidReason.SENDER_EPOCH
    !isSafePositiveLong(resetGeneration) -> RequestInvalidReason.RESET_GENERATION
    else -> null
  }
}
