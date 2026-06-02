package com.example.core.gemini

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RateLimiter {
    private const val REQUESTS_PER_MINUTE = 60
    private const val WINDOW_MS = 60_000L
    private const val MIN_INTERVAL_MS = WINDOW_MS / REQUESTS_PER_MINUTE

    private val mutex = Mutex()
    private val requestTimestamps = mutableListOf<Long>()

    suspend fun waitIfNeeded() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val windowStart = now - WINDOW_MS

            requestTimestamps.removeAll { it < windowStart }

            if (requestTimestamps.size >= REQUESTS_PER_MINUTE) {
                val oldest = requestTimestamps.first()
                val waitTime = oldest + WINDOW_MS - now + 100
                delay(waitTime.coerceAtLeast(0))
                requestTimestamps.removeAll { it < (System.currentTimeMillis() - WINDOW_MS) }
            }

            val lastRequest = requestTimestamps.lastOrNull()
            if (lastRequest != null) {
                val timeSinceLast = System.currentTimeMillis() - lastRequest
                if (timeSinceLast < MIN_INTERVAL_MS) {
                    delay(MIN_INTERVAL_MS - timeSinceLast)
                }
            }

            requestTimestamps.add(System.currentTimeMillis())
        }
    }
}
