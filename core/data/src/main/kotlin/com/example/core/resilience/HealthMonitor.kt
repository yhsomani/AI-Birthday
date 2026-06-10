package com.example.core.resilience

import android.util.Log

data class HealthSnapshot(
    val circuitBreakerStates: Map<String, CircuitState> = emptyMap(),
    val deadLetterCount: Int = 0,
    val recentErrors: List<String> = emptyList(),
    val isHealthy: Boolean = true,
)

object HealthMonitor {
    private const val TAG = "HealthMonitor"
    private val circuitBreakers = mutableMapOf<String, CircuitBreaker>()
    private val recentErrors = mutableListOf<String>()
    private const val MAX_RECENT_ERRORS = 50

    fun register(name: String, breaker: CircuitBreaker) {
        circuitBreakers[name] = breaker
    }

    fun recordError(context: String, error: String) {
        val safeContext = SensitiveLogRedactor.redact(context)
        val safeError = SensitiveLogRedactor.redact(error)
        synchronized(recentErrors) {
            recentErrors.add("[$safeContext] $safeError")
            if (recentErrors.size > MAX_RECENT_ERRORS) {
                recentErrors.removeAt(0)
            }
        }
        Log.w(TAG, "Error recorded: [$safeContext] $safeError")
    }

    fun snapshot(): HealthSnapshot {
        val breakerStates = circuitBreakers.mapValues { it.value.currentState() }
        val dlqCount = DeadLetterQueue.count()
        val errors = synchronized(recentErrors) { recentErrors.toList().takeLast(10) }
        val hasOpenBreakers = breakerStates.any { it.value != CircuitState.CLOSED }
        return HealthSnapshot(
            circuitBreakerStates = breakerStates,
            deadLetterCount = dlqCount,
            recentErrors = errors,
            isHealthy = !hasOpenBreakers && dlqCount < 10,
        )
    }

    fun isHealthy(): Boolean = snapshot().isHealthy
}
