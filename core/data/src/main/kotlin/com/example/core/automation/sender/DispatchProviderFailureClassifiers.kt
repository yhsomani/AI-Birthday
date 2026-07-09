package com.example.core.automation.sender

import com.example.core.accessibility.WhatsAppSendFailureReason
import javax.mail.MessagingException

internal const val CODE_ANDROID_SEND_SMS_PERMISSION = "ANDROID_SEND_SMS_PERMISSION"
internal const val CODE_WHATSAPP_CONSENT_NOT_GRANTED = "APP_CONSENT_NOT_GRANTED"
internal const val CODE_ACCESSIBILITY_AUTOMATION_UNAVAILABLE = "ACCESSIBILITY_AUTOMATION_UNAVAILABLE"
internal const val CODE_SMTP_AUTHENTICATION_FAILED = "SMTP_AUTHENTICATION_FAILED"

internal fun Throwable.providerCode(): String {
    return this.findProviderCause { it !is MessagingException }?.javaClass?.simpleName
        ?: javaClass.simpleName
        ?: javaClass.name.substringAfterLast('.')
}

internal fun WhatsAppSendFailureReason.providerErrorType(): String {
    return when (this) {
        WhatsAppSendFailureReason.SERVICE_DISABLED,
        WhatsAppSendFailureReason.INVALID_PHONE_NUMBER,
        WhatsAppSendFailureReason.APP_NOT_FOUND -> DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE
        WhatsAppSendFailureReason.DEVICE_LOCKED,
        WhatsAppSendFailureReason.CHAT_OPEN_TIMEOUT,
        WhatsAppSendFailureReason.COMPOSE_FIELD_NOT_FOUND,
        WhatsAppSendFailureReason.TEXT_VERIFICATION_FAILED,
        WhatsAppSendFailureReason.SEND_BUTTON_NOT_FOUND,
        WhatsAppSendFailureReason.SEND_CONFIRMATION_TIMEOUT,
        WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT -> DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_FAILURE
    }
}

internal fun WhatsAppSendFailureReason.providerErrorCode(): String {
    return when (this) {
        WhatsAppSendFailureReason.SERVICE_DISABLED -> CODE_ACCESSIBILITY_AUTOMATION_UNAVAILABLE
        WhatsAppSendFailureReason.INVALID_PHONE_NUMBER -> "WHATSAPP_INVALID_PHONE_NUMBER"
        WhatsAppSendFailureReason.APP_NOT_FOUND -> "WHATSAPP_APP_NOT_FOUND"
        WhatsAppSendFailureReason.DEVICE_LOCKED -> "DEVICE_LOCKED"
        WhatsAppSendFailureReason.CHAT_OPEN_TIMEOUT -> "CHAT_OPEN_TIMEOUT"
        WhatsAppSendFailureReason.COMPOSE_FIELD_NOT_FOUND -> "COMPOSE_FIELD_NOT_FOUND"
        WhatsAppSendFailureReason.TEXT_VERIFICATION_FAILED -> "TEXT_VERIFICATION_FAILED"
        WhatsAppSendFailureReason.SEND_BUTTON_NOT_FOUND -> "SEND_BUTTON_NOT_FOUND"
        WhatsAppSendFailureReason.SEND_CONFIRMATION_TIMEOUT -> "SEND_CONFIRMATION_TIMEOUT"
        WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT -> "SENDER_CALLBACK_TIMEOUT"
    }
}

internal fun WhatsAppSendFailureReason.redactedProviderMessage(): String {
    return when (this) {
        WhatsAppSendFailureReason.SERVICE_DISABLED ->
            "WhatsApp Accessibility service is disabled; setup must be reviewed before retry."
        WhatsAppSendFailureReason.INVALID_PHONE_NUMBER ->
            "Contact phone number is not usable for WhatsApp automation."
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
        WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT ->
            "WhatsApp automation did not complete before the sender watchdog timeout."
    }
}

internal inline fun <reified T : Throwable> Throwable.containsProviderCause(): Boolean {
    return findProviderCause { it is T } != null
}

internal fun Throwable.findProviderCause(predicate: (Throwable) -> Boolean): Throwable? {
    if (predicate(this)) return this
    cause?.findProviderCause(predicate)?.let { return it }
    return (this as? MessagingException)?.nextException?.findProviderCause(predicate)
}
