package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.data.R
import com.example.core.resilience.StructuredLogger
import com.example.domain.repository.EventRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.GenerateMessageUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@HiltWorker
class MessageGenerationWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val eventRepository: EventRepository,
    private val generateMessageUseCase: GenerateMessageUseCase,
    private val preferencesRepository: PreferencesRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!preferencesRepository.isAiWishGenerationEnabled()) {
            StructuredLogger.i(TAG, "AI wish generation disabled; skipping worker")
            return Result.success()
        }

        val apiKey = preferencesRepository.getGeminiApiKey()
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (apiKey.isNullOrBlank() && firebaseUser == null) {
            StructuredLogger.w(TAG, "Gemini API key not configured and user not authenticated; skipping worker")
            NotificationHelper.showSetupNotification(
                applicationContext,
                applicationContext.getString(R.string.notification_setup_ai_title),
                applicationContext.getString(R.string.notification_setup_ai_message),
            )
            return Result.failure()
        }

        return try {
            // Prepare a week of AI drafts so Smart Approve has a useful review window
            // while exact dispatch still happens at each contact's scheduled send time.
            val lookaheadEndMs = System.currentTimeMillis() + MESSAGE_GENERATION_LOOKAHEAD_MS

            val upcomingEvents = eventRepository.getEventsBefore(lookaheadEndMs)
            StructuredLogger.i(TAG, "Found ${upcomingEvents.size} upcoming events for generation")

            coroutineScope {
                val deferredList = upcomingEvents.map { event ->
                    async {
                        try {
                            val outcome = generateMessageUseCase(
                                GenerateMessageUseCase.Request(
                                    eventId = event.id,
                                    regenerateFailedOccurrence = true
                                )
                            )
                            when (outcome) {
                                GenerateMessageUseCase.GenerationOutcome.AiDisabled -> {
                                    StructuredLogger.i(TAG, "AI wish generation disabled while processing event ${event.id}; skipping")
                                }
                                GenerateMessageUseCase.GenerationOutcome.AlreadyExists -> {
                                    StructuredLogger.i(TAG, "Message already queued or processed for event ${event.id}; skipping")
                                }
                                GenerateMessageUseCase.GenerationOutcome.ContactNotFound -> {
                                    StructuredLogger.w(TAG, "Contact missing for event ${event.id}; skipping")
                                }
                                GenerateMessageUseCase.GenerationOutcome.EventNotFound -> {
                                    StructuredLogger.w(TAG, "Event ${event.id} no longer exists; skipping")
                                }
                                is GenerateMessageUseCase.GenerationOutcome.Generated -> {
                                    StructuredLogger.i(TAG, "Generated message for event ${event.id}", mapOf(
                                        "pendingId" to outcome.pendingId,
                                        "approvalMode" to outcome.approvalMode.raw,
                                        "retries" to outcome.retries.toString(),
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            StructuredLogger.w(TAG, "Failed to generate message for event ${event.id}", e)
                        }
                    }
                }
                deferredList.awaitAll()
            }

            Result.success()
        } catch (e: Exception) {
            StructuredLogger.w(TAG, "doWork failed; will retry with backoff", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "MessageGenerationWorker"
        const val MESSAGE_GENERATION_LOOKAHEAD_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
