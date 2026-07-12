package com.yashsomani.birthdayautopilot.automation.sms

import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.coordination.CoordinationCallResult
import com.yashsomani.birthdayautopilot.coordination.CoordinationRequestFactory
import com.yashsomani.birthdayautopilot.coordination.CoordinationRuntimeHandle
import com.yashsomani.birthdayautopilot.coordination.DistributionChannel
import com.yashsomani.birthdayautopilot.coordination.RequestBuildResult
import com.yashsomani.birthdayautopilot.coordination.RetryOutcome
import com.yashsomani.birthdayautopilot.coordination.RetryProof
import com.yashsomani.birthdayautopilot.coordination.TestReportOutcome
import com.yashsomani.birthdayautopilot.coordination.TestReportResult
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import java.nio.charset.StandardCharsets
import java.util.UUID

internal sealed interface SmsOutcomeCoordinationCall<out Value> {
  data class Authoritative<Value>(val value: Value) : SmsOutcomeCoordinationCall<Value>
  data class Unavailable(val safeCode: String) : SmsOutcomeCoordinationCall<Nothing>
}

internal interface SmsOutcomeCoordinationPort {
  suspend fun authorizeRetry(
    permit: CoordinationPermitEntity,
    proof: RetryProof,
  ): SmsOutcomeCoordinationCall<RetryOutcome>

  suspend fun reportTest(
    permit: CoordinationPermitEntity,
    result: TestReportResult,
  ): SmsOutcomeCoordinationCall<TestReportOutcome>
}

internal class FirebaseSmsOutcomeCoordinationPort(
  private val runtime: CoordinationRuntimeHandle,
) : SmsOutcomeCoordinationPort {
  override suspend fun authorizeRetry(
    permit: CoordinationPermitEntity,
    proof: RetryProof,
  ): SmsOutcomeCoordinationCall<RetryOutcome> = withBinding(permit) { binding ->
    when (val request = CoordinationRequestFactory.retry(
      binding,
      permit.opaqueClaimId,
      SmsRetryRequestIdentity.forPermit(permit.permitId),
      proof,
    )) {
      is RequestBuildResult.Invalid -> invalid("RETRY_${request.reason.name}")
      is RequestBuildResult.Ready -> call { it.authorizeSafeRetry(request.request) }
    }
  }

  override suspend fun reportTest(
    permit: CoordinationPermitEntity,
    result: TestReportResult,
  ): SmsOutcomeCoordinationCall<TestReportOutcome> = withBinding(permit) { binding ->
    val armRequestId = permit.armRequestId ?: return@withBinding invalid("TEST_ARM_REQUEST_MISSING")
    when (val request = CoordinationRequestFactory.testReport(
      binding,
      permit.opaqueClaimId,
      armRequestId,
      result,
    )) {
      is RequestBuildResult.Invalid -> invalid("TEST_REPORT_${request.reason.name}")
      is RequestBuildResult.Ready -> call { it.reportTestOutcome(request.request) }
    }
  }

  private suspend fun <T> withBinding(
    permit: CoordinationPermitEntity,
    block: suspend (com.yashsomani.birthdayautopilot.coordination.CoordinationBinding) ->
      SmsOutcomeCoordinationCall<T>,
  ): SmsOutcomeCoordinationCall<T> = when (val result = CoordinationRequestFactory.binding(
    LEDGER_GENERATION,
    permit.installationId,
    permit.senderEpoch,
    permit.resetGeneration,
    BuildConfig.VERSION_CODE,
    COORDINATION_POLICY_VERSION,
    distributionChannel(),
  )) {
    is RequestBuildResult.Invalid -> invalid("BINDING_${result.reason.name}")
    is RequestBuildResult.Ready -> block(result.request)
  }

  private suspend fun <T> call(
    block: suspend (com.yashsomani.birthdayautopilot.coordination.FirebaseCoordinationClient) ->
      CoordinationCallResult<T>,
  ): SmsOutcomeCoordinationCall<T> {
    val client = runtime.clientOrNull()
      ?: return SmsOutcomeCoordinationCall.Unavailable("COORDINATION_CONFIGURATION_MISSING")
    return when (val call = block(client)) {
      is CoordinationCallResult.Authoritative -> SmsOutcomeCoordinationCall.Authoritative(call.outcome)
      is CoordinationCallResult.Unavailable -> SmsOutcomeCoordinationCall.Unavailable(
        "COORDINATION_${call.reason.name}",
      )
    }
  }

  private fun distributionChannel(): DistributionChannel = when (BuildConfig.APP_ENV) {
    "dev" -> DistributionChannel.DEV
    "staging" -> DistributionChannel.STAGING
    "lab" -> DistributionChannel.RESTRICTED_LAB
    "prod" -> if (BuildConfig.APPROVED_DISTRIBUTION_CHANNEL == "google-play") {
      DistributionChannel.PLAY
    } else {
      DistributionChannel.DIRECT_MANAGED
    }
    else -> DistributionChannel.DEV
  }

  private fun invalid(code: String) = SmsOutcomeCoordinationCall.Unavailable(code)

  private companion object {
    const val LEDGER_GENERATION = "birthday-ledger-v1"
    const val COORDINATION_POLICY_VERSION = 1
  }
}

internal object SmsRetryRequestIdentity {
  fun forPermit(permitId: String): String {
    require(PERMIT_ID.matches(permitId)) { "retry-permit-id-invalid" }
    return UUID.nameUUIDFromBytes(
      "BirthdayAutopilot.RetryRequest.v1|$permitId".toByteArray(StandardCharsets.US_ASCII),
    ).toString()
  }

  private val PERMIT_ID = Regex("^[A-Za-z0-9._-]{1,128}$")
}
