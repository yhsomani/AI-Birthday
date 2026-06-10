package com.example.core.resilience

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ResiliencePrimitivesTest {

    @Test
    fun retryWithExponentialBackoff_retriesUntilSuccess() = runTest {
        var attempts = 0

        val result = Retry.withExponentialBackoff(
            config = RetryConfig(maxRetries = 2, baseDelayMs = 0),
            description = "token=secret",
        ) {
            attempts++
            if (attempts < 3) {
                throw IllegalStateException("apiKey=secret")
            }
            "sent"
        }

        assertEquals("sent", result)
        assertEquals(3, attempts)
    }

    @Test
    fun retryWithExponentialBackoff_throwsRetryExhaustedWithLastCause() = runTest {
        var attempts = 0

        val result = runCatching {
            Retry.withExponentialBackoff(
                config = RetryConfig(maxRetries = 1, baseDelayMs = 0),
                description = "send message",
            ) {
                attempts++
                throw IllegalArgumentException("temporary failure")
            }
        }

        val exception = result.exceptionOrNull()
        assertTrue(exception is RetryExhaustedException)
        val exhausted = exception as RetryExhaustedException
        assertEquals("send message", exhausted.description)
        assertEquals(1, exhausted.maxRetries)
        assertTrue(exhausted.cause is IllegalArgumentException)
        assertEquals(2, attempts)
    }

    @Test
    fun retryWithExponentialBackoff_doesNotRetryOpenCircuit() = runTest {
        val openCircuit = CircuitBreakerOpenException("messages", CircuitState.OPEN)
        var attempts = 0

        val result = runCatching {
            Retry.withExponentialBackoff(
                config = RetryConfig(maxRetries = 3, baseDelayMs = 0),
                description = "dispatch",
            ) {
                attempts++
                throw openCircuit
            }
        }

        assertSame(openCircuit, result.exceptionOrNull())
        assertEquals(1, attempts)
    }

    @Test
    fun retryWithExponentialBackoffOrNull_returnsNullAfterExhaustion() = runTest {
        val result = Retry.withExponentialBackoffOrNull(
            config = RetryConfig(maxRetries = 0, baseDelayMs = 0),
            description = "generate",
        ) {
            throw IllegalStateException("provider unavailable")
        }

        assertNull(result)
    }

    @Test
    fun circuitBreaker_opensAfterThresholdAndRecoversAfterHalfOpenSuccesses() = runTest {
        val breaker = CircuitBreaker(
            name = "sync",
            config = CircuitBreakerConfig(
                failureThreshold = 2,
                successThreshold = 2,
                openTimeoutMs = 0,
                halfOpenMaxCalls = 2,
            ),
        )

        repeat(2) {
            runCatching {
                breaker.call {
                    throw IllegalStateException("failure")
                }
            }
        }

        assertEquals(CircuitState.OPEN, breaker.currentState())
        assertEquals("first recovery", breaker.call { "first recovery" })
        assertEquals(CircuitState.HALF_OPEN, breaker.currentState())
        assertEquals("second recovery", breaker.call { "second recovery" })
        assertEquals(CircuitState.CLOSED, breaker.currentState())
    }

    @Test
    fun circuitBreaker_reopensWhenHalfOpenCallFails() = runTest {
        val breaker = CircuitBreaker(
            name = "sync",
            config = CircuitBreakerConfig(
                failureThreshold = 1,
                openTimeoutMs = 0,
            ),
        )

        runCatching {
            breaker.call {
                throw IllegalStateException("initial failure")
            }
        }

        assertEquals(CircuitState.OPEN, breaker.currentState())

        runCatching {
            breaker.call {
                throw IllegalStateException("still failing")
            }
        }

        assertEquals(CircuitState.OPEN, breaker.currentState())
    }
}
