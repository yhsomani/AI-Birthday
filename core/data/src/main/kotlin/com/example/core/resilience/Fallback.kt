package com.example.core.resilience

import android.util.Log

interface FallbackProvider<T> {
    suspend fun primary(): T
    suspend fun fallback(): T
}

class FallbackOrchestrator<T>(
    private val providers: List<FallbackProvider<T>>,
    private val name: String = "fallback",
) {
    private val tag = "Fallback[${SensitiveLogRedactor.redact(name)}]"

    suspend fun execute(): T {
        for ((index, provider) in providers.withIndex()) {
            try {
                val result = if (index == 0) provider.primary() else provider.fallback()
                if (index > 0) {
                    Log.i(tag, "Used fallback provider $index")
                }
                return result
            } catch (e: Exception) {
                val safeMessage = SensitiveLogRedactor.redact(e.message ?: e.javaClass.simpleName)
                Log.w(tag, "Provider $index failed: $safeMessage")
            }
        }
        throw FallbackExhaustedException(name, providers.size)
    }
}

class FallbackExhaustedException(
    val name: String,
    val providerCount: Int,
) : Exception("All $providerCount fallback providers exhausted for '$name'")
