package com.example.core.resilience

import android.util.Log

data class DeadLetterEntry(
    val id: String,
    val payload: String,
    val errorMessage: String,
    val errorType: String,
    val retryCount: Int,
    val timestampMs: Long = System.currentTimeMillis(),
)

object DeadLetterQueue {
    private const val TAG = "DeadLetterQueue"
    private val entries = mutableListOf<DeadLetterEntry>()

    fun enqueue(entry: DeadLetterEntry) {
        synchronized(entries) {
            entries.add(entry)
        }
        Log.w(TAG, "DLQ: Enqueued ${entry.id} (${entry.errorType}) after ${entry.retryCount} retries")
    }

    fun dequeue(id: String): DeadLetterEntry? {
        synchronized(entries) {
            return entries.find { it.id == id }?.also { entries.remove(it) }
        }
    }

    fun getAll(): List<DeadLetterEntry> {
        synchronized(entries) {
            return entries.toList()
        }
    }

    fun count(): Int {
        synchronized(entries) {
            return entries.size
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }
}
