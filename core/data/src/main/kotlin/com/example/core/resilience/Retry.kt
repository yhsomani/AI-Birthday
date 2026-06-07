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
        var lastException: Exception? = null
        for (attempt in 0..config.maxRetries) {
            try {
                return block()
            } catch (e: CircuitBreakerOpenException) {
                Log.w(TAG, "$description: Circuit breaker open, not retrying")
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxRetries) {
                    val delayMs = calculateDelay(attempt, config)
                    Log.w(TAG, "$description: Attempt ${attempt + 1} failed, retrying in ${delayMs}ms: ${e.message}")
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
            Log.e(TAG, "$description: All retries exhausted", e.cause)
            null
        } catch (e: Exception) {
            Log.e(TAG, "$description: Non-retryable failure", e)
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
