package com.example.domain.usecase

import com.example.domain.service.PreferencesRepository
import com.example.domain.service.TestSendService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestSendUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val testSendService: TestSendService,
) {
    suspend operator fun invoke(messageText: String): Outcome {
        val cleaned = messageText.trim()
        if (cleaned.isBlank()) return Outcome.BlankMessage
        if (
            preferencesRepository.getSenderEmail().isBlank() ||
            preferencesRepository.getSenderEmailPassword().isBlank()
        ) {
            return Outcome.MissingEmailSetup
        }

        return try {
            testSendService.sendEmailToSelf(cleaned)
            Outcome.Sent
        } catch (_: Exception) {
            Outcome.SendFailed
        }
    }

    sealed class Outcome {
        data object Sent : Outcome()
        data object MissingEmailSetup : Outcome()
        data object BlankMessage : Outcome()
        data object SendFailed : Outcome()
    }
}
