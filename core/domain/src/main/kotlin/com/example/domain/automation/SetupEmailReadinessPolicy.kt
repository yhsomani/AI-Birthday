package com.example.domain.automation

enum class SetupEmailReadinessReason {
    INVALID_SENDER,
    VERIFIED,
    UNVERIFIED,
    MISSING_FOR_CONTACTS,
    OPTIONAL,
}

data class SetupEmailReadiness(
    val reason: SetupEmailReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
    val emailPreferredContactCount: Int = 0,
)

object SetupEmailReadinessPolicy {
    fun isSenderReady(
        senderEmail: String,
        senderEmailPassword: String,
    ): Boolean {
        return senderEmail.trim().isNotBlank() &&
            senderEmailPassword.trim().isNotBlank() &&
            EmailAddressSyntaxPolicy.isUsableAddress(senderEmail)
    }

    fun evaluate(
        senderEmail: String,
        senderEmailPassword: String,
        emailSelfTestVerified: Boolean,
        emailPreferredContactCount: Int,
    ): SetupEmailReadiness {
        val senderSyntaxValid = senderEmail.trim().isBlank() ||
            EmailAddressSyntaxPolicy.isUsableAddress(senderEmail)
        val senderReady = isSenderReady(
            senderEmail = senderEmail,
            senderEmailPassword = senderEmailPassword,
        )

        return when {
            !senderSyntaxValid -> SetupEmailReadiness(
                reason = SetupEmailReadinessReason.INVALID_SENDER,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
            senderReady && emailSelfTestVerified -> SetupEmailReadiness(
                reason = SetupEmailReadinessReason.VERIFIED,
                status = SetupReadinessStatus.OK,
            )
            senderReady -> SetupEmailReadiness(
                reason = SetupEmailReadinessReason.UNVERIFIED,
                status = SetupReadinessStatus.WARNING,
            )
            emailPreferredContactCount > 0 -> SetupEmailReadiness(
                reason = SetupEmailReadinessReason.MISSING_FOR_CONTACTS,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                emailPreferredContactCount = emailPreferredContactCount,
            )
            else -> SetupEmailReadiness(
                reason = SetupEmailReadinessReason.OPTIONAL,
                status = SetupReadinessStatus.WARNING,
            )
        }
    }
}
