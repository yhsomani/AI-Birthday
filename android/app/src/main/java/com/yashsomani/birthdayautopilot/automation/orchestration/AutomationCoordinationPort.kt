package com.yashsomani.birthdayautopilot.automation.orchestration

import com.yashsomani.birthdayautopilot.coordination.ArmDecisionOutcome
import com.yashsomani.birthdayautopilot.coordination.ArmStatusOutcome
import com.yashsomani.birthdayautopilot.coordination.AccountModeAction
import com.yashsomani.birthdayautopilot.coordination.AccountModeOutcome
import com.yashsomani.birthdayautopilot.coordination.ClaimOutcome
import com.yashsomani.birthdayautopilot.coordination.CoordinationCallResult
import com.yashsomani.birthdayautopilot.coordination.CoordinationPurpose
import com.yashsomani.birthdayautopilot.coordination.CoordinationRequestFactory
import com.yashsomani.birthdayautopilot.coordination.CoordinationRuntimeHandle
import com.yashsomani.birthdayautopilot.coordination.DistributionChannel
import com.yashsomani.birthdayautopilot.coordination.LeaseOutcome
import com.yashsomani.birthdayautopilot.coordination.RegistrationOutcome
import com.yashsomani.birthdayautopilot.coordination.SenderTransferOutcome
import com.yashsomani.birthdayautopilot.coordination.AccountDeletionAcceptance
import com.yashsomani.birthdayautopilot.coordination.AccountDeletionReceiptOutcome
import com.yashsomani.birthdayautopilot.coordination.CoordinationLifecycleStatusOutcome
import com.yashsomani.birthdayautopilot.coordination.CoordinationOperationOutcome
import com.yashsomani.birthdayautopilot.coordination.RequestBuildResult

internal data class CoordinationBindingSpec(
  val ledgerGeneration: String,
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
  val appBuildNumber: Int,
  val policyVersion: Int,
  val distributionChannel: DistributionChannel,
)

internal data class RegistrationSpec(
  val ledgerGeneration: String,
  val installationId: String,
  val appBuildNumber: Int,
  val policyVersion: Int,
  val distributionChannel: DistributionChannel,
)

internal data class BirthdayClaimSpec(
  val binding: CoordinationBindingSpec,
  val claimRequestId: String,
  val recipientPrehashAliases: List<String>,
  val destinationPrehashAliases: List<String>,
)

internal data class TestClaimSpec(
  val binding: CoordinationBindingSpec,
  val testRequestId: String,
  val configurationPrehash: String,
  val destinationPrehash: String,
)

internal data class ArmSpec(
  val binding: CoordinationBindingSpec,
  val purpose: CoordinationPurpose,
  val claimId: String,
  val armRequestId: String,
  val attempt: Int,
)

internal data class AccountModeSpec(
  val binding: CoordinationBindingSpec,
  val action: AccountModeAction,
  val testClaimId: String? = null,
  val boundTestReceiptPrehash: String? = null,
  val readinessContractVersion: Int? = null,
)

internal data class SenderTransferSpec(
  val activeBinding: CoordinationBindingSpec,
  val targetInstallationId: String,
)

internal data class SenderReleaseSpec(
  val requestId: String,
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
)

internal sealed interface OrchestrationCall<out Value> {
  data class Authoritative<Value>(val value: Value) : OrchestrationCall<Value>
  data class Unavailable(val safeCode: String) : OrchestrationCall<Nothing>
}

internal interface AutomationCoordinationPort {
  suspend fun register(spec: RegistrationSpec): OrchestrationCall<RegistrationOutcome>
  suspend fun renewLease(
    binding: CoordinationBindingSpec,
    purpose: CoordinationPurpose,
  ): OrchestrationCall<LeaseOutcome>
  suspend fun claimBirthday(spec: BirthdayClaimSpec): OrchestrationCall<ClaimOutcome>
  suspend fun claimTest(spec: TestClaimSpec): OrchestrationCall<ClaimOutcome>
  suspend fun arm(spec: ArmSpec): OrchestrationCall<ArmDecisionOutcome>
  suspend fun getArmStatus(spec: ArmSpec): OrchestrationCall<ArmStatusOutcome>
  suspend fun changeAccountMode(spec: AccountModeSpec): OrchestrationCall<AccountModeOutcome>
  suspend fun beginSenderTransfer(
    spec: SenderTransferSpec,
  ): OrchestrationCall<SenderTransferOutcome>
  suspend fun completeSenderTransfer(
    spec: SenderTransferSpec,
  ): OrchestrationCall<SenderTransferOutcome>
  suspend fun requestAccountDeletion(
    requestId: String,
  ): OrchestrationCall<AccountDeletionAcceptance>
  suspend fun accountDeletionReceipt(
    receiptId: String,
  ): OrchestrationCall<AccountDeletionReceiptOutcome>
  suspend fun resetContactDerivedState(
    requestId: String,
  ): OrchestrationCall<CoordinationOperationOutcome>
  suspend fun releaseAndroidSender(
    spec: SenderReleaseSpec,
  ): OrchestrationCall<CoordinationOperationOutcome>
  suspend fun coordinationLifecycleStatus(): OrchestrationCall<CoordinationLifecycleStatusOutcome>
}

internal class FirebaseAutomationCoordinationPort(
  private val runtime: CoordinationRuntimeHandle,
) : AutomationCoordinationPort {
  override suspend fun register(spec: RegistrationSpec): OrchestrationCall<RegistrationOutcome> =
    when (val request = CoordinationRequestFactory.registration(
      spec.ledgerGeneration,
      spec.installationId,
      spec.appBuildNumber,
      spec.policyVersion,
      spec.distributionChannel,
    )) {
      is RequestBuildResult.Invalid -> invalid("REGISTRATION", request.reason.name)
      is RequestBuildResult.Ready -> call { it.register(request.request) }
    }

  override suspend fun renewLease(
    binding: CoordinationBindingSpec,
    purpose: CoordinationPurpose,
  ): OrchestrationCall<LeaseOutcome> = withBinding(binding) { built ->
    call { it.renewLease(CoordinationRequestFactory.lease(built, purpose)) }
  }

  override suspend fun claimBirthday(
    spec: BirthdayClaimSpec,
  ): OrchestrationCall<ClaimOutcome> = withBinding(spec.binding) { binding ->
    when (val request = CoordinationRequestFactory.birthdayClaim(
      binding,
      spec.claimRequestId,
      spec.recipientPrehashAliases,
      spec.destinationPrehashAliases,
    )) {
      is RequestBuildResult.Invalid -> invalid("BIRTHDAY_CLAIM", request.reason.name)
      is RequestBuildResult.Ready -> call { it.claim(request.request) }
    }
  }

  override suspend fun claimTest(spec: TestClaimSpec): OrchestrationCall<ClaimOutcome> =
    withBinding(spec.binding) { binding ->
      when (val request = CoordinationRequestFactory.testClaim(
        binding,
        spec.testRequestId,
        spec.configurationPrehash,
        spec.destinationPrehash,
      )) {
        is RequestBuildResult.Invalid -> invalid("TEST_CLAIM", request.reason.name)
        is RequestBuildResult.Ready -> call { it.claim(request.request) }
      }
    }

  override suspend fun arm(spec: ArmSpec): OrchestrationCall<ArmDecisionOutcome> =
    withArmRequest(spec) { client, request -> client.arm(request) }

  override suspend fun getArmStatus(spec: ArmSpec): OrchestrationCall<ArmStatusOutcome> =
    withArmRequest(spec) { client, request -> client.getArmStatus(request) }

  override suspend fun changeAccountMode(
    spec: AccountModeSpec,
  ): OrchestrationCall<AccountModeOutcome> = withBinding(spec.binding) { binding ->
    val request = when (spec.action) {
      AccountModeAction.PAUSE_FOR_REPAIR -> RequestBuildResult.Ready(
        CoordinationRequestFactory.pauseForRepair(binding),
      )
      AccountModeAction.ACTIVATE_AUTOMATION -> CoordinationRequestFactory.activateAutomation(
        binding = binding,
        testClaimId = spec.testClaimId.orEmpty(),
        boundTestReceiptPrehash = spec.boundTestReceiptPrehash.orEmpty(),
        readinessContractVersion = spec.readinessContractVersion ?: -1,
      )
    }
    when (request) {
      is RequestBuildResult.Invalid -> invalid("ACCOUNT_MODE", request.reason.name)
      is RequestBuildResult.Ready -> call { it.changeAccountMode(request.request) }
    }
  }

  override suspend fun beginSenderTransfer(
    spec: SenderTransferSpec,
  ): OrchestrationCall<SenderTransferOutcome> = withBinding(spec.activeBinding) { binding ->
    when (val request = CoordinationRequestFactory.beginSenderTransfer(
      binding,
      spec.targetInstallationId,
    )) {
      is RequestBuildResult.Invalid -> invalid("BEGIN_TRANSFER", request.reason.name)
      is RequestBuildResult.Ready -> call { it.beginSenderTransfer(request.request) }
    }
  }

  override suspend fun completeSenderTransfer(
    spec: SenderTransferSpec,
  ): OrchestrationCall<SenderTransferOutcome> = withBinding(spec.activeBinding) { binding ->
    when (val request = CoordinationRequestFactory.completeSenderTransfer(
      binding,
      spec.targetInstallationId,
    )) {
      is RequestBuildResult.Invalid -> invalid("COMPLETE_TRANSFER", request.reason.name)
      is RequestBuildResult.Ready -> call { it.completeSenderTransfer(request.request) }
    }
  }

  override suspend fun requestAccountDeletion(
    requestId: String,
  ): OrchestrationCall<AccountDeletionAcceptance> = when (
    val request = CoordinationRequestFactory.accountDeletion(requestId)
  ) {
    is RequestBuildResult.Invalid -> invalid("ACCOUNT_DELETION", request.reason.name)
    is RequestBuildResult.Ready -> call { it.requestAccountDeletion(request.request) }
  }

  override suspend fun accountDeletionReceipt(
    receiptId: String,
  ): OrchestrationCall<AccountDeletionReceiptOutcome> = when (
    val request = CoordinationRequestFactory.accountDeletionReceipt(receiptId)
  ) {
    is RequestBuildResult.Invalid -> invalid("ACCOUNT_DELETION_RECEIPT", request.reason.name)
    is RequestBuildResult.Ready -> call { it.accountDeletionReceipt(request.request) }
  }

  override suspend fun resetContactDerivedState(
    requestId: String,
  ): OrchestrationCall<CoordinationOperationOutcome> = when (
    val request = CoordinationRequestFactory.resetContactDerivedState(requestId)
  ) {
    is RequestBuildResult.Invalid -> invalid("CONTACT_DERIVED_RESET", request.reason.name)
    is RequestBuildResult.Ready -> call { it.resetContactDerivedState(request.request) }
  }

  override suspend fun releaseAndroidSender(
    spec: SenderReleaseSpec,
  ): OrchestrationCall<CoordinationOperationOutcome> = when (
    val request = CoordinationRequestFactory.releaseAndroidSender(
      requestId = spec.requestId,
      installationId = spec.installationId,
      senderEpoch = spec.senderEpoch,
      resetGeneration = spec.resetGeneration,
    )
  ) {
    is RequestBuildResult.Invalid -> invalid("SENDER_RELEASE", request.reason.name)
    is RequestBuildResult.Ready -> call { it.releaseAndroidSender(request.request) }
  }

  override suspend fun coordinationLifecycleStatus():
    OrchestrationCall<CoordinationLifecycleStatusOutcome> = call {
      it.coordinationLifecycleStatus(CoordinationRequestFactory.coordinationLifecycleStatus())
    }

  private suspend fun <T> withBinding(
    spec: CoordinationBindingSpec,
    block: suspend (com.yashsomani.birthdayautopilot.coordination.CoordinationBinding) ->
      OrchestrationCall<T>,
  ): OrchestrationCall<T> = when (val result = CoordinationRequestFactory.binding(
    spec.ledgerGeneration,
    spec.installationId,
    spec.senderEpoch,
    spec.resetGeneration,
    spec.appBuildNumber,
    spec.policyVersion,
    spec.distributionChannel,
  )) {
    is RequestBuildResult.Invalid -> invalid("BINDING", result.reason.name)
    is RequestBuildResult.Ready -> block(result.request)
  }

  private suspend fun <T> withArmRequest(
    spec: ArmSpec,
    block: suspend (
      com.yashsomani.birthdayautopilot.coordination.FirebaseCoordinationClient,
      com.yashsomani.birthdayautopilot.coordination.ArmRequest,
    ) -> CoordinationCallResult<T>,
  ): OrchestrationCall<T> = withBinding(spec.binding) { binding ->
    when (val request = CoordinationRequestFactory.arm(
      binding,
      spec.purpose,
      spec.claimId,
      spec.armRequestId,
      spec.attempt,
    )) {
      is RequestBuildResult.Invalid -> invalid("ARM", request.reason.name)
      is RequestBuildResult.Ready -> call { block(it, request.request) }
    }
  }

  private suspend fun <T> call(
    block: suspend (com.yashsomani.birthdayautopilot.coordination.FirebaseCoordinationClient) ->
      CoordinationCallResult<T>,
  ): OrchestrationCall<T> {
    val client = runtime.clientOrNull()
      ?: return OrchestrationCall.Unavailable("COORDINATION_TIER_CONFIGURATION_MISSING")
    return when (val result = block(client)) {
      is CoordinationCallResult.Authoritative -> OrchestrationCall.Authoritative(result.outcome)
      is CoordinationCallResult.Unavailable -> OrchestrationCall.Unavailable(
        "COORDINATION_${result.reason.name}",
      )
    }
  }

  private fun invalid(operation: String, reason: String): OrchestrationCall.Unavailable =
    OrchestrationCall.Unavailable("COORDINATION_${operation}_${reason}")
}
