package com.yashsomani.birthdayautopilot.coordination

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableOptions
import com.yashsomani.birthdayautopilot.auth.AndroidIdentityConfigurationResolver
import com.yashsomani.birthdayautopilot.auth.FirebaseAccountBindingProvider
import com.yashsomani.birthdayautopilot.auth.FirebaseAppCheckGate
import com.yashsomani.birthdayautopilot.auth.IdentityConfigurationResult
import com.yashsomani.birthdayautopilot.auth.TaskResult
import com.yashsomani.birthdayautopilot.auth.awaitSanitized
import java.util.concurrent.TimeUnit

internal object CoordinationEndpointPolicy {
  const val REGION = "asia-south1"
  const val CALL_TIMEOUT_SECONDS = 35L
  const val LIMITED_USE_APP_CHECK_TOKENS = true

  const val REGISTER = "registerAndroidInstallation"
  const val RENEW_LEASE = "renewSenderLease"
  const val CHANGE_MODE = "changeAccountMode"
  const val CLAIM_OCCURRENCE = "claimOccurrence"
  const val CLAIM_TEST = "claimTest"
  const val ARM_ATTEMPT = "armAttempt"
  const val GET_ARM_STATUS = "getArmStatus"
  const val REPORT_TEST = "reportTestOutcome"
  const val AUTHORIZE_RETRY = "authorizeSafeRetry"
  const val COMPANION_STATUS = "companionStatus"
  const val BEGIN_SENDER_TRANSFER = "beginSenderTransfer"
  const val COMPLETE_SENDER_TRANSFER = "completeSenderTransfer"
  const val REQUEST_ACCOUNT_DELETION = "requestAccountDeletion"
  const val ACCOUNT_DELETION_RECEIPT = "accountDeletionReceipt"
  const val RESET_CONTACT_DERIVED_STATE = "resetContactDerivedState"
  const val RELEASE_ANDROID_SENDER = "releaseAndroidSender"
  const val COORDINATION_LIFECYCLE_STATUS = "coordinationLifecycleStatus"

  val allowedFunctionNames: Set<String> = setOf(
    REGISTER,
    RENEW_LEASE,
    CHANGE_MODE,
    CLAIM_OCCURRENCE,
    CLAIM_TEST,
    ARM_ATTEMPT,
    GET_ARM_STATUS,
    REPORT_TEST,
    AUTHORIZE_RETRY,
    COMPANION_STATUS,
    BEGIN_SENDER_TRANSFER,
    COMPLETE_SENDER_TRANSFER,
    REQUEST_ACCOUNT_DELETION,
    ACCOUNT_DELETION_RECEIPT,
    RESET_CONTACT_DERIVED_STATE,
    RELEASE_ANDROID_SENDER,
    COORDINATION_LIFECYCLE_STATUS,
  )
}

internal sealed interface NativePreflightResult {
  data object Ready : NativePreflightResult

  data object NotAuthenticated : NativePreflightResult

  data object AccountMismatch : NativePreflightResult

  data object AppCheckUnavailable : NativePreflightResult
}

internal fun interface CoordinationPreflight {
  suspend fun verify(): NativePreflightResult
}

internal fun interface AppCheckOnlyPreflight {
  suspend fun verify(): Boolean
}

internal class FirebaseAppCheckOnlyPreflight(
  private val app: FirebaseApp,
  private val appCheckGate: FirebaseAppCheckGate = FirebaseAppCheckGate(),
) : AppCheckOnlyPreflight {
  override suspend fun verify(): Boolean = appCheckGate.attest(app)
}

internal class FirebaseNativeCoordinationPreflight(
  firebaseApp: FirebaseApp,
  private val accountBindingPredicate: NativeAccountBindingPredicate,
  private val appCheckGate: FirebaseAppCheckGate = FirebaseAppCheckGate(),
) : CoordinationPreflight {
  private val accountBinding = FirebaseAccountBindingProvider(firebaseApp)
  private val app = firebaseApp

  override suspend fun verify(): NativePreflightResult {
    val before = accountBinding.current() ?: return NativePreflightResult.NotAuthenticated
    if (!accountBindingPredicate.matches(before)) return NativePreflightResult.AccountMismatch
    if (!appCheckGate.attest(app)) return NativePreflightResult.AppCheckUnavailable
    val after = accountBinding.current() ?: return NativePreflightResult.NotAuthenticated
    return if (
      before.firebaseUid == after.firebaseUid &&
      before.googleSubject == after.googleSubject &&
      accountBindingPredicate.matches(after)
    ) {
      NativePreflightResult.Ready
    } else {
      NativePreflightResult.AccountMismatch
    }
  }

  override fun toString(): String = "FirebaseNativeCoordinationPreflight(<redacted>)"
}

internal sealed interface CallableTransportResult {
  data class Response(val value: Any?) : CallableTransportResult {
    override fun toString(): String = "CallableTransportResult.Response(<redacted>)"
  }

  data object AmbiguousFailure : CallableTransportResult
}

internal fun interface CoordinationCallableTransport {
  suspend fun call(functionName: String, payload: Map<String, Any>): CallableTransportResult
}

enum class CoordinationSessionStatus {
  SESSION_READY,
  TIER_CONFIGURATION_MISSING,
  SIGN_IN_REQUIRED,
  ACCOUNT_MISMATCH,
  APP_CHECK_UNAVAILABLE,
  NATIVE_PREFLIGHT_UNAVAILABLE,
}

/**
 * Native-only readiness handle for AppGraph and readiness projections. SESSION_READY proves a
 * currently bound Firebase Google session plus an App Check attestation; it deliberately does not
 * claim network reachability or backend deployment. Every operation repeats preflight immediately
 * before its single callable invocation.
 */
internal interface CoordinationRuntimeHandle {
  suspend fun sessionStatus(): CoordinationSessionStatus

  fun clientOrNull(): FirebaseCoordinationClient?
}

internal object FirebaseCoordinationRuntime {
  fun resolve(
    context: Context,
    environment: String,
    accountBindingPredicate: NativeAccountBindingPredicate,
  ): CoordinationRuntimeHandle = try {
    when (
      val configuration = AndroidIdentityConfigurationResolver(
        context.applicationContext,
        environment,
      ).resolve()
    ) {
      IdentityConfigurationResult.Missing -> MissingCoordinationRuntime
      is IdentityConfigurationResult.Ready -> ConfiguredCoordinationRuntime(
        FirebaseNativeCoordinationPreflight(
          configuration.configuration.firebaseApp,
          accountBindingPredicate,
        ),
        FirebaseCallableTransport(configuration.configuration.firebaseApp),
        FirebaseAppCheckOnlyPreflight(configuration.configuration.firebaseApp),
      )
    }
  } catch (_: Exception) {
    UnavailableCoordinationRuntime
  }
}

internal data object MissingCoordinationRuntime : CoordinationRuntimeHandle {
  override suspend fun sessionStatus(): CoordinationSessionStatus =
    CoordinationSessionStatus.TIER_CONFIGURATION_MISSING

  override fun clientOrNull(): FirebaseCoordinationClient? = null
}

internal data object UnavailableCoordinationRuntime : CoordinationRuntimeHandle {
  override suspend fun sessionStatus(): CoordinationSessionStatus =
    CoordinationSessionStatus.NATIVE_PREFLIGHT_UNAVAILABLE

  override fun clientOrNull(): FirebaseCoordinationClient? = null
}

internal class ConfiguredCoordinationRuntime(
  private val preflight: CoordinationPreflight,
  transport: CoordinationCallableTransport,
  appCheckOnlyPreflight: AppCheckOnlyPreflight = AppCheckOnlyPreflight {
    preflight.verify() == NativePreflightResult.Ready
  },
) : CoordinationRuntimeHandle {
  private val client = FirebaseCoordinationClient(preflight, transport, appCheckOnlyPreflight)

  override suspend fun sessionStatus(): CoordinationSessionStatus = try {
    when (preflight.verify()) {
      NativePreflightResult.Ready -> CoordinationSessionStatus.SESSION_READY
      NativePreflightResult.NotAuthenticated -> CoordinationSessionStatus.SIGN_IN_REQUIRED
      NativePreflightResult.AccountMismatch -> CoordinationSessionStatus.ACCOUNT_MISMATCH
      NativePreflightResult.AppCheckUnavailable -> CoordinationSessionStatus.APP_CHECK_UNAVAILABLE
    }
  } catch (_: Exception) {
    CoordinationSessionStatus.NATIVE_PREFLIGHT_UNAVAILABLE
  }

  override fun clientOrNull(): FirebaseCoordinationClient = client

  override fun toString(): String = "ConfiguredCoordinationRuntime(<redacted>)"
}

/**
 * Owns the Firebase SDK objects. Auth and limited-use App Check tokens are attached by the SDK;
 * neither token is returned to the caller or represented in a loggable object.
 */
internal class FirebaseCallableTransport(
  firebaseApp: FirebaseApp,
) : CoordinationCallableTransport {
  private val functions = FirebaseFunctions.getInstance(firebaseApp, CoordinationEndpointPolicy.REGION)
  private val callableOptions = HttpsCallableOptions.Builder()
    .setLimitedUseAppCheckTokens(CoordinationEndpointPolicy.LIMITED_USE_APP_CHECK_TOKENS)
    .build()

  override suspend fun call(
    functionName: String,
    payload: Map<String, Any>,
  ): CallableTransportResult {
    if (functionName !in CoordinationEndpointPolicy.allowedFunctionNames) {
      return CallableTransportResult.AmbiguousFailure
    }
    return try {
      val task = functions
        .getHttpsCallable(functionName, callableOptions)
        .withTimeout(CoordinationEndpointPolicy.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .call(payload)
      when (val result = task.awaitSanitized()) {
        is TaskResult.Success -> CallableTransportResult.Response(result.value.data)
        is TaskResult.Failure -> CallableTransportResult.AmbiguousFailure
      }
    } catch (_: Exception) {
      CallableTransportResult.AmbiguousFailure
    }
  }

  override fun toString(): String = "FirebaseCallableTransport(<redacted>)"
}

internal class FirebaseCoordinationClient(
  private val preflight: CoordinationPreflight,
  private val transport: CoordinationCallableTransport,
  private val appCheckOnlyPreflight: AppCheckOnlyPreflight = AppCheckOnlyPreflight {
    preflight.verify() == NativePreflightResult.Ready
  },
) {
  suspend fun register(
    request: RegistrationRequest,
  ): CoordinationCallResult<RegistrationOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.registration(it, request) }

  suspend fun renewLease(
    request: LeaseRequest,
  ): CoordinationCallResult<LeaseOutcome> = execute(
    request.functionName,
    request,
    CoordinationResponseParser::lease,
  )

  suspend fun changeAccountMode(
    request: AccountModeRequest,
  ): CoordinationCallResult<AccountModeOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.accountMode(it, request) }

  suspend fun claim(
    request: ClaimRequest<*>,
  ): CoordinationCallResult<ClaimOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.claim(it, request) }

  /** This is the only dispatch of this Arm request. Callers must persist ambiguity and reconcile. */
  suspend fun arm(
    request: ArmRequest,
  ): CoordinationCallResult<ArmDecisionOutcome> = execute(
    CoordinationEndpointPolicy.ARM_ATTEMPT,
    request,
  ) { CoordinationResponseParser.arm(it, request) }

  /** Query-only reconciliation; it never redispatches armAttempt. */
  suspend fun getArmStatus(
    request: ArmRequest,
  ): CoordinationCallResult<ArmStatusOutcome> = execute(
    CoordinationEndpointPolicy.GET_ARM_STATUS,
    request,
  ) { CoordinationResponseParser.armStatus(it, request) }

  suspend fun authorizeSafeRetry(
    request: RetryRequest,
  ): CoordinationCallResult<RetryOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.retry(it, request) }

  suspend fun reportTestOutcome(
    request: TestReportRequest,
  ): CoordinationCallResult<TestReportOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.testReport(it, request) }

  suspend fun companionStatus(
    request: CompanionStatusRequest,
  ): CoordinationCallResult<CompanionStatusOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.companionStatus(it, request) }

  suspend fun beginSenderTransfer(
    request: SenderTransferRequest,
  ): CoordinationCallResult<SenderTransferOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.senderTransfer(it, request, completing = false) }

  suspend fun completeSenderTransfer(
    request: SenderTransferRequest,
  ): CoordinationCallResult<SenderTransferOutcome> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.senderTransfer(it, request, completing = true) }

  suspend fun requestAccountDeletion(
    request: AccountDeletionRequest,
  ): CoordinationCallResult<AccountDeletionAcceptance> = execute(
    request.functionName,
    request,
  ) { CoordinationResponseParser.accountDeletion(it, request) }

  suspend fun accountDeletionReceipt(
    request: AccountDeletionReceiptRequest,
  ): CoordinationCallResult<AccountDeletionReceiptOutcome> = executeAppCheckOnly(
    request.functionName,
    request,
    CoordinationResponseParser::accountDeletionReceipt,
  )

  suspend fun resetContactDerivedState(
    request: ContactDerivedResetRequest,
  ): CoordinationCallResult<CoordinationOperationOutcome> = execute(
    request.functionName,
    request,
    CoordinationResponseParser::contactDerivedReset,
  )

  suspend fun releaseAndroidSender(
    request: SenderReleaseRequest,
  ): CoordinationCallResult<CoordinationOperationOutcome> = execute(
    request.functionName,
    request,
    CoordinationResponseParser::senderRelease,
  )

  suspend fun coordinationLifecycleStatus(
    request: CoordinationLifecycleStatusRequest,
  ): CoordinationCallResult<CoordinationLifecycleStatusOutcome> = execute(
    request.functionName,
    request,
    CoordinationResponseParser::coordinationLifecycleStatus,
  )

  private suspend fun <T> execute(
    functionName: String,
    request: PreparedCoordinationRequest<*>,
    parser: (Any?) -> T?,
  ): CoordinationCallResult<T> {
    val preflightResult = try {
      preflight.verify()
    } catch (_: Exception) {
      return CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.NATIVE_PREFLIGHT_UNAVAILABLE,
      )
    }
    when (preflightResult) {
      NativePreflightResult.NotAuthenticated -> return CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.NOT_AUTHENTICATED,
      )
      NativePreflightResult.AccountMismatch -> return CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.ACCOUNT_MISMATCH,
      )
      NativePreflightResult.AppCheckUnavailable -> return CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.APP_CHECK_UNAVAILABLE,
      )
      NativePreflightResult.Ready -> Unit
    }
    val result = try {
      transport.call(functionName, request.callablePayload())
    } catch (_: Exception) {
      CallableTransportResult.AmbiguousFailure
    }
    return when (result) {
      CallableTransportResult.AmbiguousFailure -> CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.AMBIGUOUS_CALL,
      )
      is CallableTransportResult.Response -> parser(result.value)?.let {
        CoordinationCallResult.Authoritative(it)
      } ?: CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.INVALID_SERVER_RESPONSE,
      )
    }
  }

  private suspend fun <T> executeAppCheckOnly(
    functionName: String,
    request: PreparedCoordinationRequest<*>,
    parser: (Any?) -> T?,
  ): CoordinationCallResult<T> {
    val appChecked = try {
      appCheckOnlyPreflight.verify()
    } catch (_: Exception) {
      false
    }
    if (!appChecked) {
      return CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.APP_CHECK_UNAVAILABLE,
      )
    }
    val result = try {
      transport.call(functionName, request.callablePayload())
    } catch (_: Exception) {
      CallableTransportResult.AmbiguousFailure
    }
    return when (result) {
      CallableTransportResult.AmbiguousFailure -> CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.AMBIGUOUS_CALL,
      )
      is CallableTransportResult.Response -> parser(result.value)?.let {
        CoordinationCallResult.Authoritative(it)
      } ?: CoordinationCallResult.Unavailable(
        CoordinationUnavailableReason.INVALID_SERVER_RESPONSE,
      )
    }
  }

  override fun toString(): String = "FirebaseCoordinationClient(<redacted>)"
}
