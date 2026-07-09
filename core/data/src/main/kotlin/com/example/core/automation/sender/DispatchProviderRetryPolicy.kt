package com.example.core.automation.sender

import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.domain.model.dispatch.DispatchAttemptResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

data class ProviderDispatchFailure(
    val result: DispatchAttemptResult,
    val errorType: String,
    val errorCode: String?,
    val redactedErrorMessage: String,
    val nextRetryDelayMs: Long?,
) {
    val isRetryable: Boolean
        get() = result == DispatchAttemptResult.FAILED_RETRYABLE
}

object DispatchProviderRetryPolicy {
    const val DEFAULT_RETRY_DELAY_MS: Long = 15 * 60 * 1000L
    const val MAX_AUTOMATIC_RETRY_FAILURES: Int = 3

    const val ERROR_NO_DELIVERY_ROUTE = "NO_DELIVERY_ROUTE"
    const val ERROR_DISPATCH_FAILURE = "DISPATCH_FAILURE"
    const val ERROR_SMS_PERMISSION_DENIED = "SMS_PERMISSION_DENIED"
    const val ERROR_SMS_TRANSIENT_PROVIDER_FAILURE = "SMS_TRANSIENT_PROVIDER_FAILURE"
    const val ERROR_WHATSAPP_CONSENT_REQUIRED = "WHATSAPP_CONSENT_REQUIRED"
    const val ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE = "WHATSAPP_AUTOMATION_UNAVAILABLE"
    const val ERROR_WHATSAPP_AUTOMATION_FAILURE = "WHATSAPP_AUTOMATION_FAILURE"
    const val ERROR_EMAIL_INVALID_ADDRESS = "EMAIL_INVALID_ADDRESS"
    const val ERROR_EMAIL_AUTHENTICATION_FAILED = "EMAIL_AUTHENTICATION_FAILED"
    const val ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE = "EMAIL_TRANSIENT_PROVIDER_FAILURE"

    fun noDeliveryRoute(): ProviderDispatchFailure {
        return finalFailure(
            errorType = ERROR_NO_DELIVERY_ROUTE,
            errorCode = null,
            redactedErrorMessage = "All automatic delivery routes failed.",
        )
    }

    fun dispatchFailure(): ProviderDispatchFailure {
        return finalFailure(
            errorType = ERROR_DISPATCH_FAILURE,
            errorCode = null,
            redactedErrorMessage = "All automatic delivery routes failed.",
        )
    }

    fun smsPermissionDenied(): ProviderDispatchFailure {
        return finalFailure(
            errorType = ERROR_SMS_PERMISSION_DENIED,
            errorCode = CODE_ANDROID_SEND_SMS_PERMISSION,
            redactedErrorMessage = "SMS permission is missing; automatic SMS cannot be sent.",
        )
    }

    fun smsProviderException(throwable: Throwable): ProviderDispatchFailure {
        if (throwable is SecurityException) return smsPermissionDenied()
        return retryableFailure(
            errorType = ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            errorCode = throwable.providerCode(),
            redactedErrorMessage = "SMS provider failed before delivery confirmation; retry is allowed.",
        )
    }

    fun whatsAppConsentRequired(): ProviderDispatchFailure {
        return finalFailure(
            errorType = ERROR_WHATSAPP_CONSENT_REQUIRED,
            errorCode = CODE_WHATSAPP_CONSENT_NOT_GRANTED,
            redactedErrorMessage = "WhatsApp automation consent has not been confirmed in AI Doctor.",
        )
    }

    fun whatsAppAutomationUnavailable(): ProviderDispatchFailure {
        return whatsAppAutomationFailure(WhatsAppSendFailureReason.SERVICE_DISABLED)
    }

    fun whatsAppAutomationFailure(reason: WhatsAppSendFailureReason): ProviderDispatchFailure {
        return finalFailure(
            errorType = reason.providerErrorType(),
            errorCode = reason.providerErrorCode(),
            redactedErrorMessage = reason.redactedProviderMessage(),
        )
    }

    fun emailProviderException(throwable: Throwable): ProviderDispatchFailure {
        throwable.findProviderCause { it is EmailAddressValidationException }
            ?.let { cause ->
                val validation = cause as EmailAddressValidationException
                return finalFailure(
                    errorType = ERROR_EMAIL_INVALID_ADDRESS,
                    errorCode = when (validation.field) {
                        EmailAddressField.SENDER -> "EMAIL_INVALID_SENDER_ADDRESS"
                        EmailAddressField.RECIPIENT -> "EMAIL_INVALID_RECIPIENT_ADDRESS"
                    },
                    redactedErrorMessage = when (validation.field) {
                        EmailAddressField.SENDER ->
                            "Configured sender email address is invalid; setup must be reviewed."
                        EmailAddressField.RECIPIENT ->
                            "Contact email address is invalid; update the contact before retry."
                    },
                )
            }

        if (throwable.containsProviderCause<AuthenticationFailedException>()) {
            return finalFailure(
                errorType = ERROR_EMAIL_AUTHENTICATION_FAILED,
                errorCode = CODE_SMTP_AUTHENTICATION_FAILED,
                redactedErrorMessage = "Email provider rejected configured credentials; setup must be reviewed.",
            )
        }

        val isTransientProviderFailure = throwable.containsProviderCause<MessagingException>() ||
            throwable.containsProviderCause<SocketTimeoutException>() ||
            throwable.containsProviderCause<UnknownHostException>() ||
            throwable.containsProviderCause<IOException>()

        return retryableFailure(
            errorType = ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE,
            errorCode = throwable.providerCode(),
            redactedErrorMessage = if (isTransientProviderFailure) {
                "Email provider failed before accepting the message; retry is allowed."
            } else {
                "Email send failed before delivery confirmation; retry is allowed."
            },
        )
    }

    fun select(current: ProviderDispatchFailure?, candidate: ProviderDispatchFailure): ProviderDispatchFailure {
        return when {
            current == null -> candidate
            current.isRetryable && !candidate.isRetryable -> current
            !current.isRetryable && candidate.isRetryable -> candidate
            else -> candidate
        }
    }

    fun applyAutomaticRetryLimit(
        failure: ProviderDispatchFailure,
        retryCount: Int,
    ): ProviderDispatchFailure {
        if (!failure.isRetryable || retryCount < MAX_AUTOMATIC_RETRY_FAILURES) {
            return failure
        }
        return failure.copy(
            result = DispatchAttemptResult.FAILED_FINAL,
            redactedErrorMessage = "Automatic retry limit reached after $retryCount retryable failures. Last failure: ${failure.redactedErrorMessage}",
            nextRetryDelayMs = null,
        )
    }

    private fun retryableFailure(
        errorType: String,
        errorCode: String?,
        redactedErrorMessage: String,
    ): ProviderDispatchFailure {
        return ProviderDispatchFailure(
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            errorType = errorType,
            errorCode = errorCode,
            redactedErrorMessage = redactedErrorMessage,
            nextRetryDelayMs = DEFAULT_RETRY_DELAY_MS,
        )
    }

    private fun finalFailure(
        errorType: String,
        errorCode: String?,
        redactedErrorMessage: String,
    ): ProviderDispatchFailure {
        return ProviderDispatchFailure(
            result = DispatchAttemptResult.FAILED_FINAL,
            errorType = errorType,
            errorCode = errorCode,
            redactedErrorMessage = redactedErrorMessage,
            nextRetryDelayMs = null,
        )
    }
}
