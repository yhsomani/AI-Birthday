package com.example.core.resilience

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val successThreshold: Int = 2,
    val openTimeoutMs: Long = 30_000L,
    val halfOpenMaxCalls: Int = 3,
)

class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig(),
) {
    private val mutex = Mutex()
    private var state = CircuitState.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var lastFailureTime = 0L
    private var halfOpenCalls = 0

    suspend fun <T> call(block: suspend () -> T): T {
        if (!canProceed()) {
            Log.w(TAG, "$name: Circuit is $state, call rejected")
            throw CircuitBreakerOpenException(name, state)
        }
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private suspend fun canProceed(): Boolean = mutex.withLock {
        when (state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime >= config.openTimeoutMs) {
                    state = CircuitState.HALF_OPEN
                    halfOpenCalls = 0
                    successCount = 0
                    Log.i(TAG, "$name: Circuit transitioning OPEN -> HALF_OPEN")
                    true
                } else false
            }
            CircuitState.HALF_OPEN -> {
                if (halfOpenCalls < config.halfOpenMaxCalls) {
                    halfOpenCalls++
                    true
                } else false
            }
        }
    }

    private suspend fun onSuccess() = mutex.withLock {
        when (state) {
            CircuitState.CLOSED -> failureCount = 0
            CircuitState.HALF_OPEN -> {
                successCount++
                if (successCount >= config.successThreshold) {
                    state = CircuitState.CLOSED
                    failureCount = 0
                    successCount = 0
                    halfOpenCalls = 0
                    Log.i(TAG, "$name: Circuit recovered HALF_OPEN -> CLOSED")
                }
            }
            CircuitState.OPEN -> { }
        }
    }

    private suspend fun onFailure() = mutex.withLock {
        when (state) {
            CircuitState.CLOSED -> {
                failureCount++
                if (failureCount >= config.failureThreshold) {
                    state = CircuitState.OPEN
                    lastFailureTime = System.currentTimeMillis()
                    Log.w(TAG, "$name: Circuit tripped CLOSED -> OPEN (failures=$failureCount)")
                }
            }
            CircuitState.HALF_OPEN -> {
                state = CircuitState.OPEN
                lastFailureTime = System.currentTimeMillis()
                halfOpenCalls = 0
                successCount = 0
                Log.w(TAG, "$name: Circuit re-opened HALF_OPEN -> OPEN")
            }
            CircuitState.OPEN -> {
                lastFailureTime = System.currentTimeMillis()
            }
        }
    }

    fun currentState(): CircuitState = state

    companion object {
        private const val TAG = "CircuitBreaker"
    }
}

class CircuitBreakerOpenException(
    val breakerName: String,
    val state: CircuitState,
) : Exception("Circuit breaker '$breakerName' is $state")
