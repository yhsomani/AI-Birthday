package com.example.core.resilience

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow

data class RetryConfig(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30_000L,
    val multiplier: Double = 2.0,
)

object Retry {

    private const val TAG = "Retry"

    suspend fun <T> withExponentialBackoff(
        config: RetryConfig = RetryConfig(),
        description: String = "operation",
        block: suspend () -> T,
    ): T {
        val safeDescription = SensitiveLogRedactor.redact(description)
        var lastException: Exception? = null
        for (attempt in 0..config.maxRetries) {
            try {
                return block()
            } catch (e: CircuitBreakerOpenException) {
                Log.w(TAG, "$safeDescription: Circuit breaker open, not retrying")
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxRetries) {
                    val delayMs = calculateDelay(attempt, config)
                    val safeMessage = SensitiveLogRedactor.redact(e.message ?: e.javaClass.simpleName)
                    Log.w(TAG, "$safeDescription: Attempt ${attempt + 1} failed, retrying in ${delayMs}ms: $safeMessage")
                    delay(delayMs)
                }
            }
        }
        throw RetryExhaustedException(description, config.maxRetries, lastException)
    }

    suspend fun <T> withExponentialBackoffOrNull(
        config: RetryConfig = RetryConfig(),
        description: String = "operation",
        block: suspend () -> T,
    ): T? {
        return try {
            withExponentialBackoff(config, description, block)
        } catch (e: RetryExhaustedException) {
            val safeDescription = SensitiveLogRedactor.redact(description)
            val causeName = e.cause?.javaClass?.simpleName ?: "UnknownException"
            Log.e(TAG, "$safeDescription: All retries exhausted ($causeName)")
            null
        } catch (e: Exception) {
            val safeDescription = SensitiveLogRedactor.redact(description)
            Log.e(TAG, "$safeDescription: Non-retryable failure (${e.javaClass.simpleName})")
            null
        }
    }

    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val delay = config.baseDelayMs * config.multiplier.pow(attempt)
        return delay.toLong().coerceAtMost(config.maxDelayMs)
    }
}

class RetryExhaustedException(
    val description: String,
    val maxRetries: Int,
    override val cause: Throwable?,
) : Exception("$description failed after $maxRetries retries", cause)
