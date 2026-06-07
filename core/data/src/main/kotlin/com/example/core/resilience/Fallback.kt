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
    private val tag = "Fallback[$name]"

    suspend fun execute(): T {
        for ((index, provider) in providers.withIndex()) {
            try {
                val result = if (index == 0) provider.primary() else provider.fallback()
                if (index > 0) {
                    Log.i(tag, "Used fallback provider $index")
                }
                return result
            } catch (e: Exception) {
                Log.w(tag, "Provider $index failed: ${e.message}")
            }
        }
        throw FallbackExhaustedException(name, providers.size)
    }
}

class FallbackExhaustedException(
    val name: String,
    val providerCount: Int,
) : Exception("All $providerCount fallback providers exhausted for '$name'")
