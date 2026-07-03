package com.example.domain.automation

enum class GoogleContactsReadinessReason {
    READY,
    ACCESS_MISSING,
}

enum class GeminiAccessReadinessReason {
    API_KEY_CONFIGURED,
    FIREBASE_AUTH_AVAILABLE,
    MISSING_ACCESS,
}

enum class AiWishGenerationReadinessReason {
    ENABLED,
    DISABLED,
}

enum class SetupProviderCircuitState {
    NONE,
    CLOSED,
    HALF_OPEN,
    OPEN,
}

enum class GeminiCircuitReadinessReason {
    NO_STATE,
    CLOSED,
    HALF_OPEN,
    OPEN,
}

data class GoogleContactsReadiness(
    val reason: GoogleContactsReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
)

data class GeminiAccessReadiness(
    val reason: GeminiAccessReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
)

data class AiWishGenerationReadiness(
    val reason: AiWishGenerationReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.REQUIRED,
)

data class GeminiCircuitReadiness(
    val reason: GeminiCircuitReadinessReason,
    val status: SetupReadinessStatus,
    val group: SetupReadinessGroup = SetupReadinessGroup.RELIABILITY,
    val circuitState: SetupProviderCircuitState = SetupProviderCircuitState.NONE,
)

object SetupAccountProviderReadinessPolicy {
    fun evaluateGoogleContacts(
        hasGoogleContactsAccess: Boolean,
    ): GoogleContactsReadiness {
        return if (hasGoogleContactsAccess) {
            GoogleContactsReadiness(
                reason = GoogleContactsReadinessReason.READY,
                status = SetupReadinessStatus.OK,
            )
        } else {
            GoogleContactsReadiness(
                reason = GoogleContactsReadinessReason.ACCESS_MISSING,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
        }
    }

    fun evaluateGeminiAccess(
        hasGeminiApiKey: Boolean,
        hasFirebaseAuth: Boolean,
    ): GeminiAccessReadiness {
        return when {
            hasGeminiApiKey -> GeminiAccessReadiness(
                reason = GeminiAccessReadinessReason.API_KEY_CONFIGURED,
                status = SetupReadinessStatus.OK,
            )
            hasFirebaseAuth -> GeminiAccessReadiness(
                reason = GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE,
                status = SetupReadinessStatus.OK,
            )
            else -> GeminiAccessReadiness(
                reason = GeminiAccessReadinessReason.MISSING_ACCESS,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
        }
    }

    fun evaluateAiWishGeneration(
        aiWishGenerationEnabled: Boolean,
    ): AiWishGenerationReadiness {
        return if (aiWishGenerationEnabled) {
            AiWishGenerationReadiness(
                reason = AiWishGenerationReadinessReason.ENABLED,
                status = SetupReadinessStatus.OK,
            )
        } else {
            AiWishGenerationReadiness(
                reason = AiWishGenerationReadinessReason.DISABLED,
                status = SetupReadinessStatus.ACTION_REQUIRED,
            )
        }
    }

    fun evaluateGeminiCircuit(
        circuitState: SetupProviderCircuitState,
    ): GeminiCircuitReadiness {
        return when (circuitState) {
            SetupProviderCircuitState.NONE -> GeminiCircuitReadiness(
                reason = GeminiCircuitReadinessReason.NO_STATE,
                status = SetupReadinessStatus.OK,
                circuitState = circuitState,
            )
            SetupProviderCircuitState.CLOSED -> GeminiCircuitReadiness(
                reason = GeminiCircuitReadinessReason.CLOSED,
                status = SetupReadinessStatus.OK,
                circuitState = circuitState,
            )
            SetupProviderCircuitState.HALF_OPEN -> GeminiCircuitReadiness(
                reason = GeminiCircuitReadinessReason.HALF_OPEN,
                status = SetupReadinessStatus.WARNING,
                circuitState = circuitState,
            )
            SetupProviderCircuitState.OPEN -> GeminiCircuitReadiness(
                reason = GeminiCircuitReadinessReason.OPEN,
                status = SetupReadinessStatus.ACTION_REQUIRED,
                circuitState = circuitState,
            )
        }
    }
}
