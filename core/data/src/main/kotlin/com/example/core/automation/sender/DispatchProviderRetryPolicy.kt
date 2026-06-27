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

    const val ERROR_NO_DELIVERY_ROUTE = "NO_DELIVERY_ROUTE"
    const val ERROR_DISPATCH_FAILURE = "DISPATCH_FAILURE"
    const val ERROR_SMS_PERMISSION_DENIED = "SMS_PERMISSION_DENIED"
    const val ERROR_SMS_TRANSIENT_PROVIDER_FAILURE = "SMS_TRANSIENT_PROVIDER_FAILURE"
    const val ERROR_WHATSAPP_CONSENT_REQUIRED = "WHATSAPP_CONSENT_REQUIRED"
    const val ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE = "WHATSAPP_AUTOMATION_UNAVAILABLE"
    const val ERROR_WHATSAPP_AUTOMATION_FAILURE = "WHATSAPP_AUTOMATION_FAILURE"
    const val ERROR_EMAIL_AUTHENTICATION_FAILED = "EMAIL_AUTHENTICATION_FAILED"
    const val ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE = "EMAIL_TRANSIENT_PROVIDER_FAILURE"

    private const val CODE_ANDROID_SEND_SMS_PERMISSION = "ANDROID_SEND_SMS_PERMISSION"
    private const val CODE_WHATSAPP_CONSENT_NOT_GRANTED = "APP_CONSENT_NOT_GRANTED"
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

    private fun WhatsAppSendFailureReason.providerErrorType(): String {
        return when (this) {
            WhatsAppSendFailureReason.SERVICE_DISABLED,
            WhatsAppSendFailureReason.APP_NOT_FOUND -> ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE
            WhatsAppSendFailureReason.DEVICE_LOCKED,
            WhatsAppSendFailureReason.CHAT_OPEN_TIMEOUT,
            WhatsAppSendFailureReason.COMPOSE_FIELD_NOT_FOUND,
            WhatsAppSendFailureReason.TEXT_VERIFICATION_FAILED,
            WhatsAppSendFailureReason.SEND_BUTTON_NOT_FOUND,
            WhatsAppSendFailureReason.SEND_CONFIRMATION_TIMEOUT -> ERROR_WHATSAPP_AUTOMATION_FAILURE
        }
    }

    private fun WhatsAppSendFailureReason.providerErrorCode(): String {
        return when (this) {
            WhatsAppSendFailureReason.SERVICE_DISABLED -> CODE_ACCESSIBILITY_AUTOMATION_UNAVAILABLE
            WhatsAppSendFailureReason.APP_NOT_FOUND -> "WHATSAPP_APP_NOT_FOUND"
            WhatsAppSendFailureReason.DEVICE_LOCKED -> "DEVICE_LOCKED"
            WhatsAppSendFailureReason.CHAT_OPEN_TIMEOUT -> "CHAT_OPEN_TIMEOUT"
            WhatsAppSendFailureReason.COMPOSE_FIELD_NOT_FOUND -> "COMPOSE_FIELD_NOT_FOUND"
            WhatsAppSendFailureReason.TEXT_VERIFICATION_FAILED -> "TEXT_VERIFICATION_FAILED"
            WhatsAppSendFailureReason.SEND_BUTTON_NOT_FOUND -> "SEND_BUTTON_NOT_FOUND"
            WhatsAppSendFailureReason.SEND_CONFIRMATION_TIMEOUT -> "SEND_CONFIRMATION_TIMEOUT"
        }
    }

    private fun WhatsAppSendFailureReason.redactedProviderMessage(): String {
        return when (this) {
            WhatsAppSendFailureReason.SERVICE_DISABLED ->
                "WhatsApp Accessibility service is disabled; setup must be reviewed before retry."
            WhatsAppSendFailureReason.DEVICE_LOCKED ->
                "Device was locked; WhatsApp automation did not run."
            WhatsAppSendFailureReason.APP_NOT_FOUND ->
                "WhatsApp or WhatsApp Business was not installed or visible to the app."
            WhatsAppSendFailureReason.CHAT_OPEN_TIMEOUT ->
                "WhatsApp chat did not open before the automation timeout."
            WhatsAppSendFailureReason.COMPOSE_FIELD_NOT_FOUND ->
                "WhatsApp compose field could not be found."
            WhatsAppSendFailureReason.TEXT_VERIFICATION_FAILED ->
                "WhatsApp compose text could not be verified before send."
            WhatsAppSendFailureReason.SEND_BUTTON_NOT_FOUND ->
                "WhatsApp send button could not be found."
            WhatsAppSendFailureReason.SEND_CONFIRMATION_TIMEOUT ->
                "WhatsApp send confirmation timed out before delivery handoff."
        }
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
