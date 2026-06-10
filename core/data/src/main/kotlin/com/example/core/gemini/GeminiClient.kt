package com.example.core.gemini

import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.CircuitBreaker
import com.example.core.resilience.CircuitBreakerConfig
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.Retry
import com.example.core.resilience.RetryConfig
import com.example.core.resilience.StructuredLogger
import com.google.firebase.vertexai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val securePrefs: SecurePrefs
) {
    private val circuitBreaker = CircuitBreaker("gemini", CircuitBreakerConfig(
        failureThreshold = 5,
        openTimeoutMs = 60_000L,
    ))

    private var googleAiModel: com.google.ai.client.generativeai.GenerativeModel? = null
    private var googleAiModelApiKey: String? = null

    init {
        HealthMonitor.register("gemini", circuitBreaker)
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = securePrefs.getGeminiApiKey()
            val response = circuitBreaker.call {
                Retry.withExponentialBackoff(
                    config = RetryConfig(maxRetries = 2),
                    description = "gemini.generate",
                ) {
                    try {
                        if (apiKey.isNotEmpty()) {
                            StructuredLogger.d(TAG, "Using user-provided API key (Google AI SDK)")
                            generateWithGoogleKey(prompt, apiKey)
                        } else {
                            StructuredLogger.d(TAG, "Using Firebase Vertex AI (no user API key)")
                            generativeModel.generateContent(prompt).text
                        }
                    } catch (e: Exception) {
                        val errMsg = e.message ?: ""
                        if (errMsg.contains("quota", ignoreCase = true) || errMsg.contains("429") || errMsg.contains("exhausted", ignoreCase = true)) {
                            StructuredLogger.w(TAG, "Gemini 429 / resource exhausted detected. Delaying for 60s before retry.")
                            kotlinx.coroutines.delay(60_000L)
                        }
                        throw e
                    }
                }
            }
            response ?: """{ "error": "Empty response" }"""
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Unknown error"
            StructuredLogger.e(TAG, "AI generation failure", e)
            HealthMonitor.recordError("GeminiClient.generate", msg)
            """{ "error": "AI generation failure: $msg" }"""
        }
    }

    private suspend fun generateWithGoogleKey(prompt: String, apiKey: String): String? {
        val cachedModel = googleAiModel
        val model = if (cachedModel == null || googleAiModelApiKey != apiKey) {
            com.google.ai.client.generativeai.GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            ).also {
                googleAiModel = it
                googleAiModelApiKey = apiKey
            }
        } else {
            cachedModel
        }
        return model.generateContent(prompt).text
    }

    companion object {
        private const val TAG = "GeminiClient"
    }
}
