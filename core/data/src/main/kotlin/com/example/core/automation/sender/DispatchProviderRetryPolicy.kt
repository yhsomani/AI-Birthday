package com.example.core.automation.sender

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

    const val ERROR_NO_DELIVERY_ROUTE = "NO_DELIVERY_ROUTE"
    const val ERROR_DISPATCH_FAILURE = "DISPATCH_FAILURE"
    const val ERROR_SMS_PERMISSION_DENIED = "SMS_PERMISSION_DENIED"
    const val ERROR_SMS_TRANSIENT_PROVIDER_FAILURE = "SMS_TRANSIENT_PROVIDER_FAILURE"
    const val ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE = "WHATSAPP_AUTOMATION_UNAVAILABLE"
    const val ERROR_EMAIL_AUTHENTICATION_FAILED = "EMAIL_AUTHENTICATION_FAILED"
    const val ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE = "EMAIL_TRANSIENT_PROVIDER_FAILURE"

    private const val CODE_ANDROID_SEND_SMS_PERMISSION = "ANDROID_SEND_SMS_PERMISSION"
    private const val CODE_ACCESSIBILITY_AUTOMATION_UNAVAILABLE = "ACCESSIBILITY_AUTOMATION_UNAVAILABLE"
    private const val CODE_SMTP_AUTHENTICATION_FAILED = "SMTP_AUTHENTICATION_FAILED"

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

    fun whatsAppAutomationUnavailable(): ProviderDispatchFailure {
        return finalFailure(
            errorType = ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE,
            errorCode = CODE_ACCESSIBILITY_AUTOMATION_UNAVAILABLE,
            redactedErrorMessage = "WhatsApp automation was unavailable; setup must be reviewed before retry.",
        )
    }

    fun emailProviderException(throwable: Throwable): ProviderDispatchFailure {
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

    private fun Throwable.providerCode(): String {
        return this.findProviderCause { it !is MessagingException }?.javaClass?.simpleName
            ?: javaClass.simpleName
            ?: javaClass.name.substringAfterLast('.')
    }

    private inline fun <reified T : Throwable> Throwable.containsProviderCause(): Boolean {
        return findProviderCause { it is T } != null
    }

    private fun Throwable.findProviderCause(predicate: (Throwable) -> Boolean): Throwable? {
        if (predicate(this)) return this
        cause?.findProviderCause(predicate)?.let { return it }
        return (this as? MessagingException)?.nextException?.findProviderCause(predicate)
    }
}
